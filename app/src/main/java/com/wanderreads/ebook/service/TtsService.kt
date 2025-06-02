package com.wanderreads.ebook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wanderreads.ebook.MainActivity
import com.wanderreads.ebook.R
import com.wanderreads.ebook.data.repository.BookRepository
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.util.PageDirection
import com.wanderreads.ebook.util.TtsManager
import com.wanderreads.ebook.util.reader.BookReaderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TTS朗读服务
 * 在后台运行TTS，保持全局状态，使翻页或切换应用时朗读不受影响
 */
class TtsService : Service() {
    
    companion object {
        private const val TAG = "TtsService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "tts_service_channel"
        private const val CHANNEL_NAME = "TTS朗读服务"
    }
    
    // 服务绑定器
    inner class LocalBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }
    
    private val binder = LocalBinder()
    
    // TTS管理器
    private lateinit var ttsManager: TtsManager
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 当前朗读信息
    private var currentBookTitle: String = ""
    
    // 阅读引擎相关
    private var bookRepository: BookRepository? = null
    private var currentReaderEngine: BookReaderEngine? = null
    private var currentBook: Book? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TtsService已创建")
        
        // 创建通知通道
        createNotificationChannel()
        
        // 初始化TTS管理器
        ttsManager = TtsManager.getInstance(applicationContext)
        
        // 获取应用依赖
        val ebookApplication = applicationContext as? com.wanderreads.ebook.EbookApplication
        ebookApplication?.let {
            val dependencies = it.provideDependencies()
            bookRepository = dependencies.bookRepository
        }
        
        // 启动监听TTS状态的协程
        serviceScope.launch {
            ttsManager.ttsState.collectLatest { state ->
                // 根据TTS状态更新通知
                updateNotification(state)
                
                // 处理页面朗读完成事件
                if (state.status == TtsManager.STATUS_PLAYING && state.pageCompleted) {
                    handlePageCompleted(state.bookId, state.currentPage)
                }
                
                // 如果TTS停止，考虑停止服务
                if (state.status == TtsManager.STATUS_STOPPED) {
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
    }
    
    /**
     * 处理页面朗读完成事件
     * 自动翻页并继续朗读
     */
    private suspend fun handlePageCompleted(bookId: String?, currentPage: Int) {
        if (bookId == null) return
        
        try {
            // 确保有阅读引擎
            ensureReaderEngine(bookId)
            
            currentReaderEngine?.let { engine ->
                // 检查是否有下一页
                if (currentPage < engine.getTotalPages() - 1) {
                    // 有下一页，自动翻到下一页
                    val nextPage = currentPage + 1
                    Log.d(TAG, "TTS服务自动翻页: 从${currentPage}翻到${nextPage}")
                    
                    // 重置pageCompleted状态
                    ttsManager.resetPageCompletedFlag()
                    
                    // 获取下一页文本并朗读
                    engine.goToPage(nextPage)
                    val nextPageText = engine.getCurrentPageText()
                    
                    if (!nextPageText.isNullOrBlank()) {
                        ttsManager.startReading(bookId, nextPage, nextPageText)
                        
                        // 获取当前同步状态
                        val syncState = ttsManager.isSyncPageState.value
                        Log.d(TAG, "TTS服务自动翻页后开始朗读: page=${nextPage}, 同步状态=${syncState}")
                        
                        // 如果是同步状态(IsSyncPageState=1)，则需要通知前端UI更新
                        if (syncState == 1) {
                            // 发送广播通知前端UI需要更新
                            val intent = Intent("com.wanderreads.ebook.TTS_PAGE_CHANGED")
                            intent.putExtra("bookId", bookId)
                            intent.putExtra("pageIndex", nextPage)
                            applicationContext.sendBroadcast(intent)
                            Log.d(TAG, "发送UI更新广播: 同步状态=${syncState}, 页面=${nextPage}")
                        } else {
                            // 非同步状态(IsSyncPageState=0)，不需要更新前端UI
                            Log.d(TAG, "后台自动翻页，不更新UI: 同步状态=${syncState}, 页面=${nextPage}")
                        }
                    } else {
                        // 下一页文本为空，停止朗读
                        ttsManager.stopReading()
                        Log.d(TAG, "下一页文本为空，停止朗读")
                    }
                } else {
                    // 已到最后一页，停止朗读
                    ttsManager.stopReading()
                    Log.d(TAG, "TTS已到最后一页，停止朗读")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动翻页失败: ${e.message}", e)
            ttsManager.stopReading()
        }
    }
    
    /**
     * 确保有可用的阅读引擎
     */
    private suspend fun ensureReaderEngine(bookId: String) {
        // 如果已有引擎且是同一本书，直接返回
        if (currentReaderEngine != null && currentBook?.id == bookId) {
            return
        }
        
        // 清理旧引擎
        currentReaderEngine?.close()
        currentReaderEngine = null
        
        // 获取书籍信息
        val book = withContext(Dispatchers.IO) {
            bookRepository?.getBookById(bookId)
        }
        
        if (book != null) {
            currentBook = book
            
            // 创建新引擎
            val engine = BookReaderEngine.create(applicationContext, book.type)
            engine.initialize(book, book.lastReadPage)
            engine.loadContent()
            
            currentReaderEngine = engine
            Log.d(TAG, "为书籍创建阅读引擎: ${book.title}")
        } else {
            Log.e(TAG, "找不到书籍: $bookId")
            throw IllegalStateException("找不到书籍: $bookId")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 获取书籍标题（如果有）
        intent?.getStringExtra("bookTitle")?.let {
            currentBookTitle = it
        }
        
        // 创建初始通知
        val notification = createNotification(
            title = "正在朗读",
            content = if (currentBookTitle.isNotEmpty()) "正在朗读：$currentBookTitle" else "正在朗读中..."
        )
        
        // 检查Android 13+的通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(
                this, 
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionState == PackageManager.PERMISSION_GRANTED) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            // 低于Android 13的版本不需要POST_NOTIFICATIONS权限
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        Log.d(TAG, "TtsService即将销毁")
        
        // 清理资源
        currentReaderEngine?.close()
        currentReaderEngine = null
        
        serviceScope.cancel()
        super.onDestroy()
    }
    
    /**
     * 创建通知通道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于在后台朗读电子书时显示通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(title: String, content: String): Notification {
        // 创建返回应用的PendingIntent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 使用应用图标
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 根据TTS状态更新通知
     */
    private fun updateNotification(state: TtsManager.TtsState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        when (state.status) {
            TtsManager.STATUS_PLAYING -> {
                val notification = createNotification(
                    title = "正在朗读",
                    content = if (currentBookTitle.isNotEmpty()) "正在朗读：$currentBookTitle" else "正在朗读中..."
                )
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            TtsManager.STATUS_PAUSED -> {
                val notification = createNotification(
                    title = "朗读已暂停",
                    content = if (currentBookTitle.isNotEmpty()) "已暂停朗读：$currentBookTitle" else "朗读已暂停"
                )
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            else -> {
                // 不更新通知
            }
        }
    }
} 