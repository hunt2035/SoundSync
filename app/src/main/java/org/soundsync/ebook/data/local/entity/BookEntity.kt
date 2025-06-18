package org.soundsync.ebook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.soundsync.ebook.domain.model.Book
import org.soundsync.ebook.domain.model.BookType

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
    val lastOpenedDate: Long,
    val urlPath: String? = null, // 网页导入的URL
    val originalFilePath: String? = null // 原始文件路径，例如PDF转TXT时保存原PDF路径
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
            lastOpenedDate = lastOpenedDate,
            urlPath = urlPath,
            originalFilePath = originalFilePath
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
                lastOpenedDate = book.lastOpenedDate,
                urlPath = book.urlPath,
                originalFilePath = book.originalFilePath
            )
        }
    }
} 