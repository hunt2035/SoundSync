package com.wanderreads.ebook.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wanderreads.ebook.MainActivity
import com.wanderreads.ebook.R
import com.wanderreads.ebook.domain.model.Record
import com.wanderreads.ebook.domain.model.SynthesisParams
import com.wanderreads.ebook.domain.model.SynthesisRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * TTS语音合成服务
 * 负责将文本转换为语音文件，并保存到Documents/WanderReads/voices目录下
 */
class TtsSynthesisService : Service() {
    
    companion object {
        private const val TAG = "TtsSynthesisService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tts_synthesis_channel"
        private const val CHANNEL_NAME = "语音合成"
        
        // 状态常量
        const val STATUS_IDLE = 0
        const val STATUS_PREPARING = 1
        const val STATUS_SYNTHESIZING = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_ERROR = 4
        const val STATUS_CANCELED = 5
    }
    
    // TTS引擎
    private var tts: TextToSpeech? = null
    
    // 合成任务队列
    private val synthesisQueue = ConcurrentLinkedQueue<SynthesisTask>()
    
    // 当前状态
    private val _synthesisState = MutableStateFlow(SynthesisState())
    val synthesisState: StateFlow<SynthesisState> = _synthesisState.asStateFlow()
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 本地绑定器
    private val binder = LocalBinder()
    
    // 语音文件输出流
    private var outputStream: OutputStream? = null
    
    // 合成回调接口
    private var synthesisCallback: SynthesisCallback? = null
    
    // 是否正在处理任务
    private var isProcessing = false
    
    // 当前正在合成的任务
    private var currentTask: SynthesisTask? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initTts()
        Log.d(TAG, "TtsSynthesisService已创建")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("")
        
