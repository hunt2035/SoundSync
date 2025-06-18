package org.soundsync.ebook.data.repository

import org.soundsync.ebook.domain.model.Book
import kotlinx.coroutines.flow.Flow

/**
 * 电子书仓库接口
 */
interface BookRepository {
    
    /**
     * 添加电子书
     */
    suspend fun addBook(book: Book)
    
    /**
     * 更新电子书
     */
    suspend fun updateBook(book: Book)
    
    /**
     * 删除电子书
     */
    suspend fun deleteBook(book: Book)
    
    /**
     * 根据ID获取电子书
     */
    suspend fun getBookById(bookId: String): Book?
    
    /**
     * 获取所有电子书
     */
    fun getAllBooks(): Flow<List<Book>>
    
    /**
     * 获取最近阅读的电子书
     */
    fun getRecentBooks(limit: Int): Flow<List<Book>>
    
    /**
     * 搜索电子书
     */
    fun searchBooks(query: String): Flow<List<Book>>
    
    /**
     * 更新阅读进度
     */
    suspend fun updateReadingProgress(bookId: String, lastReadPage: Int, lastReadPosition: Float)
    
    /**
     * 更新电子书最后打开时间
     */
    suspend fun updateLastOpenedDate(bookId: String, lastOpenedDate: Long)
    
    /**
     * 从本地存储导入电子书
     */
    suspend fun importBookFromStorage(filePath: String): Result<Book>
    
    /**
     * 根据文件哈希获取电子书
     */
    suspend fun getBookByHash(fileHash: String): Book?
} 