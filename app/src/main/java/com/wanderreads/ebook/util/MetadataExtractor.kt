package com.wanderreads.ebook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.wanderreads.ebook.domain.model.BookFormat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * 元数据提取器
 */
class MetadataExtractor(private val context: Context) {
    private val TAG = "MetadataExtractor"

    /**
     * 提取电子书元数据
     */
    suspend fun extractMetadata(
        file: File,
        format: BookFormat
    ): Result<BookMetadata> = withContext(Dispatchers.IO) {
        try {
            val metadata = when (format) {
                BookFormat.EPUB -> extractEpubMetadata(file)
                BookFormat.PDF -> extractPdfMetadata(file)
                BookFormat.MOBI -> extractMobiMetadata(file)
                BookFormat.TXT -> extractTxtMetadata(file)
                BookFormat.MD -> extractTxtMetadata(file) // MD格式使用与TXT相同的元数据提取方法
                BookFormat.DOC, BookFormat.DOCX -> extractWordMetadata(file) // Word文件元数据提取
                else -> BookMetadata(
                    title = file.nameWithoutExtension,
                    author = "",
                    pageCount = 0,
                    coverImage = null
                )
            }
            
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "提取元数据失败: ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 提取EPUB元数据
     */
    private fun extractEpubMetadata(file: File): BookMetadata {
        val zipFile = ZipFile(file)
        
        // 读取容器文件
        val containerEntry = zipFile.getEntry("META-INF/container.xml")
        if (containerEntry == null) {
            throw IOException("无效的EPUB文件：缺少container.xml")
        }
        
        val containerContent = String(zipFile.getInputStream(containerEntry).readBytes())
        val rootFilePath = extractRootFilePath(containerContent)
        
        // 读取OPF文件
        val opfEntry = zipFile.getEntry(rootFilePath)
        if (opfEntry == null) {
            throw IOException("无效的EPUB文件：缺少OPF文件")
        }
        
        val opfContent = String(zipFile.getInputStream(opfEntry).readBytes())
        val opfDoc = Jsoup.parse(opfContent)
        
        // 提取元数据
        val title = extractMetadataValue(opfDoc, "title") ?: file.nameWithoutExtension
        val author = extractMetadataValue(opfDoc, "creator") ?: ""
        val language = extractMetadataValue(opfDoc, "language")
        val publisher = extractMetadataValue(opfDoc, "publisher")
        val isbn = extractMetadataValue(opfDoc, "identifier")
        
        // 提取封面图片
        val coverImage = extractCoverImage(opfDoc, zipFile)
        
        // 估算页数
        val pageCount = opfDoc.select("spine itemref").size.coerceAtLeast(1)
        
        return BookMetadata(
            title = title,
            author = author,
            pageCount = pageCount,
            coverImage = coverImage,
            language = language,
            publisher = publisher,
            isbn = isbn
        )
    }
    
    /**
     * 从container.xml中提取根文件路径
     */
    private fun extractRootFilePath(containerContent: String): String {
        val doc = Jsoup.parse(containerContent)
        val rootfile = doc.select("rootfile").first()
        return rootfile?.attr("full-path") ?: throw IOException("无法找到OPF文件路径")
    }
    
    /**
     * 提取元数据值
     */
    private fun extractMetadataValue(doc: Document, name: String): String? {
        return doc.select("metadata $name").first()?.text()
    }
    
    /**
     * 提取封面图片
     */
    private fun extractCoverImage(opfDoc: Document, zipFile: ZipFile): Bitmap? {
        // 查找封面图片ID
        val coverId = opfDoc.select("meta[name=cover]").attr("content")
        if (coverId.isEmpty()) {
            return null
        }
        
        // 查找封面图片的href
        val coverItem = opfDoc.select("manifest item[id=$coverId]").first()
        val coverHref = coverItem?.attr("href") ?: return null
        
        // 读取封面图片数据
        val coverEntry = zipFile.getEntry(coverHref)
        if (coverEntry != null) {
            val data = zipFile.getInputStream(coverEntry).readBytes()
            return BitmapFactory.decodeByteArray(data, 0, data.size)
        }
        
        return null
    }

    /**
     * 提取PDF元数据
     */
    private fun extractPdfMetadata(file: File): BookMetadata {
        // 使用PdfBox提取元数据
        val document = PDDocument.load(file)
        
        val title = document.documentInformation?.title ?: file.nameWithoutExtension
        val author = document.documentInformation?.author ?: ""
        val pageCount = document.numberOfPages
        
        // 提取封面图片 (第一页)
        val coverImage = extractPdfCoverImage(file)
        
        document.close()
        
        return BookMetadata(
            title = title,
            author = author,
            pageCount = pageCount,
            coverImage = coverImage
        )
    }
    
    /**
     * 提取PDF封面图片 (使用Android原生API)
     */
    private fun extractPdfCoverImage(file: File): Bitmap? {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = PdfRenderer(fileDescriptor)
            
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                fileDescriptor.close()
                bitmap
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "提取PDF封面失败", e)
            null
        }
    }

