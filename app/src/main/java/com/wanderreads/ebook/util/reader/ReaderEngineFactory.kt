package com.example.ebook.util.reader

import android.content.Context
import com.example.ebook.domain.model.BookFormat
import com.example.ebook.util.reader.epub.EpubReaderEngine
import com.example.ebook.util.reader.txt.TxtReaderEngine

/**
 * 阅读引擎工厂类
 * 负责创建不同格式书籍的阅读引擎
 */
object ReaderEngineFactory {
    /**
     * 创建阅读引擎
     * @param context 上下文
     * @param format 书籍格式
     * @return 适合该格式的阅读引擎实例
     */
    fun createEngine(context: Context, format: BookFormat): BookReaderEngine {
        return when (format) {
            BookFormat.TXT -> TxtReaderEngine(context)
            BookFormat.EPUB -> EpubReaderEngine(context)
            // 暂时其他格式也用TXT阅读引擎处理
            BookFormat.PDF -> TxtReaderEngine(context)
            BookFormat.MOBI -> TxtReaderEngine(context)
            BookFormat.UNKNOWN -> TxtReaderEngine(context)
        }
    }
} 