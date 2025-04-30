package com.example.ebook.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebook.data.repository.BookRepository
import com.example.ebook.domain.model.Book
import com.example.ebook.util.EpubRenderer
import com.example.ebook.util.PageDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.ebook.util.EpubRenderer.EpubBook

/**
 * EPUB阅读器视图模型
 */
class EpubReaderViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val bookId: String
) : AndroidViewModel(application) {

    // EPUB渲染工具
    private val epubRenderer = EpubRenderer(application.applicationContext)
    
    // 当前解析的EPUB Book对象
    private var epubBook: EpubBook? = null
    
    // UI状态流
    private val _uiState = MutableStateFlow(EpubReaderUiState())
    val uiState: StateFlow<EpubReaderUiState> = _uiState.asStateFlow()
    
    init {
        loadBook()
    }
    
    /**
     * 加载图书
     */
    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 从仓库获取图书信息
                val book = bookRepository.getBookById(bookId)
                
                if (book != null) {
                    // 更新图书信息
                    _uiState.update { it.copy(book = book) }
                    
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
                                    chapters = chapters,
                                    chapterTitles = chapters.map { it.title },
                                    totalChapters = totalChapters,
                                    currentChapterIndex = startChapter,
                                    isLoading = true // 保持加载状态，直到章节内容加载完成
                                )
                            }
                            
                            // 加载章节内容
                            loadChapter(startChapter)
                            
                            // 更新图书的最后打开时间
                            bookRepository.updateLastOpenedDate(book.id, System.currentTimeMillis())
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
                } else {
                    _uiState.update { it.copy(error = "找不到图书", isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "加载图书失败: ${e.message}", isLoading = false) }
            }
        }
    }
    
    /**
     * 加载指定章节
     */
    private fun loadChapter(chapterIndex: Int) {
        val book = uiState.value.book ?: return
        val epub = epubBook ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
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
    
    /**
     * 导航到指定章节
     */
    fun navigateToChapter(chapterIndex: Int) {
        if (chapterIndex != uiState.value.currentChapterIndex) {
            loadChapter(chapterIndex)
        }
    }
    
    /**
     * 前往下一章
     */
    fun nextChapter() {
        val currentIndex = uiState.value.currentChapterIndex
        val totalChapters = uiState.value.totalChapters
        
        if (currentIndex < totalChapters - 1) {
            loadChapter(currentIndex + 1)
        }
    }
    
    /**
     * 前往上一章
     */
    fun previousChapter() {
        val currentIndex = uiState.value.currentChapterIndex
        
        if (currentIndex > 0) {
            loadChapter(currentIndex - 1)
        }
    }
    
    /**
     * 翻页导航
     */
    fun navigatePage(direction: PageDirection) {
        // 返回翻页的JavaScript
        val js = epubRenderer.getPageNavigationJs(direction)
        _uiState.update { it.copy(pageNavigationJs = js) }
    }
    
    /**
     * 章节内到达末页 - 加载下一章
     */
    fun onLastPage() {
        nextChapter()
    }
    
    /**
     * 章节内到达首页 - 加载上一章
     */
    fun onFirstPage() {
        previousChapter()
    }
    
    /**
     * 更新页面信息
     */
    fun updatePageInfo(currentPage: Int, totalPages: Int) {
        val book = uiState.value.book ?: return
        
        // 计算章节内的阅读位置
        val progress = if (totalPages > 1) currentPage.toFloat() / (totalPages - 1) else 0f
        
        viewModelScope.launch {
            bookRepository.updateReadingProgress(
                bookId = book.id,
                lastReadPage = uiState.value.currentChapterIndex,
                lastReadPosition = progress
            )
        }
        
        _uiState.update { 
            it.copy(
                currentPage = currentPage,
                totalPages = totalPages,
                inChapterProgress = progress
            )
        }
    }
    
    /**
     * 更新阅读方向
     */
    fun updateReadingDirection(isRtl: Boolean) {
        _uiState.update { it.copy(isRightToLeft = isRtl) }
    }
    
    /**
     * 调整字体大小
     */
    fun changeFontSize(size: Int) {
        _uiState.update { 
            it.copy(
                fontSize = size,
                fontSizeJs = epubRenderer.getFontSizeJs(size)
            )
        }
    }
    
    /**
     * 调整行高
     */
    fun changeLineHeight(height: Float) {
        _uiState.update { 
            it.copy(
                lineHeight = height,
                lineHeightJs = epubRenderer.getLineHeightJs(height)
            )
        }
    }
    
    /**
     * 切换阅读主题
     */
    fun toggleTheme(isDarkMode: Boolean) {
        _uiState.update { 
            it.copy(
                isDarkMode = isDarkMode,
                themeJs = epubRenderer.getThemeJs(isDarkMode)
            )
        }
    }
    
    /**
     * 切换字体
     */
    fun changeFont(fontFamily: String) {
        _uiState.update { 
            it.copy(
                fontFamily = fontFamily,
                fontFamilyJs = epubRenderer.getFontFamilyJs(fontFamily)
            )
        }
    }
    
    /**
     * 页面加载完成的回调
     */
    fun onPageLoadFinished() {
        // 重置页面导航JS
        _uiState.update { it.copy(pageNavigationJs = null) }
    }
    
    /**
     * 设置页面边距
     */
    fun setMargin(margin: Int) {
        _uiState.update { it.copy(margin = margin) }
    }
    
    /**
     * 获取设置字体大小的JavaScript
     */
    fun getFontSizeJs(): String {
        return epubRenderer.getFontSizeJs(uiState.value.fontSize)
    }
    
    /**
     * 获取设置行高的JavaScript
     */
    fun getLineHeightJs(): String {
        return epubRenderer.getLineHeightJs(uiState.value.lineHeight)
    }
    
    /**
     * 获取设置主题的JavaScript
     */
    fun getThemeJs(): String {
        return epubRenderer.getThemeJs(uiState.value.isDarkMode)
    }
    
    /**
     * 获取设置字体的JavaScript
     */
    fun getFontFamilyJs(): String {
        return epubRenderer.getFontFamilyJs(uiState.value.fontFamily)
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        // 清理不需要的资源
    }
}

/**
 * EPUB阅读器UI状态
 */
data class EpubReaderUiState(
    val book: Book? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentChapterHtml: String? = null,
    val currentChapterIndex: Int = 0,
    val totalChapters: Int = 0,
    val chapters: List<com.example.ebook.util.ChapterInfo> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val inChapterProgress: Float = 0f,
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val isDarkMode: Boolean = false,
    val fontFamily: String = "Default",
    val isRightToLeft: Boolean = false,
    val margin: Int = 20,
    val fontSizeJs: String? = null,
    val lineHeightJs: String? = null,
    val fontFamilyJs: String? = null,
    val themeJs: String? = null,
    val pageNavigationJs: String? = null
) 