package org.soundsync.ebook.domain.model

import java.io.File
import java.util.UUID

/**
 * 电子书模型
 */
data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String = "",
    val filePath: String,
    val coverPath: String? = null,
    val fileHash: String = "",
    val type: BookType,
    val lastReadPage: Int = 0,
    val lastReadPosition: Float = 0f,
    val totalPages: Int = 0,
    val addedDate: Long = System.currentTimeMillis(),
    val lastOpenedDate: Long = System.currentTimeMillis(),
    val urlPath: String? = null,
    val originalFilePath: String? = null // 原始文件路径，例如PDF转TXT时保存原PDF路径
) {
    val file: File
        get() = File(filePath)
        
    val fileName: String
        get() = file.name
        
    val readingProgress: Float
        get() = if (totalPages > 0) lastReadPage.toFloat() / totalPages else 0f
}

/**
 * 电子书格式
 */
enum class BookFormat {
    PDF, EPUB, TXT, MOBI, MD, DOCX, DOC, UNKNOWN;
    
    companion object {
        fun fromFileName(fileName: String): BookFormat {
            return when {
                fileName.endsWith(".pdf", ignoreCase = true) -> PDF
                fileName.endsWith(".epub", ignoreCase = true) -> EPUB
                fileName.endsWith(".txt", ignoreCase = true) -> TXT
                fileName.endsWith(".mobi", ignoreCase = true) -> MOBI
                fileName.endsWith(".md", ignoreCase = true) -> MD
                fileName.endsWith(".docx", ignoreCase = true) -> DOCX
                fileName.endsWith(".doc", ignoreCase = true) -> DOC
                else -> UNKNOWN
            }
        }
    }
} 