package com.wanderreads.ebook.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 文本处理工具类
 */
object TextProcessor {
    private const val TAG = "TextProcessor"
    private const val ROOT_DIR = "WanderReads"
    
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
     * 检查是否有外部存储权限
     * 在Android 11+上需要MANAGE_EXTERNAL_STORAGE权限
     * 在较低版本Android上只需检查存储状态
     */
    fun hasExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager() // Android 11+需要此权限
        } else {
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED // 低版本Android只需检查存储状态
        }
    }
    
    /**
     * 保存文本到外部存储的Documents/WanderReads/books目录
     */
    suspend fun saveTextToFile(
        context: Context,
        text: String,
        fileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        var createdExternalFile = false
        
        try {
            // 处理文本
            val processedText = processText(text)
            
            // 尝试保存到外部存储
            val canUseExternalStorage = hasExternalStoragePermission()
            Log.d(TAG, "外部存储是否可写: $canUseExternalStorage")
            
            if (canUseExternalStorage) {
                try {
                    // 分类处理不同Android版本
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        // Android 10 特殊处理：直接尝试使用legacy方式访问Documents目录
                        Log.d(TAG, "在Android 10上使用legacy storage访问外部存储")
                        
                        val externalDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        Log.d(TAG, "外部Documents目录路径: ${externalDocumentsDir.absolutePath}, 存在: ${externalDocumentsDir.exists()}")
                        
                        // 确保Documents目录存在
                        if (!externalDocumentsDir.exists()) {
                            val created = externalDocumentsDir.mkdirs()
                            Log.d(TAG, "创建Documents目录结果: $created")
                            if (!created) {
                                throw IOException("无法创建Documents目录，将尝试其他方法")
                            }
                        }
                        
                        // 创建应用根目录
                        val appRootDir = File(externalDocumentsDir, ROOT_DIR)
                        if (!appRootDir.exists()) {
                            val created = appRootDir.mkdirs()
                            Log.d(TAG, "创建应用根目录结果: $created")
                            if (!created) {
                                throw IOException("无法创建应用根目录，将尝试其他方法")
                            }
                        }
                        
                        // 创建books目录
                        val booksDir = File(appRootDir, "books")
                        if (!booksDir.exists()) {
                            val created = booksDir.mkdirs()
                            Log.d(TAG, "创建books目录结果: $created")
                            if (!created) {
                                throw IOException("无法创建books目录，将尝试其他方法")
                            }
                        }
                        
                        // 生成txt文件
                        outputFile = File(booksDir, "$fileName.txt")
                        Log.d(TAG, "将使用外部Documents/WanderReads/books目录保存文件: ${outputFile.absolutePath}")
                        
                        // 写入文件
                        try {
                            FileOutputStream(outputFile).use { outputStream ->
                                outputStream.write(processedText.toByteArray())
                                outputStream.flush()
                            }
                            createdExternalFile = true
                            Log.d(TAG, "成功写入文件到外部存储: ${outputFile.absolutePath}")
                        } catch (e: Exception) {
                            Log.e(TAG, "写入文件到外部存储失败: ${e.message}", e)
                            throw e  // 重新抛出异常，触发后续的备用方案
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11+ 使用MANAGE_EXTERNAL_STORAGE权限
                        val externalDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        val appRootDir = File(externalDocumentsDir, ROOT_DIR)
                        val booksDir = File(appRootDir, "books")
                        
                        // 逐级确保目录存在
                        if (!externalDocumentsDir.exists() && !externalDocumentsDir.mkdirs()) {
                            throw IOException("无法创建外部Documents目录")
                        }
                        
                        if (!appRootDir.exists() && !appRootDir.mkdirs()) {
                            throw IOException("无法创建应用根目录")
                        }
                        
                        if (!booksDir.exists() && !booksDir.mkdirs()) {
                            throw IOException("无法创建books目录")
                        }
                        
                        // 生成txt文件
                        outputFile = File(booksDir, "$fileName.txt")
                        Log.d(TAG, "将使用外部Documents/WanderReads/books目录保存文件: ${outputFile.absolutePath}")
                        
                        // 写入文件
                        FileOutputStream(outputFile).use { outputStream ->
                            outputStream.write(processedText.toByteArray())
                            outputStream.flush()
                        }
                        createdExternalFile = true
                    } else {
                        // Android 9及以下版本
                        val externalDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        val appRootDir = File(externalDocumentsDir, ROOT_DIR)
                        val booksDir = File(appRootDir, "books")
                        
                        // 逐级确保目录存在
                        if (!externalDocumentsDir.exists() && !externalDocumentsDir.mkdirs()) {
                            throw IOException("无法创建外部Documents目录")
                        }
                        
                        if (!appRootDir.exists() && !appRootDir.mkdirs()) {
                            throw IOException("无法创建应用根目录")
                        }
                        
                        if (!booksDir.exists() && !booksDir.mkdirs()) {
                            throw IOException("无法创建books目录")
                        }
                        
                        // 生成txt文件
                        outputFile = File(booksDir, "$fileName.txt")
                        Log.d(TAG, "将使用外部Documents/WanderReads/books目录保存文件: ${outputFile.absolutePath}")
                        
                        // 写入文件
                        FileOutputStream(outputFile).use { outputStream ->
                            outputStream.write(processedText.toByteArray())
                            outputStream.flush()
                        }
                        createdExternalFile = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "使用外部存储失败: ${e.message}", e)
                    // 清除可能创建的不完整文件
                    if (outputFile != null && outputFile.exists() && !createdExternalFile) {
                        try {
                            outputFile.delete()
                        } catch (ex: Exception) {
                            Log.e(TAG, "删除不完整文件失败: ${ex.message}")
                        }
                    }
                    
                    // 重置outputFile，准备尝试备用方案
                    outputFile = null
                    
                    // Android 11+特殊提示
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        Log.e(TAG, "Android 11+设备缺少MANAGE_EXTERNAL_STORAGE权限")
                    }
                }
            } else {
                Log.w(TAG, "外部存储不可用或权限不足，将使用应用专属目录或私有目录")
            }
            
            // 尝试备用方案：Android 10 使用应用专属目录
            if (outputFile == null && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                try {
                    Log.d(TAG, "尝试使用Android 10应用专属外部存储目录")
                    val appSpecificExternalDir = context.getExternalFilesDir(null)
                    if (appSpecificExternalDir != null) {
                        val booksDir = File(appSpecificExternalDir, "newtxt").apply {
                            if (!exists()) {
                                val created = mkdirs()
                                Log.d(TAG, "创建应用专属books目录结果: $created")
                                if (!created) {
                                    throw IOException("无法创建应用专属books目录")
                                }
                            }
                        }
                        
                        outputFile = File(booksDir, "$fileName.txt")
                        Log.d(TAG, "将使用应用专属存储目录保存文件: ${outputFile.absolutePath}")
                        
                        // 写入文件
                        FileOutputStream(outputFile).use { outputStream ->
                            outputStream.write(processedText.toByteArray())
                            outputStream.flush()
                        }
                        createdExternalFile = true
                    } else {
                        throw IOException("无法获取应用专属外部存储目录")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "使用应用专属外部存储失败: ${e.message}", e)
                    if (outputFile != null && outputFile.exists()) {
                        outputFile.delete()
                    }
                    outputFile = null
                }
            }
            
            // 最后的备用方案：使用应用私有目录
            if (outputFile == null || !createdExternalFile) {
                outputFile = saveToInternalStorage(context, processedText, fileName).getOrThrow()
            }
            
            // 通知媒体库更新（适用于外部存储）
            if (createdExternalFile) {
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputFile.absolutePath),
                        arrayOf("text/plain"),
                        null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "媒体扫描失败: ${e.message}")
                }
            }
            
            Result.success(outputFile)
        } catch (e: Exception) {
            // 清除可能创建的不完整文件
            if (outputFile != null && outputFile.exists() && !createdExternalFile) {
                try {
                    outputFile.delete()
                } catch (ex: Exception) {
                    Log.e(TAG, "无法删除不完整文件: ${ex.message}")
                }
            }
            
            Log.e(TAG, "保存文本失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 保存文本到应用私有目录（作为备用方案）
     */
    private suspend fun saveToInternalStorage(
        context: Context,
        text: String,
        fileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "尝试保存到应用私有目录")
            // 创建保存文本的目录
            val targetDir = File(context.filesDir, "newtxt").apply { 
                if (!exists()) {
                    if (!mkdirs()) {
                        Log.e(TAG, "无法创建应用私有目录: ${absolutePath}")
                        return@withContext Result.failure(IOException("无法创建应用私有目录: ${absolutePath}"))
                    }
                }
            }
            
            // 生成txt文件
            val targetFile = File(targetDir, "$fileName.txt")
            
            // 写入文件
            FileOutputStream(targetFile).use { outputStream ->
                outputStream.write(text.toByteArray())
                outputStream.flush()
            }
            
            Log.d(TAG, "文本保存到内部存储成功: ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "保存文本到内部存储失败: ${e.message}", e)
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