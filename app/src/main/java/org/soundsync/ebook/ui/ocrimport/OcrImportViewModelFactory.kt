package org.soundsync.ebook.ui.ocrimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.soundsync.ebook.data.repository.BookRepository

/**
 * OCR导入ViewModel工厂
 */
class OcrImportViewModelFactory(
    private val bookRepository: BookRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OcrImportViewModel::class.java)) {
            return OcrImportViewModel(bookRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 