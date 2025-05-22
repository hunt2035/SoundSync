package com.wanderreads.ebook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.wanderreads.ebook.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.DecimalFormat

/**
 * 文件工具类
 */
object FileUtil {
    private const val TAG = "FileUtil"
    private const val BUFFER_SIZE = 8192
    private const val ROOT_DIR = "WanderReads"

    /**
     * 检查外部存储是否可写
     */
    fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }
    
    /**
     * 复制Uri到应用私有目录
     */
    suspend fun copyUriToAppStorage(context: Context, uri: Uri, subDir: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(context, uri)
            val targetDir = File(context.filesDir, subDir).apply { 
                if (!exists()) mkdirs() 
            }
            val targetFile = File(targetDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            } ?: return@withContext Result.failure(Exception("无法打开文件"))

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "复制文件失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 复制Uri到外部存储Documents目录下的WanderReads目录
     */
    suspend fun copyToExternalStorage(context: Context, uri: Uri, subDir: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(context, uri)
            
            // 创建外部存储目录
            val externalDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appRootDir = File(externalDocumentsDir, ROOT_DIR)
            val targetDir = File(appRootDir, subDir)
            
            if (!targetDir.exists()) {
                if (!targetDir.mkdirs()) {
                    Log.e(TAG, "无法创建目录: ${targetDir.absolutePath}")
                    return@withContext Result.failure(Exception("无法创建目录: ${targetDir.absolutePath}"))
                }
            }
            
            val targetFile = File(targetDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            } ?: return@withContext Result.failure(Exception("无法打开文件"))

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "复制文件到外部存储失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 保存封面图片到外部存储
     */
    suspend fun saveCoverImageToExternal(
        context: Context, 
        bitmap: Bitmap, 
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val externalDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appRootDir = File(externalDocumentsDir, ROOT_DIR)
            val coverDir = File(appRootDir, "covers")
            
            if (!coverDir.exists()) {
                if (!coverDir.mkdirs()) {
                    return@withContext Result.failure(Exception("无法创建封面目录"))
                }
            }
            
            val coverFile = File(coverDir, fileName)
            
            FileOutputStream(coverFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
            }
            
            Result.success(coverFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "保存封面图片到外部存储失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从Uri获取文件名
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        documentFile?.name?.let { return it }

        // 尝试从内容提供者查询文件名
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }

        // 如果无法获取文件名，使用时间戳
        return "ebook_${System.currentTimeMillis()}.${getFileExtension(uri.toString())}"
    }

    /**
     * 获取文件扩展名
     */
    fun getFileExtension(path: String): String {
        return path.substringAfterLast('.', "")
    }

    /**
     * 计算文件SHA-256哈希值
     */
    suspend fun calculateSHA256(file: File): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "计算哈希值失败", e)
            ""
        }
    }

    /**
     * 保存封面图片到缓存目录
     */
    suspend fun saveCoverImage(
        context: Context, 
        bitmap: Bitmap, 
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val coverDir = File(context.filesDir, "covers").apply { 
                if (!exists()) mkdirs() 
            }
            val coverFile = File(coverDir, fileName)
            
            FileOutputStream(coverFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
            }
            
            Result.success(coverFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "保存封面图片失败", e)
            Result.failure(e)
        }
    }

    /**
     * 创建缩略图
     */
    suspend fun createThumbnail(
        inputPath: String, 
        width: Int, 
        height: Int
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputPath, options)
            
            val sourceWidth = options.outWidth
            val sourceHeight = options.outHeight
            
            var inSampleSize = 1
            if (sourceHeight > height || sourceWidth > width) {
                val halfHeight = sourceHeight / 2
                val halfWidth = sourceWidth / 2
                
                while (halfHeight / inSampleSize >= height && halfWidth / inSampleSize >= width) {
                    inSampleSize *= 2
                }
            }
            
            options.apply {
                inJustDecodeBounds = false
                this.inSampleSize = inSampleSize
            }
            
            val bitmap = BitmapFactory.decodeFile(inputPath, options)
            Result.success(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "创建缩略图失败", e)
            Result.failure(e)
        }
    }

    /**
     * 检测文件是否存在
     */
    fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    /**
     * 获取格式化的文件大小
     */
    fun getFormattedFileSize(sizeInBytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        
        val formatter = DecimalFormat("#.##")
        return when {
            sizeInBytes < kb -> "$sizeInBytes B"
            sizeInBytes < mb -> "${formatter.format(sizeInBytes / kb)} KB"
            sizeInBytes < gb -> "${formatter.format(sizeInBytes / mb)} MB"
            else -> "${formatter.format(sizeInBytes / gb)} GB"
        }
    }

    /**
     * 检查是否支持的电子书格式
     */
    fun isSupportedEbookFormat(fileName: String): Boolean {
        val format = BookFormat.fromFileName(fileName)
        return format != BookFormat.UNKNOWN
    }
    
    /**
     * 获取外部存储中的应用根目录
     */
    fun getExternalAppDir(): File {
        val externalDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(externalDocumentsDir, ROOT_DIR)
    }
} 