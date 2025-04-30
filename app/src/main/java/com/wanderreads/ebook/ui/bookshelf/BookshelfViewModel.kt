package com.example.ebook.ui.bookshelf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebook.data.repository.BookRepository
import com.example.ebook.domain.model.Book
import com.example.ebook.domain.model.BookType
import com.example.ebook.util.TextProcessor
import com.example.ebook.util.WebBookImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 书架状态
 */
data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * UI事件
 */
sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
}

/**
 * 书架ViewModel
 */
class BookshelfViewModel(
    private val context: Context,
    val bookRepository: BookRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BookshelfUiState(isLoading = true))
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()
    
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents
    
    init {
        loadBooks()
    }
    
    private fun loadBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
                )
                .collect { books ->
                    _uiState.value = BookshelfUiState(
                        books = books,
                        isLoading = false
                    )
                }
        }
    }
    
    /**
     * 刷新书架数据
     */
    fun refreshBooks() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadBooks()
    }
    
    fun importBookFromFile(filePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            bookRepository.importBookFromStorage(filePath)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "导入电子书失败"
                    )
                }
        }
    }
    
    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
        }
    }
    
    fun searchBooks(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                loadBooks()
                return@launch
            }
            
            bookRepository.searchBooks(query)
                .collect { books ->
                    _uiState.value = _uiState.value.copy(
                        books = books,
                        isLoading = false
                    )
                }
        }
    }
    
    /**
     * 从网址导入书籍
     */
    fun importBookFromUrl(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // 从网址导入内容并保存为文本文件
                WebBookImporter.importFromUrl(context, url)
                    .onSuccess { file ->
                        // 创建书籍模型
                        val book = Book(
                            title = file.nameWithoutExtension,
                            filePath = file.absolutePath,
                            type = BookType.TXT
                        )
                        
                        // 添加到书库
                        bookRepository.addBook(book)
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        loadBooks() // 刷新书架
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "导入网页内容失败"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "导入网页内容失败"
                )
            }
        }
    }
    
    /**
     * 创建新文本并添加到书架
     */
    fun createNewText(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // 从文本内容提取标题
                val title = TextProcessor.extractTitle(text)
                
                // 保存文本到文件
                TextProcessor.saveTextToFile(context, text, title)
                    .onSuccess { file ->
                        // 创建书籍模型
                        val book = Book(
                            title = title,
                            filePath = file.absolutePath,
                            type = BookType.TXT
                        )
                        
                        // 添加到书库
                        bookRepository.addBook(book)
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        loadBooks() // 刷新书架
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "创建文本文件失败"
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "创建文本文件失败"
                )
            }
        }
    }

    suspend fun addBook(
        title: String,
        filePath: String,
        coverPath: String? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val fileName = file.name
                val fileHash = generateFileHash(file)
                
                // 检查是否已经存在
                val existingBook = bookRepository.getBookByHash(fileHash)
                if (existingBook != null) {
                    _uiEvents.emit(UiEvent.ShowToast("图书已存在"))
                    return@withContext
                }
                
                // 根据文件扩展名判断图书类型
                val bookType = determineBookType(fileName)
                
                // 创建新书
                val book = Book(
                    title = title,
                    author = "", // 后续可以从文件中提取
                    filePath = filePath,
                    coverPath = coverPath,
                    fileHash = fileHash,
                    type = bookType,
                    addedDate = System.currentTimeMillis(),
                    lastOpenedDate = System.currentTimeMillis()
                )
                
                // 添加到数据库
                bookRepository.addBook(book)
                
                // 刷新列表
                refreshBooks()
                
                // 显示成功提示
                _uiEvents.emit(UiEvent.ShowToast("图书添加成功"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("添加图书失败: ${e.message}"))
            }
        }
    }

    // 根据文件名确定图书类型
    private fun determineBookType(fileName: String): BookType {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> BookType.PDF
            fileName.endsWith(".epub", ignoreCase = true) -> BookType.EPUB
            fileName.endsWith(".txt", ignoreCase = true) -> BookType.TXT
            else -> BookType.UNKNOWN
        }
    }
    
    // 生成文件哈希值
    private fun generateFileHash(file: File): String {
        return try {
            val fileSize = file.length().toString()
            val lastModified = file.lastModified().toString()
            
            // 简单哈希：文件名 + 大小 + 修改时间
            val input = file.name + fileSize + lastModified
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // 如果无法生成哈希，使用备用方法
            "${file.name}-${file.length()}-${file.lastModified()}"
        }
    }
} 