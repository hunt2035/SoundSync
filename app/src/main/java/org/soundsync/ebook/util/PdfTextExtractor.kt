package org.soundsync.ebook.util

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException

/**
 * PDF文本提取工具类
 * 用于从PDF文件中提取纯文本内容
 */
object PdfTextExtractor {
    private const val TAG = "PdfTextExtractor"

    /**
     * 从PDF文件中提取文本内容
     *
     * @param file PDF文件
     * @return 提取的文本内容，如果提取失败则返回空字符串
     */
    fun extractText(file: File): String {
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "文件不存在或无法读取: ${file.absolutePath}")
            return ""
        }

        var document: PDDocument? = null
        try {
            document = PDDocument.load(file)
            
            // 检查文档是否被加密
            if (document.isEncrypted) {
                Log.w(TAG, "PDF文档已加密，可能无法正确提取文本")
                try {
                    // 尝试使用空密码解密
                    document.close()
                    document = PDDocument.load(file, "")
                } catch (e: Exception) {
                    Log.e(TAG, "无法解密PDF文档", e)
                    return ""
                }
            }
            
            // 初始化文本提取器
            val stripper = PDFTextStripper()
            
            // 提取所有页面的文本
            val text = stripper.getText(document)
            
            // 处理提取的文本，优化格式
            return formatExtractedText(text)
            
        } catch (e: IOException) {
            Log.e(TAG, "提取PDF文本时发生错误", e)
            return ""
        } finally {
            try {
                document?.close()
            } catch (e: IOException) {
                Log.e(TAG, "关闭PDF文档时发生错误", e)
            }
        }
    }
    
    /**
     * 优化提取的文本格式
     * 
     * @param text 原始提取的文本
     * @return 格式优化后的文本
     */
    private fun formatExtractedText(text: String): String {
        if (text.isBlank()) return text
        
        return text
            // 移除连续的空行，保留最多两个连续换行符
            .replace(Regex("\n{3,}"), "\n\n")
            // 移除行首空格
            .replace(Regex("^\\s+", RegexOption.MULTILINE), "")
            // 移除行尾空格
            .replace(Regex("\\s+$", RegexOption.MULTILINE), "")
            // 将连续的空格替换为单个空格
            .replace(Regex("\\s{2,}"), " ")
            // 确保文本以换行符结束
            .let { if (it.endsWith("\n")) it else "$it\n" }
    }
} 