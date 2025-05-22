package com.wanderreads.ebook.util

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * 网页导入工具类
 */
object WebBookImporter {
    private const val TAG = "WebBookImporter"
    private const val CONNECT_TIMEOUT = 30000 // 增加到30秒超时
    private const val MAX_RETRIES = 3 // 最大重试次数
    private const val INITIAL_BACKOFF_DELAY = 1000L // 初始重试等待时间（毫秒）
    private const val ROOT_DIR = "WanderReads"
    
    /**
     * 从URL导入网页内容，保存为TXT文件
     *
     * @param context 上下文
     * @param url 网页地址
     * @return 成功返回文件对象和URL，失败返回异常
     */
    suspend fun importFromUrl(
        context: Context,
        url: String
    ): Result<Pair<File, String>> = withContext(Dispatchers.IO) {
        try {
            // 优先尝试保存到外部存储
            val canUseExternalStorage = FileUtil.isExternalStorageWritable()
            Log.d(TAG, "外部存储是否可写: $canUseExternalStorage")
            
            var outputFile: File? = null

            if (canUseExternalStorage) {
                // 尝试使用外部存储
                try {
                    // 创建外部存储中的webbook目录
                    val externalDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    val appRootDir = File(externalDocumentsDir, ROOT_DIR)
                    val webBookDir = File(appRootDir, "webbook").apply {
                        if (!exists()) {
                            if (!mkdirs()) {
                                Log.w(TAG, "无法创建外部目录: $absolutePath，将尝试使用应用私有目录")
                                throw IOException("无法创建目录: $absolutePath")
                            }
                        }
                    }
                    
                    // 获取当前时间戳作为文件名
                    val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
                        .format(Date())
                    val fileName = "Web$timestamp.txt"
                    outputFile = File(webBookDir, fileName)
                } catch (e: Exception) {
                    Log.w(TAG, "使用外部存储失败: ${e.message}，将使用应用私有目录")
                    // 如果外部存储失败，回退到应用私有目录
                    outputFile = null
                }
            }
            
            // 如果外部存储不可用或创建失败，使用应用私有目录
            if (outputFile == null) {
                val privateDir = File(context.filesDir, "webbook").apply {
                    if (!exists()) {
                        if (!mkdirs()) {
                            return@withContext Result.failure(IOException("无法创建应用私有目录"))
                        }
                    }
                }
                val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
                    .format(Date())
                val fileName = "Web$timestamp.txt"
                outputFile = File(privateDir, fileName)
                Log.d(TAG, "将使用应用私有目录保存文件: ${outputFile.absolutePath}")
            }
            
            // 抓取网页内容（带重试机制）
            Log.d(TAG, "开始获取网页内容: $url")
            val document = fetchWebPageWithRetry(url)
            
            // 解析网页内容为纯文本
            Log.d(TAG, "网页获取成功，开始提取内容")
            val title = document.title()
            val content = extractTextContent(document)
            
            // 写入文件
            Log.d(TAG, "写入文件: ${outputFile.absolutePath}")
            writeToFile(outputFile, title, content)
            
            Log.d(TAG, "网页导入成功，文件路径: ${outputFile.absolutePath}")
            Result.success(Pair(outputFile, url))
        } catch (e: Exception) {
            Log.e(TAG, "网页导入失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 抓取网页内容（带重试机制）
     */
    private suspend fun fetchWebPageWithRetry(url: String): Document = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        var currentDelay = INITIAL_BACKOFF_DELAY
        
        // 尝试多次请求
        for (attempt in 1..MAX_RETRIES) {
            try {
                Log.d(TAG, "尝试获取网页，第 $attempt 次尝试: $url")
                // 尝试获取网页
                return@withContext Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                    .timeout(CONNECT_TIMEOUT)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                    .ignoreContentType(false)
                    .maxBodySize(0) // 不限制内容大小
                    .get()
            } catch (e: SocketTimeoutException) {
                // 连接超时，记录并准备重试
                Log.w(TAG, "网页获取超时，第${attempt}次尝试，将在${currentDelay}ms后重试", e)
                lastException = e
                
                // 等待后重试
                delay(currentDelay)
                
                // 指数退避策略，每次失败后等待时间加长，最多不超过30秒
                currentDelay = min(currentDelay * 2, 30000L)
            } catch (e: IOException) {
                // 其他网络错误，记录并准备重试
                Log.w(TAG, "网页获取失败，第${attempt}次尝试，将在${currentDelay}ms后重试", e)
                lastException = e
                
                // 等待后重试
                delay(currentDelay)
                
                // 指数退避策略
                currentDelay = min(currentDelay * 2, 30000L)
            }
        }
        
        // 所有重试都失败，抛出异常
        throw lastException ?: IOException("网页获取失败，已尝试${MAX_RETRIES}次")
    }
    
    /**
     * 从文档中提取文本内容，保留格式
     */
    private fun extractTextContent(document: Document): String {
        // 移除不需要的元素
        document.select("script, style, iframe, nav, footer, header, aside, .ad, .advertisement, .banner, .cookie-notice").remove()
        
        // 提取正文内容
        val contentElements = document.select("article, .content, .post, .entry, .article, main, #content, .post-content, .entry-content")
        
        // 如果找到内容元素，使用它们，否则使用body
        val bodyElement = if (contentElements.isNotEmpty()) {
            contentElements.first()
        } else {
            document.body()
        }
        
        // 处理段落和换行
        processElement(bodyElement)
        
        // 获取处理后的HTML文本内容
        val htmlContent = bodyElement.html()
        
        // 将所有HTML标签转换为普通文本
        val plainText = Jsoup.parse(htmlContent).text()
            .replace(" . ", ".\n")  // 处理句号后的换行
            .replace(" ? ", "?\n")  // 处理问号后的换行
            .replace(" ! ", "!\n")  // 处理感叹号后的换行
            .replace("\\s{2,}".toRegex(), " ")  // 移除多余空格
        
        // 分割成行
        val lines = plainText.split("\n").toMutableList()
        
        // 移除开头的空行
        while (lines.isNotEmpty() && lines.first().trim().isEmpty()) {
            lines.removeAt(0)
        }
        
        // 构建格式化文本
        val sb = StringBuilder()
        
        // 确保至少有一行
        if (lines.isEmpty()) {
            lines.add("未命名文档")
        }
        
        // 第一行作为标题
        val title = lines.removeAt(0).trim()
        sb.append(title).append("\n\n")
        
        // 其余行前面空出2个字符，表示段落开始
        for (line in lines) {
            if (line.trim().isNotEmpty()) {
                sb.append("  ").append(line.trim()).append("\n\n")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 处理元素，将HTML元素转换为文本格式
     */
    private fun processElement(element: Element) {
        // 详细处理段落和换行标签
        element.select("br").before("\n")
        element.select("p").before("\n").after("\n")
        element.select("div").before("\n")
        element.select("h1, h2, h3, h4, h5, h6").before("\n").after("\n")
        element.select("li").before("\n• ")
        
        // 保留一些格式
        element.select("b, strong").prepend("**").append("**")
        element.select("i, em").prepend("*").append("*")
    }
    
    /**
     * 写入内容到文件
     */
    private suspend fun writeToFile(
        file: File,
        title: String,
        content: String
    ) = withContext(Dispatchers.IO) {
        try {
            OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
                writer.write(content)
                writer.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入文件失败: ${file.absolutePath}", e)
            throw e
        }
    }
} 