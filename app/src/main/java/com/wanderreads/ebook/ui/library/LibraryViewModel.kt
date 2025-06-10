package com.wanderreads.ebook.ui.library

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wanderreads.ebook.domain.model.BookFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 书库类别
 */
enum class LibraryCategory(val displayName: String, val dirName: String, val icon: String) {
    TEXT_BOOKS("新建文本", "txtfiles", "text_fields"),
    WEB_BOOKS("网址导入", "webbook", "language"),
    LOCAL_BOOKS("本地导入", "books", "book"),
    VOICE_FILES("语音文件", "voices", "record_voice_over")
}

/**
 * 书库视图模型状态
 */
data class LibraryUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val categories: Map<LibraryCategory, Int> = emptyMap(),
    val currentCategory: LibraryCategory? = null,
    val files: List<BookFile> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isRenameDialogVisible: Boolean = false,
    val fileToRename: BookFile? = null,
    val newFileName: String = "",
    val isDeleteConfirmationVisible: Boolean = false,
    val singleFileToDelete: String? = null
)

/**
 * 书库视图模型
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    private val rootDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "WanderReads"
    )
    
    init {
        loadCategories()
    }
    
    /**
     * 加载所有类别及其文件数量
     */
    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val categories = mutableMapOf<LibraryCategory, Int>()
                
                LibraryCategory.values().forEach { category ->
                    val dir = File(rootDir, category.dirName)
                    val count = if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.size ?: 0
                    } else {
                        0
                    }
                    categories[category] = count
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    categories = categories
                )
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "加载类别失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载类别失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 加载指定类别的文件列表
     */
    fun loadFiles(category: LibraryCategory) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                error = null,
                currentCategory = category,
                files = emptyList(),
                selectedFiles = emptySet(),
                isSelectionMode = false
            )
            
            try {
                val dir = File(rootDir, category.dirName)
                val files = if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()
                        ?.filter { it.isFile }
                        ?.map { BookFile.fromFile(it) }
                        ?.sortedByDescending { it.lastModified }
                        ?: emptyList()
                } else {
                    emptyList()
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    files = files
                )
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "加载文件失败", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载文件失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 打开文件浏览器
     */
    fun openFileExplorer(context: Context, category: LibraryCategory) {
        try {
            val dir = File(rootDir, category.dirName)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            Log.d("LibraryViewModel", "尝试打开目录: ${dir.absolutePath}")
            
            // 构建正确的Uri格式
            val documentPath = "primary:Documents/WanderReads/${category.dirName}"
            Log.d("LibraryViewModel", "目标文档路径: $documentPath")
            
            val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+使用DocumentsContract.buildDocumentUri
                val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentPath)
                Log.d("LibraryViewModel", "构建的URI (O+): $uri")
                uri
            } else {
                // 旧版本使用基本Uri
                val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents/WanderReads/${category.dirName}")
                Log.d("LibraryViewModel", "构建的URI (旧版本): $uri")
                uri
            }
            
            // 尝试方法1: 使用ACTION_GET_CONTENT
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                
                context.startActivity(intent)
                Log.d("LibraryViewModel", "成功使用ACTION_GET_CONTENT打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_GET_CONTENT打开失败: ${e.message}", e)
            }
            
            // 尝试方法2: 使用ACTION_OPEN_DOCUMENT
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                
                context.startActivity(intent)
                Log.d("LibraryViewModel", "成功使用ACTION_OPEN_DOCUMENT打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_OPEN_DOCUMENT打开失败: ${e.message}", e)
            }
            
            // 尝试方法3: 使用ACTION_OPEN_DOCUMENT_TREE
            try {
                val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                
                // 对于Android 11+，使用EXTRA_INITIAL_URI
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    treeIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                } else {
                    // 对于旧版本，尝试使用不同的键
                    treeIntent.putExtra("android.provider.extra.INITIAL_URI", initialUri)
                    treeIntent.putExtra("android.content.extra.SHOW_ADVANCED", true)
                }
                
                treeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(treeIntent)
                Log.d("LibraryViewModel", "成功使用ACTION_OPEN_DOCUMENT_TREE打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_OPEN_DOCUMENT_TREE打开失败: ${e.message}", e)
            }
            
            // 尝试方法4: 使用特定设备的文件管理器
            try {
                // 指定系统文件管理器的包名和类名
                val systemFileManagerPackages = arrayOf(
                    // 通用Android系统文件管理器
                    Pair("com.android.documentsui", "com.android.documentsui.files.FilesActivity"),
                    // 三星设备
                    Pair("com.sec.android.app.myfiles", "com.sec.android.app.myfiles.common.MainActivity"),
                    // 华为设备
                    Pair("com.huawei.filemanager", "com.huawei.filemanager.activity.MainActivity"),
                    // 小米设备
                    Pair("com.miui.fileexplorer", "com.miui.fileexplorer.FileExplorerTabActivity"),
                    // OPPO设备
                    Pair("com.coloros.filemanager", "com.coloros.filemanager.MainActivity"),
                    // VIVO设备
                    Pair("com.vivo.filemanager", "com.vivo.filemanager.activity.MainActivity")
                )
                
                // 尝试使用系统文件管理器打开
                var launched = false
                for ((packageName, className) in systemFileManagerPackages) {
                    try {
                        val backupIntent = Intent(Intent.ACTION_VIEW)
                        backupIntent.setDataAndType(initialUri, "resource/folder")
                        backupIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        backupIntent.component = ComponentName(packageName, className)
                        
                        context.startActivity(backupIntent)
                        launched = true
                        Log.d("LibraryViewModel", "成功使用 $packageName 打开文件浏览器")
                        break
                    } catch (e: Exception) {
                        Log.d("LibraryViewModel", "尝试使用 $packageName 打开文件浏览器失败: ${e.message}")
                        // 继续尝试下一个
                    }
                }
                
                if (launched) return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用特定设备文件管理器打开失败: ${e.message}", e)
            }
            
            // 尝试方法5: 使用通用方式打开
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW)
                genericIntent.setDataAndType(Uri.fromFile(dir), "resource/folder")
                genericIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(genericIntent)
                Log.d("LibraryViewModel", "成功使用通用方式打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用通用方式打开文件浏览器失败: ${e.message}", e)
                
                // 最后尝试: 打开任意文件浏览器
                try {
                    val finalIntent = Intent(Intent.ACTION_VIEW)
                    finalIntent.type = "resource/folder"
                    finalIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(Intent.createChooser(finalIntent, "选择文件浏览器打开目录"))
                    Log.d("LibraryViewModel", "成功使用文件浏览器选择器打开")
                    return
                } catch (e2: Exception) {
                    Log.e("LibraryViewModel", "所有方法均失败: ${e2.message}", e2)
                    _uiState.value = _uiState.value.copy(
                        error = "无法打开文件浏览器: ${e2.message}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "打开文件浏览器失败", e)
            _uiState.value = _uiState.value.copy(
                error = "无法打开文件浏览器: ${e.message}"
            )
        }
    }
    
    /**
     * 删除文件
     */
    fun deleteFile(filePath: String) {
        viewModelScope.launch {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        // 刷新当前类别的文件列表
                        _uiState.value.currentCategory?.let { loadFiles(it) }
                        // 更新类别计数
                        loadCategories()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "删除文件失败"
                        )
                    }
                }
                // 清除待删除文件
                _uiState.value = _uiState.value.copy(
                    singleFileToDelete = null,
                    isDeleteConfirmationVisible = false
                )
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "删除文件失败", e)
                _uiState.value = _uiState.value.copy(
                    error = "删除文件失败: ${e.message}",
                    singleFileToDelete = null,
                    isDeleteConfirmationVisible = false
                )
            }
        }
    }
    
    /**
     * 显示单个文件删除确认对话框
     */
    fun showSingleFileDeleteConfirmation(filePath: String) {
        _uiState.value = _uiState.value.copy(
            isDeleteConfirmationVisible = true,
            singleFileToDelete = filePath
        )
    }
    
    /**
     * 执行删除操作（根据上下文确定删除单个文件还是批量删除）
     */
    fun executeDelete() {
        val singleFilePath = _uiState.value.singleFileToDelete
        if (singleFilePath != null) {
            deleteFile(singleFilePath)
        } else {
            deleteSelectedFiles()
        }
    }
    
    /**
     * 显示删除确认对话框（批量删除）
     */
    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(
            isDeleteConfirmationVisible = true,
            singleFileToDelete = null
        )
    }
    
    /**
     * 隐藏删除确认对话框
     */
    fun hideDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(
            isDeleteConfirmationVisible = false,
            singleFileToDelete = null
        )
    }
    
    /**
     * 批量删除文件
     */
    fun deleteSelectedFiles() {
        viewModelScope.launch {
            try {
                val selectedPaths = _uiState.value.selectedFiles
                var deletedCount = 0
                
                selectedPaths.forEach { path ->
                    val file = File(path)
                    if (file.exists() && file.delete()) {
                        deletedCount++
                    }
                }
                
                // 刷新当前类别的文件列表
                _uiState.value.currentCategory?.let { loadFiles(it) }
                // 更新类别计数
                loadCategories()
                
                _uiState.value = _uiState.value.copy(
                    selectedFiles = emptySet(),
                    isSelectionMode = false,
                    isDeleteConfirmationVisible = false
                )
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "批量删除文件失败", e)
                _uiState.value = _uiState.value.copy(
                    error = "批量删除文件失败: ${e.message}",
                    isDeleteConfirmationVisible = false
                )
            }
        }
    }
    
    /**
     * 重命名文件
     */
    fun renameFile(oldFilePath: String, newName: String) {
        viewModelScope.launch {
            try {
                val oldFile = File(oldFilePath)
                if (!oldFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        error = "文件不存在"
                    )
                    return@launch
                }
                
                val parentDir = oldFile.parentFile
                val newFile = File(parentDir, newName)
                
                if (newFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        error = "文件名已存在"
                    )
                    return@launch
                }
                
                val renamed = oldFile.renameTo(newFile)
                if (renamed) {
                    // 刷新当前类别的文件列表
                    _uiState.value.currentCategory?.let { loadFiles(it) }
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "重命名文件失败"
                    )
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "重命名文件失败", e)
                _uiState.value = _uiState.value.copy(
                    error = "重命名文件失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 切换文件选择状态
     */
    fun toggleFileSelection(filePath: String) {
        val selectedFiles = _uiState.value.selectedFiles.toMutableSet()
        if (selectedFiles.contains(filePath)) {
            selectedFiles.remove(filePath)
        } else {
            selectedFiles.add(filePath)
        }
        
        _uiState.value = _uiState.value.copy(
            selectedFiles = selectedFiles,
            isSelectionMode = selectedFiles.isNotEmpty()
        )
    }
    
    /**
     * 切换选择模式
     */
    fun toggleSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = !_uiState.value.isSelectionMode,
            selectedFiles = emptySet()
        )
    }
    
    /**
     * 显示重命名对话框
     */
    fun showRenameDialog(file: BookFile) {
        _uiState.value = _uiState.value.copy(
            isRenameDialogVisible = true,
            fileToRename = file,
            newFileName = file.fileName
        )
    }
    
    /**
     * 隐藏重命名对话框
     */
    fun hideRenameDialog() {
        _uiState.value = _uiState.value.copy(
            isRenameDialogVisible = false,
            fileToRename = null,
            newFileName = ""
        )
    }
    
    /**
     * 更新新文件名
     */
    fun updateNewFileName(name: String) {
        _uiState.value = _uiState.value.copy(
            newFileName = name
        )
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            error = null
        )
    }
    
    /**
     * 全选所有文件
     */
    fun selectAllFiles() {
        val allFilePaths = _uiState.value.files.map { it.filePath }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedFiles = allFilePaths
        )
    }
    
    /**
     * 取消全选所有文件
     */
    fun deselectAllFiles() {
        _uiState.value = _uiState.value.copy(
            selectedFiles = emptySet()
        )
    }
    
    /**
     * 检查是否已全选
     */
    fun isAllSelected(): Boolean {
        val allFiles = _uiState.value.files
        val selectedFiles = _uiState.value.selectedFiles
        return allFiles.isNotEmpty() && allFiles.size == selectedFiles.size
    }
}

/**
 * 书库视图模型工厂
 */
class LibraryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 