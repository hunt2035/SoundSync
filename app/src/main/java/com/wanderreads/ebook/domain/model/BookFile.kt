package com.wanderreads.ebook.domain.model

import java.io.File

/**
 * 电子书文件模型
 */
data class BookFile(
    val file: File,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long
) {
    companion object {
        fun fromFile(file: File): BookFile {
            return BookFile(
                file = file,
                filePath = file.absolutePath,
                fileName = file.name,
                fileSize = file.length(),
                lastModified = file.lastModified()
            )
        }
    }
} 