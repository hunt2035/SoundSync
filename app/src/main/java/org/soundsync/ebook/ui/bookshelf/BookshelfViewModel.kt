package org.soundsync.ebook.ui.bookshelf

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.soundsync.ebook.MainActivity
import org.soundsync.ebook.data.repository.BookRepository
import org.soundsync.ebook.domain.model.Book
import org.soundsync.ebook.domain.model.BookType
import org.soundsync.ebook.util.TextProcessor
import org.soundsync.ebook.util.WebBookImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.soundsync.ebook.util.ReadingProgressDebugger
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest

/**
 * 书籍排序方式
 */
enum class BookSort {
    ADDED_DATE, // 添加时间
    LAST_OPENED, // 最后打开时间
    TITLE, // 标题
    AUTHOR // 作者
}

/**
 * 书架状态
 */
data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentSort: BookSort = BookSort.ADDED_DATE,
    val isSelectionMode: Boolean = false,
    val selectedBooks: Set<String> = emptySet(),
    val isDeleteConfirmationVisible: Boolean = false,
    val deleteBookFiles: Boolean = false
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
    application: Application,
    val bookRepository: BookRepository
) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    
    // 日志标签
    companion object {
        private const val TAG = "BookshelfViewModel"
    }
    
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
                    // 添加调试日志
                    Log.d(TAG, "加载书籍列表: 总数=${books.size}")
                    books.forEachIndexed { index, book ->
                        if (index < 3) { // 只记录前3本书的详细信息
                            Log.d(TAG, "书籍[$index]: ${book.title}, totalPages=${book.totalPages}, lastReadPage=${book.lastReadPage}, progress=${(book.readingProgress * 100).toInt()}%")
                        }
                    }

                    val problemBooks = books.filter { it.totalPages == 0 }
                    if (problemBooks.isNotEmpty()) {
                        Log.w(TAG, "发现${problemBooks.size}本书籍的totalPages为0，这会导致阅读进度显示为0%")
                    }

                    _uiState.value = BookshelfUiState(
                        books = books,
                        isLoading = false,
                        currentSort = _uiState.value.currentSort
                    )
                }
        }
    }
    
    /**
     * 排序书籍
     */
    fun sortBooks(sortBy: BookSort) {
        viewModelScope.launch {
            val currentBooks = _uiState.value.books
            val sortedBooks = when (sortBy) {
                BookSort.ADDED_DATE -> currentBooks.sortedByDescending { it.addedDate }
                BookSort.LAST_OPENED -> currentBooks.sortedByDescending { it.lastOpenedDate }
                BookSort.TITLE -> currentBooks.sortedBy { it.title }
                BookSort.AUTHOR -> currentBooks.sortedBy { it.author.ifBlank { "zzz" } } // 空作者放最后
            }
            
            _uiState.value = _uiState.value.copy(
                books = sortedBooks,
                currentSort = sortBy
            )
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
                Log.d(TAG, "开始从网址导入: $url")
                
                // 检查Android 11+设备是否有MANAGE_EXTERNAL_STORAGE权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    // 提示用户授予权限
                    MainActivity.getInstance()?.showAllFilesAccessPermissionDialog()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "需要'所有文件访问权限'才能保存到外部存储，请授予权限后重试"
                    )
                    return@launch
                }
                
                // 从网址导入内容并保存为文本文件
                WebBookImporter.importFromUrl(context, url)
                    .onSuccess { (file, urlPath) ->
                        Log.d(TAG, "网页导入成功，文件保存在: ${file.absolutePath}")
                        
                        try {
                            // 读取文件获取第一行作为标题
                            val fileText = file.readText(Charsets.UTF_8)
                            val firstLine = fileText.trim().split("\n").firstOrNull()?.trim() ?: file.nameWithoutExtension
                            
                            // 创建书籍模型
                            val book = Book(
                                title = firstLine,  // 使用第一行作为标题
                                filePath = file.absolutePath,
                                type = BookType.TXT,
                                urlPath = urlPath,  // 保存网址
                                addedDate = System.currentTimeMillis(),
                                fileHash = generateFileHash(file)
                            )
                            
                            // 添加到书库
                            Log.d(TAG, "向书库添加网页导入的书籍: ${book.title}")
                            bookRepository.addBook(book)
                            _uiState.value = _uiState.value.copy(isLoading = false)
                            _uiEvents.emit(UiEvent.ShowToast("网页导入成功"))
                            loadBooks() // 刷新书架
                        } catch (e: Exception) {
                            Log.e(TAG, "解析导入文件或添加到书库失败", e)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "处理导入文件失败: ${e.message}"
                            )
                        }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "网页导入失败", error)
                        val errorMessage = when {
                            error is IOException && error.message?.contains("无法创建目录") == true -> 
                                "无法创建存储目录，请检查应用权限设置"
                            error is SocketTimeoutException -> 
                                "网页加载超时，请检查网络连接或稍后重试"
                            error.message?.contains("外部存储不可写") == true -> 
                                "外部存储不可写，请在系统设置中授予应用存储权限"
                            error.message?.contains("Status=404") == true ->
                                "网页不存在(404错误)，请检查网址是否正确"
                            error.message?.contains("Status=403") == true ->
                                "访问被拒绝(403错误)，该网站可能禁止爬取内容"
                            error.message?.contains("Status=5") == true ->
                                "服务器错误，请稍后重试"
                            error.message?.contains("UnknownHostException") == true ->
                                "无法解析网址，请检查网址是否正确或网络连接是否正常"
                            else -> 
                                "导入网页内容失败: ${error.message}"
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = errorMessage
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "网页导入过程中发生异常", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "导入网页内容失败: ${e.message}"
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
                // 检查Android 11+设备是否有MANAGE_EXTERNAL_STORAGE权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    // 提示用户授予权限
                    MainActivity.getInstance()?.showAllFilesAccessPermissionDialog()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "需要'所有文件访问权限'才能保存到外部存储，请授予权限后重试"
                    )
                    return@launch
                }
                
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
            fileName.endsWith(".md", ignoreCase = true) -> BookType.MD
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

    /**
     * 切换选择模式
     */
    fun toggleSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = !_uiState.value.isSelectionMode,
            selectedBooks = emptySet()
        )
    }

    /**
     * 切换书籍选择状态
     */
    fun toggleBookSelection(bookId: String) {
        val selectedBooks = _uiState.value.selectedBooks.toMutableSet()
        if (selectedBooks.contains(bookId)) {
            selectedBooks.remove(bookId)
        } else {
            selectedBooks.add(bookId)
        }
        
        _uiState.value = _uiState.value.copy(
            selectedBooks = selectedBooks
        )
    }

    /**
     * 全选所有书籍
     */
    fun selectAllBooks() {
        val allBookIds = _uiState.value.books.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedBooks = allBookIds
        )
    }

    /**
     * 取消全选所有书籍
     */
    fun deselectAllBooks() {
        _uiState.value = _uiState.value.copy(
            selectedBooks = emptySet()
        )
    }

    /**
     * 检查是否已全选
     */
    fun isAllSelected(): Boolean {
        val allBooks = _uiState.value.books
        val selectedBooks = _uiState.value.selectedBooks
        return allBooks.isNotEmpty() && allBooks.size == selectedBooks.size
    }

    /**
     * 显示删除确认对话框
     */
    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(
            isDeleteConfirmationVisible = true
        )
    }

    /**
     * 隐藏删除确认对话框
     */
    fun hideDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(
            isDeleteConfirmationVisible = false,
            deleteBookFiles = false
        )
    }

    /**
     * 设置是否同时删除书籍文件
     */
    fun setDeleteBookFiles(delete: Boolean) {
        _uiState.value = _uiState.value.copy(
            deleteBookFiles = delete
        )
    }

    /**
     * 批量删除选中的书籍
     */
    fun deleteSelectedBooks() {
        viewModelScope.launch {
            try {
                val selectedBookIds = _uiState.value.selectedBooks
                val deleteFiles = _uiState.value.deleteBookFiles
                
                // 获取选中的书籍
                val booksToDelete = _uiState.value.books.filter { selectedBookIds.contains(it.id) }
                
                for (book in booksToDelete) {
                    // 删除数据库中的书籍记录
                    bookRepository.deleteBook(book)
                    
                    // 如果需要，同时删除书籍文件
                    if (deleteFiles && book.filePath.isNotBlank()) {
                        val file = File(book.filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                }
                
                // 重置状态
                _uiState.value = _uiState.value.copy(
                    isSelectionMode = false,
                    selectedBooks = emptySet(),
                    isDeleteConfirmationVisible = false,
                    deleteBookFiles = false
                )
                
                // 显示成功提示
                _uiEvents.emit(UiEvent.ShowToast("已删除${booksToDelete.size}本书籍"))
                
            } catch (e: Exception) {
                Log.e(TAG, "批量删除书籍失败", e)
                _uiState.value = _uiState.value.copy(
                    error = "删除书籍失败: ${e.message}",
                    isDeleteConfirmationVisible = false
                )
            }
        }
    }
    
    /**
     * 显示功能开发中的提示信息
     */
    fun showFeatureInDevelopmentMessage() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast("功能开发中，敬请期待..."))
        }
    }

    /**
     * 检查所有书籍的阅读进度状态
     * 用于调试阅读进度显示问题
     */
    fun checkReadingProgress() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val debugger = ReadingProgressDebugger(context, bookRepository)
                debugger.checkAllBooksProgress()
            } catch (e: Exception) {
                Log.e(TAG, "检查阅读进度失败", e)
                _uiEvents.emit(UiEvent.ShowToast("检查阅读进度失败: ${e.message}"))
            }
        }
    }

    /**
     * 修复所有书籍的阅读进度问题
     */
    fun fixReadingProgress() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val context = getApplication<Application>().applicationContext
                val debugger = ReadingProgressDebugger(context, bookRepository)
                debugger.fixAllBooksProgress()

                // 刷新书架
                loadBooks()

                _uiEvents.emit(UiEvent.ShowToast("阅读进度修复完成"))
            } catch (e: Exception) {
                Log.e(TAG, "修复阅读进度失败", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
                _uiEvents.emit(UiEvent.ShowToast("修复阅读进度失败: ${e.message}"))
            }
        }
    }
}