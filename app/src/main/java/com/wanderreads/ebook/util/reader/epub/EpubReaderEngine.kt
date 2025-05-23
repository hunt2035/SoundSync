package com.wanderreads.ebook.util.reader.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import android.util.Log
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.util.PageDirection
import com.wanderreads.ebook.util.reader.BookReaderEngine
import com.wanderreads.ebook.util.reader.ReaderEngineState
import com.wanderreads.ebook.util.reader.SearchResult
import com.wanderreads.ebook.util.reader.model.BookChapter
import com.wanderreads.ebook.util.reader.model.ReaderConfig
import com.wanderreads.ebook.util.reader.model.ReaderContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import java.nio.charset.Charset

/**
 * EPUB电子书阅读引擎
 * 处理EPUB格式的电子书
 */
class EpubReaderEngine(private val context: Context) : BookReaderEngine {
    private val TAG = "EpubReaderEngine"
    
    // 引擎状态
    private val _state = MutableStateFlow(ReaderEngineState())
    override val state: StateFlow<ReaderEngineState> = _state.asStateFlow()
    
    private var book: Book? = null
    private var bookPath: String? = null
    private var currentPage: Int = 0
    private var totalPages: Int = 0
    private var currentChapter: Int = 0
    private var currentChapterIndex: Int = 0
    private var chapters: MutableList<BookChapter> = mutableListOf()
    private var readerConfig: ReaderConfig = ReaderConfig()
    private var contentCache: MutableMap<Int, ReaderContent> = mutableMapOf()
    private var tts: TextToSpeech? = null
    private var currentContent: ReaderContent? = null
    
    // EPUB特有
    private var epubBook: EpubBook? = null
    private var spineResources: List<EpubResource> = emptyList()
    private var coverImage: ByteArray? = null
    private var hasShownCover: Boolean = false
    
    private data class EpubBook(
        val spine: List<EpubResource>,
        val resources: Map<String, EpubResource>,
        val toc: List<ChapterInfo>
    )
    
    private data class EpubResource(
        val href: String,
        val title: String?,
        val data: ByteArray,
        val mediaType: String = ""
    )
    
    private data class ChapterInfo(
        val title: String,
        val spineIndex: Int,
        val level: Int
    )

