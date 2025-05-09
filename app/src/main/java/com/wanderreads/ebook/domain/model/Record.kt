package com.wanderreads.ebook.domain.model

/**
 * 录音记录模型
 */
data class Record(
    val recId: String,
    val bookId: String,
    val title: String,
    val createdAt: Long, // 录音创建时间
    val duration: Long, // 录音时长（毫秒）
    val voiceFilePath: String,
    val chapterIndex: Long? = null,
    val pageIndex: Long? = null
) 