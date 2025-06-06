package com.wanderreads.ebook.util.reader.txt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import android.util.Log
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.domain.model.BookType
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * TXT格式阅读引擎
 */
class TxtReaderEngine(private val context: Context) : BookReaderEngine {
    private val TAG = "TxtReaderEngine"
    
    // 引擎状态
    private val _state = MutableStateFlow(ReaderEngineState())
    override val state: StateFlow<ReaderEngineState> = _state.asStateFlow()
    
    // 当前加载的书籍
    private var book: Book? = null
    
    // 书籍内容，按页分割
    private var pages: List<String> = emptyList()
    
    // 当前页码
    private var currentPage: Int = 0
    private var _currentPageIndex: Int = 0
    private var _totalPages: Int = 0
    private var _currentPageTextContent: String? = null
    
    // 章节信息
    private var chapters: MutableList<BookChapter> = mutableListOf()
    private var _currentChapterIndex: Int = 0
    
    // 阅读器配置
    private var config: ReaderConfig = ReaderConfig()
    
    // 文件编码
    private var charset: Charset = StandardCharsets.UTF_8
    
    // TTS引擎
    private var tts: TextToSpeech? = null
    
    /**
     * 初始化引擎
     */
    override suspend fun initialize(book: Book, initialPosition: Int) {
        this.book = book
        
        _state.update { 
            it.copy(
                isLoading = true,
                book = book,
                currentPage = initialPosition
            ) 
        }
        
        currentPage = initialPosition
        
        try {
            // 检测文件编码
            detectCharset(book.filePath)
            
            // 加载内容
            loadContent()
            
            // 生成章节信息
            generateChapters()
            
            _state.update { 
                it.copy(
                    isLoading = false,
                    currentPage = currentPage,
                    totalPages = pages.size,
                    readingProgress = if (pages.isNotEmpty()) currentPage.toFloat() / pages.size else 0f
                ) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化TXT阅读引擎失败", e)
            _state.update { 
                it.copy(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                ) 
            }
        }
    }
    
    /**
     * 加载内容
     */
    override suspend fun loadContent() {
        val bookFile = book?.filePath ?: return
        
        withContext(Dispatchers.IO) {
            try {
                val file = File(bookFile)
                val reader = BufferedReader(InputStreamReader(FileInputStream(file), charset))
                val content = StringBuilder()
                var line: String?
                
                // 读取整个文件内容
                while (reader.readLine().also { line = it } != null) {
                    content.append(line).append("\n")
                }
                
                reader.close()
                
                // 分页
                pages = paginateText(content.toString())
                
                _state.update { 
                    it.copy(
                        totalPages = pages.size,
                        currentPage = currentPage.coerceIn(0, pages.size - 1)
                    ) 
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载TXT内容失败", e)
                _state.update { it.copy(error = "加载内容失败: ${e.message}") }
            }
        }
    }
    
    /**
     * 获取当前页面内容
     */
    override fun getCurrentPageContent(): ReaderContent {
        if (pages.isEmpty() || currentPage >= pages.size) {
            return ReaderContent(
                text = "",
                pageIndex = 0,
                chapterIndex = 0,
                isFirstPage = true,
                isLastPage = true
            )
        }
        
        val currentChapterIndex = findChapterIndexForPage(currentPage)
        
        // 获取页面文本，并根据需要进行格式化
        val pageText = pages[currentPage]
        val formattedText = if (book?.type == BookType.MD) {
            formatMarkdownText(pageText)
        } else {
            pageText
        }
        
        return ReaderContent(
            text = formattedText,
            pageIndex = currentPage,
            chapterIndex = currentChapterIndex,
            isFirstPage = currentPage == 0,
            isLastPage = currentPage == pages.size - 1
        )
    }
    
    /**
     * 获取当前章节标题
     */
    override fun getCurrentChapterTitle(): String {
        val currentChapterIndex = findChapterIndexForPage(currentPage)
        return if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
            chapters[currentChapterIndex].title
        } else {
            "正文"
        }
    }
    
    /**
     * 翻页
     */
    override suspend fun navigatePage(direction: PageDirection): Int {
        val newPage = when (direction) {
            PageDirection.NEXT -> (currentPage + 1).coerceAtMost(pages.size - 1)
            PageDirection.PREVIOUS -> (currentPage - 1).coerceAtLeast(0)
        }
        
        if (newPage != currentPage) {
            currentPage = newPage
            _state.update { 
                it.copy(
                    currentPage = currentPage,
                    readingProgress = if (pages.isNotEmpty()) currentPage.toFloat() / pages.size else 0f
                ) 
            }
        }
        
        return currentPage
    }
    
    /**
     * 跳转到指定页
     */
    override suspend fun goToPage(pageIndex: Int) {
        if (pages.isEmpty()) return
        
        val newPage = pageIndex.coerceIn(0, pages.size - 1)
        if (newPage != currentPage) {
            currentPage = newPage
            _state.update { 
                it.copy(
                    currentPage = currentPage,
                    readingProgress = currentPage.toFloat() / pages.size
                ) 
            }
        }
    }
    
    /**
     * 跳转到指定章节
     */
    override suspend fun goToChapter(chapterIndex: Int) {
        if (chapters.isEmpty() || chapterIndex >= chapters.size) return
        
        val pageIndex = chapters[chapterIndex].startPosition
        goToPage(pageIndex)
    }
    
    /**
     * 获取章节列表
     */
    override fun getChapters(): List<BookChapter> {
        return chapters
    }
    
    /**
     * 获取当前阅读进度
     */
    override fun getReadingProgress(): Float {
        return if (pages.isNotEmpty()) currentPage.toFloat() / pages.size else 0f
    }
    
    /**
     * 格式化Markdown文本，处理格式标记
     */
    private fun formatMarkdownText(text: String): String {
        // 如果不是Markdown格式，直接返回原文本
        if (book?.type != BookType.MD) {
            return text
        }
        
        // 对Markdown格式进行额外处理
        return text
            // 增强标题显示
            .replace(Regex("^# (.+)$", RegexOption.MULTILINE), "【$1】")
            .replace(Regex("^## (.+)$", RegexOption.MULTILINE), "【$1】")
            .replace(Regex("^### (.+)$", RegexOption.MULTILINE), "【$1】")
            
            // 处理重点标记，可以在UI层进行样式处理
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "【$1】")  // 粗体
            .replace(Regex("\\*(.+?)\\*"), "「$1」")  // 斜体
            .replace(Regex("__(.+?)__"), "【$1】")  // 粗体
            .replace(Regex("_(.+?)_"), "「$1」")  // 斜体
            
            // 处理引用，改善视觉效果
            .replace(Regex("^> (.+)$", RegexOption.MULTILINE), "│ $1")
            
            // 处理行内代码
            .replace(Regex("`([^`]+?)`"), "「$1」")
            
            // 处理代码块，添加适当间距
            .replace(Regex("```(.+?)```", RegexOption.DOT_MATCHES_ALL), "\n「代码」\n$1\n「代码结束」\n")
            
            // 处理水平线
            .replace(Regex("^-{3,}$", RegexOption.MULTILINE), "\n--------------------\n")
            
            // 处理链接，只保留链接文本
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
            
            // 处理图片链接，替换为说明文字
            .replace(Regex("!\\[(.+?)\\]\\(.+?\\)"), "[图片: $1]")
    }
    
