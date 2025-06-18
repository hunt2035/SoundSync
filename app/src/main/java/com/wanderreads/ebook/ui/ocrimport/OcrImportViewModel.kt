package com.wanderreads.ebook.ui.ocrimport

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanderreads.ebook.data.repository.BookRepository
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.domain.model.BookType
import com.wanderreads.ebook.util.OcrTextExtractor
import com.wanderreads.ebook.util.TextProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/**
 * OCR导入ViewModel
 */
class OcrImportViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {
    
    private val TAG = "OcrImportViewModel"
    
    // UI状态
    private val _uiState = MutableStateFlow(OcrImportUiState())
    val uiState: StateFlow<OcrImportUiState> = _uiState.asStateFlow()
    
    /**
     * 设置待裁剪的图片URI
     */
    fun setCropImageUri(uri: Uri) {
        _uiState.update { it.copy(
            cropImageUri = uri,
            showCropScreen = true,
            showImportMethodSelection = false
        )}
    }
    
    /**
     * 取消裁剪操作
     */
    fun cancelCrop() {
        _uiState.update { it.copy(
            showCropScreen = false,
            cropImageUri = null,
            showImportMethodSelection = true
        )}
    }

    /**
     * 从图片URI提取文本
     */
    fun extractTextFromImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    isLoading = true,
                    errorMessage = null
                )}
                
                // 提取文本
                val extractedText = OcrTextExtractor.extractTextFromUri(context, imageUri)
                
                if (extractedText.isBlank()) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = "未能从图片中提取到文本"
                    )}
                    return@launch
                }
                
                // 更新UI状态
                _uiState.update { it.copy(
                    isLoading = false,
                    extractedText = extractedText,
                    showTextPreview = true,
                    showImportMethodSelection = false,
                    showCropScreen = false
                )}
                
            } catch (e: Exception) {
                Log.e(TAG, "文本提取失败", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "文本提取失败: ${e.message}"
                )}
            }
        }
    }
    
    /**
     * 从Bitmap提取文本
     */
    fun extractTextFromBitmap(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    isLoading = true,
                    errorMessage = null
                )}
                
                // 提取文本
                val extractedText = OcrTextExtractor.extractTextFromBitmap(bitmap)
                
                if (extractedText.isBlank()) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = "未能从图片中提取到文本"
                    )}
                    return@launch
                }
                
                // 更新UI状态
                _uiState.update { it.copy(
                    isLoading = false,
                    extractedText = extractedText,
                    showTextPreview = true,
                    showImportMethodSelection = false,
                    showCropScreen = false
                )}
                
            } catch (e: Exception) {
                Log.e(TAG, "文本提取失败", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = "文本提取失败: ${e.message}"
                )}
            }
        }
    }
    
    /**
     * 保存提取的文本
     */
    fun saveExtractedText(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    isSaving = true,
                    errorMessage = null
                )}
                
                val text = uiState.value.extractedText
                if (text.isBlank()) {
                    _uiState.update { it.copy(
                        isSaving = false,
                        errorMessage = "没有可保存的文本"
                    )}
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
                            type = BookType.TXT,
                            addedDate = System.currentTimeMillis(),
                            fileHash = generateFileHash(file)
                        )
                        
                        // 添加到书库
                        bookRepository.addBook(book)
                        
                        // 更新UI状态
                        _uiState.update { it.copy(
                            isSaving = false,
                            saveSuccess = true,
                            savedBook = book
                        )}
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(
                            isSaving = false,
                            errorMessage = "保存文本失败: ${error.message}"
                        )}
                    }
                
            } catch (e: Exception) {
                Log.e(TAG, "保存文本失败", e)
                _uiState.update { it.copy(
                    isSaving = false,
                    errorMessage = "保存文本失败: ${e.message}"
                )}
            }
        }
    }
    
    /**
     * 更新提取的文本
     */
    fun updateExtractedText(text: String) {
        _uiState.update { it.copy(extractedText = text) }
    }
    
    /**
     * 重置状态
     */
    fun resetState() {
        _uiState.update { 
            OcrImportUiState(
                extractedText = it.extractedText,
                showImportMethodSelection = it.showImportMethodSelection
            )
        }
    }
    
    /**
     * 清除提取的文本
     */
    fun clearExtractedText() {
        _uiState.update { it.copy(
            extractedText = "",
            showTextPreview = false
        )}
    }
    
    /**
     * 隐藏文本预览
     */
    fun hideTextPreview() {
        _uiState.update { it.copy(
            showTextPreview = false,
            showImportMethodSelection = true
        )}
    }
    
    /**
     * 重置导入方式选择状态
     */
    fun resetImportMethodSelection() {
        _uiState.update { it.copy(showImportMethodSelection = true) }
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
    
    override fun onCleared() {
        super.onCleared()
        // 释放OCR资源
        OcrTextExtractor.close()
    }
}

/**
 * OCR导入UI状态
 */
data class OcrImportUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val extractedText: String = "",
    val showTextPreview: Boolean = false,
    val showImportMethodSelection: Boolean = true,
    val showCropScreen: Boolean = false,
    val cropImageUri: Uri? = null,
    val saveSuccess: Boolean = false,
    val savedBook: Book? = null,
    val errorMessage: String? = null
) 