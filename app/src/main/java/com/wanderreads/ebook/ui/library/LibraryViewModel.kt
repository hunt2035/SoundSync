package com.wanderreads.ebook.ui.library

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
    val newFileName: String = ""
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
            
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(dir.absolutePath), "resource/folder")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
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
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "删除文件失败", e)
                _uiState.value = _uiState.value.copy(
                    error = "删除文件失败: ${e.message}"
                )
            }
        }
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
                    isSelectionMode = false
                )
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "批量删除文件失败", e)
                _uiState.value = _uiState.value.copy(
                    error = "批量删除文件失败: ${e.message}"
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