    /**
     * 获取当前页面的纯文本内容（用于TTS）
     */
    override fun getCurrentPageText(): String {
        if (pages.isEmpty() || currentPage >= pages.size) {
            return ""
        }
        
        val pageText = pages[currentPage]
        _currentPageTextContent = if (book?.type == BookType.MD) {
            // 对Markdown文本进行额外处理，以便更好地被TTS引擎朗读
            pageText
                .replace(Regex("[*_`~]"), "") // 移除格式标记
                .replace(Regex("#+ "), "") // 移除标题标记
        } else {
            pageText
        }
        
        return _currentPageTextContent ?: ""
    }
    
    /**
     * 获取当前章节的全部文本内容（用于TTS和语音合成）
     */
    override fun getCurrentChapterText(): String {
        // 同步变量
        _currentChapterIndex = findChapterIndexForPage(currentPage)
        
        // 如果章节列表为空或索引无效，返回当前页内容
        if (chapters.isEmpty() || _currentChapterIndex < 0 || _currentChapterIndex >= chapters.size) {
            return getCurrentPageText()
        }
        
        // 获取当前章节
        val currentChapter = chapters[_currentChapterIndex]
        
        // 如果章节内容为空，尝试重建章节内容
        if (currentChapter.content.isBlank()) {
            // 计算章节的起始页和结束页
            val startPage = currentChapter.startPosition
            val endPage = if (_currentChapterIndex < chapters.size - 1) {
                chapters[_currentChapterIndex + 1].startPosition - 1
            } else {
                pages.size - 1
            }
            
            // 收集章节内的所有页面内容
            val chapterContent = StringBuilder()
            for (i in startPage..endPage) {
                if (i < pages.size) {
                    chapterContent.append(pages[i])
                    if (i < endPage) chapterContent.append("\n")
                }
            }
            
            // 直接修改章节内容
            currentChapter.content = chapterContent.toString()
        }
        
        return currentChapter.content
    }
    
