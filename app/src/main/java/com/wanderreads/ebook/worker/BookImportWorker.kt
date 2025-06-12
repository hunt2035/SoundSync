package com.wanderreads.ebook.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.wanderreads.ebook.data.local.AppDatabase
import com.wanderreads.ebook.data.repository.BookRepositoryImpl
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.domain.model.BookFormat
import com.wanderreads.ebook.domain.model.BookType
import com.wanderreads.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_ERROR
import com.wanderreads.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_FILENAME
import com.wanderreads.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_PROGRESS
import com.wanderreads.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_STEP
import com.wanderreads.ebook.ui.importbook.ImportBookViewModel.Companion.KEY_URI
import com.wanderreads.ebook.util.FileUtil
import com.wanderreads.ebook.util.MetadataExtractor
import com.wanderreads.ebook.util.PdfTextExtractor
import com.wanderreads.ebook.util.WordTextExtractor
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
            
            // 检查外部存储是否可写
            if (!FileUtil.isExternalStorageWritable()) {
                return@withContext createFailureResult("外部存储不可写，请检查权限设置")
            }
            
            // 验证文件大小
            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
            if (fileSize <= 0) {
                return@withContext createFailureResult("无法读取文件或文件为空: $fileName")
            }
            
            // 验证外部存储空间
            val externalDir = FileUtil.getExternalAppDir()
            val freeSpace = externalDir.parentFile?.freeSpace ?: context.filesDir.freeSpace
            if (fileSize > freeSpace) {
                return@withContext createFailureResult("存储空间不足，需要 ${FileUtil.getFormattedFileSize(fileSize)}，" +
                        "可用 ${FileUtil.getFormattedFileSize(freeSpace)}")
            }
            
            // 复制文件到外部存储
            setProgress(ImportStep.FILE_VALIDATION, fileName, 50)
            val copyResult = FileUtil.copyToExternalStorage(context, uri, "books")
            if (copyResult.isFailure) {
                return@withContext createFailureResult("复制文件失败: ${copyResult.exceptionOrNull()?.message}")
            }
            
            val bookFile = copyResult.getOrNull() ?: 
                return@withContext createFailureResult("复制文件失败")
            
            setProgress(ImportStep.FILE_VALIDATION, fileName, 100)
            
            // 步骤2：元数据提取
            setProgress(ImportStep.METADATA_EXTRACTION, fileName, 0)
            
            // 特殊处理：如果是PDF或Word格式，提取文本内容并创建TXT文件
            var finalBookFile = bookFile
            var finalBookFormat = bookFormat
            var txtFilePath: String? = null
            
            // 处理PDF文件
            if (bookFormat == BookFormat.PDF) {
                Log.d(TAG, "正在处理PDF文件: ${bookFile.name}")
                
                // 提取PDF文本内容
                val pdfText = PdfTextExtractor.extractText(bookFile)
                
                if (pdfText.isBlank()) {
                    Log.w(TAG, "PDF文本提取结果为空")
                } else {
                    // 创建与PDF同名的TXT文件
                    val txtFileName = "${bookFile.nameWithoutExtension}.txt"
                    val txtFile = File(bookFile.parent, txtFileName)
                    
                    try {
                        // 写入文本内容到TXT文件
                        txtFile.writeText(pdfText)
                        Log.d(TAG, "成功从PDF提取文本并保存为TXT: ${txtFile.absolutePath}")
                        
                        // 更新处理文件和格式
                        finalBookFile = txtFile
                        finalBookFormat = BookFormat.TXT
                        txtFilePath = txtFile.absolutePath
                    } catch (e: Exception) {
                        Log.e(TAG, "保存PDF提取文本到TXT文件失败", e)
                        // 如果保存失败，仍然使用原始PDF文件
                    }
                }
            }
            // 处理Word文件 (DOC/DOCX)
            else if (bookFormat == BookFormat.DOC || bookFormat == BookFormat.DOCX) {
                Log.d(TAG, "正在处理Word文件: ${bookFile.name}")
                
                // 提取Word文本内容
                val wordText = WordTextExtractor.extractText(bookFile)
                
                if (wordText.isBlank()) {
                    Log.w(TAG, "Word文本提取结果为空")
                } else {
                    // 创建与Word文件同名的TXT文件
                    val txtFileName = "${bookFile.nameWithoutExtension}.txt"
                    val txtFile = File(bookFile.parent, txtFileName)
                    
                    try {
                        // 写入文本内容到TXT文件
                        txtFile.writeText(wordText)
                        Log.d(TAG, "成功从Word提取文本并保存为TXT: ${txtFile.absolutePath}")
                        
                        // 更新处理文件和格式
                        finalBookFile = txtFile
                        finalBookFormat = BookFormat.TXT
                        txtFilePath = txtFile.absolutePath
                    } catch (e: Exception) {
                        Log.e(TAG, "保存Word提取文本到TXT文件失败", e)
                        // 如果保存失败，仍然使用原始Word文件
                    }
                }
            }
            
            // 计算哈希值
            val fileHash = FileUtil.calculateSHA256(finalBookFile)
            
            // 检查是否重复导入
            val bookDao = AppDatabase.getInstance(context).bookDao()
            val bookRepository = BookRepositoryImpl(context, bookDao)
            val allBooks = withContext(Dispatchers.Default) {
                bookRepository.getAllBooks().first()
            }
            
            // 如果存在相同哈希值的书籍，认为是重复导入
            val duplicateBook = allBooks.find { book -> 
                book.filePath == finalBookFile.absolutePath || (fileHash.isNotEmpty() && book.fileHash == fileHash)
            }
            
            if (duplicateBook != null) {
                // 已存在相同书籍，删除临时文件
                if (txtFilePath != null && txtFilePath != duplicateBook.filePath) {
                    File(txtFilePath).delete()
                }
                return@withContext createFailureResult("该书籍已存在: ${duplicateBook.title}")
            }
            
            setProgress(ImportStep.METADATA_EXTRACTION, fileName, 30)
            
            // 提取元数据
            val metadataExtractor = MetadataExtractor(context)
            val metadataResult = metadataExtractor.extractMetadata(finalBookFile, finalBookFormat)
            
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
                
                // 保存封面图片到外部存储
                val coverResult = FileUtil.saveCoverImageToExternal(context, coverBitmap, coverFileName)
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
                filePath = finalBookFile.absolutePath,
                coverPath = coverPath,
                fileHash = fileHash,
                type = when(finalBookFormat) {
                    BookFormat.EPUB -> BookType.EPUB
                    BookFormat.PDF -> BookType.PDF
                    BookFormat.TXT -> BookType.TXT
                    BookFormat.MD -> BookType.MD
                    BookFormat.DOC, BookFormat.DOCX -> BookType.WORD
                    BookFormat.MOBI, BookFormat.UNKNOWN -> BookType.UNKNOWN
                },
                // 对于从PDF或Word转换来的TXT文件，保存原始文件路径
                originalFilePath = if ((bookFormat == BookFormat.PDF || bookFormat == BookFormat.DOC || bookFormat == BookFormat.DOCX) 
                    && finalBookFormat == BookFormat.TXT) {
                    bookFile.absolutePath
                } else null,
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