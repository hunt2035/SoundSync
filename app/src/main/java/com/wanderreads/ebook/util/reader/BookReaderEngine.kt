package com.wanderreads.ebook.util.reader

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.domain.model.BookFormat
import com.wanderreads.ebook.domain.model.BookType
import com.wanderreads.ebook.util.PageDirection
import com.wanderreads.ebook.util.reader.model.BookChapter
import com.wanderreads.ebook.util.reader.model.ReaderConfig
import com.wanderreads.ebook.util.reader.model.ReaderContent
import kotlinx.coroutines.flow.StateFlow

/**
 * 阅读引擎接口
 * 所有格式的阅读器实现都应该遵循这个接口
 */
interface BookReaderEngine {
    /**
     * 引擎状态流
     */
    val state: StateFlow<ReaderEngineState>
    
    /**
     * 初始化引擎
     * @param book 要加载的书籍
     * @param initialPosition 初始阅读位置
     */
    suspend fun initialize(book: Book, initialPosition: Int = 0)
    
    /**
     * 加载内容
     */
    suspend fun loadContent()
    
    /**
     * 获取当前页面内容
     */
    fun getCurrentPageContent(): ReaderContent
    
    /**
     * 获取当前章节标题
     */
    fun getCurrentChapterTitle(): String
    
    /**
     * 翻页
     * @param direction 翻页方向
     * @return 翻页后的新页码
     */
    suspend fun navigatePage(direction: PageDirection): Int
    
    /**
     * 跳转到指定页
     * @param pageIndex 目标页码
     */
    suspend fun goToPage(pageIndex: Int)
    
    /**
     * 跳转到指定章节
     * @param chapterIndex 目标章节索引
     */
    suspend fun goToChapter(chapterIndex: Int)
    
    /**
     * 获取章节列表
     */
    fun getChapters(): List<BookChapter>
    
    /**
     * 获取当前阅读进度
     * @return 0.0-1.0之间的值，表示阅读进度百分比
     */
    fun getReadingProgress(): Float
    
    /**
     * 获取当前页面的纯文本内容（用于TTS）
     */
    fun getCurrentPageText(): String
    
    /**
     * 获取当前章节的全部文本内容（用于TTS和语音合成）
     */
    fun getCurrentChapterText(): String
    
    /**
     * 检查是否有下一页
     * @return 是否有下一页
     */
    fun hasNextPage(): Boolean
    
    /**
     * 获取总页数
     * @return 总页数
     */
    fun getTotalPages(): Int
    
    /**
     * 更新阅读配置
     * @param config 新的阅读配置
     */
    fun updateConfig(config: ReaderConfig)
    
    /**
     * 保存阅读进度
     */
    suspend fun saveReadingProgress()
    
    /**
     * 清理资源
     */
    fun close()
    
    /**
     * 搜索文本
     * @param query 搜索关键词
     * @return 搜索结果列表
     */
    suspend fun searchText(query: String): List<SearchResult>
    
    /**
     * 获取书籍封面
     */
    suspend fun getBookCover(): Bitmap?
    
    /**
     * 初始化文本朗读
     */
    fun initTts(tts: TextToSpeech)
    
    companion object {
        /**
         * 创建适合指定格式的阅读引擎
         */
        fun create(context: Context, type: BookType): BookReaderEngine {
            // 将BookType转换为BookFormat
            val format = when(type) {
                BookType.EPUB -> BookFormat.EPUB
                BookType.PDF -> BookFormat.PDF
                BookType.TXT -> BookFormat.TXT
                BookType.UNKNOWN -> BookFormat.UNKNOWN
            }
            return ReaderEngineFactory.createEngine(context, format)
        }
    }
}

/**
 * 搜索结果
 */
data class SearchResult(
    val pageIndex: Int,
    val chapterIndex: Int,
    val text: String,
    val highlightStart: Int,
    val highlightEnd: Int
)

/**
 * 阅读引擎状态
 */
data class ReaderEngineState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val currentChapter: Int = 0,
    val totalChapters: Int = 0,
    val readingProgress: Float = 0f,
    val book: Book? = null
) 