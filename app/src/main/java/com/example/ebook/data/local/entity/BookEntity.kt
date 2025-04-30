package com.example.ebook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ebook.domain.model.Book
import com.example.ebook.domain.model.BookType

/**
 * 电子书数据库实体
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String?,
    val fileHash: String,
    val type: String,
    val lastReadPage: Int,
    val lastReadPosition: Float,
    val totalPages: Int,
    val addedDate: Long,
    val lastOpenedDate: Long
) {
    fun toBook(): Book {
        return Book(
            id = id,
            title = title,
            author = author,
            filePath = filePath,
            coverPath = coverPath,
            fileHash = fileHash,
            type = BookType.valueOf(type),
            lastReadPage = lastReadPage,
            lastReadPosition = lastReadPosition,
            totalPages = totalPages,
            addedDate = addedDate,
            lastOpenedDate = lastOpenedDate
        )
    }
    
    companion object {
        fun fromBook(book: Book): BookEntity {
            return BookEntity(
                id = book.id,
                title = book.title,
                author = book.author,
                filePath = book.filePath,
                coverPath = book.coverPath,
                fileHash = book.fileHash,
                type = book.type.name,
                lastReadPage = book.lastReadPage,
                lastReadPosition = book.lastReadPosition,
                totalPages = book.totalPages,
                addedDate = book.addedDate,
                lastOpenedDate = book.lastOpenedDate
            )
        }
    }
} 