    /**
     * 初始化引擎
     */
    override suspend fun initialize(book: Book, initialPosition: Int) {
        this.book = book
        this.bookPath = book.filePath
        this.currentPage = initialPosition
        
        withContext(Dispatchers.IO) {
            try {
                val epubFile = File(book.filePath)
                val zipFile = ZipFile(epubFile)
                
                // 读取容器文件
                val containerEntry = zipFile.getEntry("META-INF/container.xml")
                if (containerEntry == null) {
                    throw IOException("无效的EPUB文件：缺少container.xml")
                }
                
                val containerContent = String(zipFile.getInputStream(containerEntry).readBytes())
                val rootFilePath = extractRootFilePath(containerContent)
                
                // 读取OPF文件
                val opfEntry = zipFile.getEntry(rootFilePath)
                if (opfEntry == null) {
                    throw IOException("无效的EPUB文件：缺少OPF文件")
                }
                
                val opfContent = String(zipFile.getInputStream(opfEntry).readBytes())
                val opfDoc = Jsoup.parse(opfContent)
                
                // 解析spine
                spineResources = parseSpine(opfDoc, zipFile)
                
                // 解析资源
                val resources = parseResources(opfDoc, zipFile)
                
                // 解析目录
                val toc = parseToc(opfDoc, zipFile)
                
                epubBook = EpubBook(spineResources, resources, toc)
                
                // 保存封面图像数据
                coverImage = findCoverImage(resources)
                
                // 提取章节信息
                extractChapters()
                
                // 计算总页数 = spine中资源数量 + 1(封面页)
                totalPages = spineResources.size + 1
                
                // 更新状态
                _state.value = ReaderEngineState(
                    isLoading = false,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    currentChapter = getChapterForPage(currentPage),
                    totalChapters = chapters.size,
                    readingProgress = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f,
                    book = book
                )
                
                // 预加载内容
                preloadContent()
            } catch (e: Exception) {
                Log.e(TAG, "EPUB初始化失败", e)
                _state.value = _state.value.copy(
                    error = "加载EPUB失败: ${e.message}",
                    isLoading = false
                )
            }
        }
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
                spine.add(EpubResource(manifestItem.href, manifestItem.title, data, manifestItem.mediaType))
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
    private fun parseResources(opfDoc: Document, zipFile: ZipFile): Map<String, EpubResource> {
        val resources = mutableMapOf<String, EpubResource>()
        val manifest = parseManifest(opfDoc)
        
        manifest.values.forEach { item ->
            val entry = zipFile.getEntry(item.href)
            if (entry != null) {
                val data = zipFile.getInputStream(entry).readBytes()
                resources[item.href] = EpubResource(item.href, item.title, data, item.mediaType)
            }
        }
        
        return resources
    }
    
    /**
     * 解析目录
     */
    private fun parseToc(opfDoc: Document, zipFile: ZipFile): List<ChapterInfo> {
        val toc = mutableListOf<ChapterInfo>()
        val manifest = parseManifest(opfDoc)
        
        // 查找toc.ncx文件
        val tocId = opfDoc.select("spine").attr("toc")
        val tocItem = manifest[tocId] ?: return toc
        
        val tocEntry = zipFile.getEntry(tocItem.href)
        if (tocEntry != null) {
            val tocContent = String(zipFile.getInputStream(tocEntry).readBytes())
            val tocDoc = Jsoup.parse(tocContent)
            
            parseNavPoints(tocDoc.select("navPoint"), toc, 0, 10)
        }
        
        return toc
    }
    
    /**
     * 解析导航点
     */
    private fun parseNavPoints(navPoints: List<Element>, chapters: MutableList<ChapterInfo>, level: Int, maxDepth: Int = 10) {
        // 安全检查：防止递归太深导致栈溢出
        if (level >= maxDepth) {
            Log.w(TAG, "解析导航点达到最大深度限制: $maxDepth")
            return
        }
        
        navPoints.forEach { navPoint ->
            val title = navPoint.select("navLabel text").text()
            val content = navPoint.select("content").attr("src")
            
            // 查找对应的spine索引
            val spineIndex = findSpineIndexForContent(content)
            chapters.add(ChapterInfo(title, spineIndex, level))
            
            // 递归处理子导航点
            // 使用 > 选择器确保只选择直接子元素，避免选择自身导致无限递归
            val children = navPoint.select("> navPoint")
            if (children.isNotEmpty()) {
                parseNavPoints(children, chapters, level + 1, maxDepth)
            }
        }
    }
    
    /**
     * 查找内容对应的spine索引
     */
    private fun findSpineIndexForContent(content: String): Int {
        // 对href进行标准化处理，移除锚点和查询参数
        val normalizedContent = content.split("#").first().split("?").first()
        
        // 在spine中查找匹配的resource
        for ((index, resource) in spineResources.withIndex()) {
            // 对href进行标准化处理
            val normalizedHref = resource.href.split("#").first().split("?").first()
            
            // 检查content是否包含或等于href
            if (normalizedContent == normalizedHref || 
                normalizedContent.endsWith(normalizedHref) || 
                normalizedHref.endsWith(normalizedContent)) {
                return index
            }
        }
        
        // 如果找不到，记录警告并返回第一个索引
        Log.w(TAG, "无法找到内容对应的spine索引: $content")
        return 0
    }
    
    /**
     * 查找封面图片
     */
    private fun findCoverImage(resources: Map<String, EpubResource>): ByteArray? {
        // 查找封面图片
        return resources.values
            .filter { 
                it.mediaType.contains("image") || 
                it.href.endsWith(".jpg") || 
                it.href.endsWith(".jpeg") || 
                it.href.endsWith(".png") 
            }
            .firstOrNull()
            ?.data
    }
    
    private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val title: String?
    )
    
    /**
     * 提取章节信息
     */
    private fun extractChapters() {
        val toc = epubBook?.toc ?: emptyList()
        
        if (toc.isEmpty()) {
            // 如果没有目录，使用spine创建章节
            chapters = spineResources.mapIndexed { index, resource ->
                val title = resource.title ?: "第${index + 1}章"
                BookChapter(
                    title = title,
                    index = index,
                    startPosition = index + 1  // +1 因为第0页是封面
                )
            }.toMutableList()
        } else {
            // 使用目录创建章节
            chapters = toc.mapIndexed { index, chapter ->
                BookChapter(
                    title = chapter.title,
                    index = index,
                    startPosition = chapter.spineIndex + 1, // +1 因为第0页是封面
                    subChapters = emptyList() // 简化版本，不处理子章节
                )
            }.toMutableList()
        }
    }
    
    /**
     * 预加载内容
     */
    private suspend fun preloadContent() {
        withContext(Dispatchers.IO) {
            // 添加封面页
            contentCache[0] = createCoverContent()
            
            // 加载第一章内容
            if (currentPage > 0 && currentPage < totalPages) {
                getPageContentFromSpine(currentPage - 1)  // -1 因为第0页是封面
            }
        }
    }
    