    /**
     * 提取MOBI元数据
     */
    private fun extractMobiMetadata(file: File): BookMetadata {
        // MOBI格式解析复杂，这里使用简化处理
        // 真实应用中可以集成专门的MOBI解析库
        
        return BookMetadata(
            title = file.nameWithoutExtension,
            author = "",
            pageCount = 0,
            coverImage = null
        )
    }

    /**
     * 提取TXT元数据
     */
    private fun extractTxtMetadata(file: File): BookMetadata {
        // 尝试检测编码
        val charset = detectTextEncoding(file)
        
        // 尝试从文件名提取信息
        val fileName = file.nameWithoutExtension
        val parts = fileName.split(" - ", "-", limit = 2)
        
        val title = if (parts.size > 1) parts[1].trim() else fileName
        val author = if (parts.size > 1) parts[0].trim() else ""
        
        // 估算页数 (每页约2000字符)
        val pageCount = (file.length() / 2000).toInt().coerceAtLeast(1)
        
        return BookMetadata(
            title = title,
            author = author,
            pageCount = pageCount,
            coverImage = null,
            charset = charset.name()
        )
    }

    /**
     * 检测文本编码
     */
    private fun detectTextEncoding(file: File): Charset {
        // 简化的编码检测，实际应用中可以使用更复杂的算法
        val buffer = ByteArray(4096)
        
        file.inputStream().use { input ->
            val count = input.read(buffer)
            if (count > 0) {
                // 检查BOM标记
                if (count >= 3 && buffer[0].toInt() == 0xEF && buffer[1].toInt() == 0xBB && buffer[2].toInt() == 0xBF) {
                    return StandardCharsets.UTF_8
                }
                
                // 检查是否包含中文字符
                val utf8Str = String(buffer, 0, count, StandardCharsets.UTF_8)
                val gbkStr = String(buffer, 0, count, Charset.forName("GBK"))
                
                // 简单启发式判断
                // 如果UTF-8字符串包含替换字符，可能是GBK编码
                return if (utf8Str.contains('\uFFFD') && !gbkStr.contains('\uFFFD')) {
                    Charset.forName("GBK")
                } else {
                    StandardCharsets.UTF_8
                }
            }
        }
        
        // 默认UTF-8
        return StandardCharsets.UTF_8
    }

    /**
     * 提取Word文件元数据
     */
    private fun extractWordMetadata(file: File): BookMetadata {
        // 从文件名中提取信息
        val fileName = file.nameWithoutExtension
        val parts = fileName.split(" - ", "-", limit = 2)
        
        val title = if (parts.size > 1) parts[1].trim() else fileName
        val author = if (parts.size > 1) parts[0].trim() else ""
        
        // 估算页数 (每页约2000字符)
        // 尝试提取文本来更准确地估计页数
        val text = try {
            when {
                file.name.endsWith(".docx", ignoreCase = true) -> {
                    com.wanderreads.ebook.util.WordTextExtractor.extractText(file)
                }
                file.name.endsWith(".doc", ignoreCase = true) -> {
                    com.wanderreads.ebook.util.WordTextExtractor.extractText(file)
                }
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取Word文本失败", e)
            ""
        }
        
        val pageCount = if (text.isNotEmpty()) {
            (text.length / 2000).toInt().coerceAtLeast(1)
        } else {
            (file.length() / 8000).toInt().coerceAtLeast(1) // Word二进制文件估算
        }
        
        return BookMetadata(
            title = title,
            author = author,
            pageCount = pageCount,
            coverImage = null
        )
    }
}

/**
 * 电子书元数据类
 */
data class BookMetadata(
    val title: String,
    val author: String,
    val pageCount: Int,
    val coverImage: Bitmap?,
    val publisher: String? = null,
    val language: String? = null,
    val isbn: String? = null,
    val charset: String? = null
) 