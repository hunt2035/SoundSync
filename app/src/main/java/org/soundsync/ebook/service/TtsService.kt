package org.soundsync.ebook.service

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
import org.soundsync.ebook.MainActivity
import org.soundsync.ebook.R
import org.soundsync.ebook.data.repository.BookRepository
import org.soundsync.ebook.domain.model.Book
import org.soundsync.ebook.util.PageDirection
import org.soundsync.ebook.util.TtsManager
import org.soundsync.ebook.util.reader.BookReaderEngine
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
        val ebookApplication = applicationContext as? org.soundsync.ebook.EbookApplication
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
            // 使用withContext(Dispatchers.Default)代替supervisorScope
            // 这样可以避免子协程的取消影响整个操作
            withContext(Dispatchers.Default) {
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
                        
                        // 获取下一页文本
                        engine.goToPage(nextPage)
                        val nextPageText = engine.getCurrentPageText()
                        
                        if (!nextPageText.isNullOrBlank()) {
                            // 获取当前同步状态
                            val syncState = ttsManager.isSyncPageState.value
                            Log.d(TAG, "TTS服务自动翻页前的同步状态: ${syncState}")
                            
                            // 获取MainActivity的全局阅读位置
                            val mainActivity = org.soundsync.ebook.MainActivity.getInstance()
                            val readBookId = org.soundsync.ebook.MainActivity.readBookId
                            
                            // 判断当前查看的书籍是否与正在朗读的书籍相同
                            val isSameBook = readBookId != null && readBookId == bookId
                            Log.d(TAG, "当前查看的书籍与朗读的书籍是否相同: $isSameBook (readBookId=$readBookId, currentBookId=$bookId)")
                            
                            if (syncState == 1 && isSameBook) {
                                // 如果是同步状态(IsSyncPageState=1)且书籍相同，发送广播通知前端UI更新
                                val intent = Intent("org.soundsync.ebook.TTS_PAGE_CHANGED")
                                intent.putExtra("bookId", bookId)
                                intent.putExtra("pageIndex", nextPage)
                                applicationContext.sendBroadcast(intent)
                                Log.d(TAG, "发送UI更新广播: 同步状态=${syncState}, 页面=${nextPage}")
                                
                                // 等待一小段时间，确保UI已更新
                                try {
                                    kotlinx.coroutines.delay(200) // 增加延迟时间，确保UI有足够时间更新
                                } catch (e: Exception) {
                                    Log.w(TAG, "等待UI更新时发生异常: ${e.message}")
                                    // 继续执行，不要因为延迟失败而中断整个流程
                                }
                                
                                // 只有当前界面是阅读界面且查看的书籍与朗读的书籍相同时，才更新全局阅读位置
                                if (isSameBook) {
                                    mainActivity?.updateReadingPosition(bookId, nextPage, engine.getTotalPages())
                                    Log.d(TAG, "更新全局阅读位置: bookId=$bookId, page=$nextPage")
                                } else {
                                    Log.d(TAG, "不更新全局阅读位置，因为当前不在阅读界面或查看的书籍与朗读的书籍不同")
                                }
                                
                                try {
                                    // 开始朗读新页面
                                    ttsManager.startReading(bookId, nextPage, nextPageText)
                                    Log.d(TAG, "同步状态下开始朗读新页面: page=${nextPage}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "开始朗读新页面失败: ${e.message}", e)
                                    // 即使开始朗读失败，也不影响整个翻页流程
                                }
                            } else {
                                // 非同步状态(IsSyncPageState=0)或书籍不同，不需要更新前端UI，直接开始朗读
                                try {
                                    // 只有当前界面是阅读界面且查看的书籍与朗读的书籍相同时，才更新全局阅读位置
                                    if (isSameBook) {
                                        mainActivity?.updateReadingPosition(bookId, nextPage, engine.getTotalPages())
                                        Log.d(TAG, "更新全局阅读位置: bookId=$bookId, page=$nextPage")
                                    } else {
                                        Log.d(TAG, "不更新全局阅读位置，因为当前不在阅读界面或查看的书籍与朗读的书籍不同")
                                    }
                                    
                                    ttsManager.startReading(bookId, nextPage, nextPageText)
                                    Log.d(TAG, "非同步状态或书籍不同，直接朗读新页面: page=${nextPage}, 同步状态=${syncState}, 书籍相同=${isSameBook}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "开始朗读新页面失败: ${e.message}", e)
                                }
                            }
                            
                            // 更新数据库中的lastReadPage字段
                            try {
                                // 获取当前书籍
                                val book = bookRepository?.getBookById(bookId)
                                if (book != null) {
                                    // 更新阅读进度
                                    bookRepository?.updateReadingProgress(
                                        bookId = bookId,
                                        lastReadPage = nextPage,
                                        lastReadPosition = 0f // 由于是自动翻页，设置为页面开始位置
                                    )
                                    Log.d(TAG, "已更新数据库中的lastReadPage: bookId=$bookId, page=$nextPage")
                                } else {
                                    Log.e(TAG, "无法更新lastReadPage，找不到书籍: $bookId")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "更新数据库中的lastReadPage失败: ${e.message}", e)
                            }
                            
                            // 再次检查同步状态
                            val newSyncState = ttsManager.isSyncPageState.value
                            Log.d(TAG, "开始朗读新页面后的同步状态: ${newSyncState}")
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
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.e(TAG, "自动翻页失败(协程被取消): ${e.message}", e)
            // 尝试恢复TTS朗读
            try {
                // 重置pageCompleted状态
                ttsManager.resetPageCompletedFlag()
                
                // 获取下一页
                val nextPage = currentPage + 1
                
                // 获取MainActivity的全局阅读位置
                val mainActivity = org.soundsync.ebook.MainActivity.getInstance()
                val readBookId = org.soundsync.ebook.MainActivity.readBookId
                
                // 判断当前查看的书籍是否与正在朗读的书籍相同
                val isSameBook = readBookId != null && readBookId == bookId
                
                // 只有当前界面是阅读界面且查看的书籍与朗读的书籍相同时，才更新全局阅读位置
                if (isSameBook) {
                    mainActivity?.updateReadingPosition(bookId, nextPage, currentReaderEngine?.getTotalPages() ?: 0)
                    Log.d(TAG, "异常恢复中更新全局阅读位置: bookId=$bookId, page=$nextPage")
                } else {
                    Log.d(TAG, "异常恢复中不更新全局阅读位置，因为当前不在阅读界面或查看的书籍与朗读的书籍不同")
                }
                
                // 只有当书籍相同时，才发送UI更新广播
                if (isSameBook) {
                    val intent = Intent("org.soundsync.ebook.TTS_PAGE_CHANGED")
                    intent.putExtra("bookId", bookId)
                    intent.putExtra("pageIndex", nextPage)
                    applicationContext.sendBroadcast(intent)
                    Log.d(TAG, "在异常恢复中发送UI更新广播: 页面=${nextPage}")
                }
                
                // 更新数据库中的lastReadPage字段
                try {
                    // 获取当前书籍
                    val book = bookRepository?.getBookById(bookId)
                    if (book != null) {
                        Log.d(TAG, "异常恢复前的书籍状态: title=${book.title}, totalPages=${book.totalPages}, lastReadPage=${book.lastReadPage}, progress=${(book.readingProgress * 100).toInt()}%")

                        // 检查totalPages是否合理
                        if (book.totalPages == 0) {
                            Log.w(TAG, "警告: 书籍totalPages为0，TTS更新进度将无效! 需要先修复书籍的totalPages")
                        } else if (nextPage >= book.totalPages) {
                            Log.w(TAG, "警告: TTS页面索引($nextPage) >= 书籍总页数(${book.totalPages})")
                        }

                        // 更新阅读进度
                        bookRepository?.updateReadingProgress(
                            bookId = bookId,
                            lastReadPage = nextPage,
                            lastReadPosition = 0f // 由于是自动翻页，设置为页面开始位置
                        )

                        // 验证更新后的状态
                        val updatedBook = bookRepository?.getBookById(bookId)
                        if (updatedBook != null) {
                            Log.d(TAG, "异常恢复后的书籍状态: lastReadPage=${updatedBook.lastReadPage}, progress=${(updatedBook.readingProgress * 100).toInt()}%")
                        }

                        Log.d(TAG, "异常恢复中已更新数据库中的lastReadPage: bookId=$bookId, page=$nextPage")
                    } else {
                        Log.e(TAG, "异常恢复中无法更新lastReadPage，找不到书籍: $bookId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "异常恢复中更新数据库中的lastReadPage失败: ${e.message}", e)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "尝试恢复TTS朗读失败: ${ex.message}", ex)
                // 不停止TTS，让用户可以手动继续
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
        // 创建返回应用的PendingIntent，并传递当前朗读的书籍ID和页码
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // 添加额外信息，指示这是从TTS通知点击而来，并传递当前朗读的书籍和页面信息
            putExtra("FROM_TTS_NOTIFICATION", true)
            putExtra("TTS_BOOK_ID", ttsManager.bookId)
            putExtra("TTS_PAGE_INDEX", ttsManager.currentPage)
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
        
        // 创建播放/暂停按钮的PendingIntent
        val playPauseIntent = Intent(this, TtsNotificationReceiver::class.java).apply {
            action = if (ttsManager.ttsState.value.status == TtsManager.STATUS_PLAYING) {
                "org.soundsync.ebook.PAUSE_TTS"
            } else {
                "org.soundsync.ebook.RESUME_TTS"
            }
        }
        
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            playPauseIntent,
            pendingIntentFlags
        )
        
        // 创建停止按钮的PendingIntent
        val stopIntent = Intent(this, TtsNotificationReceiver::class.java).apply {
            action = "org.soundsync.ebook.STOP_TTS"
        }
        
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            stopIntent,
            pendingIntentFlags
        )
        
        // 根据当前TTS状态选择播放/暂停图标
        val playPauseIcon = if (ttsManager.ttsState.value.status == TtsManager.STATUS_PLAYING) {
            R.drawable.ic_pause // 需要添加暂停图标资源
        } else {
            R.drawable.ic_play // 需要添加播放图标资源
        }
        
        // 构建带有操作按钮的通知
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 使用应用图标
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            // 添加播放/暂停按钮
            .addAction(
                playPauseIcon,
                if (ttsManager.ttsState.value.status == TtsManager.STATUS_PLAYING) "暂停" else "播放",
                playPausePendingIntent
            )
            // 添加停止按钮
            .addAction(
                R.drawable.ic_stop, // 需要添加停止图标资源
                "停止",
                stopPendingIntent
            )
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