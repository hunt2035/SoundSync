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
import com.wanderreads.ebook.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TtsService已创建")
        
        // 创建通知通道
        createNotificationChannel()
        
        // 初始化TTS管理器
        ttsManager = TtsManager.getInstance(applicationContext)
        
        // 启动监听TTS状态的协程
        serviceScope.launch {
            ttsManager.ttsState.collectLatest { state ->
                // 根据TTS状态更新通知
                updateNotification(state)
                
                // 如果TTS停止，考虑停止服务
                if (state.status == TtsManager.STATUS_STOPPED) {
                    stopForeground(true)
                    stopSelf()
                }
            }
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