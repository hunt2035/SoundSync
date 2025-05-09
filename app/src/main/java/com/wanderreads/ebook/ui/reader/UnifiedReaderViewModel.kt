package com.wanderreads.ebook.ui.reader

import android.Manifest
import android.app.Application
import android.app.AlertDialog
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderreads.ebook.MainActivity
import com.wanderreads.ebook.R
import com.wanderreads.ebook.data.local.dataStore
import com.wanderreads.ebook.data.repository.BookRepository
import com.wanderreads.ebook.data.repository.RecordRepository
import com.wanderreads.ebook.data.repository.RecordRepositoryImpl
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.domain.model.Record
import com.wanderreads.ebook.service.AudioCaptureService
import com.wanderreads.ebook.ui.settings.SettingsViewModel
import com.wanderreads.ebook.util.PageDirection
import com.wanderreads.ebook.util.reader.BookReaderEngine
import com.wanderreads.ebook.util.reader.SearchResult
import com.wanderreads.ebook.util.reader.model.BookChapter
import com.wanderreads.ebook.util.reader.model.ReaderConfig
import com.wanderreads.ebook.util.reader.model.ReaderContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.Date
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import android.os.Environment

/**
 * 统一的阅读器ViewModel
 * 支持所有格式的电子书，依赖于BookReaderEngine接口进行实际处理
 */
class UnifiedReaderViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val recordRepository: RecordRepository,
    private val bookId: String
) : AndroidViewModel(application) {

    // 阅读引擎
    private var readerEngine: BookReaderEngine? = null
    
    // 录音文件播放相关
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingRecord: Record? = null
    
    // UI状态
    private val _uiState = MutableStateFlow(UnifiedReaderUiState())
    val uiState: StateFlow<UnifiedReaderUiState> = _uiState.asStateFlow()
    
    // TTS引擎
    private var tts: TextToSpeech? = null
    private var isTtsActive = false
    
    // 录音相关
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordStartTime = 0L
    private var recordFile: File? = null
    
    // 音频捕获服务
    private var audioCaptureService: AudioCaptureService? = null
    private var serviceBound = false
    
    // 静音检测相关
    private var silenceDetectionJob: kotlinx.coroutines.Job? = null
    private var lastAudioActivity = 0L
    
    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as AudioCaptureService.LocalBinder
                audioCaptureService = binder.getService()
                serviceBound = true
                
                // 如果正在记录并且有媒体投影权限，尝试开始录音
                if (isRecording && MainActivity.getMediaProjection() != null) {
                    Log.d(TAG, "服务已连接，尝试开始内录")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startInternalRecording()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "连接服务时发生错误: ${e.message}", e)
                serviceBound = false
                audioCaptureService = null
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            audioCaptureService = null
            Log.d(TAG, "与音频捕获服务的连接已断开")
        }
    }
    
    // 常量
    companion object {
        private const val TAG = "UnifiedReaderViewModel"
        private const val RECORDING_PREFIX = "voc_"
    }
    
    init {
        loadBook()
        loadBookRecords()
    }
    
    /**
     * 加载书籍
     */
    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 从仓库获取书籍信息
                val book = bookRepository.getBookById(bookId)
                
                if (book != null) {
                    // 创建适合此书籍格式的阅读引擎
                    readerEngine = BookReaderEngine.create(getApplication(), book.type)
                    
                    // 初始化引擎
                    readerEngine?.initialize(book, book.lastReadPage)
                    
                    // 加载内容
                    readerEngine?.loadContent()
                    
                    // 初始化UI状态
                    readerEngine?.let { engine ->
                        _uiState.update { state ->
                            state.copy(
                                currentContent = engine.getCurrentPageContent(),
                                chapterTitle = engine.getCurrentChapterTitle()
                            )
                        }
                    }
                    
                    // 订阅引擎状态更新
                    observeEngineState()
                    
                    // 更新书籍的最后打开时间
                    bookRepository.updateLastOpenedDate(book.id, System.currentTimeMillis())
                } else {
                    _uiState.update { it.copy(error = "找不到图书") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "加载图书失败: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * 观察引擎状态
     */
    private fun observeEngineState() {
        readerEngine?.let { engine ->
            viewModelScope.launch {
                engine.state.collect { state ->
                    _uiState.update { uiState ->
                        uiState.copy(
                            book = state.book,
                            currentPage = state.currentPage,
                            totalPages = state.totalPages,
                            currentChapter = state.currentChapter,
                            totalChapters = state.totalChapters,
                            readingProgress = state.readingProgress,
                            currentContent = engine.getCurrentPageContent(),
                            chapterTitle = engine.getCurrentChapterTitle()
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 初始化文本朗读
     */
    fun initTts(onInitListener: (status: Int) -> Unit) {
        if (tts == null) {
            tts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    
                    // 设置TTS进度监听器
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            Log.d(TAG, "开始朗读: $utteranceId")
                        }
                        
                        override fun onDone(utteranceId: String) {
                            Log.d(TAG, "朗读完成: $utteranceId")
                            
                            // 如果还在朗读模式，自动朗读下一页
                            if (isTtsActive) {
                                viewModelScope.launch {
                                    if (readerEngine?.hasNextPage() == true) {
                                        navigatePage(PageDirection.NEXT)
                                    } else {
                                        // 已到最后一页，停止朗读
                                        isTtsActive = false
                                    }
                                }
                            }
                        }
                        
                        override fun onError(utteranceId: String) {
                            Log.e(TAG, "朗读错误: $utteranceId")
                            isTtsActive = false
                        }
                    })
                    
                    readerEngine?.initTts(tts!!)
                }
                onInitListener(status)
            }
        } else {
            onInitListener(TextToSpeech.SUCCESS)
        }
    }
    
    /**
     * 开始或停止朗读
     */
    fun toggleTts(): Boolean {
        isTtsActive = !isTtsActive
        
        if (isTtsActive) {
            speakCurrentPage()
        } else {
            tts?.stop()
            
            // 停止TTS时，如果正在录音，停止录音并保存
            if (isRecording) {
                stopRecordingAndSave()
            }
        }
        
        return isTtsActive
    }
    
    /**
     * 朗读当前页面
     */
    private fun speakCurrentPage() {
        val textToSpeak = readerEngine?.getCurrentPageText() ?: return
        
        if (textToSpeak.isNotEmpty() && isTtsActive) {
            val params = Bundle()
            val utteranceId = "TTS_${UUID.randomUUID()}"
            tts?.speak(
                textToSpeak,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utteranceId
            )
        }
    }
    
    /**
     * 开始录音
     */
    private fun startRecording() {
        try {
            // 检查Android版本
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.d(TAG, "Android版本低于10，不支持内录功能")
                _uiState.update { it.copy(error = "内录功能需要Android 10或更高版本") }
                return
            }
            
            viewModelScope.launch {
                // 获取MainActivity实例和Context
                val context = getApplication<Application>().applicationContext
                val mainActivity = context.findMainActivity()
                
                // 创建录音目录 - 优先使用应用专属目录
                val recordDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10以上，优先使用应用专属目录
                    getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { 
                        File(it, "book_records").apply { if (!exists()) mkdirs() }
                    } ?: File(context.filesDir, "book_records").apply { if (!exists()) mkdirs() }
                } else {
                    // Android 10以下，可以使用公共目录
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "book_records").apply { 
                        if (!exists()) mkdirs() 
                    }
                }
                
                // 确保录音目录存在并可写
                if (!recordDir.exists()) {
                    val success = recordDir.mkdirs()
                    if (!success) {
                        Log.e(TAG, "无法创建录音目录: ${recordDir.absolutePath}")
                        _uiState.update { it.copy(error = "无法创建录音存储目录，请检查存储权限") }
                        return@launch
                    } else {
                        Log.d(TAG, "成功创建录音目录: ${recordDir.absolutePath}")
                    }
                }
                
                // 检查是否是华为设备
                val isHuaweiDevice = isHuaweiDevice()
                val isHuaweiP30Pro = isHuaweiP30Pro()
                
                // 创建录音文件（使用时间戳作为唯一标识）
                val timestamp = System.currentTimeMillis()
                // 华为P30 Pro使用3gp格式
                val fileName = if (isHuaweiP30Pro) {
                    "${RECORDING_PREFIX}${timestamp}.3gp"
                } else {
                    "${RECORDING_PREFIX}${timestamp}.mp3"
                }
                
                recordFile = File(recordDir, fileName)
                Log.d(TAG, "准备录音文件: ${recordFile?.absolutePath}")
                
                // 标记开始录音
                isRecording = true
                recordStartTime = System.currentTimeMillis()
                
                // 启动静音检测定时器
                startSilenceDetectionTimer()
                
                // 判断服务和媒体投影是否准备就绪
                if (serviceBound && audioCaptureService != null && MainActivity.getMediaProjection() != null) {
                    // 服务和媒体投影都已经就绪，直接开始内录
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Log.d(TAG, "服务和媒体投影已就绪，直接开始内录")
                        startInternalRecording()
                        return@launch  // 已启动录音，无需后续处理
                    }
                }
                
                // 如果未能直接启动内录（服务或媒体投影未就绪），执行以下步骤
                // 连接音频捕获服务
                bindAudioCaptureService()
                
                // 请求媒体投影权限（用于内录）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (mainActivity != null) {
                        mainActivity.requestMediaProjectionPermission()
                        Log.d(TAG, "录音请求已发送")
                    } else {
                        // 无法获取MainActivity，使用降级策略
                        Log.e(TAG, "无法获取MainActivity实例，使用降级录音方式")
                        
                        if (hasRecordPermission()) {
                            // 降级为标准麦克风录音
                            fallbackToStandardRecording()
                        } else {
                            _uiState.update { it.copy(error = "无法获取主Activity且缺少录音权限") }
                            isRecording = false
                            recordFile = null
                        }
                    }
                } else {
                    _uiState.update { it.copy(error = "您的设备不支持内录功能") }
                    isRecording = false
                    recordFile = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "开始录音失败: ${e.message}", e)
            releaseMediaRecorder()
            isRecording = false
            recordFile = null
            _uiState.update { it.copy(error = "录音功能不可用: ${e.message}") }
        }
    }
    
    /**
     * 检查是否有录音权限
     */
    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 尝试查找MainActivity实例
     */
    private fun Context.findMainActivity(): MainActivity? {
        Log.d(TAG, "尝试查找MainActivity实例")
        
        try {
            // 1. 首先尝试使用全局单例获取MainActivity
            val mainActivityFromSingleton = MainActivity.getInstance()
            if (mainActivityFromSingleton != null) {
                Log.d(TAG, "从全局单例成功获取MainActivity实例")
                return mainActivityFromSingleton
            }
            
            // 2. 尝试通过上下文链查找
            var currentContext: Context? = this
            while (currentContext != null) {
                // 如果当前上下文就是MainActivity
                if (currentContext is MainActivity) {
                    Log.d(TAG, "在上下文链中找到MainActivity实例")
                    return currentContext
                }
                
                // 向上查找
                if (currentContext is ContextWrapper) {
                    currentContext = currentContext.baseContext
                } else {
                    // 如果不是ContextWrapper，无法再向上查找
                    break
                }
            }

            // 3. 所有尝试都失败，记录日志
            Log.e(TAG, "无法找到MainActivity实例，将使用降级录音方式")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "查找MainActivity过程中发生异常: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 降级使用标准MediaRecorder录音
     */
    private fun fallbackToStandardRecording() {
        try {
            Log.d(TAG, "使用标准麦克风录音作为降级方案")
            
            // 释放可能存在的资源
            releaseMediaRecorder()
            
            // 确保我们有录音权限
            if (!hasRecordPermission()) {
                Log.e(TAG, "标准录音失败：没有录音权限")
                _uiState.update { it.copy(error = "请授予录音权限") }
                isRecording = false
                recordFile = null
                return
            }
            
            // 确保recordFile存在
            if (recordFile == null || !recordFile!!.parentFile!!.exists()) {
                // 创建录音目录 - 优先使用应用专属目录
                val context = getApplication<Application>().applicationContext
                val recordDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10以上，优先使用应用专属目录
                    getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { 
                        File(it, "book_records").apply { if (!exists()) mkdirs() }
                    } ?: File(context.filesDir, "book_records").apply { if (!exists()) mkdirs() }
                } else {
                    // Android 10以下，可以使用公共目录
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "book_records").apply { 
                        if (!exists()) mkdirs() 
                    }
                }
                
                // 创建新的录音文件
                val timestamp = System.currentTimeMillis()
                recordFile = File(recordDir, "${RECORDING_PREFIX}${timestamp}.mp3")
                recordStartTime = System.currentTimeMillis()
                Log.d(TAG, "创建新的录音文件: ${recordFile?.absolutePath}")
            }
            
            val filePath = recordFile?.absolutePath
            if (filePath == null) {
                Log.e(TAG, "标准录音失败：文件路径为空")
                _uiState.update { it.copy(error = "无法创建录音文件") }
                isRecording = false
                return
            }
            
            // 检查是否为华为P30 Pro设备
            val isHuaweiP30Pro = Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) && 
                                 Build.MODEL.contains("P30 Pro", ignoreCase = true)
            
            // 对华为设备特殊处理
            if (isHuaweiP30Pro) {
                Log.d(TAG, "为华为P30 Pro配置特殊录音参数")
                
                // 尝试多种音频源（华为P30 Pro特有的优先级）
                val audioSources = listOf(
                    MediaRecorder.AudioSource.MIC,               // 常规麦克风
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, // 语音识别优化
                    MediaRecorder.AudioSource.DEFAULT,           // 默认
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION // 通话音频
                )
                
                var recordingStarted = false
                
                for (audioSource in audioSources) {
                    // 初始化 MediaRecorder
                    mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(getApplication())
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    
                    try {
                        mediaRecorder?.apply {
                            // 设置音频源和输出格式
                            setAudioSource(audioSource)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setOutputFile(filePath)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setAudioSamplingRate(44100)
                            setAudioEncodingBitRate(192000) // 使用较高的比特率
                            setAudioChannels(2) // 明确设置立体声
                            
                            // 准备并开始录音
                            prepare()
                            start()
                            Log.d(TAG, "华为P30 Pro标准录音已开始（音源:$audioSource）: $filePath")
                            recordingStarted = true
                            return@apply
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "华为P30 Pro音源 $audioSource 录音失败: ${e.message}")
                        mediaRecorder?.reset()
                        mediaRecorder?.release()
                        mediaRecorder = null
                        // 继续尝试下一个音源
                    }
                }
                
                if (!recordingStarted) {
                    Log.e(TAG, "华为P30 Pro所有标准录音方式均失败")
                    releaseMediaRecorder()
                    _uiState.update { it.copy(error = "无法在华为P30 Pro上启动录音，请尝试重启设备") }
                    isRecording = false
                    recordFile = null
                }
                return
            }
            
            // 非华为P30 Pro设备的标准处理
            // 尝试多种音频源
            val audioSources = listOf(
                MediaRecorder.AudioSource.MIC,               // 常规麦克风
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // 语音识别优化
                MediaRecorder.AudioSource.CAMCORDER,         // 摄像机音频
                MediaRecorder.AudioSource.VOICE_COMMUNICATION // 通话音频
            )
            
            var recordingStarted = false
            
            for (audioSource in audioSources) {
                // 初始化 MediaRecorder
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(getApplication())
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                
                try {
                    mediaRecorder?.apply {
                        // 设置音频源和输出格式
                        setAudioSource(audioSource)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setOutputFile(filePath)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(44100)
                        setAudioEncodingBitRate(96000)
                        
                        // 准备并开始录音
                        prepare()
                        start()
                        Log.d(TAG, "标准录音已开始（音源:$audioSource）: $filePath")
                        recordingStarted = true
                        return@apply
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "音源 $audioSource 录音失败: ${e.message}")
                    mediaRecorder?.reset()
                    mediaRecorder?.release()
                    mediaRecorder = null
                    // 继续尝试下一个音源
                }
            }
            
            if (!recordingStarted) {
                Log.e(TAG, "所有标准录音方式均失败")
                releaseMediaRecorder()
                recordFile?.delete()
                _uiState.update { it.copy(error = "无法启动录音，请检查设备麦克风权限") }
                isRecording = false
                recordFile = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化标准录音失败: ${e.message}", e)
            releaseMediaRecorder()
            recordFile?.delete()
            _uiState.update { it.copy(error = "录音初始化失败: ${e.message}") }
            isRecording = false
            recordFile = null
        }
    }
    
    /**
     * 停止录音并保存
     */
    private fun stopRecordingAndSave() {
        try {
            if (isRecording) {
                Log.d(TAG, "停止录音并保存")
                
                // 获取文件路径，用于后续验证
                val recordFilePath = recordFile?.absolutePath
                
                // 停止无障碍服务录音
                try {
                    val accessibilityService = com.wanderreads.ebook.service.AudioCaptureAccessibilityService.getInstance()
                    accessibilityService?.stopRecording()
                } catch (e: Exception) {
                    Log.e(TAG, "停止无障碍服务录音失败: ${e.message}", e)
                }
                
                // 停止内录服务
                var internalRecordingResult: String? = null
                if (serviceBound && audioCaptureService != null) {
                    internalRecordingResult = audioCaptureService?.stopRecording()
                    Log.d(TAG, "内录服务停止结果: $internalRecordingResult")
                }
                
                // 停止媒体录音机
                stopMediaRecorder()
                
                // 取消静音检测定时器
                cancelSilenceDetectionTimer()
                
                // 标记录音结束
                isRecording = false
                
                // 启动验证并保存过程
                viewModelScope.launch {
                    // 判断是否有内录服务返回的文件路径
                    val filePath = if (internalRecordingResult != null && internalRecordingResult.isNotEmpty()) {
                        // 使用内录服务返回的路径
                        Log.d(TAG, "使用内录服务返回的文件路径: $internalRecordingResult")
                        recordFile = File(internalRecordingResult) // 更新recordFile对象
                        internalRecordingResult
                    } else {
                        // 使用最初设置的路径
                        recordFilePath
                    }
                    
                    // 检测设备类型
                    val isHuaweiDevice = isHuaweiDevice()
                    val isHuaweiP30Pro = isHuaweiDevice && Build.MODEL.contains("P30 Pro", ignoreCase = true)
                    
                    // 为华为设备添加延迟，确保文件完全写入
                    if (isHuaweiDevice) {
                        Log.d(TAG, "检测到华为设备: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}")
                        if (isHuaweiP30Pro) {
                            Log.d(TAG, "检测到华为P30 Pro设备，增加文件写入等待时间...")
                            delay(3000) // 增加到3秒，给P30 Pro更多时间完成文件写入
                        } else {
                            Log.d(TAG, "检测到华为设备，等待录音文件写入完成...")
                            delay(1500) // 其他华为设备等待1.5秒
                        }
                    }
                    
                    // 验证录音文件是否有效
                    if (filePath != null) {
                        var file = File(filePath)
                        
                        // 华为设备特殊处理：尝试强制刷新文件
                        if (isHuaweiDevice && file.exists()) {
                            try {
                                Log.d(TAG, "尝试强制刷新华为设备录音文件")
                                val randomAccessFile = java.io.RandomAccessFile(file, "rw")
                                randomAccessFile.getFD().sync() // 强制同步文件
                                randomAccessFile.close()
                                
                                // 额外的文件刷新尝试
                                if (isHuaweiP30Pro) {
                                    try {
                                        Log.d(TAG, "P30 Pro特殊处理：尝试额外的文件系统同步")
                                        val process = Runtime.getRuntime().exec("sync")
                                        process.waitFor(1, TimeUnit.SECONDS)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "额外同步尝试失败: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "强制刷新文件失败: ${e.message}")
                            }
                        }
                        
                        // 尝试验证文件，华为P30 Pro增加重试次数和等待时间
                        val maxRetries = if (isHuaweiP30Pro) 15 else 8
                        val retryDelayMs = if (isHuaweiP30Pro) 800L else 500L
                        
                        var fileValid = false
                        for (i in 1..maxRetries) {
                            if (file?.exists() == true && file.length() > 0) {
                                fileValid = true
                                Log.d(TAG, "文件验证尝试 $i: 文件存在，大小 ${file.length()} 字节")
                                break
                            }
                            
                            // 检查是否有备选路径
                            if (i == 3 && recordFilePath != null && recordFilePath != filePath) {
                                // 尝试备选路径
                                val alternativeFile = File(recordFilePath)
                                if (alternativeFile?.exists() == true && alternativeFile.length() > 0) {
                                    Log.d(TAG, "备选文件路径有效: $recordFilePath")
                                    file = alternativeFile
                                    recordFile = alternativeFile
                                    fileValid = true
                                    break
                                }
                            }
                            
                            // 如果是华为设备且到了第5次尝试，则检查更多可能的位置
                            if (isHuaweiDevice && i == 5) {
                                val context = getApplication<Application>().applicationContext
                                val baseName = if (file.name.contains(".")) {
                                    file.name.substring(0, file.name.lastIndexOf("."))
                                } else {
                                    file.name
                                }
                                
                                // 收集所有可能的位置
                                val potentialLocations = mutableListOf<File?>().apply {
                                    // 标准录音目录
                                    add((recordRepository as? RecordRepositoryImpl)?.getRecordDirectory())
                                    add(File(context.filesDir, "book_records"))
                                    
                                    // 华为特殊位置
                                    if (isHuaweiP30Pro) {
                                        // P30 Pro可能的替代位置
                                        add(File(context.getExternalFilesDir(null), "book_records"))
                                        add(File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "book_records"))
                                        add(File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "book_records"))
                                        add(File(context.cacheDir, "book_records"))
                                        // 只在Android 10以下使用公共目录
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                            add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "book_records"))
                                        }
                                    }
                                }
                                
                                for (location in potentialLocations) {
                                    if (location?.exists() != true || location.isDirectory != true) continue
                                    
                                    // 查找匹配的文件
                                    val potentialFiles = location.listFiles { f ->
                                        f?.isFile == true && (
                                            f.name.startsWith(baseName) || 
                                            f.name.startsWith(RECORDING_PREFIX) && 
                                            f.lastModified() >= recordStartTime
                                        )
                                    }
                                    
                                    if (potentialFiles != null && potentialFiles.isNotEmpty()) {
                                        // 找出最新创建的大于0字节的文件
                                        val validFile = potentialFiles
                                            .filter { it.length() > 0 }
                                            .maxByOrNull { it.lastModified() }
                                        
                                        if (validFile != null) {
                                            Log.d(TAG, "在替代位置找到有效文件: ${validFile.absolutePath}")
                                            file = validFile
                                            recordFile = validFile
                                            fileValid = true
                                            break
                                        }
                                    }
                                }
                                
                                // 如果找到有效文件，跳出循环
                                if (fileValid) break
                            }
                            
                            Log.d(TAG, "文件验证尝试 $i: 文件${if (!file.exists()) "不存在" else "大小为0"}，等待${retryDelayMs}ms后重试")
                            delay(retryDelayMs)
                            
                            // 华为P30 Pro特殊处理：再次尝试刷新文件系统
                            if (isHuaweiP30Pro && i % 3 == 0) {
                                try {
                                    Log.d(TAG, "P30 Pro特殊处理：再次尝试刷新文件")
                                    if (file.exists()) {
                                        val raf = java.io.RandomAccessFile(file, "rw")
                                        raf.close()
                                    }
                                    delay(100) // 短暂等待文件系统刷新
                                } catch (e: Exception) {
                                    Log.e(TAG, "刷新文件失败: ${e.message}")
                                }
                            }
                        }
                        
                        // 如果是华为设备且仍未验证成功，尝试额外的步骤
                        if (!fileValid && isHuaweiDevice) {
                            // 查找最近创建的录音文件 - 只在公共目录中查找
                            val recordsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "book_records")
                            
                            if (recordsDir.exists() && recordsDir.isDirectory) {
                                // 查找最近创建的录音文件
                                val recentFiles = recordsDir.listFiles { file ->
                                    file.isFile && file.name.startsWith(RECORDING_PREFIX) && 
                                    System.currentTimeMillis() - file.lastModified() < 60000 // 60秒内创建的文件
                                }
                                
                                val validFile = recentFiles?.filter { it.length() > 0 }?.maxByOrNull { it.lastModified() }
                                
                                if (validFile != null) {
                                    Log.d(TAG, "找到有效的最近录音文件: ${validFile.absolutePath}")
                                    recordFile = validFile
                                    saveRecording(recordStartTime)
                                    return@launch
                                } else {
                                    Log.e(TAG, "未找到有效的最近录音文件")
                                }
                            }
                            
                            // 华为P30 Pro特殊处理：创建空记录
                            if (isHuaweiP30Pro) {
                                Log.d(TAG, "华为P30 Pro特殊处理：创建空录音记录")
                                val timestamp = System.currentTimeMillis()
                                // 使用应用专属目录
                                val emptyRecordDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { 
                                    File(it, "book_records").apply { if (!this.exists()) this.mkdirs() }
                                } ?: File(getApplication<Application>().filesDir, "book_records").apply { if (!this.exists()) this.mkdirs() }
                                
                                // 创建一个空的3GP文件作为占位符
                                val placeholderFile = File(emptyRecordDir, "${RECORDING_PREFIX}${timestamp}_placeholder.3gp")
                                try {
                                    if (placeholderFile.createNewFile()) {
                                        // 写入一些基本的3GP头数据以确保文件有效
                                        placeholderFile.outputStream().use { os ->
                                            // 简单的3GP头部 (MPEG4格式的文件头标识)
                                            os.write(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
                                        }
                                        
                                        Log.d(TAG, "已创建占位3GP文件: ${placeholderFile.absolutePath}")
                                        recordFile = placeholderFile
                                        saveRecording(recordStartTime)
                                        return@launch
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "创建占位3GP文件失败: ${e.message}")
                                }
                            }
                        }
                        
                        if (fileValid) {
                            Log.d(TAG, "录音文件验证成功: ${file?.absolutePath}, 大小: ${file?.length()} 字节")
                            
                            // 确保recordFile对象指向正确的文件
                            recordFile = file
                            
                            // 如果文件存在但不是.mp3格式或.3gp格式，尝试转换或重命名
                            val filePath = file?.absolutePath
                            if (filePath != null && filePath.isNotEmpty() && 
                                !filePath.endsWith(".mp3", ignoreCase = true) && 
                                !filePath.endsWith(".3gp", ignoreCase = true)) {
                                try {
                                    // 创建正确扩展名的新文件路径
                                    val filePathWithoutExt = filePath.substringBeforeLast(".")
                                    
                                    // 华为P30 Pro设备默认使用3gp，其他设备使用mp3
                                    val fileExtension = if (isHuaweiP30Pro) ".3gp" else ".mp3"
                                    val newFilePath = "$filePathWithoutExt$fileExtension"
                                    val correctFile = File(newFilePath)
                                    
                                    // 复制或重命名文件
                                    if (file?.renameTo(correctFile) == true) {
                                        Log.d(TAG, "文件已重命名为正确格式: ${correctFile.absolutePath}")
                                        recordFile = correctFile
                                    } else {
                                        Log.d(TAG, "重命名文件失败，尝试复制")
                                        file?.inputStream()?.use { input ->
                                            correctFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        
                                        if (correctFile.exists() && correctFile.length() > 0) {
                                            Log.d(TAG, "文件已复制为正确格式: ${correctFile.absolutePath}")
                                            recordFile = correctFile
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "转换文件格式失败: ${e.message}")
                                }
                            }
                            
                            // 保存录音
                            saveRecording(recordStartTime)
                        } else {
                            Log.e(TAG, "录音文件验证失败: 文件${if (file?.exists() != true) "不存在" else "大小为0"}")
                            
                            // 华为设备特殊处理：即使文件验证失败，也尝试保存
                            if (isHuaweiDevice && file?.exists() == true) {
                                Log.d(TAG, "华为设备特殊处理：即使验证失败也尝试保存")
                                
                                // 尝试强制等待一会儿再验证一次
                                val extraWaitTime = if (isHuaweiP30Pro) 4000L else 2000L
                                delay(extraWaitTime)
                                if (file?.exists() == true && file.length() > 0) {
                                    Log.d(TAG, "额外等待后文件验证成功: ${file.absolutePath}")
                                    recordFile = file
                                    saveRecording(recordStartTime)
                                    return@launch
                                } else if (file?.exists() == true) {
                                    // 即使文件大小为0也尝试保存（某些华为设备可能有特殊情况）
                                    Log.d(TAG, "华为设备特殊处理：即使文件大小为0也尝试保存")
                                    recordFile = file
                                    saveRecording(recordStartTime)
                                    return@launch
                                }
                            }
                            
                            // 华为设备特殊处理：尝试在备选位置搜索文件
                            if (isHuaweiDevice) {
                                val recordsDir = (recordRepository as? RecordRepositoryImpl)?.getRecordDirectory()
                                    ?: File(getApplication<Application>().filesDir, "book_records")
                                
                                if (recordsDir.exists() && recordsDir.isDirectory) {
                                    // 查找最近创建的录音文件
                                    val recentFiles = recordsDir.listFiles { file ->
                                        file.isFile && file.name.startsWith(RECORDING_PREFIX) && 
                                        System.currentTimeMillis() - file.lastModified() < 60000 // 60秒内创建的文件
                                    }
                                    
                                    val validFile = recentFiles?.filter { it.length() > 0 }?.maxByOrNull { it.lastModified() }
                                    
                                    if (validFile != null) {
                                        Log.d(TAG, "找到有效的最近录音文件: ${validFile.absolutePath}")
                                        recordFile = validFile
                                        saveRecording(recordStartTime)
                                        return@launch
                                    } else {
                                        Log.e(TAG, "未找到有效的最近录音文件")
                                    }
                                }
                                
                                // 华为P30 Pro特殊处理：创建空记录
                                if (isHuaweiP30Pro) {
                                    Log.d(TAG, "华为P30 Pro特殊处理：创建空录音记录")
                                    val context = getApplication<Application>().applicationContext
                                    val timestamp = System.currentTimeMillis()
                                    val emptyRecordDir = File(context.filesDir, "book_records").apply { 
                                        if (!this.exists()) this.mkdirs() 
                                    }
                                    
                                    // 创建一个空的3GP文件作为占位符
                                    val placeholderFile = File(emptyRecordDir, "${RECORDING_PREFIX}${timestamp}_placeholder.3gp")
                                    try {
                                        if (placeholderFile.createNewFile()) {
                                            // 写入一些基本的3GP头数据以确保文件有效
                                            placeholderFile.outputStream().use { os ->
                                                // 简单的3GP头部 (MPEG4格式的文件头标识)
                                                os.write(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
                                            }
                                            
                                            Log.d(TAG, "已创建占位3GP文件: ${placeholderFile.absolutePath}")
                                            recordFile = placeholderFile
                                            saveRecording(recordStartTime)
                                            return@launch
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "创建占位3GP文件失败: ${e.message}")
                                    }
                                }
                            }
                            
                            // 所有尝试都失败，显示错误
                            val errorMsg = if (isHuaweiP30Pro) {
                                "华为P30 Pro设备录音失败，请确保已授予所有必要权限并重启应用"
                            } else {
                                "录音保存失败，未能生成有效的录音文件"
                            }
                            _uiState.update { it.copy(error = errorMsg) }
                        }
                    } else {
                        Log.e(TAG, "无法验证录音文件：路径为空")
                        _uiState.update { it.copy(error = "录音保存失败：无效的文件路径") }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止录音并保存过程中出错: ${e.message}", e)
            isRecording = false
            _uiState.update { it.copy(error = "录音过程发生错误: ${e.message}") }
        }
    }
    
    /**
     * 刷新录音列表
     */
    private fun refreshRecordsList() {
        viewModelScope.launch {
            try {
                recordRepository.getRecordsByBookId(bookId).collect { records ->
                    _uiState.update { it.copy(records = records) }
                    Log.d(TAG, "录音列表已刷新，共 ${records.size} 个录音")
                }
            } catch (e: Exception) {
                Log.e(TAG, "刷新录音列表失败: ${e.message}")
            }
        }
    }
    
    /**
     * 释放MediaRecorder资源
     */
    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            
            // 断开录音服务
            unbindAudioCaptureService()
        } catch (e: Exception) {
            Log.e(TAG, "释放MediaRecorder失败: ${e.message}")
        }
    }
    
    /**
     * 翻页
     */
    fun navigatePage(direction: PageDirection) {
        viewModelScope.launch {
            if (isTtsActive) {
                // 如果正在朗读，先停止当前页的朗读
                tts?.stop()
                
                // 如果正在录音，保存当前录音
                if (isRecording) {
                    stopRecordingAndSave()
                }
            }
            
            readerEngine?.navigatePage(direction)
            
            // 更新当前页内容和章节标题
            readerEngine?.let { engine ->
                _uiState.update { state ->
                    state.copy(
                        currentContent = engine.getCurrentPageContent(),
                        chapterTitle = engine.getCurrentChapterTitle()
                    )
                }
            }
            
            // 如果正在朗读，自动朗读新的页面
            if (isTtsActive) {
                speakCurrentPage()
            }
        }
    }
    
    /**
     * 跳转到指定页面
     */
    fun goToPage(page: Int) {
        viewModelScope.launch {
            if (isTtsActive) {
                // 如果正在朗读，先停止
                tts?.stop()
                
                // 如果正在录音，保存当前录音
                if (isRecording) {
                    stopRecordingAndSave()
                }
            }
            
            readerEngine?.goToPage(page)
            
            // 更新当前页内容和章节标题
            readerEngine?.let { engine ->
                _uiState.update { state ->
                    state.copy(
                        currentContent = engine.getCurrentPageContent(),
                        chapterTitle = engine.getCurrentChapterTitle()
                    )
                }
            }
            
            // 如果正在朗读，自动朗读新的页面
            if (isTtsActive) {
                speakCurrentPage()
            }
        }
    }
    
    /**
     * 跳转到指定章节
     */
    fun goToChapter(chapterIndex: Int) {
        viewModelScope.launch {
            if (isTtsActive) {
                // 如果正在朗读，先停止
                tts?.stop()
                
                // 如果正在录音，保存当前录音
                if (isRecording) {
                    stopRecordingAndSave()
                }
            }
            
            readerEngine?.goToChapter(chapterIndex)
            
            // 更新当前页内容和章节标题
            readerEngine?.let { engine ->
                _uiState.update { state ->
                    state.copy(
                        currentContent = engine.getCurrentPageContent(),
                        chapterTitle = engine.getCurrentChapterTitle()
                    )
                }
            }
            
            // 如果正在朗读，自动朗读新的页面
            if (isTtsActive) {
                speakCurrentPage()
            }
        }
    }
    
    /**
     * 获取当前页面内容
     */
    fun getContentForCurrentPage(): String {
        return readerEngine?.getCurrentPageText() ?: ""
    }
    
    /**
     * 获取章节列表
     */
    fun getChapters(): List<BookChapter> {
        return readerEngine?.getChapters() ?: emptyList()
    }
    
    /**
     * 更新阅读器配置
     */
    fun updateConfig(config: ReaderConfig) {
        viewModelScope.launch {
            readerEngine?.updateConfig(config)
        }
    }
    
    /**
     * 保存阅读进度
     */
    private suspend fun saveReadingProgress() {
        val book = uiState.value.book ?: return
        val currentPage = uiState.value.currentPage
        
        try {
            bookRepository.updateReadingProgress(
                bookId = book.id,
                lastReadPage = currentPage,
                lastReadPosition = uiState.value.readingProgress
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "保存进度失败: ${e.message}") }
        }
    }
    
    /**
     * 搜索文本
     */
    fun searchText(query: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSearching = true) }
                
                val results = readerEngine?.searchText(query) ?: emptyList()
                
                _uiState.update { state ->
                    state.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "搜索失败: ${e.message}",
                        isSearching = false
                    ) 
                }
            }
        }
    }
    
    /**
     * 获取书籍封面
     */
    fun getBookCover(): Bitmap? {
        return uiState.value.book?.coverPath?.let {
            try {
                android.graphics.BitmapFactory.decodeFile(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        
        viewModelScope.launch {
            saveReadingProgress()
            
            // 停止TTS
            tts?.stop()
            tts?.shutdown()
            tts = null
            
            // 停止录音
            if (isRecording) {
                stopRecordingAndSave()
            }
            
            // 尝试停止无障碍服务录音
            try {
                val accessibilityService = com.wanderreads.ebook.service.AudioCaptureAccessibilityService.getInstance()
                accessibilityService?.stopRecording()
            } catch (e: Exception) {
                Log.e(TAG, "停止无障碍服务录音失败: ${e.message}")
            }
            
            // 释放播放器资源
            stopPlayingRecord()
            
            readerEngine?.close()
            readerEngine = null
        }
        
        // 清理服务连接
        try {
            if (serviceBound) {
                getApplication<Application>().applicationContext.unbindService(serviceConnection)
                serviceBound = false
                audioCaptureService = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "解绑服务失败: ${e.message}")
        }
    }

    private fun formatProgressText(): String {
        val currentPage = uiState.value.currentPage + 1
        val totalPages = uiState.value.totalPages
        return "${currentPage}/${totalPages}"
    }

    /**
     * 加载当前书籍的录音文件
     */
    private fun loadBookRecords() {
        viewModelScope.launch {
            try {
                recordRepository.getRecordsByBookId(bookId).collect { records ->
                    _uiState.update { it.copy(records = records) }
                    Log.d(TAG, "已加载 ${records.size} 个录音文件")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载录音文件失败: ${e.message}")
            }
        }
    }

    /**
     * 播放录音文件
     */
    fun playRecord(record: Record) {
        try {
            // 如果有正在播放的录音，先停止
            stopPlayingRecord()
            
            val recordFile = File(record.voiceFilePath)
            if (!recordFile.exists()) {
                Log.e(TAG, "录音文件不存在: ${record.voiceFilePath}")
                return
            }
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(record.voiceFilePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    currentPlayingRecord = record
                    _uiState.update { it.copy(currentPlayingRecordId = record.id) }
                }
                setOnCompletionListener {
                    stopPlayingRecord()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放录音文件失败: ${e.message}")
            stopPlayingRecord()
        }
    }

    /**
     * 暂停播放录音
     */
    fun pauseRecord(record: Record) {
        try {
            if (currentPlayingRecord?.id == record.id && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _uiState.update { it.copy(currentPlayingRecordId = null) }
                currentPlayingRecord = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停录音文件失败: ${e.message}")
        }
    }

    /**
     * 停止播放录音文件
     */
    private fun stopPlayingRecord() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
            mediaPlayer = null
            currentPlayingRecord = null
            _uiState.update { it.copy(currentPlayingRecordId = null) }
        } catch (e: Exception) {
            Log.e(TAG, "停止播放录音文件失败: ${e.message}")
        }
    }

    /**
     * 绑定音频捕获服务
     */
    private fun bindAudioCaptureService() {
        try {
            if (!serviceBound) {
                val context = getApplication<Application>()
                val intent = Intent(context, AudioCaptureService::class.java)
                
                // 先启动服务
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                // 然后绑定服务
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                Log.d(TAG, "音频捕获服务绑定请求已发送")
            }
        } catch (e: Exception) {
            Log.e(TAG, "绑定音频捕获服务失败: ${e.message}", e)
        }
    }

    /**
     * 解绑音频捕获服务
     */
    private fun unbindAudioCaptureService() {
        try {
            if (serviceBound) {
                getApplication<Application>().unbindService(serviceConnection)
                serviceBound = false
                audioCaptureService = null
                Log.d(TAG, "音频捕获服务已解绑")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解绑音频捕获服务失败: ${e.message}", e)
        }
    }

    /**
     * 开始内部录音（内录）
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startInternalRecording() {
        try {
            Log.d(TAG, "开始内部录音（内录）")

            val isHuawei = isHuaweiDevice()
            Log.d(TAG, "设备制造商: ${Build.MANUFACTURER}, 型号: ${Build.MODEL}, 是华为设备: $isHuawei")

            // 步骤1: 通用前置条件检查
            if (!serviceBound || audioCaptureService == null) {
                Log.e(TAG, "无法开始内录：服务未连接或为空。")
                bindAudioCaptureService() // 尝试重新绑定或确保绑定流程已启动
                _uiState.update { it.copy(error = "录音服务准备中，请稍后重试或检查权限") }
                // 考虑是否需要重置 isRecording 状态，如果决定在这里彻底中止
                // isRecording = false 
                // recordFile = null
                return
            }

            val mediaProjection = MainActivity.getMediaProjection()
            if (mediaProjection == null) {
                Log.e(TAG, "无法开始内录：媒体投影为空。请确保已授予屏幕录制权限。")
                _uiState.update { it.copy(error = "请先授予屏幕录制权限以进行内录") }
                // isRecording = false
                // recordFile = null
                return
            }
            
            val filePath = recordFile?.absolutePath
            if (filePath == null) {
                Log.e(TAG, "无法开始内录：录音文件路径为空")
                _uiState.update { it.copy(error = "录音文件路径无效") }
                isRecording = false // 确保在无法继续时重置状态
                return
            }

            // 步骤2: 华为设备首先尝试无障碍服务
            if (isHuawei) {
                Log.d(TAG, "检测到华为设备，首先尝试通过无障碍服务内录，文件路径: $filePath")
                if (tryAccessibilityInternalRecording(filePath)) {
                    Log.d(TAG, "通过无障碍服务为华为设备启动内录成功: $filePath")
                    // isRecording 应该在调用 startRecording() 时已设置为 true
                    // 无障碍服务成功启动，流程结束
                    return 
                }
                Log.d(TAG, "华为设备通过无障碍服务内录失败或未启用，将尝试使用 MediaProjection 方式内录。")
                // 无障碍服务失败，继续执行下面的 MediaProjection 逻辑
            }

            // 步骤3: 对于非华为设备，或者华为设备无障碍录音失败的情况
            // 使用 AudioCaptureService 进行基于 MediaProjection 的内录
            Log.d(TAG, "尝试通过 AudioCaptureService (MediaProjection) 进行内录，文件路径: $filePath")
            audioCaptureService?.setMediaProjection(mediaProjection) // 确保服务获取到最新的 MediaProjection

            // 调用服务端的 startRecording。
            // AudioCaptureService.startRecording 内部会判断 isHuaweiDevice 并调用 startHuaweiRecording
            val success = audioCaptureService?.startRecording(filePath) ?: false

            if (success) {
                Log.d(TAG, "AudioCaptureService 内录已成功开始: $filePath")
                // isRecording 应该在调用 startRecording() 时已设置为 true
            } else {
                Log.e(TAG, "AudioCaptureService 内录失败。")
                // 步骤4: 内录完全失败后的降级处理
                if (hasRecordPermission()) {
                    Log.d(TAG, "所有内录尝试失败，尝试降级到标准麦克风录音。")
                    fallbackToStandardRecording()
                } else {
                    _uiState.update { it.copy(error = "内录失败且无麦克风录音权限") }
                    isRecording = false
                    recordFile = null 
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "开始内录时发生异常: ${e.message}", e)
            // 统一的异常处理和降级
            if (hasRecordPermission()) {
                Log.d(TAG, "内录异常，尝试使用标准麦克风录音")
                try {
                    fallbackToStandardRecording()
                } catch (e2: Exception) {
                    Log.e(TAG, "降级到标准麦克风录音也失败: ${e2.message}", e2)
                    _uiState.update { it.copy(error = "录音启动失败: ${e2.message}") }
                    isRecording = false
                    recordFile = null
                }
            } else {
                _uiState.update { it.copy(error = "内录启动错误: ${e.message}") }
                isRecording = false
                recordFile = null
            }
        }
    }

    /**
     * 检查是否为华为设备
     */
    private fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        Log.d(TAG, "设备信息 - 制造商: $manufacturer, 品牌: $brand, 型号: ${Build.MODEL}")
        return manufacturer.equals("HUAWEI", ignoreCase = true) || 
               brand.equals("HUAWEI", ignoreCase = true) || 
               manufacturer.equals("华为", ignoreCase = true)
    }
    
    /**
     * 判断当前设备是否为华为P30 Pro
     */
    private fun isHuaweiP30Pro(): Boolean {
        val isHuawei = isHuaweiDevice()
        val model = Build.MODEL ?: ""
        Log.d(TAG, "检测到${if (isHuawei) "华为" else "非华为"}设备: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}")
        return isHuawei && (model.contains("VOG-AL10", ignoreCase = true) || 
                           model.contains("P30 Pro", ignoreCase = true) ||
                           model.contains("VOG", ignoreCase = true))
    }

    /**
     * 尝试使用华为专用方法实现内录功能
     */
    private fun tryHuaweiInternalRecording(): Boolean {
        try {
            Log.d(TAG, "尝试使用华为专用方法实现内录")
            
            // 确保我们有录音权限
            if (!hasRecordPermission()) {
                Log.e(TAG, "华为内录功能需要录音权限")
                return false
            }
            
            // 获取文件路径
            val filePath = recordFile?.absolutePath ?: run {
                Log.e(TAG, "华为内录失败：文件路径为空")
                return false
            }
            
            // 尝试使用无障碍服务方式
            if (isAccessibilityServiceEnabled()) {
                val accessibilityResult = tryAccessibilityInternalRecording(filePath)
                if (accessibilityResult) {
                    return true
                }
            }
            
            // 尝试使用标准麦克风录音作为备选方案
            return false
        } catch (e: Exception) {
            Log.e(TAG, "华为专用内录方法异常: ${e.message}", e)
            return false
        }
    }

    /**
     * 检查无障碍服务是否启用
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val context = getApplication<Application>()
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            
            // 检查是否有我们的无障碍服务在运行
            val serviceName = "com.wanderreads.ebook/.service.AudioCaptureAccessibilityService"
            
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            
            for (service in enabledServices) {
                val serviceId = service.id
                if (serviceId == serviceName) {
                    return true
                }
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "检查无障碍服务状态失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 尝试使用AccessibilityService方式实现内录
     */
    private fun tryAccessibilityInternalRecording(filePath: String): Boolean {
        try {
            Log.d(TAG, "尝试通过无障碍服务实现内录")
            
            // 获取无障碍服务实例
            val accessibilityService = com.wanderreads.ebook.service.AudioCaptureAccessibilityService.getInstance()
            
            if (accessibilityService == null) {
                Log.e(TAG, "无障碍服务实例为空，请确保已启用无障碍服务")
                
                // 引导用户开启无障碍服务
                showAccessibilityServiceGuide()
                return false
            }
            
            // 使用无障碍服务实现内录
            val result = accessibilityService.startRecording(filePath)
            
            if (result) {
                Log.d(TAG, "通过无障碍服务成功开始内录")
                return true
            } else {
                Log.e(TAG, "通过无障碍服务开始内录失败")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "通过无障碍服务实现内录时发生异常: ${e.message}", e)
            return false
        }
    }

    /**
     * 显示无障碍服务开启引导
     */
    private fun showAccessibilityServiceGuide() {
        // 更新UI状态，显示无障碍服务引导提示
        _uiState.update { 
            it.copy(
                showAccessibilityGuide = true
            ) 
        }
        
        // 获取Application上下文
        val context = getApplication<Application>().applicationContext
        
        try {
            // 创建意图，打开无障碍服务设置页面
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // 创建消息提示
            val message = context.getString(R.string.huawei_accessibility_guide_message)
            
            // 在主线程中显示对话框（需要使用Activity Context）
            val mainActivity = context.findMainActivity()
            if (mainActivity != null) {
                Handler(Looper.getMainLooper()).post {
                    android.app.AlertDialog.Builder(mainActivity)
                        .setTitle(context.getString(R.string.huawei_accessibility_guide_title))
                        .setMessage(message)
                        .setPositiveButton(context.getString(R.string.go_to_settings)) { dialog, _ -> 
                            dialog.dismiss()
                            context.startActivity(intent)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .setCancelable(false)
                        .show()
                }
            } else {
                // 未获取到Activity，直接打开设置
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "无法打开无障碍服务设置页面: ${e.message}", e)
            _uiState.update { 
                it.copy(
                    error = "请手动前往设置 > 无障碍 > 已安装的服务，开启\"漫阅内录服务\""
                ) 
            }
        }
    }

    /**
     * 关闭无障碍服务引导对话框
     */
    fun dismissAccessibilityGuide() {
        _uiState.update { it.copy(showAccessibilityGuide = false) }
    }

    /**
     * 手动切换录音状态
     * @return 切换后的录音状态(true: 正在录音, false: 未在录音)
     */
    fun toggleManualRecording(): Boolean {
        if (isRecording) {
            stopRecordingAndSave()
            return false
        } else {
            startRecording()
            return true
        }
    }

    /**
     * 获取当前录音状态
     * @return 当前是否正在录音
     */
    fun isRecordingActive(): Boolean {
        return isRecording
    }

    /**
     * 启动静音检测定时器
     */
    private fun startSilenceDetectionTimer() {
        // 取消之前的静音检测任务
        silenceDetectionJob?.cancel()
        
        // 记录当前时间为最后一次音频活动时间
        lastAudioActivity = System.currentTimeMillis()
        
        // 启动新的静音检测任务
        silenceDetectionJob = viewModelScope.launch {
            while (isRecording) {
                // 每秒检查一次
                delay(1000)
                
                // 检查是否有TTS活动
                val isTtsSpeaking = tts?.isSpeaking ?: false
                
                if (isTtsSpeaking) {
                    // 如果TTS正在说话，更新最后音频活动时间
                    lastAudioActivity = System.currentTimeMillis()
                } else {
                    // 计算静音时间
                    val silenceDuration = System.currentTimeMillis() - lastAudioActivity
                    
                    // 如果静音超过10秒，停止录音
                    if (silenceDuration > 10000) {
                        Log.d(TAG, "检测到超过10秒静音，自动停止录音")
                        stopRecordingAndSave()
                        break
                    }
                }
            }
        }
    }

    /**
     * 取消静音检测定时器
     */
    private fun cancelSilenceDetectionTimer() {
        silenceDetectionJob?.cancel()
        silenceDetectionJob = null
    }

    /**
     * 保存录音记录
     */
    private suspend fun saveRecording(startTime: Long) {
        viewModelScope.launch {
            try {
                if (recordFile == null || !recordFile!!.exists()) {
                    Log.e(TAG, "保存录音失败：记录文件为空或不存在")
                    _uiState.update { it.copy(error = "无法保存录音：文件不存在") }
                    return@launch
                }
                
                val filepath = recordFile!!.absolutePath
                val length = System.currentTimeMillis() - startTime
                val duration = TimeUnit.MILLISECONDS.toSeconds(length)
                
                // 构建录音记录
                val record = Record(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    voiceFilePath = filepath,
                    voiceLength = duration.toInt(),
                    addedDate = System.currentTimeMillis()
                )
                
                // 处理文件扩展名，确保匹配实际格式
                val isHuaweiP30Pro = isHuaweiP30Pro()
                if (isHuaweiP30Pro && !filepath.endsWith(".3gp", ignoreCase = true) && filepath.contains(".")) {
                    try {
                        // 如果是华为P30 Pro但文件不是3gp格式，尝试修改扩展名
                        val newPath = filepath.substringBeforeLast(".") + ".3gp"
                        val newFile = File(newPath)
                        
                        if (recordFile!!.renameTo(newFile)) {
                            Log.d(TAG, "文件已重命名为3GP格式: $newPath")
                            record.voiceFilePath = newPath
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "修改文件扩展名失败: ${e.message}")
                    }
                }
                
                // 保存到数据库
                recordRepository.saveRecord(record)
                
                // 更新UI状态
                loadBookRecords() // 重新加载录音列表
                Log.d(TAG, "录音记录已保存: ${record.voiceFilePath}, 时长: ${record.voiceLength}秒")
                
                // 显示成功提示
                _uiState.update { it.copy(message = "录音保存成功") }
            } catch (e: Exception) {
                Log.e(TAG, "保存录音记录时发生错误: ${e.message}", e)
                _uiState.update { it.copy(error = "保存录音失败: ${e.message}") }
            }
        }
    }

    /**
     * 停止媒体录音机
     */
    private fun stopMediaRecorder() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: Exception) {
                        Log.e(TAG, "停止媒体录音机失败: ${e.message}")
                    } finally {
                        reset()
                        release()
                    }
                }
                mediaRecorder = null
                Log.d(TAG, "媒体录音机已停止并释放")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止媒体录音机时出错: ${e.message}")
        }
    }
}

/**
 * 统一阅读器UI状态
 */
data class UnifiedReaderUiState(
    val book: Book? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val currentChapter: Int = 0,
    val totalChapters: Int = 0,
    val chapterTitle: String = "",
    val currentContent: ReaderContent? = null,
    val readingProgress: Float = 0f,
    val config: ReaderConfig = ReaderConfig(),
    val isSearching: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val showControls: Boolean = false,
    val records: List<Record> = emptyList(),
    val currentPlayingRecordId: String? = null,
    val showAccessibilityGuide: Boolean = false,
    val message: String? = null
)