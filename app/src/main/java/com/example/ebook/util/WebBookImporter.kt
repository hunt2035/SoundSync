package com.example.ebook.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 网页导入工具类
 */
object WebBookImporter {
    private const val TAG = "WebBookImporter"
    private const val CONNECT_TIMEOUT = 15000 // 15秒超时
    
    /**
     * 从URL导入网页内容，保存为TXT文件
     *
     * @param context 上下文
     * @param url 网页地址
     * @return 成功返回文件对象，失败返回异常
     */
    suspend fun importFromUrl(
        context: Context,
        url: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 创建webbook目录
            val webBookDir = File(context.filesDir, "webbook").apply {
                if (!exists()) {
                    mkdirs()
                }
            }
            
            // 获取当前时间戳作为文件名
            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
                .format(Date())
            val fileName = "Web$timestamp.txt"
            val outputFile = File(webBookDir, fileName)
            
            // 抓取网页内容
            val document = fetchWebPage(url)
            
            // 解析网页内容为纯文本
            val title = document.title()
            val content = extractTextContent(document)
            
            // 写入文件
            writeToFile(outputFile, title, content)
            
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "网页导入失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 抓取网页内容
     */
    private suspend fun fetchWebPage(url: String): Document = withContext(Dispatchers.IO) {
        Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .timeout(CONNECT_TIMEOUT)
            .get()
    }
    
    /**
     * 从文档中提取文本内容
     */
    private fun extractTextContent(document: Document): String {
        // 移除不需要的元素
        document.select("script, style, iframe, nav, footer, header, aside, .ad, .advertisement, .banner, .cookie-notice").remove()
        
        // 提取正文内容
        val contentElements = document.select("article, .content, .post, .entry, .article, main, #content, .post-content, .entry-content")
        
        // 如果找到内容元素，使用它们，否则使用body
        val textContent = if (contentElements.isNotEmpty()) {
            contentElements.text()
        } else {
            document.body().text()
        }
        
        return textContent
    }
    
    /**
     * 写入内容到文件
     */
    private suspend fun writeToFile(
        file: File,
        title: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
            writer.write(title)
            writer.write("\n\n")
            writer.write(content)
        }
    }
} 