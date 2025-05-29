package com.wanderreads.ebook.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderreads.ebook.MainActivity
import com.wanderreads.ebook.data.repository.BookRepository
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.domain.model.BookType
import com.wanderreads.ebook.util.ChapterInfo
import com.wanderreads.ebook.util.EpubRenderer
import com.wanderreads.ebook.util.EpubRenderer.EpubBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.withContext
import android.graphics.Typeface
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.TypedValue
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 阅读器ViewModel，处理电子书阅读相关的逻辑
 */
class ReaderViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val bookId: String
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val epubRenderer = EpubRenderer(context)
    
    private var epubBook: EpubBook? = null
    
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // 初始化函数，加载图书数据
    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 从仓库获取图书信息
                val book = bookRepository.getBookById(bookId)
                
                if (book != null) {
                    // 更新图书信息
                    _uiState.update { it.copy(book = book) }
                    
                    // 加载图书内容
                    loadContent(book)
                    
                    // 更新图书的最后打开时间
                    bookRepository.updateLastOpenedDate(book.id, System.currentTimeMillis())
                } else {
                    _uiState.update { it.copy(error = "找不到图书") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "加载图书失败: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadContent(book: Book) = 
        withContext(Dispatchers.IO) {
            try {
                when (book.type) {
                    BookType.EPUB -> loadEpub(book)
                    BookType.PDF -> loadPdfContent(book)
                    BookType.TXT -> loadTxt(book)
                    else -> _uiState.update { it.copy(error = "暂不支持此格式: ${book.type}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "加载内容失败: ${e.message}") }
            }
        }

    private fun loadTxt(book: Book) {
        try {
            val file = File(book.filePath)
            val reader = BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8"))
            val content = StringBuilder()
            var line: String?
            
            // 读取整个文件内容
            line = reader.readLine()
            while (line != null) {
                content.append(line).append("\n")
                line = reader.readLine()
            }
            
            reader.close()
            
            // 将内容按照屏幕大小分页
            val pages = paginateTextByScreenSize(content.toString())
            
            _uiState.update { 
                it.copy(
                    bookContent = content.toString(),
                    currentPage = book.lastReadPage.coerceIn(0, pages.size - 1),
                    pages = pages,
                    totalPages = pages.size
                ) 
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "解析TXT文件失败: ${e.message}") }
        }
    }

    private fun loadEpub(book: Book) {
        viewModelScope.launch {
            try {
                // 解析EPUB文件
                epubRenderer.parseEpub(book.filePath).fold(
                    onSuccess = { parsedBook ->
                        epubBook = parsedBook
                        
                        // 提取章节信息
                        val chapters = epubRenderer.extractChapterTitles(parsedBook)
                        
                        // 设置总章节数
                        val totalChapters = epubRenderer.getTotalChapters(parsedBook)
                        
                        // 加载上次阅读的章节，如果是新书则从第一章开始
                        val startChapter = book.lastReadPage.coerceIn(0, totalChapters - 1)
                        
                        _uiState.update { state ->
                            state.copy(
                                bookType = BookType.EPUB,
                                chapters = chapters,
                                chapterTitles = chapters.map { it.title },
                                totalChapters = totalChapters,
                                currentChapterIndex = startChapter,
                                isLoading = true // 保持加载状态，直到章节内容加载完成
                            )
                        }
                        
                        // 加载章节内容
                        loadEpubChapter(startChapter)
                    },
                    onFailure = { error ->
                        _uiState.update { 
                            it.copy(
                                error = "解析EPUB文件失败: ${error.message}",
                                isLoading = false
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "加载EPUB图书失败: ${e.message}", isLoading = false) }
            }
        }
    }

    private fun loadEpubChapter(chapterIndex: Int) {
        val book = uiState.value.book ?: return
        val epub = epubBook ?: return
        
        viewModelScope.launch {
            epubRenderer.prepareChapterForWebView(epub, chapterIndex).fold(
                onSuccess = { html ->
                    _uiState.update { 
                        it.copy(
                            currentChapterHtml = html,
                            currentChapterIndex = chapterIndex,
                            isLoading = false,
                            error = null
                        ) 
                    }
                    
                    // 保存阅读进度
                    bookRepository.updateReadingProgress(book.id, chapterIndex, 0f)
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            error = "加载章节失败: ${error.message}",
                            isLoading = false
                        ) 
                    }
                }
            )
        }
    }

    // 将内容分页
    private fun splitContentIntoPages(content: String): List<String> {
        val charsPerPage = 1000 // 每页显示的字符数
        val pages = mutableListOf<String>()
        
        var startIndex = 0
        while (startIndex < content.length) {
            val endIndex = minOf(startIndex + charsPerPage, content.length)
            pages.add(content.substring(startIndex, endIndex))
            startIndex = endIndex
        }
        
        return pages
    }

    // 新的分页方法，基于屏幕尺寸计算每页内容
    private fun paginateTextByScreenSize(content: String): List<String> {
        val pages = mutableListOf<String>()
        
        // 获取屏幕尺寸
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 计算可用于文本显示的区域（考虑边距）
        val horizontalMargin = dpToPx(32f, displayMetrics) // 左右各16dp边距
        val verticalMargin = dpToPx(48f, displayMetrics)   // 上下各24dp边距
        val availableWidth = screenWidth - horizontalMargin
        val availableHeight = screenHeight - verticalMargin
        
        // 创建TextPaint来测量文本
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = spToPx(18f, displayMetrics) // 18sp的字体大小
            typeface = Typeface.SERIF
        }
        
        // 计算行高
        val lineHeight = textPaint.fontSpacing * 1.5f // 1.5倍行距
        
        // 计算每页可容纳的行数
        val linesPerPage = (availableHeight / lineHeight).toInt()
        
        // 将文本分成行
        val lines = mutableListOf<String>()
        val paragraphs = content.split("\n")
        
        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("")
                continue
            }
            
            var startIndex = 0
            while (startIndex < paragraph.length) {
                val remainingText = paragraph.substring(startIndex)
                val measuredWidth = textPaint.measureText(remainingText)
                
                if (measuredWidth <= availableWidth) {
                    // 剩余文本可以放在一行
                    lines.add(remainingText)
                    break
                } else {
                    // 需要计算这一行可以放多少文字
                    var endIndex = 0
                    var lastSpace = -1
                    var lineWidth = 0f
                    
                    for (i in remainingText.indices) {
                        val char = remainingText[i]
                        val charWidth = textPaint.measureText(char.toString())
                        
                        if (lineWidth + charWidth > availableWidth) {
                            endIndex = i
                            break
                        }
                        
                        lineWidth += charWidth
                        if (char == ' ') {
                            lastSpace = i
                        }
                    }
                    
                    // 如果有空格，尽量在空格处断行
                    if (lastSpace != -1 && lastSpace > 0) {
                        endIndex = lastSpace + 1
                    }
                    
                    if (endIndex == 0) {
                        // 如果一个字都放不下，至少放一个字
                        endIndex = 1
                    }
                    
                    lines.add(remainingText.substring(0, endIndex))
                    startIndex += endIndex
                }
            }
        }
        
        // 将行组合成页
        var currentPage = StringBuilder()
        var currentLineCount = 0
        
        for (line in lines) {
            if (currentLineCount >= linesPerPage) {
                // 当前页已满，创建新页
                pages.add(currentPage.toString())
                currentPage = StringBuilder(line).append("\n")
                currentLineCount = 1
            } else {
                // 继续添加行到当前页
                currentPage.append(line).append("\n")
                currentLineCount++
            }
        }
        
        // 添加最后一页（如果有内容）
        if (currentPage.isNotEmpty()) {
            pages.add(currentPage.toString())
        }
        
        return pages
    }
    
    // 辅助方法：将dp转为像素
    private fun dpToPx(dp: Float, displayMetrics: DisplayMetrics): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, displayMetrics)
    }
    
    // 辅助方法：将sp转为像素
    private fun spToPx(sp: Float, displayMetrics: DisplayMetrics): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, displayMetrics)
    }

    // 翻到下一页
    fun nextPage() {
        _uiState.update { currentState ->
            if (currentState.currentPage < currentState.totalPages - 1) {
                val newPage = currentState.currentPage + 1
                saveReaderProgress(newPage)
                updateGlobalReadingPosition(currentState.book?.id, newPage, currentState.totalPages)
                currentState.copy(currentPage = newPage)
            } else {
                currentState
            }
        }
    }

    // 翻到上一页
    fun previousPage() {
        _uiState.update { currentState ->
            if (currentState.currentPage > 0) {
                val newPage = currentState.currentPage - 1
                saveReaderProgress(newPage)
                updateGlobalReadingPosition(currentState.book?.id, newPage, currentState.totalPages)
                currentState.copy(currentPage = newPage)
            } else {
                currentState
            }
        }
    }

    // 跳转到指定页
    fun goToPage(page: Int) {
        _uiState.update { currentState ->
            if (page >= 0 && page < currentState.totalPages) {
                saveReaderProgress(page)
                updateGlobalReadingPosition(currentState.book?.id, page, currentState.totalPages)
                currentState.copy(currentPage = page)
            } else {
                currentState
            }
        }
    }
    
    // 更新全局阅读位置
    private fun updateGlobalReadingPosition(bookId: String?, currentPage: Int, totalPages: Int) {
        val mainActivity = MainActivity.getInstance()
        mainActivity?.updateReadingPosition(bookId, currentPage, totalPages)
    }

    // 保存阅读进度
    private fun saveReaderProgress(page: Int) {
        val book = _uiState.value.book ?: return
        
        viewModelScope.launch {
            try {
                // 计算阅读进度百分比
                val progress = if (_uiState.value.totalPages > 0) {
                    page.toFloat() / _uiState.value.totalPages.toFloat()
                } else {
                    0.0f
                }
                
                // 更新数据库
                bookRepository.updateReadingProgress(
                    bookId = book.id,
                    lastReadPage = page,
                    lastReadPosition = progress
                )
            } catch (e: Exception) {
                // 忽略保存进度时的错误
            }
        }
    }
    
    /**
     * 获取当前页内容
     * 用于TTS功能
     */
    fun getContentForCurrentPage(): String {
        return _uiState.value.pages.getOrNull(_uiState.value.currentPage) ?: ""
    }

    // PDF加载函数    
    private fun loadPdfContent(book: Book) {
        // 实际实现PDF加载功能
        // 这里只是占位代码
        _uiState.update { it.copy(error = "PDF加载功能暂未实现") }
    }
}

/**
 * 阅读器界面状态
 */
data class ReaderUiState(
    val isLoading: Boolean = false,
    val book: Book? = null,
    val bookContent: String = "",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val pages: List<String> = emptyList(),
    val error: String? = null,
    val bookType: BookType? = null,
    val chapters: List<ChapterInfo> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
    val totalChapters: Int = 0,
    val currentChapterIndex: Int = 0,
    val currentChapterHtml: String = ""
) 