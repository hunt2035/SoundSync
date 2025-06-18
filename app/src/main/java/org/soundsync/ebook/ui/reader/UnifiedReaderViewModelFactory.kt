package org.soundsync.ebook.ui.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.soundsync.ebook.data.repository.BookRepository
import org.soundsync.ebook.data.repository.RecordRepository

/**
 * 统一阅读器ViewModelFactory
 * 用于创建UnifiedReaderViewModel实例
 */
class UnifiedReaderViewModelFactory(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val recordRepository: RecordRepository,
    private val bookId: String,
    private val initialPage: Int = 0
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UnifiedReaderViewModel::class.java)) {
            return UnifiedReaderViewModel(application, bookRepository, recordRepository, bookId, initialPage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 