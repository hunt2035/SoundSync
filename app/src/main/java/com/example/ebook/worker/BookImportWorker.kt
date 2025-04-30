package com.example.ebook.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.ebook.data.local.AppDatabase
import com.example.ebook.data.repository.BookRepositoryImpl
import com.example.ebook.domain.model.Book
import com.example.ebook.domain.model.BookFormat
import com.example.ebook.domain.model.BookType
import com.example.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_ERROR
import com.example.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_FILENAME
import com.example.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_PROGRESS
import com.example.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_STEP
import com.example.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_URI
import com.example.ebook.util.FileUtil
import com.example.ebook.util.MetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 电子书导入后台工作器
 */
class BookImportWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val TAG = "BookImportWorker"
    
    // 导入步骤总数
    private val TOTAL_STEPS = 4
    
    // 各步骤权重
    private val STEP_WEIGHTS = mapOf(
        ImportStep.FILE_VALIDATION to 10,
        ImportStep.METADATA_EXTRACTION to 40,
        ImportStep.COVER_GENERATION to 30,
        ImportStep.DATABASE_INSERTION to 20
    )
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILENAME) ?: "未知文件"
        
        try {
            val uri = Uri.parse(uriString)
            
            // 步骤1：文件验证
            setProgress(ImportStep.FILE_VALIDATION, fileName, 0)
            val bookFormat = BookFormat.fromFileName(fileName)
            if (bookFormat == BookFormat.UNKNOWN) {
                return@withContext createFailureResult("不支持的文件格式: $fileName")
            }
            
            // 验证文件大小
            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
            if (fileSize <= 0) {
                return@withContext createFailureResult("无法读取文件或文件为空: $fileName")
            }
            
            // 验证存储空间
            val freeSpace = File(context.filesDir.path).freeSpace
            if (fileSize > freeSpace) {
                return@withContext createFailureResult("存储空间不足，需要 ${FileUtil.getFormattedFileSize(fileSize)}，" +
                        "可用 ${FileUtil.getFormattedFileSize(freeSpace)}")
            }
            
            // 复制文件到应用私有存储
            setProgress(ImportStep.FILE_VALIDATION, fileName, 50)
            val copyResult = FileUtil.copyUriToAppStorage(context, uri, "books")
            if (copyResult.isFailure) {
                return@withContext createFailureResult("复制文件失败: ${copyResult.exceptionOrNull()?.message}")
            }
            
            val bookFile = copyResult.getOrNull() ?: 
                return@withContext createFailureResult("复制文件失败")
            
            setProgress(ImportStep.FILE_VALIDATION, fileName, 100)
            
            // 步骤2：元数据提取
            setProgress(ImportStep.METADATA_EXTRACTION, fileName, 0)
            
            // 计算哈希值
            val fileHash = FileUtil.calculateSHA256(bookFile)
            
            // 检查是否重复导入
            val bookDao = AppDatabase.getInstance(context).bookDao()
            val bookRepository = BookRepositoryImpl(context, bookDao)
            val allBooks = withContext(Dispatchers.Default) {
                bookRepository.getAllBooks().first()
            }
            
            // 如果存在相同哈希值的书籍，认为是重复导入
            val duplicateBook = allBooks.find { book -> 
                book.filePath == bookFile.absolutePath || (fileHash.isNotEmpty() && book.fileHash == fileHash)
            }
            
            if (duplicateBook != null) {
                // 已存在相同书籍，删除临时文件
                bookFile.delete()
                return@withContext createFailureResult("该书籍已存在: ${duplicateBook.title}")
            }
            
            setProgress(ImportStep.METADATA_EXTRACTION, fileName, 30)
            
            // 提取元数据
            val metadataExtractor = MetadataExtractor(context)
            val metadataResult = metadataExtractor.extractMetadata(bookFile, bookFormat)
            
            if (metadataResult.isFailure) {
                return@withContext createFailureResult("元数据提取失败: ${metadataResult.exceptionOrNull()?.message}")
            }
            
            val metadata = metadataResult.getOrNull() ?: 
                return@withContext createFailureResult("元数据提取失败")
            
            setProgress(ImportStep.METADATA_EXTRACTION, fileName, 100)
            
            // 步骤3：封面图片处理
            setProgress(ImportStep.COVER_GENERATION, fileName, 0)
            var coverPath: String? = null
            
            metadata.coverImage?.let { coverBitmap ->
                // 生成封面图片文件名
                val coverFileName = "${UUID.randomUUID()}.jpg"
                
                // 保存封面图片
                val coverResult = FileUtil.saveCoverImage(context, coverBitmap, coverFileName)
                if (coverResult.isSuccess) {
                    coverPath = coverResult.getOrNull()
                }
                
                // 回收Bitmap
                coverBitmap.recycle()
            }
            
            setProgress(ImportStep.COVER_GENERATION, fileName, 100)
            
            // 步骤4：数据库插入
            setProgress(ImportStep.DATABASE_INSERTION, fileName, 0)
            
            // 创建书籍对象
            val book = Book(
                title = metadata.title,
                author = metadata.author,
                filePath = bookFile.absolutePath,
                coverPath = coverPath,
                fileHash = fileHash,
                type = when(bookFormat) {
                    BookFormat.EPUB -> BookType.EPUB
                    BookFormat.PDF -> BookType.PDF
                    BookFormat.TXT -> BookType.TXT
                    BookFormat.MOBI, BookFormat.UNKNOWN -> BookType.UNKNOWN
                },
                totalPages = metadata.pageCount,
                addedDate = System.currentTimeMillis(),
                lastOpenedDate = System.currentTimeMillis()
            )
            
            // 将书籍添加到数据库
            bookRepository.addBook(book)
            
            setProgress(ImportStep.DATABASE_INSERTION, fileName, 100)
            
            // 导入成功
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "导入失败", e)
            createFailureResult("导入失败: ${e.message}")
        }
    }
    
    private fun createFailureResult(errorMessage: String?): Result {
        val outputData = Data.Builder()
            .putString(KEY_ERROR, errorMessage)
            .build()
        return Result.failure(outputData)
    }
    
    private suspend fun setProgress(step: ImportStep, fileName: String, stepProgress: Int) {
        // 计算总进度百分比
        var totalProgress = 0
        
        // 加上之前完成步骤的权重
        for (s in ImportStep.values()) {
            if (s.ordinal < step.ordinal) {
                totalProgress += STEP_WEIGHTS[s] ?: 0
            }
        }
        
        // 加上当前步骤的进度占比
        val currentStepWeight = STEP_WEIGHTS[step] ?: 0
        totalProgress += (currentStepWeight * stepProgress / 100)
        
        val progressData = Data.Builder()
            .putString(KEY_FILENAME, fileName)
            .putInt(KEY_PROGRESS, totalProgress)
            .putInt(KEY_STEP, step.ordinal)
            .build()
        
        setProgress(progressData)
    }
}

/**
 * 导入步骤
 */
enum class ImportStep {
    FILE_VALIDATION,
    METADATA_EXTRACTION,
    COVER_GENERATION,
    DATABASE_INSERTION
} 