    /**
     * 创建封面内容
     */
    private fun createCoverContent(): ReaderContent {
        val coverHtml = if (coverImage != null) {
            """
            <html>
            <head>
                <style>
                    body { 
                        margin: 0; 
                        padding: 0; 
                        display: flex; 
                        justify-content: center; 
                        align-items: center; 
                        height: 100vh; 
                        background-color: #0A1929;
                    }
                    img {
                        max-width: 100%;
                        max-height: 100vh;
                        object-fit: contain;
                    }
                    .title {
                        position: absolute;
                        bottom: 20px;
                        width: 100%;
                        text-align: center;
                        color: white;
                        font-size: 24px;
                        padding: 10px;
                        background-color: rgba(0, 0, 0, 0.5);
                    }
                </style>
            </head>
            <body>
                <img src="data:image/jpeg;base64,${android.util.Base64.encodeToString(coverImage, android.util.Base64.DEFAULT)}">
                <div class="title">${book?.title ?: "未知标题"}</div>
            </body>
            </html>
            """.trimIndent()
        } else {
            """
            <html>
            <head>
                <style>
                    body { 
                        display: flex; 
                        justify-content: center; 
                        align-items: center; 
                        height: 100vh; 
                        background-color: #0A1929;
                        color: white;
                        text-align: center;
                    }
                    .cover {
                        padding: 20px;
                    }
                    h1 {
                        font-size: 28px;
                        margin-bottom: 20px;
                    }
                    h2 {
                        font-size: 20px;
                        font-style: italic;
                        margin-bottom: 40px;
                    }
                </style>
            </head>
            <body>
                <div class="cover">
                    <h1>${book?.title ?: "未知标题"}</h1>
                    <h2>${book?.author ?: "未知作者"}</h2>
                </div>
            </body>
            </html>
            """.trimIndent()
        }
        
        return ReaderContent(
            text = book?.title ?: "未知标题",
            html = coverHtml,
            pageIndex = 0,
            chapterIndex = 0,
            isFirstPage = true,
            isLastPage = totalPages <= 1
        )
    }
    
