package org.soundsync.ebook.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.soundsync.ebook.data.repository.BookRepository
import org.soundsync.ebook.domain.model.Book
import org.soundsync.ebook.domain.model.BookType
import java.io.File

/**
 * 阅读进度调试工具
 * 用于诊断和修复阅读进度显示问题
 */
class ReadingProgressDebugger(
    private val context: Context,
    private val bookRepository: BookRepository
) {
    companion object {
        private const val TAG = "ReadingProgressDebugger"
    }

    /**
     * 检查所有书籍的阅读进度状态
     */
    suspend fun checkAllBooksProgress() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始检查所有书籍的阅读进度状态...")

                bookRepository.getAllBooks().collect { books ->
                    Log.d(TAG, "总共找到 ${books.size} 本书籍")

                    books.forEachIndexed { index, book ->
                        checkSingleBookProgress(book, index + 1)
                    }

                    // 统计问题书籍
                    val problemBooks = books.filter { it.totalPages == 0 }
                    Log.w(TAG, "发现 ${problemBooks.size} 本书籍的totalPages为0，这会导致阅读进度显示为0%")

                    problemBooks.forEach { book ->
                        Log.w(TAG, "问题书籍: ${book.title} (${book.type.name}) - totalPages=${book.totalPages}, lastReadPage=${book.lastReadPage}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查书籍进度时发生错误", e)
            }
        }
    }

    /**
     * 检查单本书籍的进度状态
     */
    private fun checkSingleBookProgress(book: Book, index: Int) {
        Log.d(TAG, "[$index] 检查书籍: ${book.title}")
        Log.d(TAG, "  - 文件路径: ${book.filePath}")
        Log.d(TAG, "  - 书籍类型: ${book.type.name}")
        Log.d(TAG, "  - 总页数: ${book.totalPages}")
        Log.d(TAG, "  - 最后阅读页: ${book.lastReadPage}")
        Log.d(TAG, "  - 最后阅读位置: ${book.lastReadPosition}")
        Log.d(TAG, "  - 计算的阅读进度: ${(book.readingProgress * 100).toInt()}%")
        
        // 检查文件是否存在
        val file = File(book.filePath)
        if (!file.exists()) {
            Log.w(TAG, "  - 警告: 文件不存在!")
            return
        }
        
        // 检查totalPages是否合理
        if (book.totalPages == 0) {
            Log.w(TAG, "  - 问题: totalPages为0，需要修复")
            
            // 尝试重新计算页数
            val estimatedPages = estimateBookPages(book)
            Log.d(TAG, "  - 估算页数: $estimatedPages")
        } else if (book.lastReadPage > book.totalPages) {
            Log.w(TAG, "  - 问题: lastReadPage(${book.lastReadPage}) > totalPages(${book.totalPages})")
        }
        
        Log.d(TAG, "  - 文件大小: ${formatFileSize(file.length())}")
        Log.d(TAG, "  ---")
    }

    /**
     * 修复所有问题书籍的totalPages
     */
    suspend fun fixAllBooksProgress() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始修复所有书籍的阅读进度问题...")

                bookRepository.getAllBooks().collect { books ->
                    val problemBooks = books.filter { it.totalPages == 0 }

                    Log.d(TAG, "找到 ${problemBooks.size} 本需要修复的书籍")

                    problemBooks.forEach { book ->
                        fixSingleBookProgress(book)
                    }

                    Log.d(TAG, "修复完成!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "修复书籍进度时发生错误", e)
            }
        }
    }

    /**
     * 修复单本书籍的进度问题
     */
    private suspend fun fixSingleBookProgress(book: Book) {
        try {
            Log.d(TAG, "修复书籍: ${book.title}")
            
            val estimatedPages = estimateBookPages(book)
            if (estimatedPages > 0) {
                val updatedBook = book.copy(totalPages = estimatedPages)
                bookRepository.updateBook(updatedBook)
                
                Log.d(TAG, "  - 已更新totalPages: 0 -> $estimatedPages")
                Log.d(TAG, "  - 新的阅读进度: ${(updatedBook.readingProgress * 100).toInt()}%")
            } else {
                Log.w(TAG, "  - 无法估算页数，跳过修复")
            }
        } catch (e: Exception) {
            Log.e(TAG, "修复书籍 ${book.title} 时发生错误", e)
        }
    }

    /**
     * 估算书籍页数
     */
    private fun estimateBookPages(book: Book): Int {
        val file = File(book.filePath)
        if (!file.exists()) return 0
        
        return when (book.type) {
            BookType.TXT -> {
                // TXT文件：每2000字符算一页
                (file.length() / 2000).toInt().coerceAtLeast(1)
            }
            BookType.PDF -> {
                // PDF文件：尝试读取实际页数，失败则按文件大小估算
                try {
                    // 这里可以使用PDF库来获取实际页数
                    // 暂时使用文件大小估算：每50KB一页
                    (file.length() / (50 * 1024)).toInt().coerceAtLeast(1)
                } catch (e: Exception) {
                    (file.length() / (50 * 1024)).toInt().coerceAtLeast(1)
                }
            }
            BookType.EPUB -> {
                // EPUB文件：按文件大小估算，每100KB一章
                (file.length() / (100 * 1024)).toInt().coerceAtLeast(1)
            }
            else -> {
                // 其他格式：按文件大小估算
                (file.length() / 10000).toInt().coerceAtLeast(1)
            }
        }
    }

    /**
     * 格式化文件大小显示
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * 检查特定书籍的TTS进度更新
     */
    suspend fun checkTtsProgressUpdate(bookId: String, pageIndex: Int) {
        withContext(Dispatchers.IO) {
            try {
                val book = bookRepository.getBookById(bookId)
                if (book != null) {
                    Log.d(TAG, "检查TTS进度更新:")
                    Log.d(TAG, "  - 书籍: ${book.title}")
                    Log.d(TAG, "  - 当前totalPages: ${book.totalPages}")
                    Log.d(TAG, "  - TTS页面索引: $pageIndex")
                    Log.d(TAG, "  - 当前lastReadPage: ${book.lastReadPage}")
                    Log.d(TAG, "  - 当前阅读进度: ${(book.readingProgress * 100).toInt()}%")

                    when {
                        book.totalPages == 0 -> {
                            Log.w(TAG, "  - 警告: totalPages为0，TTS更新进度无效!")
                        }
                        pageIndex >= book.totalPages -> {
                            Log.w(TAG, "  - 警告: TTS页面索引 " + pageIndex + " >= totalPages " + book.totalPages)
                        }
                        else -> {
                            // 正常情况，无需特殊处理
                        }
                    }
                } else {
                    Log.e(TAG, "找不到书籍: $bookId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查TTS进度更新时发生错误", e)
            }
        }
    }
}
