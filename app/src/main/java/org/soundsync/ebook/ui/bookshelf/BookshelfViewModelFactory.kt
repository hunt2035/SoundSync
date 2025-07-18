package org.soundsync.ebook.ui.bookshelf

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.soundsync.ebook.data.repository.BookRepository

/**
 * BookshelfViewModel的工厂类
 */
class BookshelfViewModelFactory(
    private val application: Application,
    private val bookRepository: BookRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookshelfViewModel::class.java)) {
            return BookshelfViewModel(
                application = application,
                bookRepository = bookRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 