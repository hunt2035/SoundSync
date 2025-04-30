package com.example.ebook.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebook.data.repository.BookRepository
import com.example.ebook.domain.model.Book
import com.example.ebook.util.PageDirection
import com.example.ebook.util.reader.BookReaderEngine
import com.example.ebook.util.reader.model.BookChapter
import com.example.ebook.util.reader.model.ReaderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import android.os.Bundle
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * 统一的阅读器ViewModel
 * 支持所有格式的电子书，依赖于BookReaderEngine接口进行实际处理
 */
class UnifiedReaderViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val bookId: String
) : AndroidViewModel(application) {

    // 阅读引擎
    private var readerEngine: BookReaderEngine? = null
    
    // UI状态
    private val _uiState = MutableStateFlow(UnifiedReaderUiState())
    val uiState: StateFlow<UnifiedReaderUiState> = _uiState.asStateFlow()
    
    // TTS引擎
    private var tts: TextToSpeech? = null
    private var isTtsActive = false
    
    init {
        loadBook()
    }
    
    /**
     * 加载书籍
     */
    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 从仓库获取书籍信息
                val book = bookRepository.getBookById(bookId)
                
                if (book != null) {
                    // 创建适合此书籍格式的阅读引擎
                    readerEngine = BookReaderEngine.create(getApplication(), book.type)
                    
                    // 初始化引擎
                    readerEngine?.initialize(book, book.lastReadPage)
                    
                    // 订阅引擎状态更新
                    observeEngineState()
                    
                    // 更新书籍的最后打开时间
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
    
    /**
     * 观察引擎状态
     */
    private fun observeEngineState() {
        viewModelScope.launch {
            readerEngine?.state?.collect { engineState ->
                _uiState.update { state ->
                    state.copy(
                        book = engineState.book,
                        isLoading = engineState.isLoading,
                        error = engineState.error,
                        currentPage = engineState.currentPage,
                        totalPages = engineState.totalPages,
                        currentChapter = engineState.currentChapter,
                        totalChapters = engineState.totalChapters,
                        readingProgress = engineState.readingProgress
                    )
                }
                
                // 如果章节或页面变化，更新当前内容
                updateCurrentContent()
            }
        }
    }
    
    /**
     * 更新当前页面内容
     */
    private fun updateCurrentContent() {
        viewModelScope.launch {
            try {
                val content = readerEngine?.getCurrentPageContent()
                
                _uiState.update { state ->
                    state.copy(
                        currentContent = content,
                        chapterTitle = readerEngine?.getCurrentChapterTitle() ?: ""
                    )
                }
                
                // 如果正在朗读，朗读新页面
                if (isTtsActive) {
                    speakCurrentPage()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "获取内容失败: ${e.message}") }
            }
        }
    }
    
    /**
     * 翻页
     */
    fun navigatePage(direction: PageDirection) {
        viewModelScope.launch {
            try {
                readerEngine?.navigatePage(direction)
                saveReadingProgress()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "翻页失败: ${e.message}") }
            }
        }
    }
    
    /**
     * 跳转到指定页
     */
    fun goToPage(pageIndex: Int) {
        viewModelScope.launch {
            try {
                readerEngine?.goToPage(pageIndex)
                saveReadingProgress()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "跳转页面失败: ${e.message}") }
            }
        }
    }
    
    /**
     * 跳转到指定章节
     */
    fun goToChapter(chapterIndex: Int) {
        viewModelScope.launch {
            try {
                readerEngine?.goToChapter(chapterIndex)
                saveReadingProgress()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "跳转章节失败: ${e.message}") }
            }
        }
    }
    
    /**
     * 获取章节列表
     */
    fun getChapters(): List<BookChapter> {
        return readerEngine?.getChapters() ?: emptyList()
    }
    
    /**
     * 更新阅读配置
     */
    fun updateConfig(config: ReaderConfig) {
        viewModelScope.launch {
            try {
                readerEngine?.updateConfig(config)
                
                _uiState.update { state ->
                    state.copy(
                        config = config
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "更新配置失败: ${e.message}") }
            }
        }
    }
    
    /**
     * 保存阅读进度
     */
    private suspend fun saveReadingProgress() {
        val book = uiState.value.book ?: return
        val currentPage = uiState.value.currentPage
        
        try {
            bookRepository.updateReadingProgress(
                bookId = book.id,
                lastReadPage = currentPage,
                lastReadPosition = uiState.value.readingProgress
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "保存进度失败: ${e.message}") }
        }
    }
    
    /**
     * 搜索文本
     */
    fun searchText(query: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSearching = true) }
                
                val results = readerEngine?.searchText(query) ?: emptyList()
                
                _uiState.update { state ->
                    state.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "搜索失败: ${e.message}",
                        isSearching = false
                    ) 
                }
            }
        }
    }
    
    /**
     * 获取书籍封面
     */
    fun getBookCover(): Bitmap? {
        return uiState.value.book?.coverPath?.let {
            try {
                android.graphics.BitmapFactory.decodeFile(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 初始化文本朗读
     */
    fun initTts(onInitListener: (status: Int) -> Unit) {
        if (tts == null) {
            tts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d("TTS", "开始朗读: $utteranceId")
                        }
                        
                        override fun onDone(utteranceId: String?) {
                            Log.d("TTS", "朗读完成: $utteranceId")
                            // 当前页朗读完成后，自动翻到下一页继续朗读
                            if (isTtsActive && uiState.value.currentPage < uiState.value.totalPages - 1) {
                                viewModelScope.launch {
                                    navigatePage(PageDirection.NEXT)
                                }
                            } else if (isTtsActive && uiState.value.currentPage >= uiState.value.totalPages - 1) {
                                // 到达最后一页，停止朗读
                                isTtsActive = false
                                Log.d("TTS", "已到达最后一页，停止朗读")
                            }
                        }
                        
                        override fun onError(utteranceId: String?) {
                            Log.e("TTS", "朗读错误: $utteranceId")
                        }
                    })
                    readerEngine?.initTts(tts!!)
                }
                onInitListener(status)
            }
        } else {
            onInitListener(TextToSpeech.SUCCESS)
        }
    }
    
    /**
     * 开始或停止朗读
     */
    fun toggleTts(): Boolean {
        isTtsActive = !isTtsActive
        
        if (isTtsActive) {
            speakCurrentPage()
        } else {
            tts?.stop()
        }
        
        return isTtsActive
    }
    
    /**
     * 朗读当前页面
     */
    private fun speakCurrentPage() {
        val textToSpeak = readerEngine?.getCurrentPageText() ?: return
        
        if (textToSpeak.isNotEmpty() && isTtsActive) {
            val params = Bundle()
            val utteranceId = "TTS_${UUID.randomUUID()}"
            tts?.speak(
                textToSpeak,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utteranceId
            )
        }
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        
        viewModelScope.launch {
            saveReadingProgress()
            
            tts?.stop()
            tts?.shutdown()
            tts = null
            
            readerEngine?.close()
            readerEngine = null
        }
    }

    private fun formatProgressText(): String {
        val currentPage = uiState.value.currentPage + 1
        val totalPages = uiState.value.totalPages
        return "${currentPage}/${totalPages}"
    }
}

/**
 * 统一阅读器UI状态
 */
data class UnifiedReaderUiState(
    val book: Book? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val currentChapter: Int = 0,
    val totalChapters: Int = 0,
    val chapterTitle: String = "",
    val currentContent: com.example.ebook.util.reader.model.ReaderContent? = null,
    val readingProgress: Float = 0f,
    val config: ReaderConfig = ReaderConfig(),
    val isSearching: Boolean = false,
    val searchResults: List<com.example.ebook.util.reader.SearchResult> = emptyList(),
    val showControls: Boolean = false
) 