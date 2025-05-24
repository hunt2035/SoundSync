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
        
        // 文本分块大小（字符数）
        private const val TEXT_CHUNK_SIZE = 3000
    }
    
    /**
     * 平滑进度跟踪器
     * 用于提供流畅的进度显示体验
     */
    private inner class SmoothProgressTracker {
        private var lastReportedProgress = 0
        private var estimatedProgress = 0
        private var startTime = 0L
        private var isActive = false
        private val handler = Handler(Looper.getMainLooper())
        private var totalLength = 1 // 防止除零错误
        
        // 进度更新任务
        private val updateTask = object : Runnable {
            override fun run() {
                if (!isActive) return
                
                val elapsedTime = System.currentTimeMillis() - startTime
                // 基于已经过去的时间和文本总长度估算进度
                val timeBasedProgress = calculateTimeBasedProgress(elapsedTime)
                
                // 取估算进度和实际报告进度的较大值，避免进度后退
                estimatedProgress = maxOf(timeBasedProgress, lastReportedProgress)
                
                // 限制最大进度为99%，留出空间给实际完成事件
                if (estimatedProgress > 99) estimatedProgress = 99
                
                // 更新状态
                updateProgress(estimatedProgress)
                
                // 每200ms更新一次
                handler.postDelayed(this, 200)
            }
        }
        
        /**
         * 开始跟踪进度
         */
        fun start(textLength: Int) {
            totalLength = textLength.coerceAtLeast(1)
            lastReportedProgress = 0
            estimatedProgress = 0
            startTime = System.currentTimeMillis()
            isActive = true
            handler.post(updateTask)
        }
        
        /**
         * 更新实际进度
         */
        fun updateActualProgress(current: Int, total: Int) {
            if (total > 0) {
                lastReportedProgress = (current.toFloat() / total.toFloat() * 100).toInt()
                // 更新总长度，使后续估算更准确
                totalLength = total
            }
        }
        
        /**
         * 停止跟踪
         */
        fun stop(isCompleted: Boolean = false) {
            isActive = false
            handler.removeCallbacks(updateTask)
            if (isCompleted) {
                // 如果是完成状态，设置为100%
                updateProgress(100)
            }
        }
        
        /**
         * 基于时间计算估计进度
         */
        private fun calculateTimeBasedProgress(elapsedTime: Long): Int {
            // 估算每字符处理时间（毫秒）
            val estimatedTimePerChar = 50L // 可根据实际情况调整
            val estimatedTotalTime = totalLength * estimatedTimePerChar
            
            // 计算基于时间的进度百分比
            return ((elapsedTime.toFloat() / estimatedTotalTime.toFloat()) * 100)
                .coerceIn(0f, 99f).toInt()
        }
        
        /**
         * 更新进度显示
         */
        private fun updateProgress(progress: Int) {
            // 更新状态
            _synthesisState.value = _synthesisState.value.copy(
                progress = progress,
                message = "正在合成语音（${progress}%）"
            )
            
            // 每5%更新一次通知
            if (progress % 5 == 0) {
                updateNotification("正在合成语音...（${progress}%）")
            }
            
            // 回调通知
            synthesisCallback?.onProgress(progress)
        }
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
    
    // 进度跟踪器
    private val progressTracker = SmoothProgressTracker()
    
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
                        
                        // 启动进度跟踪器
                        currentTask?.let { task ->
                            progressTracker.start(task.text.length)
                        }
                    }
                    
                    override fun onDone(utteranceId: String) {
                        Log.d(TAG, "合成完成: $utteranceId")
                        closeOutputStream()
                        
                        // 停止进度跟踪器，并标记为已完成
                        progressTracker.stop(isCompleted = true)
                        
                        // 保存到媒体库
                        currentTask?.let { task ->
                            scanFile(task.outputPath)
                            
                            // 保存到数据库
                            saveRecordToDatabase(task)
                            
                            // 格式化文件路径，使其更易读
                            val formattedPath = task.outputPath.replace("/storage/emulated/0/", "内部存储/")
                            
                            // 更新状态
                            _synthesisState.value = _synthesisState.value.copy(
                                status = STATUS_COMPLETED,
                                message = "语音合成完成\n文件已保存至：\n$formattedPath",
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
                        
                        // 停止进度跟踪器
                        progressTracker.stop()
                        
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
                        // 使用进度跟踪器更新实际进度
                        currentTask?.let { task ->
                            progressTracker.updateActualProgress(end, task.text.length)
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
                    // 限制标题长度，避免文件名过长
                    val safeTitle = it.title
                        .replace(' ', '_')
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_") // 移除不合法的文件名字符
                        .let { title -> 
                            if (title.length > 30) title.substring(0, 30) else title 
                        }
                    val fileName = "${System.currentTimeMillis()}_$safeTitle.mp3"
                    val outputFile = File(outputDir, fileName)
                    it.outputPath = outputFile.absolutePath
                    
                    if (!outputFile.exists()) {
                        outputFile.createNewFile()
                    }
                    
                    outputStream = FileOutputStream(outputFile)
                    
                    // 检查文本长度，如果过长则分块处理
                    val text = it.text
                    if (text.length > TEXT_CHUNK_SIZE) {
                        // 文本过长，分块处理
                        Log.d(TAG, "文本长度(${text.length})超过阈值，分块处理")
                        processLargeText(text, it, outputFile)
                    } else {
                        // 文本长度适中，直接处理
                        processSingleText(text, it, outputFile)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "合成语音文件失败", e)
                    
                    // 提供更友好的错误信息
                    val errorMessage = when {
                        e.message?.contains("File name too long") == true -> 
                            "文件名过长，请使用较短的书名或章节名"
                        e.message?.contains("Permission denied") == true -> 
                            "没有存储权限，请在设置中授予应用存储权限"
                        e.message?.contains("No space left") == true || 
                        e.message?.contains("not enough space") == true -> 
                            "存储空间不足，请清理设备空间后重试"
                        else -> "创建语音文件失败: ${e.message}"
                    }
                    
                    _synthesisState.value = _synthesisState.value.copy(
                        status = STATUS_ERROR,
                        message = errorMessage
                    )
                    synthesisCallback?.onError(errorMessage)
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
     * 处理单个文本块
     */
    private fun processSingleText(text: String, task: SynthesisTask, outputFile: File) {
        // 使用API 21+的文件合成方法
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, task.params.volume)
        
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 使用最新的API合成到文件
            @Suppress("DEPRECATION")
            tts?.synthesizeToFile(text, params, outputFile, "TTS_${UUID.randomUUID()}")
        } else {
            @Suppress("DEPRECATION")
            tts?.synthesizeToFile(text, null, outputFile, "TTS_${UUID.randomUUID()}")
        }
        
        if (result != TextToSpeech.SUCCESS) {
            _synthesisState.value = _synthesisState.value.copy(
                status = STATUS_ERROR,
                message = "无法开始语音合成，错误码: $result"
            )
            synthesisCallback?.onError("无法开始语音合成，错误码: $result")
            isProcessing = false
        }
    }
    
    /**
     * 处理大文本（分块处理）
     */
    private fun processLargeText(text: String, task: SynthesisTask, outputFile: File) {
        try {
            // 将文本分成自然段落
            val paragraphs = text.split("\n", "\r\n")
            val chunks = mutableListOf<String>()
            val currentChunk = StringBuilder()
            
            // 按段落组织块，确保每个块不超过最大大小
            for (paragraph in paragraphs) {
                // 如果当前段落加上当前块会超过大小限制，则开始新块
                if (currentChunk.length + paragraph.length > TEXT_CHUNK_SIZE) {
                    // 如果当前块不为空，添加到块列表
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString())
                        currentChunk.clear()
                    }
                    
                    // 如果单个段落就超过了块大小限制，需要进一步分割
                    if (paragraph.length > TEXT_CHUNK_SIZE) {
                        // 按句子分割大段落
                        val sentences = paragraph.split(". ", "! ", "? ", "。", "！", "？")
                        for (sentence in sentences) {
                            if (currentChunk.length + sentence.length > TEXT_CHUNK_SIZE) {
                                if (currentChunk.isNotEmpty()) {
                                    chunks.add(currentChunk.toString())
                                    currentChunk.clear()
                                }
                                
                                // 如果单个句子还是太长，按字符数直接分割
                                if (sentence.length > TEXT_CHUNK_SIZE) {
                                    var start = 0
                                    while (start < sentence.length) {
                                        val end = minOf(start + TEXT_CHUNK_SIZE, sentence.length)
                                        chunks.add(sentence.substring(start, end))
                                        start = end
                                    }
                                } else {
                                    currentChunk.append(sentence)
                                }
                            } else {
                                currentChunk.append(sentence)
                            }
                        }
                    } else {
                        // 段落可以作为一个新块
                        currentChunk.append(paragraph)
                    }
                } else {
                    // 添加段落到当前块
                    if (currentChunk.isNotEmpty()) {
                        currentChunk.append("\n")
                    }
                    currentChunk.append(paragraph)
                }
            }
            
            // 添加最后一个块
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
            }
            
            Log.d(TAG, "文本已分为 ${chunks.size} 个块进行处理")
            
            // 使用第一个块开始合成
            if (chunks.isNotEmpty()) {
                val firstChunk = chunks[0]
                val remainingChunks = chunks.subList(1, chunks.size)
                
                // 设置特殊的监听器来处理多块合成
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    private var currentChunkIndex = 0
                    private val totalChunks = chunks.size
                    
                    override fun onStart(utteranceId: String) {
                        Log.d(TAG, "开始合成块 ${currentChunkIndex + 1}/${totalChunks}")
                        
                        if (currentChunkIndex == 0) {
                            // 首次开始时更新UI
                            _synthesisState.value = _synthesisState.value.copy(
                                status = STATUS_SYNTHESIZING,
                                message = "正在合成语音...",
                                progress = 0
                            )
                            updateNotification("正在合成语音...（0%）")
                            
                            // 启动进度跟踪器
                            progressTracker.start(text.length)
                        }
                    }
                    
                    override fun onDone(utteranceId: String) {
                        currentChunkIndex++
                        val progress = (currentChunkIndex.toFloat() / totalChunks * 100).toInt()
                        
                        // 更新进度
                        progressTracker.updateActualProgress(
                            currentChunkIndex * TEXT_CHUNK_SIZE, 
                            text.length
                        )
                        
                        if (currentChunkIndex < remainingChunks.size) {
                            // 继续处理下一个块
                            val nextChunk = remainingChunks[currentChunkIndex - 1]
                            val params = Bundle()
                            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, task.params.volume)
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                @Suppress("DEPRECATION")
                                tts?.synthesizeToFile(
                                    nextChunk,
                                    params,
                                    outputFile,
                                    "TTS_CHUNK_${UUID.randomUUID()}"
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                tts?.synthesizeToFile(
                                    nextChunk,
                                    null,
                                    outputFile,
                                    "TTS_CHUNK_${UUID.randomUUID()}"
                                )
                            }
                        } else {
                            // 所有块处理完成
                            Log.d(TAG, "所有块合成完成")
                            closeOutputStream()
                            
                            // 停止进度跟踪器，并标记为已完成
                            progressTracker.stop(isCompleted = true)
                            
                            // 保存到媒体库
                            scanFile(task.outputPath)
                            
                            // 保存到数据库
                            saveRecordToDatabase(task)
                            
                            // 格式化文件路径，使其更易读
                            val formattedPath = task.outputPath.replace("/storage/emulated/0/", "内部存储/")
                            
                            // 更新状态
                            _synthesisState.value = _synthesisState.value.copy(
                                status = STATUS_COMPLETED,
                                message = "语音合成完成\n文件已保存至：\n$formattedPath",
                                progress = 100,
                                outputPath = task.outputPath
                            )
                            
                            // 更新通知
                            updateNotification("语音合成完成（100%）")
                            
                            // 回调通知
                            synthesisCallback?.onCompleted(task.outputPath)
                            
                            // 继续处理队列中的任务
                            isProcessing = false
                            if (synthesisQueue.isNotEmpty()) {
                                processQueue()
                            } else {
                                stopForeground(false)
                            }
                        }
                    }
                    
                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "合成块出错: $utteranceId")
                        closeOutputStream()
                        
                        // 停止进度跟踪器
                        progressTracker.stop()
                        
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
                        // 使用进度跟踪器更新实际进度
                        val globalProgress = currentChunkIndex * TEXT_CHUNK_SIZE + end
                        progressTracker.updateActualProgress(globalProgress, text.length)
                    }
                })
                
                // 开始处理第一个块
                val params = Bundle()
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, task.params.volume)
                
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    @Suppress("DEPRECATION")
                    tts?.synthesizeToFile(
                        firstChunk, 
                        params, 
                        outputFile, 
                        "TTS_CHUNK_${UUID.randomUUID()}"
                    )
                } else {
                    @Suppress("DEPRECATION")
                    tts?.synthesizeToFile(
                        firstChunk, 
                        null, 
                        outputFile, 
                        "TTS_CHUNK_${UUID.randomUUID()}"
                    )
                }
                
                if (result != TextToSpeech.SUCCESS) {
                    _synthesisState.value = _synthesisState.value.copy(
                        status = STATUS_ERROR,
                        message = "无法开始语音合成，错误码: $result"
                    )
                    synthesisCallback?.onError("无法开始语音合成，错误码: $result")
                    isProcessing = false
                }
            } else {
                // 没有有效的文本块
                _synthesisState.value = _synthesisState.value.copy(
                    status = STATUS_ERROR,
                    message = "处理文本失败：没有有效的文本内容"
                )
                synthesisCallback?.onError("处理文本失败：没有有效的文本内容")
                isProcessing = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "分块处理文本异常", e)
            _synthesisState.value = _synthesisState.value.copy(
                status = STATUS_ERROR,
                message = "分块处理文本异常: ${e.message}"
            )
            synthesisCallback?.onError("分块处理文本异常: ${e.message}")
            isProcessing = false
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
            
            // 停止进度跟踪器
            progressTracker.stop()
            
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
        try {
            // 首选: 使用外部存储的Documents目录
            val externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appDir = File(externalDir, "WanderReads")
            val voicesDir = File(appDir, "voices")
            
            if (!voicesDir.exists()) {
                val dirCreated = voicesDir.mkdirs()
                if (!dirCreated) {
                    Log.w(TAG, "无法创建公共目录，尝试内部存储")
                    // 备选: 使用应用内部存储
                    return getAppInternalVoicesDirectory()
                }
            }
            
            return voicesDir
        } catch (e: Exception) {
            Log.e(TAG, "获取公共目录失败，使用内部存储", e)
            return getAppInternalVoicesDirectory()
        }
    }
    
    /**
     * 获取应用内部存储的语音文件目录
     */
    private fun getAppInternalVoicesDirectory(): File {
        val filesDir = applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        val voicesDir = File(filesDir, "voices")
        
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