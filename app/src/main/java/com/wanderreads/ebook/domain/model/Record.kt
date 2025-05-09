package com.wanderreads.ebook.domain.model

/**
 * 录音记录模型
 */
data class Record(
    val id: String,
    val bookId: String,
    val title: String = "",
    val addedDate: Long = 0, // 录音创建时间
    val voiceLength: Int = 0, // 录音时长（秒）
    var voiceFilePath: String,
    val chapterIndex: Long? = null,
    val pageIndex: Long? = null
) {
    companion object {
        // 添加兼容性转换方法，支持从旧格式转换
        fun fromLegacy(
            recId: String,
            bookId: String,
            title: String,
            createdAt: Long,
            duration: Long,
            voiceFilePath: String,
            chapterIndex: Long? = null,
            pageIndex: Long? = null
        ): Record {
            return Record(
                id = recId,
                bookId = bookId,
                title = title,
                addedDate = createdAt,
                voiceLength = (duration / 1000).toInt(), // 转换为秒
                voiceFilePath = voiceFilePath,
                chapterIndex = chapterIndex,
                pageIndex = pageIndex
            )
        }
    }
} 