    /**
     * 检查是否有下一页
     */
    override fun hasNextPage(): Boolean {
        _currentPageIndex = currentPage
        _totalPages = pages.size
        return _currentPageIndex < _totalPages - 1
    }
    
    /**
     * 获取总页数
     */
    override fun getTotalPages(): Int {
        return pages.size
    }
    
    /**
     * 更新阅读配置
     */
    override fun updateConfig(config: ReaderConfig) {
        this.config = config
        // 根据配置重新分页，保留当前阅读位置的相对进度
        val progress = getReadingProgress()
        paginateText(getAllContent())
        // 恢复进度
        val newPage = (progress * pages.size).toInt().coerceIn(0, pages.size - 1)
        currentPage = newPage
        _state.update { 
            it.copy(
                currentPage = currentPage,
                totalPages = pages.size
            ) 
        }
    }
    
    /**
     * 保存阅读进度
     */
    override suspend fun saveReadingProgress() {
        // 这部分需要外部实现，通常由ViewModel调用BookRepository来保存
    }
    
    /**
     * 清理资源
     */
    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
    
    /**
     * 搜索文本
     */
    override suspend fun searchText(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        if (query.isBlank() || pages.isEmpty()) {
            return results
        }
        
        for (i in pages.indices) {
            val pageContent = pages[i]
            val index = pageContent.indexOf(query, ignoreCase = true)
            
            if (index >= 0) {
                val chapterIndex = findChapterIndexForPage(i)
                
                results.add(
                    SearchResult(
                        pageIndex = i,
                        chapterIndex = chapterIndex,
                        text = pageContent.substring(
                            (index - 20).coerceAtLeast(0),
                            (index + query.length + 20).coerceAtMost(pageContent.length)
                        ),
                        highlightStart = if (index < 20) index else 20,
                        highlightEnd = if (index < 20) index + query.length else 20 + query.length
                    )
                )
            }
        }
        
        return results
    }
    
