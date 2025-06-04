package com.wanderreads.ebook.data.repository

import android.content.Context
import com.wanderreads.ebook.data.local.dao.BookDao
import com.wanderreads.ebook.data.local.entity.BookEntity
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.domain.model.BookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 电子书仓库实现
 */
class BookRepositoryImpl(
    private val context: Context,
    private val bookDao: BookDao
) : BookRepository {
    
    /**
     * 获取Context对象
     */
    fun getContext(): Context {
        return context
    }
    
    override suspend fun addBook(book: Book) {
        bookDao.insertBook(BookEntity.fromBook(book))
    }
    
    override suspend fun updateBook(book: Book) {
        bookDao.updateBook(BookEntity.fromBook(book))
    }
    
    override suspend fun deleteBook(book: Book) {
        bookDao.deleteBook(BookEntity.fromBook(book))
    }
    
    override suspend fun getBookById(bookId: String): Book? {
        return bookDao.getBookById(bookId)?.toBook()
    }
    
    override fun getAllBooks(): Flow<List<Book>> {
        return bookDao.getAllBooks().map { entities ->
            entities.map { it.toBook() }
        }
    }
    
    override fun getRecentBooks(limit: Int): Flow<List<Book>> {
        return bookDao.getRecentBooks(limit).map { entities ->
            entities.map { it.toBook() }
        }
    }
    
    override fun searchBooks(query: String): Flow<List<Book>> {
        return bookDao.searchBooks(query).map { entities ->
            entities.map { it.toBook() }
        }
    }
    
    /**
     * 更新图书阅读进度
     */
    override suspend fun updateReadingProgress(bookId: String, lastReadPage: Int, lastReadPosition: Float) {
        withContext(Dispatchers.IO) {
            val book = bookDao.getBookById(bookId)
            if (book != null) {
                bookDao.updateReadingProgress(
                    bookId = bookId,
                    lastReadPage = lastReadPage,
                    lastReadPosition = lastReadPosition,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
    
    override suspend fun updateLastOpenedDate(bookId: String, lastOpenedDate: Long) {
        bookDao.updateLastOpenedDate(
            bookId = bookId,
            lastOpenedDate = lastOpenedDate
        )
    }
    
    override suspend fun getBookByHash(fileHash: String): Book? {
        return bookDao.getBookByHash(fileHash)?.toBook()
    }
    
    override suspend fun importBookFromStorage(filePath: String): Result<Book> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("文件不存在"))
            }
            
            val bookType = determineBookType(file.name)
            if (bookType == BookType.UNKNOWN) {
                return@withContext Result.failure(Exception("不支持的文件格式"))
            }
            
            // 这里简单处理，实际应用中可能需要解析文件内容获取更多信息
            val title = file.nameWithoutExtension
            
            val book = Book(
                title = title,
                filePath = filePath,
                type = bookType
            )
            
            addBook(book)
            Result.success(book)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 根据文件名确定图书类型
    private fun determineBookType(fileName: String): BookType {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> BookType.PDF
            fileName.endsWith(".epub", ignoreCase = true) -> BookType.EPUB
            fileName.endsWith(".txt", ignoreCase = true) -> BookType.TXT
            fileName.endsWith(".md", ignoreCase = true) -> BookType.MD
            else -> BookType.UNKNOWN
        }
    }
} 