package com.wanderreads.ebook.util.reader.model

/**
 * 阅读器内容模型
 * 表示阅读器当前页面的内容
 */
data class ReaderContent(
    val text: String,                  // 纯文本内容
    val html: String? = null,          // HTML内容（用于EPUB等）
    val pageIndex: Int,                // 当前页码
    val chapterIndex: Int,             // 当前章节索引
    val isLastPage: Boolean = false,   // 是否是最后一页
    val isFirstPage: Boolean = false   // 是否是第一页
)

/**
 * 书籍章节信息
 */
data class BookChapter(
    val title: String,                // 章节标题
    val index: Int,                   // 章节索引
    val startPosition: Int,           // 章节起始位置
    val content: String = "",         // 章节内容
    val subChapters: List<BookChapter> = emptyList() // 子章节列表
)

/**
 * 阅读器配置
 */
data class ReaderConfig(
    val fontSize: Int = 18,            // 字体大小
    val lineHeight: Float = 1.6f,      // 行高
    val fontFamily: String = "Default", // 字体族
    val isDarkMode: Boolean = false,    // 是否暗黑模式
    val margin: Int = 20,               // 边距
    val backgroundColor: Int? = null,   // 背景颜色
    val textColor: Int? = null,         // 文字颜色
    val paragraphSpacing: Float = 1.0f  // 段落间距
) 