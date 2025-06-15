package com.wanderreads.ebook.ui.library

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 书库类别
 */
enum class LibraryCategory(val displayName: String, val dirName: String, val icon: String) {
    TEXT_BOOKS("新建文本", "txtfiles", "text_fields"),
    WEB_BOOKS("网址导入", "webbook", "language"),
    LOCAL_BOOKS("本地导入", "books", "book"),
    VOICE_FILES("语音文件", "voices", "record_voice_over");
    
    // 默认使用Documents目录
    open fun getDirectory(context: Context): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(File(documentsDir, "WanderReads"), dirName)
    }
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
    val singleFileToDelete: String? = null,
    // 音频播放相关状态
    val currentPlayingFilePath: String? = null,
    val isAudioPlaying: Boolean = false,
    val currentPlaybackPosition: Int = 0,
    val totalAudioDuration: Int = 0
)

/**
 * 书库视图模型
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    // 默认根目录（仅用于向后兼容，实际使用LibraryCategory.getDirectory方法）
    private val rootDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "WanderReads"
    )
    
    // 媒体播放器相关
    private var mediaPlayer: MediaPlayer? = null
    private var playbackProgressHandler: Handler? = null
    private val playbackUpdateInterval = 1000L // 1秒更新一次播放进度
    
    init {
        loadCategories()
    }
    
    // 在ViewModel销毁时释放资源
    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
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
                    val dir = category.getDirectory(getApplication())
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
                val dir = category.getDirectory(getApplication())
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
            val dir = category.getDirectory(context)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            Log.d("LibraryViewModel", "尝试打开目录: ${dir.absolutePath}")
            
            // 构建正确的Uri格式
            val documentPath = "primary:Documents/WanderReads/${category.dirName}"
            Log.d("LibraryViewModel", "目标文档路径: $documentPath")
            
            // 优先使用FileProvider生成content URI
            try {
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    dir
                )
                
                val viewIntent = Intent(Intent.ACTION_VIEW)
                viewIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                viewIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(viewIntent)
                Log.d("LibraryViewModel", "成功使用ACTION_VIEW和content URI打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_VIEW和content URI打开失败: ${e.message}", e)
            }
            
            // 尝试方法2: 使用ACTION_GET_CONTENT打开文件浏览器
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                
                // 尝试设置初始目录
                val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentPath)
                } else {
                    Uri.parse("content://com.android.externalstorage.documents/document/$documentPath")
                }
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(Intent.createChooser(intent, "选择文件浏览器"))
                Log.d("LibraryViewModel", "成功使用ACTION_GET_CONTENT打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_GET_CONTENT打开文件浏览器失败: ${e.message}", e)
            }
            
            // 尝试方法3: 使用外部应用选择器
            try {
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    dir
                )
                
                val finalIntent = Intent(Intent.ACTION_VIEW)
                finalIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                finalIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(Intent.createChooser(finalIntent, "选择文件浏览器打开目录"))
                Log.d("LibraryViewModel", "成功使用文件浏览器选择器打开")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用文件浏览器选择器打开失败: ${e.message}", e)
            }
            
            // 尝试方法4: 使用特定文件管理器打开
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
                        val contentUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            dir
                        )
                        
                        val fileManagerIntent = Intent(Intent.ACTION_VIEW)
                        fileManagerIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                        fileManagerIntent.component = ComponentName(packageName, className)
                        fileManagerIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        
                        context.startActivity(fileManagerIntent)
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
            
            // 如果以上方法都失败，尝试SAF方法
            val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentPath)
            } else {
                Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents/WanderReads/${category.dirName}")
            }
            
            // 尝试方法4: 使用ACTION_OPEN_DOCUMENT_TREE
            try {
                val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    treeIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                } else {
                    treeIntent.putExtra("android.provider.extra.INITIAL_URI", initialUri)
                    treeIntent.putExtra("android.content.extra.SHOW_ADVANCED", true)
                }
                
                treeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(treeIntent)
                Log.d("LibraryViewModel", "成功使用ACTION_OPEN_DOCUMENT_TREE打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_OPEN_DOCUMENT_TREE打开失败: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "无法打开文件浏览器: ${e.message}"
                )
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "打开文件浏览器失败", e)
            _uiState.value = _uiState.value.copy(
                error = "无法打开文件浏览器: ${e.message}"
            )
        }
    }
    
    /**
     * 打开文件所在目录并定位到该文件
     */
    fun openFileLocation(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                _uiState.update { it.copy(error = "文件不存在") }
                return
            }
            
            val parentDir = file.parentFile
            if (parentDir == null || !parentDir.exists()) {
                _uiState.update { it.copy(error = "父目录不存在") }
                return
            }
            
            Log.d("LibraryViewModel", "尝试打开文件所在目录: ${parentDir.absolutePath}")
            Log.d("LibraryViewModel", "文件名: ${file.name}")
            
            // 获取相对路径
            val relativeDirPath = if (parentDir.absolutePath.contains("WanderReads")) {
                parentDir.absolutePath.substringAfter("WanderReads")
            } else {
                ""
            }
            
            // 优先使用FileProvider生成content URI
            try {
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    parentDir
                )
                
                val viewIntent = Intent(Intent.ACTION_VIEW)
                viewIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                viewIntent.putExtra("org.openintents.extra.ABSOLUTE_PATH", parentDir.absolutePath)
                viewIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(viewIntent)
                Log.d("LibraryViewModel", "成功使用ACTION_VIEW和content URI打开文件所在目录")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_VIEW和content URI打开失败: ${e.message}", e)
            }
            
            // 尝试方法2: 使用ACTION_GET_CONTENT打开文件浏览器
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                
                // 构建指向Documents/WanderReads目录的URI
                val documentPath = if (parentDir.absolutePath.contains("WanderReads")) {
                    "primary:Documents/WanderReads${relativeDirPath}"
                } else {
                    "primary:Documents/WanderReads${relativeDirPath}"
                }
                
                // 尝试设置初始目录
                val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentPath)
                } else {
                    Uri.parse("content://com.android.externalstorage.documents/document/$documentPath")
                }
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(Intent.createChooser(intent, "选择文件浏览器"))
                Log.d("LibraryViewModel", "成功使用ACTION_GET_CONTENT打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_GET_CONTENT打开文件浏览器失败: ${e.message}", e)
            }
            
            // 尝试使用外部应用选择器
            try {
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    parentDir
                )
                
                val finalIntent = Intent(Intent.ACTION_VIEW)
                finalIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                finalIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(Intent.createChooser(finalIntent, "选择文件浏览器打开目录"))
                Log.d("LibraryViewModel", "成功使用文件浏览器选择器打开")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用文件浏览器选择器打开失败: ${e.message}", e)
            }
            
            // 尝试方法3: 使用特定文件管理器的Intent
            try {
                // 构建指向Documents/WanderReads目录的URI
                val documentPath = if (parentDir.absolutePath.contains("WanderReads")) {
                    "primary:Documents/WanderReads${relativeDirPath}"
                } else {
                    "primary:Documents/WanderReads${relativeDirPath}"
                }
                Log.d("LibraryViewModel", "目标文档路径: $documentPath")
                
                // 尝试使用系统文件管理器打开
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
                
                var launched = false
                for ((packageName, className) in systemFileManagerPackages) {
                    try {
                        val contentUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            parentDir
                        )
                        
                        val fileManagerIntent = Intent(Intent.ACTION_VIEW)
                        fileManagerIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                        fileManagerIntent.putExtra("org.openintents.extra.ABSOLUTE_PATH", parentDir.absolutePath)
                        fileManagerIntent.component = ComponentName(packageName, className)
                        fileManagerIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        
                        context.startActivity(fileManagerIntent)
                        launched = true
                        Log.d("LibraryViewModel", "成功使用 $packageName 打开文件所在目录")
                        break
                    } catch (e: Exception) {
                        Log.d("LibraryViewModel", "尝试使用 $packageName 打开文件所在目录失败: ${e.message}")
                    }
                }
                
                if (launched) return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用特定文件管理器打开失败: ${e.message}", e)
            }
            
            // 如果以上方法都失败，尝试SAF方法
            try {
                // 构建指向Documents/WanderReads目录的URI
                val documentPath = if (parentDir.absolutePath.contains("WanderReads")) {
                    "primary:Documents/WanderReads${relativeDirPath}"
                } else {
                    "primary:Documents/WanderReads${relativeDirPath}"
                }
                
                val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentPath)
                } else {
                    Uri.parse("content://com.android.externalstorage.documents/document/$documentPath")
                }
                
                val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    treeIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                } else {
                    treeIntent.putExtra("android.provider.extra.INITIAL_URI", initialUri)
                    treeIntent.putExtra("android.content.extra.SHOW_ADVANCED", true)
                }
                
                treeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(treeIntent)
                Log.d("LibraryViewModel", "成功使用ACTION_OPEN_DOCUMENT_TREE打开文件所在目录")
                return
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "使用ACTION_OPEN_DOCUMENT_TREE打开失败: ${e.message}", e)
                _uiState.update { it.copy(error = "无法打开文件所在目录: ${e.message}") }
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "打开文件所在目录失败", e)
            _uiState.update { it.copy(error = "无法打开文件所在目录: ${e.message}") }
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
     * 设置错误信息
     */
    fun setError(errorMessage: String) {
        _uiState.value = _uiState.value.copy(
            error = errorMessage
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
    
    /**
     * 播放音频文件
     */
    fun playAudioFile(file: BookFile) {
        try {
            // 如果有正在播放的音频，先停止
            stopAudioPlayback()
            
            val audioFile = File(file.filePath)
            if (!audioFile.exists()) {
                Log.e("LibraryViewModel", "音频文件不存在: ${file.filePath}")
                _uiState.update { it.copy(error = "音频文件不存在") }
                return
            }
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.filePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    val duration = mp.duration
                    _uiState.update { it.copy(
                        currentPlayingFilePath = file.filePath,
                        isAudioPlaying = true,
                        currentPlaybackPosition = 0,
                        totalAudioDuration = duration
                    ) }
                    startPlaybackProgressTracking()
                }
                setOnCompletionListener {
                    stopAudioPlayback()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "播放音频文件失败: ${e.message}")
            stopAudioPlayback()
            _uiState.update { it.copy(error = "播放失败: ${e.message}") }
        }
    }
    
    /**
     * 暂停播放音频
     */
    fun pauseAudioPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopPlaybackProgressTracking()
                _uiState.update { state -> 
                    state.copy(isAudioPlaying = false)
                }
            }
        }
    }
    
    /**
     * 继续播放音频
     */
    fun resumeAudioPlayback() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startPlaybackProgressTracking()
                _uiState.update { state -> 
                    state.copy(isAudioPlaying = true)
                }
            }
        }
    }
    
    /**
     * 停止播放音频
     */
    fun stopAudioPlayback() {
        stopPlaybackProgressTracking()
        
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "停止播放失败: ${e.message}")
            }
        }
        
        mediaPlayer = null
        
        _uiState.update { 
            it.copy(
                currentPlayingFilePath = null,
                isAudioPlaying = false,
                currentPlaybackPosition = 0,
                totalAudioDuration = 0
            )
        }
    }
    
    /**
     * 开始追踪播放进度
     */
    private fun startPlaybackProgressTracking() {
        playbackProgressHandler = Handler(Looper.getMainLooper())
        
        playbackProgressHandler?.post(object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        val position = it.currentPosition
                        _uiState.update { state -> 
                            state.copy(
                                currentPlaybackPosition = position,
                                isAudioPlaying = true
                            )
                        }
                    }
                }
                playbackProgressHandler?.postDelayed(this, playbackUpdateInterval)
            }
        })
    }
    
    /**
     * 停止追踪播放进度
     */
    private fun stopPlaybackProgressTracking() {
        playbackProgressHandler?.removeCallbacksAndMessages(null)
        playbackProgressHandler = null
    }
    
    /**
     * 调整音频播放位置
     */
    fun seekToPosition(position: Int) {
        try {
            mediaPlayer?.let {
                it.seekTo(position)
                _uiState.update { state ->
                    state.copy(currentPlaybackPosition = position)
                }
            }
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "调整播放位置失败: ${e.message}")
        }
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