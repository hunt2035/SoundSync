package com.wanderreads.ebook.domain.model

import java.util.UUID

/**
 * 书签模型
 */
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val page: Int,
    val position: Float,
    val title: String,
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis()
) 