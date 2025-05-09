package com.wanderreads.ebook.ui.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wanderreads.ebook.data.repository.BookRepository
import com.wanderreads.ebook.domain.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 历史记录视图模型
 */
class HistoryViewModel(
    private val application: Application,
    private val bookRepository: BookRepository
) : ViewModel() {

    // UI 状态
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadReadingHistory()
    }

    /**
     * 加载阅读历史记录
     */
    private fun loadReadingHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            bookRepository.getAllBooks()
                .map { books ->
                    // 过滤出有阅读记录的书籍（lastOpenedDate 非空）并按照最后打开时间倒序排序
                    books.filter { it.lastOpenedDate > 0 }
                        .sortedByDescending { it.lastOpenedDate }
                }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "加载历史记录失败"
                    )
                }
                .collectLatest { historyBooks ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        books = historyBooks,
                        error = null
                    )
                }
        }
    }

    /**
     * 刷新阅读历史记录
     */
    fun refreshHistory() {
        loadReadingHistory()
    }
}

/**
 * 历史记录 UI 状态
 */
data class HistoryUiState(
    val isLoading: Boolean = false,
    val books: List<Book> = emptyList(),
    val error: String? = null
)

/**
 * 历史记录视图模型工厂
 */
class HistoryViewModelFactory(
    private val application: Application,
    private val bookRepository: BookRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            return HistoryViewModel(application, bookRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 