package com.wanderreads.ebook.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 文件命名工具类
 * 提供统一的文件命名规则，用于新建文本、导入网址、本地导入文件、合成语音文件等
 */
object FileNamingUtil {
    private const val TAG = "FileNamingUtil"
    
    // 非法字符正则表达式 (Windows文件系统不允许的字符: \ / : * ? " < > |)
    private val ILLEGAL_CHARS_REGEX = Regex("[\\\\/:*?\"<>|]")
    
    /**
     * 生成新建文本或网址导入的文件名
     * 规则：书名前18个字符 + 下划线 + 年份后2位 + 月份(2位) + 日(2位) + 4位随机数 + .md
     * 
     * @param bookName 书名
     * @return 生成的文件名
     */
    fun generateTextFileName(bookName: String): String {
        return generateFileName(bookName, 18, "md")
    }
    
    /**
     * 生成合成语音文件名
     * 规则：书名前18个字符 + 下划线 + 年份后2位 + 月份(2位) + 日(2位) + 4位随机数 + .mp3
     * 
     * @param bookName 书名
     * @return 生成的文件名
     */
    fun generateVoiceFileName(bookName: String): String {
        return generateFileName(bookName, 18, "mp3")
    }
    
    /**
     * 生成本地导入文件名
     * 规则：原文件名(不含扩展名)前20个字符 + 下划线 + 4位随机数 + .原扩展名
     * 
     * @param originalFileName 原文件名(含扩展名)
     * @return 生成的文件名
     */
    fun generateImportedFileName(originalFileName: String): String {
        try {
            // 分离文件名和扩展名
            val lastDotIndex = originalFileName.lastIndexOf('.')
            val nameWithoutExt = if (lastDotIndex > 0) {
                originalFileName.substring(0, lastDotIndex)
            } else {
                originalFileName
            }
            val extension = if (lastDotIndex > 0) {
                originalFileName.substring(lastDotIndex + 1)
            } else {
                "unknown"
            }
            
            // 替换非法字符
            val safeBookName = nameWithoutExt.replace(ILLEGAL_CHARS_REGEX, "_")
            
            // 截取前20个字符
            val truncatedName = if (safeBookName.length > 20) {
                safeBookName.substring(0, 20)
            } else {
                safeBookName
            }
            
            // 生成4位随机数
            val randomNumber = Random.nextInt(1000, 10000)
            
            // 组合文件名
            return "${truncatedName}_${randomNumber}.${extension}"
        } catch (e: Exception) {
            Log.e(TAG, "生成导入文件名失败", e)
            // 出错时使用时间戳作为备用方案
            val timestamp = System.currentTimeMillis()
            val extension = originalFileName.substringAfterLast('.', "unknown")
            return "imported_${timestamp}.${extension}"
        }
    }
    
    /**
     * 通用文件名生成方法
     * 
     * @param bookName 书名
     * @param maxLength 书名最大长度
     * @param extension 文件扩展名
     * @return 生成的文件名
     */
    private fun generateFileName(bookName: String, maxLength: Int, extension: String): String {
        try {
            // 替换非法字符
            val safeBookName = bookName.replace(ILLEGAL_CHARS_REGEX, "_")
            
            // 截取指定长度
            val truncatedName = if (safeBookName.length > maxLength) {
                safeBookName.substring(0, maxLength)
            } else {
                safeBookName
            }
            
            // 获取当前日期，格式为年份后2位 + 月份(2位) + 日(2位)
            val dateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
            val dateString = dateFormat.format(Date())
            
            // 生成4位随机数
            val randomNumber = Random.nextInt(1000, 10000)
            
            // 组合文件名
            return "${truncatedName}_${dateString}${randomNumber}.${extension}"
        } catch (e: Exception) {
            Log.e(TAG, "生成文件名失败", e)
            // 出错时使用时间戳作为备用方案
            val timestamp = System.currentTimeMillis()
            return "file_${timestamp}.${extension}"
        }
    }
} 