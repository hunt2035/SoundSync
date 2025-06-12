package com.wanderreads.ebook.util

import android.util.Log
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Word文件文本提取工具类
 * 用于从Word文件(doc/docx)中提取纯文本内容
 */
object WordTextExtractor {
    private const val TAG = "WordTextExtractor"

    /**
     * 从Word文件中提取文本内容
     *
     * @param file Word文件(.doc或.docx)
     * @return 提取的文本内容，如果提取失败则返回空字符串
     */
    fun extractText(file: File): String {
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "文件不存在或无法读取: ${file.absolutePath}")
            return ""
        }

        var fileInputStream: FileInputStream? = null
        try {
            fileInputStream = FileInputStream(file)
            
            // 根据文件扩展名决定使用哪种提取器
            val text = when {
                file.name.endsWith(".docx", ignoreCase = true) -> {
                    // 处理DOCX文件
                    val document = XWPFDocument(fileInputStream)
                    val extractor = XWPFWordExtractor(document)
                    val content = extractor.text
                    extractor.close()
                    document.close()
                    content
                }
                file.name.endsWith(".doc", ignoreCase = true) -> {
                    // 处理DOC文件
                    val document = HWPFDocument(fileInputStream)
                    val extractor = WordExtractor(document)
                    val content = extractor.text
                    extractor.close()
                    document.close()
                    content
                }
                else -> {
                    Log.e(TAG, "不支持的文件格式: ${file.name}")
                    ""
                }
            }
            
            // 处理提取的文本，优化格式
            return formatExtractedText(text)
            
        } catch (e: Exception) {
            Log.e(TAG, "提取Word文本时发生错误", e)
            return ""
        } finally {
            try {
                fileInputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "关闭文件流失败", e)
            }
        }
    }
    
    /**
     * 格式化提取的文本内容
     * 处理特殊字符和格式问题
     */
    private fun formatExtractedText(text: String): String {
        if (text.isBlank()) return ""
        
        return text
            .replace("\r\n", "\n") // 统一换行符
            .replace("\r", "\n")    // 处理旧式Mac换行符
            .replace("\t", "    ")  // 替换制表符为4个空格
            .trim()                 // 移除首尾空白
    }
} 