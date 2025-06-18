package org.soundsync.ebook.domain.model

import java.util.UUID

/**
 * 笔记模型
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val page: Int,
    val position: Float,
    val selectedText: String = "",
    val content: String,
    val color: Int = 0xFFFFFF00.toInt(), // 默认黄色高亮
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) 