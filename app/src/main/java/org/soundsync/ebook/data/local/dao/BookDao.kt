package org.soundsync.ebook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.soundsync.ebook.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

/**
 * 电子书数据访问对象
 */
@Dao
interface BookDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)
    
    @Update
    suspend fun updateBook(book: BookEntity)
    
    @Delete
    suspend fun deleteBook(book: BookEntity)
    
    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: String): BookEntity?
    
    @Query("SELECT * FROM books WHERE fileHash = :fileHash LIMIT 1")
    suspend fun getBookByHash(fileHash: String): BookEntity?
    
    @Query("SELECT * FROM books ORDER BY addedDate DESC")
    fun getAllBooks(): Flow<List<BookEntity>>
    
    @Query("SELECT * FROM books ORDER BY lastOpenedDate DESC LIMIT :limit")
    fun getRecentBooks(limit: Int): Flow<List<BookEntity>>
    
    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<BookEntity>>
    
    @Query("UPDATE books SET lastReadPage = :lastReadPage, lastReadPosition = :lastReadPosition, lastOpenedDate = :timestamp WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: String, lastReadPage: Int, lastReadPosition: Float, timestamp: Long)
    
    @Query("UPDATE books SET lastOpenedDate = :lastOpenedDate WHERE id = :bookId")
    suspend fun updateLastOpenedDate(bookId: String, lastOpenedDate: Long)
} 