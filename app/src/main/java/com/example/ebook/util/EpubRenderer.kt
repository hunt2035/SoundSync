package com.example.ebook.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipFile

/**
 * EPUB渲染器
 * 负责解析EPUB文件并准备用于WebView渲染的内容
 */
class EpubRenderer(private val context: Context) {
    private val TAG = "EpubRenderer"
    
    // 缓存目录名
    private val CACHE_DIR = "epub_cache"
    
    // 默认样式设置
    private val DEFAULT_FONT_SIZE = 18
    private val DEFAULT_LINE_HEIGHT = 1.6f
    private val DEFAULT_MARGIN = 20
    
    // EPUB文件结构
    data class EpubBook(
        val spine: List<EpubResource>,
        val resources: Map<String, ByteArray>,
        val toc: List<ChapterInfo>
    )
    
    data class EpubResource(
        val href: String,
        val title: String?,
        val data: ByteArray
    )
    
    /**
     * 解析EPUB文件
     * @param filePath EPUB文件路径
     * @return 解析后的EPUB Book对象
     */
    suspend fun parseEpub(filePath: String): Result<EpubBook> = withContext(Dispatchers.IO) {
        try {
            val epubFile = File(filePath)
            val zipFile = ZipFile(epubFile)
            
            // 读取容器文件
            val containerEntry = zipFile.getEntry("META-INF/container.xml")
            if (containerEntry == null) {
                return@withContext Result.failure(IOException("无效的EPUB文件：缺少container.xml"))
            }
            
            val containerContent = String(zipFile.getInputStream(containerEntry).readBytes())
            val rootFilePath = extractRootFilePath(containerContent)
            
            // 读取OPF文件
            val opfEntry = zipFile.getEntry(rootFilePath)
            if (opfEntry == null) {
                return@withContext Result.failure(IOException("无效的EPUB文件：缺少OPF文件"))
            }
            
            val opfContent = String(zipFile.getInputStream(opfEntry).readBytes())
            val opfDoc = Jsoup.parse(opfContent)
            
            // 解析spine
            val spine = parseSpine(opfDoc, zipFile)
            
            // 解析资源
            val resources = parseResources(opfDoc, zipFile)
            
            // 解析目录
            val toc = parseToc(opfDoc, zipFile)
            
            val book = EpubBook(spine, resources, toc)
            
            // 预加载和缓存资源
            preloadResources(book)
            
            Result.success(book)
        } catch (e: Exception) {
            Log.e(TAG, "EPUB文件解析失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 预加载关键资源
     */
    private suspend fun preloadResources(book: EpubBook) = withContext(Dispatchers.IO) {
        val cacheDir = getCacheDir(book)
        cacheDir.mkdirs()
        
        try {
            // 预处理样式表
            book.resources.forEach { (href, data) ->
                val cssFile = File(cacheDir, href.substringAfterLast('/'))
                cssFile.parentFile?.mkdirs()
                cssFile.writeBytes(data)
            }
                
            // 预处理关键图片
            book.resources.values.take(10).forEach { data ->
                val imgFile = File(cacheDir, "image.png")
                imgFile.parentFile?.mkdirs()
                imgFile.writeBytes(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "预加载资源失败", e)
        }
    }
    
    /**
     * 准备EPUB章节用于WebView渲染
     * @param book EPUB Book对象
     * @param chapterIndex 章节索引
     * @return 用于WebView加载的HTML内容
     */
    suspend fun prepareChapterForWebView(book: EpubBook, chapterIndex: Int = 0): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 获取章节资源
            val spine = book.spine
            if (spine.size <= chapterIndex) {
                return@withContext Result.failure(IllegalArgumentException("章节索引超出范围"))
            }
            
            val resource = spine[chapterIndex]
            // 提取章节内容
            val chapterData = resource.data
            var html = String(chapterData, Charsets.UTF_8)
            
            // 处理HTML中的相对路径资源（如CSS、图片）
            html = processHtmlResources(html, book, resource)
            
            // 添加电子书阅读特定增强功能
            html = enhanceReadingExperience(html)
            
            Result.success(html)
        } catch (e: Exception) {
            Log.e(TAG, "准备章节内容失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 提取所有章节标题及结构
     * @param book EPUB Book对象
     * @return 章节标题列表和层级信息
     */
    fun extractChapterTitles(book: EpubBook): List<ChapterInfo> {
        return book.toc
    }
    
    /**
     * 获取书籍的总章节数
     * @param book EPUB Book对象
     * @return 章节总数
     */
    fun getTotalChapters(book: EpubBook): Int {
        return book.spine.size
    }
    
    /**
     * 处理HTML中的资源引用，包括CSS和图片
     */
    private fun processHtmlResources(html: String, book: EpubBook, currentResource: EpubResource): String {
        var processedHtml = html
        
        // 创建用于缓存资源的目录
        val cacheDir = getCacheDir(book)
        cacheDir.mkdirs()
        
        // 处理CSS引用
        processedHtml = processCssReferences(processedHtml, book, currentResource, cacheDir)
        
        // 处理图片引用
        processedHtml = processImageReferences(processedHtml, book, currentResource, cacheDir)
        
        // 添加响应式视图支持
        processedHtml = addResponsiveViewport(processedHtml)
        
        // 添加自定义样式使内容更美观
        processedHtml = addCustomStyles(processedHtml)
        
        return processedHtml
    }
    
    /**
     * 处理CSS引用
     */
    private fun processCssReferences(html: String, book: EpubBook, currentResource: EpubResource, cacheDir: File): String {
        var processedHtml = html
        
        // 找出所有CSS引用
        val cssPattern = "<link[^>]*href=[\"']([^\"']+\\.css)[\"'][^>]*>".toRegex()
        val cssMatches = cssPattern.findAll(html)
        
        for (match in cssMatches) {
            val cssPath = match.groupValues[1]
            
            try {
                // 解析CSS路径并获取资源
                val cssResource = findResourceByHref(book, cssPath, currentResource)
                if (cssResource != null) {
                    // 将CSS文件保存到缓存目录
                    val cssFile = File(cacheDir, cssPath.substringAfterLast('/'))
                    cssFile.parentFile?.mkdirs()
                    cssFile.writeBytes(cssResource.data)
                    
                    // 替换原始引用为缓存目录中的文件
                    val newCssPath = "file://${cssFile.absolutePath}"
                    processedHtml = processedHtml.replace(cssPath, newCssPath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理CSS引用失败: $cssPath", e)
            }
        }
        
        return processedHtml
    }
    
    /**
     * 处理图片引用
     */
    private fun processImageReferences(html: String, book: EpubBook, currentResource: EpubResource, cacheDir: File): String {
        var processedHtml = html
        
        // 找出所有图片引用
        val imgPattern = "<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>".toRegex()
        val imgMatches = imgPattern.findAll(html)
        
        for (match in imgMatches) {
            val imgPath = match.groupValues[1]
            
            try {
                // 解析图片路径并获取资源
                val imgResource = findResourceByHref(book, imgPath, currentResource)
                if (imgResource != null) {
                    // 将图片文件保存到缓存目录
                    val imgFileName = imgPath.substringAfterLast('/')
                    val imgFile = File(cacheDir, imgFileName)
                    imgFile.parentFile?.mkdirs()
                    imgFile.writeBytes(imgResource.data)
                    
                    // 替换原始引用为缓存目录中的文件
                    val newImgPath = "file://${imgFile.absolutePath}"
                    processedHtml = processedHtml.replace(imgPath, newImgPath)
                    
                    // 增强图片标签，使其响应式且居中
                    processedHtml = processedHtml.replace(
                        "<img[^>]*src=[\"']${imgPath.replace("/", "\\/")}[\"'][^>]*>".toRegex(),
                        "<img src=\"$newImgPath\" style=\"max-width:100%;height:auto;margin:0 auto;display:block;\">"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理图片引用失败: $imgPath", e)
            }
        }
        
        return processedHtml
    }
    
    /**
     * 增强阅读体验，添加更多专业阅读器功能
     */
    private fun enhanceReadingExperience(html: String): String {
        var enhancedHtml = html
        
        // 添加分页支持
        enhancedHtml = addPagingSupport(enhancedHtml)
        
        // 添加点击区域控制
        enhancedHtml = addTapZoneController(enhancedHtml)
        
        // 提升排版质量
        enhancedHtml = improveTypography(enhancedHtml)
        
        return enhancedHtml
    }
    
    /**
     * 添加分页支持
     */
    private fun addPagingSupport(html: String): String {
        // 添加分页所需的JS代码
        val pagingScript = """
        <script>
        document.addEventListener('DOMContentLoaded', function() {
            // 分页相关变量
            window.currentPage = 0;
            window.totalPages = 0;
            window.pageWidth = 0;
            window.contentElement = document.body;
            
            // 初始化分页
            function initPaging() {
                // 设置文档整体样式
                document.documentElement.style.height = '100%';
                document.documentElement.style.margin = '0';
                document.documentElement.style.padding = '0';
                document.body.style.height = '100%';
                document.body.style.margin = '0';
                document.body.style.padding = '16px';
                document.body.style.boxSizing = 'border-box';
                document.body.style.overflowX = 'hidden';
                
                // 设置内容容器样式 - 使用CSS列布局实现分页
                contentElement.style.columnWidth = window.innerWidth + 'px';
                contentElement.style.columnGap = '40px';
                contentElement.style.columnFill = 'auto';
                contentElement.style.height = window.innerHeight + 'px';
                contentElement.style.width = '100%';
                contentElement.style.margin = '0';
                contentElement.style.padding = '0';
                
                // 防止内容溢出屏幕
                var allElements = document.querySelectorAll('*');
                for (var i = 0; i < allElements.length; i++) {
                    var element = allElements[i];
                    element.style.maxWidth = '100%';
                    element.style.boxSizing = 'border-box';
                    
                    // 确保图片适配屏幕
                    if (element.tagName.toLowerCase() === 'img') {
                        element.style.maxWidth = '100%';
                        element.style.height = 'auto';
                        element.style.display = 'block';
                        element.style.margin = '1em auto';
                    }
                }
                
                // 设置文本样式
                contentElement.style.textAlign = 'justify';
                contentElement.style.lineHeight = '1.6';
                contentElement.style.textRendering = 'optimizeLegibility';
                
                // 重新计算总页数
                window.pageWidth = window.innerWidth;
                window.totalPages = Math.ceil(contentElement.scrollWidth / window.pageWidth);
                
                // 通知应用总页数
                if (window.EpubReader) {
                    window.EpubReader.onTotalPagesCalculated(window.totalPages);
                }
                
                // 更新当前页面
                updateCurrentPage();
            }
            
            // 更新当前页面
            function updateCurrentPage() {
                window.scrollTo(window.pageWidth * window.currentPage, 0);
                if (window.EpubReader) {
                    window.EpubReader.onPageChanged(window.currentPage, window.totalPages);
                }
            }
            
            // 添加滑动检测
            let startX = 0;
            let startY = 0;
            let distX = 0;
            let distY = 0;
            const threshold = 50; // 判定为滑动的最小距离
            
            document.addEventListener('touchstart', function(e) {
                startX = e.touches[0].clientX;
                startY = e.touches[0].clientY;
            });
            
            document.addEventListener('touchmove', function(e) {
                distX = e.touches[0].clientX - startX;
                distY = e.touches[0].clientY - startY;
            });
            
            document.addEventListener('touchend', function(e) {
                // 如果水平移动距离大于垂直移动距离，且超过阈值，认为是水平滑动
                if (Math.abs(distX) > Math.abs(distY) && Math.abs(distX) > threshold) {
                    if (distX > 0) {
                        // 向右滑动 - 上一页
                        previousPage();
                    } else {
                        // 向左滑动 - 下一页
                        nextPage();
                    }
                }
                distX = 0;
                distY = 0;
            });
            
            // 翻页函数
            window.nextPage = function() {
                if (window.currentPage < window.totalPages - 1) {
                    window.currentPage++;
                    updateCurrentPage();
                } else if (window.EpubReader) {
                    window.EpubReader.onLastPage();
                }
            };
            
            window.previousPage = function() {
                if (window.currentPage > 0) {
                    window.currentPage--;
                    updateCurrentPage();
                } else if (window.EpubReader) {
                    window.EpubReader.onFirstPage();
                }
            };
            
            // 窗口大小改变时重新计算
            window.addEventListener('resize', initPaging);
            
            // 初始化
            initPaging();
        });
        </script>
        """
        
        return html.replace("</body>", "$pagingScript</body>")
    }
    
    /**
     * 添加点击区域控制
     */
    private fun addTapZoneController(html: String): String {
        val tapScript = """
        <script>
        document.addEventListener('DOMContentLoaded', function() {
            // 添加点击事件监听
            document.body.addEventListener('click', function(e) {
                var screenWidth = window.innerWidth;
                var screenHeight = window.innerHeight;
                var x = e.clientX;
                var y = e.clientY;
                
                // 定义中间区域的边界
                var leftBound = screenWidth * 0.25;
                var rightBound = screenWidth * 0.75;
                var topBound = screenHeight * 0.33;
                var bottomBound = screenHeight * 0.67;
                
                // 判断点击的区域
                if (x < leftBound) {
                    // 左侧区域 - 上一页
                    if (window.EpubReader) {
                        window.EpubReader.onLeftTap();
                    } else {
                        window.previousPage();
                    }
                } else if (x > rightBound) {
                    // 右侧区域 - 下一页
                    if (window.EpubReader) {
                        window.EpubReader.onRightTap();
                    } else {
                        window.nextPage();
                    }
                } else if (y > topBound && y < bottomBound) {
                    // 中间区域 - 显示控制
                    if (window.EpubReader) {
                        window.EpubReader.onCenterTap();
                    }
                }
            });
        });
        </script>
        """
        
        return html.replace("</body>", "$tapScript</body>")
    }
    
    /**
     * 提升排版质量
     */
    private fun improveTypography(html: String): String {
        // 改进连字符和标点符号处理
        var improvedHtml = html
        
        // 替换连续的空格
        improvedHtml = improvedHtml.replace("\\s{2,}".toRegex(), " ")
        
        // 处理中文引号
        improvedHtml = improvedHtml.replace(""", "<span class=\"quote\">\"</span>")
        improvedHtml = improvedHtml.replace(""", "<span class=\"quote\">\"</span>")
        
        return improvedHtml
    }
    
    /**
     * 添加响应式视图支持
     */
    private fun addResponsiveViewport(html: String): String {
        // 检查是否已经有viewport meta标签
        if (html.contains("<meta name=\"viewport\"")) {
            return html
        }
        
        // 添加viewport meta标签使内容响应式
        val viewportMeta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes\">"
        return html.replace("<head>", "<head>\n    $viewportMeta")
    }
    
    /**
     * 添加自定义样式
     */
    private fun addCustomStyles(html: String): String {
        val customStyles = """
            <style>
                :root {
                    --font-size: ${DEFAULT_FONT_SIZE}px;
                    --line-height: ${DEFAULT_LINE_HEIGHT};
                    --text-color: #333333;
                    --bg-color: #FFFFFF;
                    --link-color: #1565C0;
                    --padding: ${DEFAULT_MARGIN}px;
                }
                
                @font-face {
                    font-family: 'CustomSerif';
                    src: local('Noto Serif CJK SC'), local('Source Han Serif CN'), local('Georgia');
                }
                
                @font-face {
                    font-family: 'CustomSans';
                    src: local('Noto Sans CJK SC'), local('Source Han Sans CN'), local('Roboto');
                }
                
                body {
                    padding: var(--padding) !important;
                    margin: 0 !important;
                    text-align: justify;
                    font-family: 'CustomSerif', serif;
                    font-size: var(--font-size);
                    line-height: var(--line-height);
                    color: var(--text-color);
                    background-color: var(--bg-color);
                    word-wrap: break-word;
                    text-rendering: optimizeLegibility;
                    -webkit-font-smoothing: antialiased;
                }
                
                img {
                    max-width: 100% !important;
                    height: auto !important;
                    margin: 1em auto !important;
                    display: block;
                }
                
                p {
                    margin: 0.8em 0;
                    text-indent: 2em;
                }
                
                h1, h2, h3, h4, h5, h6 {
                    margin: 1.2em 0 0.6em 0;
                    line-height: 1.3;
                    text-align: center;
                    font-family: 'CustomSans', sans-serif;
                }
                
                a {
                    color: var(--link-color);
                    text-decoration: none;
                }
                
                .quote {
                    font-family: serif;
                }
                
                @media (prefers-color-scheme: dark) {
                    :root {
                        --text-color: #E0E0E0;
                        --bg-color: #121212;
                        --link-color: #90CAF9;
                    }
                }
            </style>
        """.trimIndent()
        
        return html.replace("</head>", "$customStyles\n</head>")
    }
    
    /**
     * 根据href查找资源
     */
    private fun findResourceByHref(book: EpubBook, href: String, currentResource: EpubResource): EpubResource? {
        // 处理绝对路径
        if (href.startsWith("/")) {
            val resourcePath = href.substring(1)
            val resourceData = book.resources[resourcePath]
            if (resourceData != null) {
                return EpubResource(resourcePath, null, resourceData)
            }
            return null
        }
        
        // 处理相对路径
        val currentPath = currentResource.href
        val currentDir = currentPath.substringBeforeLast('/', "")
        
        val absolutePath = if (currentDir.isEmpty()) href else "$currentDir/$href"
        val normalizedPath = absolutePath.replace("//", "/")
        
        val resourceData = book.resources[normalizedPath]
        if (resourceData != null) {
            return EpubResource(normalizedPath, null, resourceData)
        }
        
        return null
    }
    
    /**
     * 获取缓存目录
     */
    private fun getCacheDir(book: EpubBook): File {
        // 使用书名作为缓存子目录名，避免特殊字符
        val safeTitleName = book.spine.firstOrNull()?.title?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: "unknown"
        val cacheDir = File(context.cacheDir, "$CACHE_DIR/$safeTitleName")
        
        // 清理旧缓存
        if (cacheDir.exists() && !isCacheValid(cacheDir)) {
            cacheDir.deleteRecursively()
        }
        
        return cacheDir
    }
    
    /**
     * 检查缓存是否有效（30天内有效）
     */
    private fun isCacheValid(cacheDir: File): Boolean {
        val cacheAge = System.currentTimeMillis() - cacheDir.lastModified()
        val maxAge = 30 * 24 * 60 * 60 * 1000L // 30天
        return cacheAge < maxAge
    }
    
    /**
     * 获取字体大小设置的JavaScript代码
     */
    fun getFontSizeJs(fontSize: Int): String {
        return """
            javascript:(function() {
                document.documentElement.style.setProperty('--font-size', '${fontSize}px');
            })()
        """.trimIndent()
    }
    
    /**
     * 获取行间距设置的JavaScript代码
     */
    fun getLineHeightJs(lineHeight: Float): String {
        return """
            javascript:(function() {
                document.documentElement.style.setProperty('--line-height', '${lineHeight}');
            })()
        """.trimIndent()
    }
    
    /**
     * 获取页面主题设置的JavaScript代码
     */
    fun getThemeJs(isDarkMode: Boolean = false): String {
        val textColor = if (isDarkMode) "#E0E0E0" else "#333333"
        val backgroundColor = if (isDarkMode) "#121212" else "#FFFFFF"
        
        return """
            javascript:(function() {
                document.documentElement.style.setProperty('--text-color', '${textColor}');
                document.documentElement.style.setProperty('--bg-color', '${backgroundColor}');
                document.body.style.backgroundColor = '${backgroundColor}';
                document.documentElement.style.backgroundColor = '${backgroundColor}';
                
                // 如果是暗模式，处理图片亮度
                ${if (isDarkMode) 
                    "var images = document.querySelectorAll('img'); " +
                    "for(var i=0; i<images.length; i++) { " +
                    "   images[i].style.filter = 'brightness(0.85)'; " +
                    "}" 
                else ""}
            })()
        """.trimIndent()
    }
    
    /**
     * 获取字体设置的JavaScript代码
     */
    fun getFontFamilyJs(fontFamily: String): String {
        return """
            javascript:(function() {
                document.body.style.fontFamily = '${fontFamily}, CustomSerif, serif';
            })()
        """.trimIndent()
    }
    
    /**
     * 获取翻页的JavaScript代码
     */
    fun getPageNavigationJs(direction: PageDirection): String {
        return when (direction) {
            PageDirection.NEXT -> {
                """
                javascript:(function() {
                    if (window.currentPage < window.totalPages - 1) {
                        window.currentPage++;
                        window.scrollTo(window.pageWidth * window.currentPage, 0);
                        if (window.EpubReader) {
                            window.EpubReader.onPageChanged(window.currentPage, window.totalPages);
                        }
                    } else if (window.EpubReader) {
                        window.EpubReader.onLastPage();
                    }
                })()
                """.trimIndent()
            }
            PageDirection.PREVIOUS -> {
                """
                javascript:(function() {
                    if (window.currentPage > 0) {
                        window.currentPage--;
                        window.scrollTo(window.pageWidth * window.currentPage, 0);
                        if (window.EpubReader) {
                            window.EpubReader.onPageChanged(window.currentPage, window.totalPages);
                        }
                    } else if (window.EpubReader) {
                        window.EpubReader.onFirstPage();
                    }
                })()
                """.trimIndent()
            }
        }
    }
    
    /**
     * 清理所有缓存
     */
    fun clearCache() {
        val baseCacheDir = File(context.cacheDir, CACHE_DIR)
        baseCacheDir.deleteRecursively()
    }
    
    /**
     * 从container.xml中提取根文件路径
     */
    private fun extractRootFilePath(containerContent: String): String {
        val doc = Jsoup.parse(containerContent)
        val rootfile = doc.select("rootfile").first()
        return rootfile?.attr("full-path") ?: throw IOException("无法找到OPF文件路径")
    }
    
    /**
     * 解析spine
     */
    private fun parseSpine(opfDoc: Document, zipFile: ZipFile): List<EpubResource> {
        val spine = mutableListOf<EpubResource>()
        val manifest = parseManifest(opfDoc)
        
        opfDoc.select("spine itemref").forEach { item ->
            val idref = item.attr("idref")
            val manifestItem = manifest[idref] ?: return@forEach
            
            val entry = zipFile.getEntry(manifestItem.href)
            if (entry != null) {
                val data = zipFile.getInputStream(entry).readBytes()
                spine.add(EpubResource(manifestItem.href, manifestItem.title, data))
            }
        }
        
        return spine
    }
    
    /**
     * 解析manifest
     */
    private fun parseManifest(opfDoc: Document): Map<String, ManifestItem> {
        val manifest = mutableMapOf<String, ManifestItem>()
        
        opfDoc.select("manifest item").forEach { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            val mediaType = item.attr("media-type")
            val title = item.attr("title")
            
            manifest[id] = ManifestItem(href, mediaType, title)
        }
        
        return manifest
    }
    
    /**
     * 解析资源
     */
    private fun parseResources(opfDoc: Document, zipFile: ZipFile): Map<String, ByteArray> {
        val resources = mutableMapOf<String, ByteArray>()
        val manifest = parseManifest(opfDoc)
        
        manifest.values.forEach { item ->
            val entry = zipFile.getEntry(item.href)
            if (entry != null) {
                val data = zipFile.getInputStream(entry).readBytes()
                resources[item.href] = data
            }
        }
        
        return resources
    }
    
    /**
     * 解析目录
     */
    private fun parseToc(opfDoc: Document, zipFile: ZipFile): List<ChapterInfo> {
        val toc = mutableListOf<ChapterInfo>()
        
        try {
            // 尝试从OPF文件中找到toc（新版EPUB规范）
            val tocSelector = opfDoc.select("spine[toc]")
            if (tocSelector.isNotEmpty()) {
                val tocId = tocSelector.first()?.attr("toc")
                val tocItem = opfDoc.select("manifest item#$tocId").first()
                
                if (tocItem != null) {
                    val tocHref = tocItem.attr("href")
                    val tocEntry = zipFile.getEntry(tocHref)
                    
                    if (tocEntry != null) {
                        val tocContent = String(zipFile.getInputStream(tocEntry).readBytes())
                        val tocDoc = Jsoup.parse(tocContent)
                        
                        // 解析NCX目录文件
                        val navPoints = tocDoc.select("navPoint")
                        navPoints.forEach { navPoint ->
                            val title = navPoint.select("navLabel text").text()
                            val content = navPoint.select("content").first()?.attr("src") ?: ""
                            val level = countParentNavPoints(navPoint)
                            
                            toc.add(ChapterInfo(title, content, level))
                        }
                    }
                }
            }
            
            // 如果没有找到目录或目录为空，则从spine创建基本目录
            if (toc.isEmpty()) {
                val spine = opfDoc.select("spine itemref")
                spine.forEachIndexed { index, item ->
                    val idref = item.attr("idref")
                    val manifestItem = opfDoc.select("manifest item#$idref").first()
                    
                    if (manifestItem != null) {
                        val href = manifestItem.attr("href")
                        val title = manifestItem.attr("title").takeIf { it.isNotEmpty() } 
                            ?: "章节 ${index + 1}"
                        
                        toc.add(ChapterInfo(title, href, 0))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析目录失败", e)
        }
        
        return toc
    }
    
    /**
     * 计算navPoint的层级
     */
    private fun countParentNavPoints(element: Element): Int {
        var level = 0
        var parent = element.parent()
        
        while (parent != null && parent.tagName() == "navPoint") {
            level++
            parent = parent.parent()
        }
        
        return level
    }
    
    private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val title: String?
    )
}

/**
 * 章节信息，包括标题、索引和层级
 */
data class ChapterInfo(
    val title: String,
    val index: String,
    val level: Int
) 