    /**
     * 获取书籍封面
     */
    override suspend fun getBookCover(): Bitmap? {
        return book?.coverPath?.let {
            try {
                BitmapFactory.decodeFile(it)
            } catch (e: Exception) {
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
     * 检测文件编码
     */
    private fun detectCharset(filePath: String) {
        try {
            val file = File(filePath)
            val buffer = ByteArray(4096)
            
            FileInputStream(file).use { input ->
                val count = input.read(buffer)
                if (count > 0) {
                    // 检查BOM标记
                    if (count >= 3 && buffer[0].toInt() == 0xEF && buffer[1].toInt() == 0xBB && buffer[2].toInt() == 0xBF) {
                        charset = StandardCharsets.UTF_8
                        return
                    }
                    
                    // 检查是否包含中文字符
                    val utf8Str = String(buffer, 0, count, StandardCharsets.UTF_8)
                    val gbkStr = String(buffer, 0, count, Charset.forName("GBK"))
                    
                    // 简单启发式判断
                    charset = if (utf8Str.contains('\uFFFD') && !gbkStr.contains('\uFFFD')) {
                        Charset.forName("GBK")
                    } else {
                        StandardCharsets.UTF_8
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检测文件编码失败，使用默认UTF-8", e)
            charset = StandardCharsets.UTF_8
        }
    }
    
    /**
     * 获取所有内容
     */
    private fun getAllContent(): String {
        return pages.joinToString("\n")
    }
    
    /**
     * 预处理Markdown文本，改进其显示效果
     */
    private fun preprocessMarkdown(text: String): String {
        // 标记特殊格式，在显示时可以使用
        var result = text
            // 保留两个连续换行符（段落分隔）
            .replace(Regex("\n{3,}"), "\n\n")
            // 行尾两个空格视为强制换行
            .replace(Regex("  $", RegexOption.MULTILINE), "\n")
            // 处理标题格式，保留标题符号但添加换行
            .replace(Regex("^(#+) (.+)$", RegexOption.MULTILINE), "$1 $2\n")
            // 将连续换行符替换为特殊标记，以便后续处理
            .replace("\n\n", "\u0000")
            // 将单个换行符(不是段落分隔符)替换为空格
            .replace("\n", " ")
            // 还原段落分隔符
            .replace("\u0000", "\n\n")
            // 改进列表显示
            .replace(Regex("^ *- (.+)$", RegexOption.MULTILINE), "• $1")
            .replace(Regex("^ *\\* (.+)$", RegexOption.MULTILINE), "• $1")
            .replace(Regex("^ *\\d+\\. (.+)$", RegexOption.MULTILINE), "• $1")

        return result
    }
    
    /**
     * 文本分页
     */
    private fun paginateText(text: String): List<String> {
        val result = mutableListOf<String>()
        
        // 处理输入文本 - 为Markdown文件添加特殊处理
        val processedText = if (book?.type == BookType.MD) {
            preprocessMarkdown(text)
        } else {
            text
        }
        
        // 获取屏幕尺寸
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 计算可用于文本显示的区域（考虑边距）
        val horizontalMargin = dpToPx(config.margin * 2f, displayMetrics)
        val verticalMargin = dpToPx(config.margin * 2f, displayMetrics)
        val availableWidth = screenWidth - horizontalMargin
        val availableHeight = screenHeight - verticalMargin
        
        // 创建Paint来测量文本
        val textPaint = Paint().apply {
            isAntiAlias = true
            textSize = spToPx(config.fontSize.toFloat(), displayMetrics)
            typeface = when (config.fontFamily) {
                "Serif" -> Typeface.SERIF
                "Sans Serif" -> Typeface.SANS_SERIF
                "Monospace" -> Typeface.MONOSPACE
                else -> Typeface.DEFAULT
            }
        }
        
        // 计算行高
        val lineHeight = textPaint.fontSpacing * config.lineHeight
        
        // 计算每页可容纳的最大字符数
        val charsPerLine = (availableWidth / textPaint.measureText("A")).toInt()
        val linesPerPage = (availableHeight / lineHeight).toInt()
        val charsPerPage = charsPerLine * linesPerPage
        
        // 分割段落
        val paragraphs = processedText.split("\n")
        
        val currentPageContent = StringBuilder()
        var currentPageChars = 0
        
        for (paragraph in paragraphs) {
            // 如果段落为空，只添加换行符
            if (paragraph.isBlank()) {
                if (currentPageChars + 1 > charsPerPage) {
                    // 当前页已满，添加到结果并开始新页
                    result.add(currentPageContent.toString())
                    currentPageContent.clear()
                    currentPageChars = 0
                }
                currentPageContent.append("\n")
                currentPageChars += 1
                continue
            }
            
            // 处理非空段落
            var remainingText = paragraph
            var isFirstLineOfParagraph = true
            
            while (remainingText.isNotEmpty()) {
                // 计算当前行可容纳的字符数
                val availableCharsInCurrentPage = charsPerPage - currentPageChars
                
                if (availableCharsInCurrentPage <= 0) {
                    // 当前页已满，添加到结果并开始新页
                    result.add(currentPageContent.toString())
                    currentPageContent.clear()
                    currentPageChars = 0
                    continue
                }
                
                // 如果是段落的第一行且不是页面的第一行，添加缩进
                val linePrefix = if (isFirstLineOfParagraph && currentPageChars > 0) "    " else ""
                val effectiveAvailableChars = availableCharsInCurrentPage - linePrefix.length
                
                // 确保至少有一个字符的空间
                if (effectiveAvailableChars <= 0) {
                    result.add(currentPageContent.toString())
                    currentPageContent.clear()
                    currentPageChars = 0
                    continue
                }
                
                val charsToTake = minOf(effectiveAvailableChars, remainingText.length)
                
                // 添加当前行文本
                currentPageContent.append(linePrefix)
                currentPageContent.append(remainingText.substring(0, charsToTake))
                currentPageChars += linePrefix.length + charsToTake
                
                // 更新剩余文本
                remainingText = remainingText.substring(charsToTake)
                isFirstLineOfParagraph = false
                
                // 如果还有剩余文本，并且当前页未满，添加换行符
                if (remainingText.isNotEmpty() && currentPageChars < charsPerPage) {
                    currentPageContent.append("\n")
                    currentPageChars += 1
                }
                
                // 如果已处理完当前段落，添加段落间距
                if (remainingText.isEmpty()) {
                    currentPageContent.append("\n")
                    currentPageChars += 1
                }
            }
        }
        
        // 添加最后一页（如果有内容）
        if (currentPageContent.isNotEmpty()) {
            result.add(currentPageContent.toString())
        }
        
        // 更新页面列表
        pages = result
        return result
    }
    
    /**
     * 生成章节信息
     */
    private fun generateChapters() {
        // 对于TXT文件，尝试根据特定模式识别章节
        // 例如：第X章、Chapter X等
        
        val chapterPatterns = listOf(
            Regex("第[0-9一二三四五六七八九十百千]+章.*"),
            Regex("Chapter\\s+\\d+.*", RegexOption.IGNORE_CASE),
            Regex("Part\\s+\\d+.*", RegexOption.IGNORE_CASE),
            Regex("Section\\s+\\d+.*", RegexOption.IGNORE_CASE)
        )
        
        val detectedChapters = mutableListOf<BookChapter>()
        var currentChapterTitle = "开始"
        var currentChapterStartPage = 0
        
        // 添加默认的开始章节
        detectedChapters.add(
            BookChapter(
                title = currentChapterTitle,
                index = 0,
                startPosition = 0
            )
        )
        
        for (i in pages.indices) {
            val pageContent = pages[i]
            
            // 检查页面开始是否匹配章节模式
            for (pattern in chapterPatterns) {
                val matchResult = pattern.find(pageContent.trim())
                
                if (matchResult != null && matchResult.range.start <= 10) { // 确保匹配在页面开头附近
                    // 找到新章节
                    currentChapterTitle = matchResult.value
                    
                    // 如果不是第一章，更新上一章的信息
                    if (detectedChapters.size > 1) {
                        val lastChapter = detectedChapters.last()
                        // 只更新非默认章节
                        if (lastChapter.title != "开始" || lastChapter.startPosition != 0) {
                            detectedChapters.add(
                                BookChapter(
                                    title = currentChapterTitle,
                                    index = detectedChapters.size,
                                    startPosition = i
                                )
                            )
                        }
                    } else {
                        detectedChapters.add(
                            BookChapter(
                                title = currentChapterTitle,
                                index = detectedChapters.size,
                                startPosition = i
                            )
                        )
                    }
                    
                    break
                }
            }
        }
        
        // 如果只有默认章节，移除它
        if (detectedChapters.size > 1 && detectedChapters[0].title == "开始") {
            detectedChapters.removeAt(0)
            // 重新分配索引
            for (i in detectedChapters.indices) {
                detectedChapters[i] = detectedChapters[i].copy(index = i)
            }
        }
        
        // 更新章节列表
        chapters = detectedChapters
        
        // 更新状态
        _state.update { 
            it.copy(
                totalChapters = chapters.size,
                currentChapter = findChapterIndexForPage(currentPage)
            ) 
        }
    }
    
    /**
     * 查找指定页面所属的章节索引
     */
    private fun findChapterIndexForPage(pageIndex: Int): Int {
        if (chapters.isEmpty()) return 0
        
        // 二分查找章节
        var left = 0
        var right = chapters.size - 1
        
        while (left <= right) {
            val mid = (left + right) / 2
            
            when {
                // 如果是最后一章，或者页码在当前章节范围内
                mid == chapters.size - 1 || 
                (pageIndex >= chapters[mid].startPosition && pageIndex < chapters[mid + 1].startPosition) -> {
                    return mid
                }
                // 如果页码小于当前章节的起始页
                pageIndex < chapters[mid].startPosition -> {
                    right = mid - 1
                }
                // 如果页码大于等于下一章节的起始页
                else -> {
                    left = mid + 1
                }
            }
        }
        
        // 默认返回第一章
        return 0
    }
    
    /**
     * 将dp转换为像素
     */
    private fun dpToPx(dp: Float, displayMetrics: DisplayMetrics): Float {
        return dp * (displayMetrics.densityDpi / 160f)
    }
    
    /**
     * 将sp转换为像素
     */
    private fun spToPx(sp: Float, displayMetrics: DisplayMetrics): Float {
        return sp * (displayMetrics.scaledDensity)
    }
} 