package com.example.ebook.ui.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ebook.data.repository.BookRepository

/**
 * 统一阅读器ViewModelFactory
 * 用于创建UnifiedReaderViewModel实例
 */
class UnifiedReaderViewModelFactory(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val bookId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UnifiedReaderViewModel::class.java)) {
            return UnifiedReaderViewModel(application, bookRepository, bookId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 