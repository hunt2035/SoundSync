package com.wanderreads.ebook.ui.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wanderreads.ebook.data.repository.BookRepository

/**
 * ReaderViewModel工厂
 */
class ReaderViewModelFactory(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val bookId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            return ReaderViewModel(
                application = application,
                bookRepository = bookRepository,
                bookId = bookId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 