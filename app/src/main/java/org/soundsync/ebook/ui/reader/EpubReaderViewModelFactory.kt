package org.soundsync.ebook.ui.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.soundsync.ebook.data.repository.BookRepository

/**
 * EPUB阅读器ViewModelFactory
 * 用于创建EpubReaderViewModel实例
 */
class EpubReaderViewModelFactory(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val bookId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EpubReaderViewModel::class.java)) {
            return EpubReaderViewModel(application, bookRepository, bookId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 