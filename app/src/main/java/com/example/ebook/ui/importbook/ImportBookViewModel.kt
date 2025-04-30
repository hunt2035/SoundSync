package com.example.ebook.ui.importbook

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.ebook.data.repository.BookRepository
import com.example.ebook.util.FileUtil
import com.example.ebook.worker.BookImportWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 导入书籍ViewModel
 */
class ImportBookViewModel(
    private val context: Context,
    private val bookRepository: BookRepository
) : ViewModel() {
    private val TAG = "ImportBookViewModel"
    
    private val _uiState = MutableStateFlow(ImportBookUiState())
    val uiState: StateFlow<ImportBookUiState> = _uiState.asStateFlow()
    
    // 正在导入的工作列表状态
    private val _importJobs = MutableStateFlow<List<ImportJob>>(emptyList())
    val importJobs: StateFlow<List<ImportJob>> = _importJobs
    
    init {
        // 启动监听WorkManager任务状态
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(context)
            workManager.getWorkInfosByTagLiveData(IMPORT_WORKER_TAG)
                .observeForever { workInfoList ->
                    val jobs = workInfoList.map { workInfo ->
                        ImportJob(
                            id = workInfo.id.toString(),
                            filename = workInfo.progress.getString(KEY_FILENAME) ?: "",
                            state = when (workInfo.state) {
                                WorkInfo.State.RUNNING -> ImportJobState.RUNNING
                                WorkInfo.State.SUCCEEDED -> ImportJobState.COMPLETED
                                WorkInfo.State.FAILED -> ImportJobState.FAILED
                                WorkInfo.State.CANCELLED -> ImportJobState.CANCELLED
                                else -> ImportJobState.PENDING
                            },
                            progress = workInfo.progress.getInt(KEY_PROGRESS, 0),
                            currentStep = workInfo.progress.getInt(KEY_STEP, 0),
                            errorMessage = workInfo.outputData.getString(KEY_ERROR)
                        )
                    }
                    _importJobs.value = jobs
                }
        }
    }
    
    /**
     * 检查文件是否支持
     */
    fun checkFileSupported(fileName: String): Boolean {
        return FileUtil.isSupportedEbookFormat(fileName)
    }
    
    /**
     * 导入单个电子书
     */
    fun importBook(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            cursor.getString(displayNameIndex)
                        } else null
                    } else null
                } ?: uri.lastPathSegment ?: "unknown.epub"
                
                // 检查文件格式是否支持
                if (!checkFileSupported(fileName)) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = "不支持的文件格式: $fileName"
                        )
                    }
                    return@launch
                }
                
                // 创建工作请求
                val workData = Data.Builder()
                    .putString(KEY_URI, uri.toString())
                    .putString(KEY_FILENAME, fileName)
                    .build()
                
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
                
                val importRequest = OneTimeWorkRequestBuilder<BookImportWorker>()
                    .setInputData(workData)
                    .setConstraints(constraints)
                    .addTag(IMPORT_WORKER_TAG)
                    .build()
                
                // 启动导入工作
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "import_$fileName",
                    ExistingWorkPolicy.REPLACE,
                    importRequest
                )
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        successMessage = "已开始导入: $fileName"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "导入失败", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "导入失败: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 批量导入电子书
     */
    fun importBooks(uris: List<Uri>) {
        for (uri in uris) {
            importBook(uri)
        }
    }
    
    /**
     * 取消导入
     */
    fun cancelImport(jobId: String) {
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(jobId))
    }
    
    /**
     * 清除消息
     */
    fun clearMessages() {
        _uiState.update { 
            it.copy(
                errorMessage = null,
                successMessage = null
            )
        }
    }
    
    /**
     * 更新是否显示完成的任务
     */
    fun updateShowCompleted(show: Boolean) {
        _uiState.update { it.copy(showCompletedJobs = show) }
    }
    
    companion object {
        const val IMPORT_WORKER_TAG = "book_import"
        const val KEY_URI = "uri"
        const val KEY_FILENAME = "filename"
        const val KEY_PROGRESS = "progress"
        const val KEY_STEP = "step"
        const val KEY_ERROR = "error"
    }
}

/**
 * 导入书籍UI状态
 */
data class ImportBookUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showCompletedJobs: Boolean = true
)

/**
 * 导入任务状态
 */
data class ImportJob(
    val id: String,
    val filename: String,
    val state: ImportJobState,
    val progress: Int,
    val currentStep: Int,
    val errorMessage: String? = null
)

/**
 * 导入任务状态枚举
 */
enum class ImportJobState {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
} 