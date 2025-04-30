package com.example.ebook.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 文本处理工具类
 */
object TextProcessor {
    /**
     * 处理文本：
     * 1. 去除开头的所有空行
     * 2. 把文本中的多个空行转换为一个空行
     */
    fun processText(text: String): String {
        // 分行处理
        val lines = text.lines()
        
        // 找到第一个非空行
        val firstNonEmptyIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstNonEmptyIndex == -1) return "" // 如果全都是空行，返回空字符串
        
        // 从第一个非空行开始处理
        val processedLines = mutableListOf<String>()
        var previousLineIsEmpty = false
        
        for (i in firstNonEmptyIndex until lines.size) {
            val line = lines[i]
            val currentLineIsEmpty = line.isBlank()
            
            if (currentLineIsEmpty) {
                if (!previousLineIsEmpty) {
                    // 只有当前一行不是空行时，才添加空行
                    processedLines.add("")
                    previousLineIsEmpty = true
                }
            } else {
                processedLines.add(line)
                previousLineIsEmpty = false
            }
        }
        
        return processedLines.joinToString("\n")
    }
    
    /**
     * 保存文本到指定目录
     */
    suspend fun saveTextToFile(
        context: android.content.Context,
        text: String,
        fileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 创建保存文本的目录
            val targetDir = File(context.filesDir, "newtxt").apply { 
                if (!exists()) mkdirs() 
            }
            
            // 生成txt文件
            val targetFile = File(targetDir, "$fileName.txt")
            
            // 处理文本并写入文件
            val processedText = processText(text)
            FileOutputStream(targetFile).use { outputStream ->
                outputStream.write(processedText.toByteArray())
                outputStream.flush()
            }
            
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 从文本内容中提取标题
     * 使用第一行作为标题，如果超过16个字符则截断
     */
    fun extractTitle(text: String): String {
        val firstLine = text.trim().split("\n").firstOrNull()?.trim() ?: "新建文本"
        return if (firstLine.length > 16) {
            firstLine.substring(0, 16)
        } else {
            firstLine
        }
    }
} 