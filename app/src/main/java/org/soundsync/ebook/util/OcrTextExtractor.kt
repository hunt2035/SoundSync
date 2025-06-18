package org.soundsync.ebook.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * OCR文本提取工具类
 * 用于从图片中提取文本内容
 */
object OcrTextExtractor {
    private const val TAG = "OcrTextExtractor"
    
    // 创建中文和拉丁文识别器，支持中英文混合识别
    private val textRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 从Uri指向的图片中提取文本
     *
     * @param context 上下文
     * @param imageUri 图片Uri
     * @return 提取的文本内容，如果提取失败则返回空字符串
     */
    suspend fun extractTextFromUri(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val result = processImage(image)
            formatRecognizedText(result)
        } catch (e: IOException) {
            Log.e(TAG, "无法从URI加载图片: ${e.message}", e)
            ""
        } catch (e: Exception) {
            Log.e(TAG, "OCR识别过程中发生错误: ${e.message}", e)
            ""
        }
    }

    /**
     * 从Bitmap中提取文本
     *
     * @param bitmap 图片Bitmap
     * @return 提取的文本内容，如果提取失败则返回空字符串
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = processImage(image)
            formatRecognizedText(result)
        } catch (e: Exception) {
            Log.e(TAG, "OCR识别过程中发生错误: ${e.message}", e)
            ""
        }
    }

    /**
     * 处理图片并识别文本
     */
    private suspend fun processImage(image: InputImage): Text {
        return try {
            textRecognizer.process(image).await()
        } catch (e: Exception) {
            Log.e(TAG, "文本识别失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 格式化识别的文本
     */
    private fun formatRecognizedText(result: Text): String {
        val textBuilder = StringBuilder()
        
        for (textBlock in result.textBlocks) {
            for (line in textBlock.lines) {
                textBuilder.append(line.text).append("\n")
            }
            textBuilder.append("\n") // 段落之间添加额外空行
        }
        
        // 优化文本格式
        val initialText = textBuilder.toString()
            .replace(Regex("\n{3,}"), "\n\n") // 将多个连续空行替换为最多两个空行
            .trim() // 移除开头和结尾的空白字符
            
        // 使用OcrLineProcessor处理换行符
        return OcrLineProcessor.processOcrText(initialText)
    }
    
    /**
     * 释放资源
     */
    fun close() {
        textRecognizer.close()
    }
} 