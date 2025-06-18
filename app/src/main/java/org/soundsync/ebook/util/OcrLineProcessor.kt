package org.soundsync.ebook.util

import android.util.Log

/**
 * OCR文本行处理工具类
 * 用于处理OCR提取文本中的换行符
 * 规则：如果换行符右边是2个空格或TAB符，则保留换行符，否则需剔除掉换行符
 */
object OcrLineProcessor {
    private const val TAG = "OcrLineProcessor"
    
    /**
     * 处理OCR提取文本中的换行符
     * 规则：如果换行符右边是2个空格或TAB符，则保留换行符，否则需剔除掉换行符
     *
     * @param text OCR提取的原始文本
     * @return 处理后的文本
     */
    fun processOcrText(text: String): String {
        if (text.isBlank()) return text
        
        val lines = text.lines()
        val processedLines = mutableListOf<String>()
        var currentLine = ""
        
        for (i in lines.indices) {
            val line = lines[i]
            
            // 如果是最后一行或空行，直接添加
            if (i == lines.size - 1 || line.isBlank()) {
                if (currentLine.isNotEmpty()) {
                    processedLines.add(currentLine)
                    currentLine = ""
                }
                if (line.isNotBlank()) {
                    processedLines.add(line)
                } else if (line.isBlank()) {
                    processedLines.add("")
                }
                continue
            }
            
            // 检查下一行是否以两个空格或Tab开头
            val nextLine = lines[i + 1]
            if (nextLine.startsWith("  ") || nextLine.startsWith("\t")) {
                // 如果下一行以两个空格或Tab开头，保留当前行的换行符
                if (currentLine.isNotEmpty()) {
                    processedLines.add(currentLine)
                    currentLine = ""
                }
                processedLines.add(line)
            } else {
                // 否则，将当前行和下一行连接（去除换行符）
                if (currentLine.isEmpty()) {
                    currentLine = line
                } else {
                    // 在连接行时，添加一个空格以保持单词间的分隔
                    currentLine = "$currentLine $line"
                }
            }
        }
        
        // 确保最后一行也被添加
        if (currentLine.isNotEmpty()) {
            processedLines.add(currentLine)
        }
        
        Log.d(TAG, "OCR文本行处理前行数: ${lines.size}, 处理后行数: ${processedLines.size}")
        return processedLines.joinToString("\n")
    }
} 