        // 检查Android 13+的通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            if (permissionState == PackageManager.PERMISSION_GRANTED) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            // 低于Android 13的版本不需要POST_NOTIFICATIONS权限
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        shutdownTts()
        closeOutputStream()
        super.onDestroy()
        Log.d(TAG, "TtsSynthesisService已销毁")
    }
    
    /**
     * 初始化TTS引擎
     */
    private fun initTts() {
        if (tts == null) {
            try {
                tts = TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.CHINESE
                        _synthesisState.value = _synthesisState.value.copy(
                            status = STATUS_IDLE,
                            message = "TTS引擎已准备就绪"
                        )
                        Log.d(TAG, "TTS引擎初始化成功")
                        
                        // 如果队列中有任务，开始处理
                        if (synthesisQueue.isNotEmpty() && !isProcessing) {
                            processQueue()
                        }
                    } else {
                        _synthesisState.value = _synthesisState.value.copy(
                            status = STATUS_ERROR,
                            message = "TTS引擎初始化失败，错误码: $status"
                        )
                        // 主动通知UI
                        synthesisCallback?.onError("TTS引擎初始化失败，错误码: $status")
                        Log.e(TAG, "TTS引擎初始化失败，错误码: $status")
                    }
                }
                
                // 设置超时保护
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_synthesisState.value.status == STATUS_PREPARING) {
                        _synthesisState.value = _synthesisState.value.copy(
                            status = STATUS_ERROR,
                            message = "TTS初始化超时"
                        )
                        synthesisCallback?.onError("TTS初始化超时")
                    }
                }, 10000) // 10秒超时
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        Log.d(TAG, "开始合成: $utteranceId")
                        _synthesisState.value = _synthesisState.value.copy(
                            status = STATUS_SYNTHESIZING,
                            message = "正在合成语音...",
                            progress = 0
                        )
                        updateNotification("正在合成语音...（0%）")
                    }
                    
                    override fun onDone(utteranceId: String) {
                        Log.d(TAG, "合成完成: $utteranceId")
                        closeOutputStream()
                        
                        // 保存到媒体库
                        currentTask?.let { task ->
                            scanFile(task.outputPath)
                            
                            // 保存到数据库
                            saveRecordToDatabase(task)
                            
                            // 更新状态
                            _synthesisState.value = _synthesisState.value.copy(
                                status = STATUS_COMPLETED,
                                message = "语音合成完成，文件已保存",
                                progress = 100,
                                outputPath = task.outputPath
                            )
                            
                            // 更新通知
                            updateNotification("语音合成完成（100%）")
                            
                            // 回调通知
                            synthesisCallback?.onCompleted(task.outputPath)
                        }
                        
                        // 继续处理队列中的任务
                        isProcessing = false
                        if (synthesisQueue.isNotEmpty()) {
                            processQueue()
                        } else {
                            stopForeground(false)
                        }
                    }
                    
                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "合成出错: $utteranceId")
                        closeOutputStream()
                        
                        _synthesisState.value = _synthesisState.value.copy(
                            status = STATUS_ERROR,
                            message = "语音合成过程中出错",
                            progress = 0
                        )
                        
                        updateNotification("语音合成失败")
                        
                        // 回调通知
                        synthesisCallback?.onError("语音合成失败")
                        
                        // 继续处理队列中的任务
                        isProcessing = false
                        if (synthesisQueue.isNotEmpty()) {
                            processQueue()
                        } else {
                            stopForeground(false)
                        }
                    }
                    
                    // API 23以下的错误回调
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String, errorCode: Int) {
                        onError(utteranceId)
                    }
                    
                    // API 21+的进度回调
                    override fun onRangeStart(
                        utteranceId: String,
                        start: Int,
                        end: Int,
                        frame: Int
                    ) {
                        // 计算进度百分比
                        currentTask?.let { task ->
                            val totalLength = task.text.length
                            val progress = if (totalLength > 0) {
                                (end.toFloat() / totalLength.toFloat() * 100).toInt()
                            } else 0
                            
                            // 更新状态
                            _synthesisState.value = _synthesisState.value.copy(
                                progress = progress,
                                message = "正在合成语音（${progress}%）"
                            )
                            
                            // 每10%更新一次通知
                            if (progress % 10 == 0) {
                                updateNotification("正在合成语音...（${progress}%）")
                            }
                            
                            // 回调通知
                            synthesisCallback?.onProgress(progress)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "TTS初始化异常", e)
                _synthesisState.value = _synthesisState.value.copy(
                    status = STATUS_ERROR,
                    message = "TTS初始化异常: ${e.message}"
                )
                synthesisCallback?.onError("TTS初始化异常: ${e.message}")
            }
        }
    }
    
    /**
     * 处理合成队列
     */
    private fun processQueue() {
        if (isProcessing || synthesisQueue.isEmpty()) return
        
        if (tts == null) {
            Log.e(TAG, "TTS引擎未初始化")
            _synthesisState.value = _synthesisState.value.copy(
                status = STATUS_ERROR,
                message = "TTS引擎未初始化"
            )
            synthesisCallback?.onError("TTS引擎未初始化")
            return
        }
        
        isProcessing = true
        
        try {
            val task = synthesisQueue.poll()
            currentTask = task
            
            task?.let {
                // 更新状态
                _synthesisState.value = _synthesisState.value.copy(
                    status = STATUS_PREPARING,
                    message = "正在准备合成语音...",
                    progress = 0,
                    bookId = it.bookId,
                    title = it.title
                )
                
                updateNotification("正在准备合成语音...")
                
                // 应用TTS参数
                tts?.setSpeechRate(it.params.speechRate)
                tts?.setPitch(it.params.pitch)
                
                // 准备输出文件
                try {
                    val outputDir = getVoicesDirectory()
                    val fileName = "${System.currentTimeMillis()}_${it.title.replace(' ', '_')}.mp3"
                    val outputFile = File(outputDir, fileName)
                    it.outputPath = outputFile.absolutePath
                    
                    if (!outputFile.exists()) {
                        outputFile.createNewFile()
                    }
                    
                    outputStream = FileOutputStream(outputFile)
                    
                    // 使用API 21+的文件合成方法
                    val params = Bundle()
                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, it.params.volume)
                    
                    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // 使用最新的API合成到文件
                        @Suppress("DEPRECATION")
                        tts?.synthesizeToFile(it.text, params, outputFile, "TTS_${UUID.randomUUID()}")
                    } else {
                        @Suppress("DEPRECATION")
                        tts?.synthesizeToFile(it.text, null, outputFile, "TTS_${UUID.randomUUID()}")
                    }
                    
                    if (result != TextToSpeech.SUCCESS) {
                        _synthesisState.value = _synthesisState.value.copy(
                            status = STATUS_ERROR,
                            message = "无法开始语音合成，错误码: $result"
                        )
                        synthesisCallback?.onError("无法开始语音合成，错误码: $result")
                        isProcessing = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "合成语音文件失败", e)
                    _synthesisState.value = _synthesisState.value.copy(
                        status = STATUS_ERROR,
                        message = "创建语音文件失败: ${e.message}"
                    )
                    synthesisCallback?.onError("创建语音文件失败: ${e.message}")
                    isProcessing = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理合成队列异常", e)
            isProcessing = false
            _synthesisState.value = _synthesisState.value.copy(
                status = STATUS_ERROR,
                message = "处理合成队列异常: ${e.message}"
            )
            synthesisCallback?.onError("处理合成队列异常: ${e.message}")
        }
    }
    
    /**
     * 添加合成任务
     */
    fun addSynthesisTask(
        text: String,
        params: SynthesisParams,
        bookId: String,
        title: String,
        callback: SynthesisCallback? = null
    ) {
        this.synthesisCallback = callback
        
        val task = SynthesisTask(
            text = text,
            params = params,
            bookId = bookId,
            title = title,
            outputPath = ""
        )
        
        synthesisQueue.add(task)
        
        // 开始处理任务
        if (!isProcessing) {
            processQueue()
        }
    }
    
    /**
     * 取消当前任务
     */
    fun cancelCurrentTask() {
        if (isProcessing) {
            tts?.stop()
            closeOutputStream()
            
            // 删除未完成的文件
            currentTask?.let { task ->
                if (task.outputPath.isNotEmpty()) {
                    val file = File(task.outputPath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
            
            // 更新状态
            _synthesisState.value = _synthesisState.value.copy(
                status = STATUS_CANCELED,
                message = "语音合成已取消",
                progress = 0
            )
            
            updateNotification("语音合成已取消")
            
            // 回调
            synthesisCallback?.onCanceled()
            
            // 重置处理状态
            isProcessing = false
            currentTask = null
            
            // 继续处理队列中的任务
            if (synthesisQueue.isNotEmpty()) {
                processQueue()
            }
        }
    }
    
    /**
     * 关闭输出流
     */
    private fun closeOutputStream() {
        try {
            outputStream?.flush()
            outputStream?.close()
            outputStream = null
        } catch (e: Exception) {
            Log.e(TAG, "关闭输出流失败", e)
        }
    }
    
    /**
     * 关闭TTS引擎
     */
    private fun shutdownTts() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于显示语音合成进度的通知"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(message: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音合成")
            .setContentText(message)
            .setSmallIcon(R.drawable.app_icon)
            .setContentIntent(pendingIntent)
            .setProgress(100, _synthesisState.value.progress, false)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 检查Android 13+的通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            if (permissionState == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NOTIFICATION_ID, createNotification(message))
            }
        } else {
            // 低于Android 13的版本不需要POST_NOTIFICATIONS权限
            notificationManager.notify(NOTIFICATION_ID, createNotification(message))
        }
    }
    
    /**
     * 获取语音文件存储目录
     */
    private fun getVoicesDirectory(): File {
        // 使用外部存储的Documents目录
        val externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val appDir = File(externalDir, "WanderReads")
        val voicesDir = File(appDir, "voices")
        
        if (!voicesDir.exists()) {
            voicesDir.mkdirs()
        }
        
        return voicesDir
    }
    
    /**
     * 扫描文件添加到媒体库
     */
    private fun scanFile(filePath: String) {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(filePath),
            arrayOf("audio/mpeg"),
            null
        )
    }
    
    /**
     * 保存记录到数据库
     */
    private fun saveRecordToDatabase(task: SynthesisTask) {
        serviceScope.launch {
            try {
                // 创建Record对象
                val record = Record(
                    id = UUID.randomUUID().toString(),
                    bookId = task.bookId,
                    title = task.title,
                    addedDate = System.currentTimeMillis(),
                    voiceLength = 0, // 暂时无法获取确切时长
                    voiceFilePath = task.outputPath,
                    isSynthesized = true,
                    synthParams = task.params
                )
                
                // 通过回调保存到数据库
                synthesisCallback?.onSaveRecord(record)
                
                Log.d(TAG, "合成记录已保存: ${task.title}")
            } catch (e: Exception) {
                Log.e(TAG, "保存合成记录失败", e)
            }
        }
    }
    
    /**
     * 本地绑定器
     */
    inner class LocalBinder : Binder() {
        fun getService(): TtsSynthesisService = this@TtsSynthesisService
    }
    
    /**
     * 合成任务数据类
     */
    data class SynthesisTask(
        val text: String,
        val params: SynthesisParams,
        val bookId: String,
        val title: String,
        var outputPath: String
    )
    
    /**
     * 合成状态数据类
     */
    data class SynthesisState(
        val status: Int = STATUS_IDLE,
        val message: String = "",
        val progress: Int = 0,
        val bookId: String = "",
        val title: String = "",
        val outputPath: String = ""
    )
    
    /**
     * 合成回调接口
     */
    interface SynthesisCallback {
        fun onProgress(progress: Int)
        fun onCompleted(outputPath: String)
        fun onError(message: String)
        fun onCanceled()
        fun onSaveRecord(record: Record)
    }
} 