    /**
     * 加载内容
     */
    override suspend fun loadContent() {
        withContext(Dispatchers.IO) {
            try {
                currentContent = getCurrentPageContent()
                contentCache[currentPage] = currentContent!!
            } catch (e: Exception) {
                Log.e(TAG, "加载内容失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 从spine获取页面内容
     */
    private fun getPageContentFromSpine(spineIndex: Int): ReaderContent {
        return contentCache.getOrPut(spineIndex + 1) {  // +1 因为第0页是封面
            if (spineIndex < 0 || spineIndex >= spineResources.size) {
                return@getOrPut ReaderContent(
                    text = "内容不存在",
                    pageIndex = spineIndex + 1,
                    chapterIndex = 0,
                    isFirstPage = spineIndex + 1 == 1,
                    isLastPage = spineIndex + 1 == totalPages - 1
                )
            }
            
            val resource = spineResources[spineIndex]
            val data = resource.data
            
            // 处理HTML内容
            val html = String(data, Charsets.UTF_8)
            
            // 使用Jsoup解析HTML，提取纯文本
            val doc = Jsoup.parse(html)
            val textContent = doc.text()
            
            // 处理图片引用，转换相对路径为绝对路径
            val processedHtml = processHtml(html, spineIndex)
            
            // 确定章节索引
            val chapterIndex = findChapterIndexForPage(spineIndex + 1)
            
            ReaderContent(
                text = textContent,
                html = processedHtml,
                pageIndex = spineIndex + 1,
                chapterIndex = chapterIndex,
                isFirstPage = spineIndex + 1 == 1,
                isLastPage = spineIndex + 1 == totalPages - 1
            )
        }
    }
    
    /**
     * 处理HTML内容，处理图片引用等
     */
    private fun processHtml(html: String, spineIndex: Int): String {
        val spine = spineResources[spineIndex]
        val doc = Jsoup.parse(html)
        
        // 处理图片路径
        doc.select("img").forEach { img ->
            val src = img.attr("src")
            if (src.isNotEmpty() && !src.startsWith("data:")) {
                // 查找图片资源
                val imgResource = findResourceByHref(src, spine.href)
                imgResource?.let { resource ->
                    val base64Image = android.util.Base64.encodeToString(resource.data, android.util.Base64.DEFAULT)
                    val mediaType = resource.mediaType.ifEmpty { "image/jpeg" }
                    img.attr("src", "data:$mediaType;base64,$base64Image")
                }
            }
        }
        
        // 处理CSS样式引用
        doc.select("link[rel=stylesheet]").forEach { link ->
            val href = link.attr("href")
            if (href.isNotEmpty()) {
                val cssResource = findResourceByHref(href, spine.href)
                cssResource?.let { resource ->
                    val css = String(resource.data, Charsets.UTF_8)
                    // 替换为内联样式
                    doc.head().append("<style>$css</style>")
                    link.remove()
                }
            }
        }
        
        return doc.html()
    }
    
    /**
     * 根据href查找资源
     */
    private fun findResourceByHref(href: String, baseHref: String): EpubResource? {
        // 解析相对路径
        val resolvedHref = resolveRelativePath(href, baseHref)
        
        // 查找资源
        return epubBook?.resources?.get(resolvedHref)
    }
    
    /**
     * 解析相对路径
     */
    private fun resolveRelativePath(relativePath: String, basePath: String): String {
        if (relativePath.startsWith("/")) {
            return relativePath.substring(1)
        }
        
        val baseDir = basePath.substringBeforeLast('/', "")
        if (baseDir.isEmpty()) {
            return relativePath
        }
        
        // 处理 ../ 路径
        var path = "$baseDir/$relativePath"
        while (path.contains("/../")) {
            val beforeParent = path.substringBeforeLast("/../")
            val parent = beforeParent.substringBeforeLast('/')
            val afterParent = path.substringAfter("/../")
            path = "$parent/$afterParent"
        }
        
        return path
    }
    
    /**
     * 获取当前页面内容
     */
    override fun getCurrentPageContent(): ReaderContent {
        // 先检查缓存
        contentCache[currentPage]?.let { return it }
        
        // 如果是第一页（封面）
        if (currentPage == 0) {
            return createCoverContent()
        }
        
        // 获取正常内容
        if (currentPage - 1 < spineResources.size) {
            return getPageContentFromSpine(currentPage - 1)
        }
        
        // 默认空内容
        return ReaderContent(
            text = "",
            pageIndex = currentPage,
            chapterIndex = currentChapter,
            isFirstPage = currentPage == 0,
            isLastPage = currentPage == totalPages - 1
        )
    }
    
    /**
     * 获取当前章节标题
     */
    override fun getCurrentChapterTitle(): String {
        // 如果是封面页
        if (currentPage == 0) {
            return "封面"
        }
        
        return if (currentChapter in chapters.indices) {
            chapters[currentChapter].title
        } else {
            "未知章节"
        }
    }

    /**
     * 获取当前页面的纯文本内容
     */
    override fun getCurrentPageText(): String {
        return currentContent?.text ?: ""
    }
    
    /**
     * 获取当前章节的全部文本内容（用于TTS和语音合成）
     */
    override fun getCurrentChapterText(): String {
        val currentChapter = chapters.getOrNull(currentChapterIndex)
        
        // 如果章节对象存在但内容为空，尝试重新加载章节内容
        if (currentChapter != null && currentChapter.content.isBlank()) {
            try {
                // 尝试从EPUB文件中读取章节内容
                val spineIndex = currentChapter.startPosition - 1 // 减1因为第0页是封面
                if (spineIndex >= 0 && spineIndex < spineResources.size) {
                    val resource = spineResources[spineIndex]
                    // 解析HTML内容为纯文本
                    val htmlContent = resource.data.let { String(it, Charset.forName("UTF-8")) }
                    if (htmlContent.isNotEmpty()) {
                        // 使用简单的HTML解析提取文本
                        val plainText = htmlToText(htmlContent)
                        // 由于BookChapter的content是val，我们需要创建一个新的对象
                        val updatedChapter = currentChapter.copy(content = plainText)
                        // 更新chapters列表中对应的章节
                        chapters[currentChapterIndex] = updatedChapter
                        return plainText
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取章节内容失败", e)
            }
        }
        
        return currentChapter?.content ?: ""
    }
    
    /**
     * 检查是否有下一页
     */
    override fun hasNextPage(): Boolean {
        return currentPage < totalPages - 1
    }
    
    /**
     * 翻页
     */
    override suspend fun navigatePage(direction: PageDirection): Int {
        val newPage = when (direction) {
            PageDirection.NEXT -> (currentPage + 1).coerceAtMost(totalPages - 1)
            PageDirection.PREVIOUS -> (currentPage - 1).coerceAtLeast(0)
        }
        
        if (newPage != currentPage) {
            currentPage = newPage
            updateCurrentChapter()
            
            // 预加载内容
            loadContent()
            
            // 更新状态
            _state.value = _state.value.copy(
                currentPage = currentPage,
                currentChapter = currentChapter,
                readingProgress = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f
            )
        }
        
        return currentPage
    }
    
    /**
     * 跳转到指定页
     */
    override suspend fun goToPage(pageIndex: Int) {
        if (pageIndex in 0 until totalPages) {
            currentPage = pageIndex
            updateCurrentChapter()
            
            // 预加载内容
            loadContent()
            
            // 更新状态
            _state.value = _state.value.copy(
                currentPage = currentPage,
                currentChapter = currentChapter,
                readingProgress = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f
            )
        }
    }
    
    /**
     * 跳转到指定章节
     */
    override suspend fun goToChapter(chapterIndex: Int) {
        if (chapterIndex in chapters.indices) {
            currentChapter = chapterIndex
            currentPage = chapters[chapterIndex].startPosition
            
            // 预加载内容
            loadContent()
            
            // 更新状态
            _state.value = _state.value.copy(
                currentPage = currentPage,
                currentChapter = currentChapter,
                readingProgress = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f
            )
        }
    }
    
    /**
     * 获取章节列表
     */
    override fun getChapters(): List<BookChapter> = chapters
    
    /**
     * 获取当前阅读进度
     */
    override fun getReadingProgress(): Float {
        return if (totalPages > 0) currentPage.toFloat() / totalPages.toFloat() else 0f
    }
    
    /**
     * 更新阅读配置
     */
    override fun updateConfig(config: ReaderConfig) {
        readerConfig = config
        // 应用配置逻辑
    }
    
    /**
     * 保存阅读进度
     */
    override suspend fun saveReadingProgress() {
        // 实际项目中这里应该保存进度到数据库
    }
    
    /**
     * 搜索文本
     */
    override suspend fun searchText(query: String): List<SearchResult> {
        // 简单的搜索实现
        val results = mutableListOf<SearchResult>()
        
        withContext(Dispatchers.IO) {
            for (page in 1 until totalPages) { // 从1开始，跳过封面
                val content = getPageContentFromSpine(page - 1)
                if (content.text.contains(query, ignoreCase = true)) {
                    val queryIndex = content.text.indexOf(query, ignoreCase = true)
                    results.add(
                        SearchResult(
                            pageIndex = page,
                            chapterIndex = findChapterIndexForPage(page),
                            text = content.text,
                            highlightStart = queryIndex,
                            highlightEnd = queryIndex + query.length
                        )
                    )
                }
            }
        }
        
        return results
    }
    
    /**
     * 获取书籍封面
     */
    override suspend fun getBookCover(): Bitmap? {
        return coverImage?.let {
            try {
                BitmapFactory.decodeStream(ByteArrayInputStream(it))
            } catch (e: Exception) {
                Log.e(TAG, "解析封面图片失败", e)
                null
            }
        }
    }
    
    /**
     * 初始化TTS引擎
     */
    override fun initTts(tts: TextToSpeech) {
        this.tts = tts
    }
    
    /**
     * 清理资源
     */
    override fun close() {
        bookPath = null
        contentCache.clear()
        tts = null
        epubBook = null
        spineResources = emptyList()
        coverImage = null
    }
    
    /**
     * 根据页码获取章节
     */
    private fun getChapterForPage(pageIndex: Int): Int {
        // 如果是封面页
        if (pageIndex == 0) return 0
        
        // 查找章节
        for (i in chapters.indices.reversed()) {
            if (pageIndex >= chapters[i].startPosition) {
                return i
            }
        }
        
        return 0
    }
    
    /**
     * 查找页面所在的章节索引
     */
    private fun findChapterIndexForPage(pageIndex: Int): Int {
        return getChapterForPage(pageIndex)
    }
    
    /**
     * 更新当前章节
     */
    private fun updateCurrentChapter() {
        currentChapter = getChapterForPage(currentPage)
        currentChapterIndex = currentChapter
    }
    
    /**
     * 将HTML内容转换为纯文本
     * 简单实现，移除HTML标签
     */
    private fun htmlToText(html: String): String {
        // 移除HTML标签
        val noTags = html.replace(Regex("<[^>]*>"), " ")
        
        // 移除多余空格
        val noExtraSpaces = noTags.replace(Regex("\\s+"), " ")
        
        // 处理常见HTML实体
        return noExtraSpaces
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()
    }
} 