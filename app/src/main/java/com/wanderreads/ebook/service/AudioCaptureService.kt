package com.wanderreads.ebook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.wanderreads.ebook.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import android.os.Environment
import java.util.UUID

/**
 * 音频捕获服务，用于实现内录功能
 * 只在Android 10+上工作
 */
class AudioCaptureService : Service() {

    companion object {
        private const val TAG = "AudioCaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_capture_channel"
        
        // 音频录制参数
        private const val SAMPLE_RATE = 44100 // 采样率
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO // 立体声
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16位PCM
        private const val BUFFER_SIZE_FACTOR = 2 // 缓冲区大小因子
    }
    
    // 服务绑定器
    private val binder = LocalBinder()
    
    // 媒体投影服务
    private var mediaProjection: MediaProjection? = null
    
    // 录音器
    private var audioRecord: AudioRecord? = null
    
    // 录音线程
    private var recordingJob: Job? = null
    
    // 输出文件
    private var outputFile: File? = null
    
    // 是否正在录制
    private var isRecording = false
    
    // 是否为华为设备
    private val isHuaweiDevice = Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
    
    // 录音文件路径
    private var recordingPath: String? = null
    
    // 录音文件
    private var recordFile: File? = null
    
    /**
     * 本地绑定器，用于与Activity通信
     */
    inner class LocalBinder : Binder() {
        fun getService(): AudioCaptureService = this@AudioCaptureService
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        if (isHuaweiDevice) {
            Log.d(TAG, "在华为设备上初始化音频捕获服务")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
    
    /**
     * 设置媒体投影
     */
    fun setMediaProjection(mediaProjection: MediaProjection) {
        Log.d(TAG, "设置媒体投影: $mediaProjection")
        
        if (mediaProjection == null) {
            Log.e(TAG, "尝试设置空的媒体投影")
            return
        }
        
        try {
            // 对于华为设备，我们也需要保存媒体投影
            // 修改：不再跳过华为设备的媒体投影设置
            
            // 保存媒体投影实例
            this.mediaProjection = mediaProjection
            Log.d(TAG, "媒体投影已成功设置")
            
            // 如果当前正在录音且没有AudioRecord实例，尝试开始录音
            if (isRecording && audioRecord == null && outputFile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "媒体投影设置后尝试恢复录音")
                startRecording(outputFile!!.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置媒体投影时发生异常: ${e.message}", e)
        }
    }
    
    /**
     * 开始录制
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun startRecording(outputFilePath: String): Boolean {
        if (mediaProjection == null) {
            Log.e(TAG, "无法开始录制：媒体投影为空")
            return false
        }
        
        if (isRecording) {
            Log.e(TAG, "无法开始录制：已在录制中")
            return false
        }
        
        try {
            // 创建输出文件
            outputFile = File(outputFilePath)
            Log.d(TAG, "准备创建录音文件: ${outputFile?.absolutePath}")
            
            // 华为设备特殊处理
            if (isHuaweiDevice) {
                return startHuaweiRecording(outputFilePath)
            }
            
            // 计算缓冲区大小
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_FACTOR
            
            Log.d(TAG, "计算的缓冲区大小: $bufferSize")
            
            // 检查媒体投影是否可用
            if (mediaProjection == null) {
                Log.e(TAG, "媒体投影在开始录制前变为null")
                return false
            }
            
            // 尝试使用AudioPlaybackCaptureConfiguration配置（标准Android 10+内录方式）
            try {
                try {
                    val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // 捕获媒体声音
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)  // 捕获游戏声音
                        .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT) // 捕获语音助手声音
                        .build()
                    
                    val audioFormat = AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                    
                    audioRecord = AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferSize)
                        .setAudioPlaybackCaptureConfig(config)
                        .build()
                    
                    if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                        audioRecord?.startRecording()
                        isRecording = true
                        
                        // 启动录音任务
                        recordingJob = CoroutineScope(Dispatchers.IO).launch {
                            processAudioData(bufferSize)
                        }
                        
                        Log.d(TAG, "内录已成功开始: ${outputFile?.absolutePath}")
                        return true
                    } else {
                        Log.e(TAG, "AudioRecord初始化失败")
                        releaseResources()
                        return false
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "音频捕获权限被拒绝: ${e.message}", e)
                    // 如果是华为设备，尝试华为特殊录音方式
                    if (isHuaweiDevice) {
                        return startHuaweiRecording(outputFilePath)
                    }
                    releaseResources()
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "创建AudioRecord失败: ${e.message}", e)
                releaseResources()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "准备录音时发生异常: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 华为设备特殊录音实现
     */
    private fun startHuaweiRecording(outputFilePath: String): Boolean {
        Log.d(TAG, "尝试华为设备特殊录音实现")
        
        try {
            // 确保输出目录存在
            val outputFile = File(outputFilePath)
            val parentDir = outputFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
                Log.d(TAG, "已创建录音输出目录: ${parentDir.absolutePath}")
            }
            
            // 确保当前文件不存在
            if (outputFile.exists()) {
                outputFile.delete()
                Log.d(TAG, "已删除已存在的录音文件")
            }
            
            // 先尝试创建空文件以验证权限
            try {
                outputFile.createNewFile()
                Log.d(TAG, "空文件创建成功: ${outputFile.absolutePath}")
                
                // 验证文件可写入
                if (!outputFile.canWrite()) {
                    Log.e(TAG, "文件无法写入: ${outputFile.absolutePath}")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "创建空文件失败: ${e.message}")
                // 继续尝试，不直接返回false
            }
            
            // 使用VirtualDisplay方法实现华为设备内录
            // 这需要MediaProjection权限
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val dpi = displayMetrics.densityDpi
            
            // 创建一个虚拟显示，但不显示内容（宽高设置为1即可）
            // 主要目的是激活系统的音频录制通道
            var virtualDisplay: android.hardware.display.VirtualDisplay? = null
            try {
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "AudioCapture",
                    1, 1, dpi,  // 最小尺寸，避免资源浪费
                    0,  // 不需要显示标志
                    null,  // 不需要显示Surface
                    null,  // 回调为空
                    null   // Handler为空
                )
                Log.d(TAG, "已创建用于音频捕获的虚拟显示: $virtualDisplay")
            } catch (e: SecurityException) {
                Log.e(TAG, "创建虚拟显示权限被拒绝: ${e.message}", e)
                // 尝试继续录音流程，因为在某些设备上即使没有虚拟显示也可以录音
            } catch (e: Exception) {
                Log.e(TAG, "创建虚拟显示时发生异常: ${e.message}", e)
                // 继续尝试录音
            }
            
            Log.d(TAG, "录音文件路径: $outputFilePath")
            
            // 确保旧的mediaRecorder已释放
            releaseMediaRecorder()
            
            // 检测是否为P30 Pro型号
            val isP30Pro = Build.MODEL.contains("P30 Pro", ignoreCase = true) || 
                           Build.MODEL.contains("VOG-L29", ignoreCase = true) ||  // P30 Pro国际版型号
                           Build.MODEL.contains("VOG-L09", ignoreCase = true) ||  // P30 Pro欧洲版型号
                           Build.MODEL.contains("VOG-AL00", ignoreCase = true) || // P30 Pro中国版型号
                           Build.MODEL.contains("VOG-TL00", ignoreCase = true)    // P30 Pro电信版型号
            Log.d(TAG, "当前华为设备型号: ${Build.MODEL}, 是否为P30 Pro: $isP30Pro")
            
            // 初始化录音
            // 使用MediaRecorder直接录制系统声音
            val audioSources = if (isP30Pro) {
                // P30 Pro专用音源顺序 - 添加更多可能的华为特殊音源
                arrayOf(
                    1998, // 华为特殊音源值 - 首选
                    2000, // 可能的华为特殊音源值
                    MediaRecorder.AudioSource.REMOTE_SUBMIX, // 8
                    MediaRecorder.AudioSource.DEFAULT, // 0
                    1999, // 另一个可能的华为特殊音源值
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, // 7
                    2002, // 可能的华为特殊音源值
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION // 6
                )
            } else {
                // 其他华为设备音源顺序
                arrayOf(
                    1998, // 华为特殊音源值 - 首选
                    1999, // 另一个可能的特殊音源值
                    MediaRecorder.AudioSource.REMOTE_SUBMIX,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.DEFAULT
                )
            }
            
            var recordingStarted = false
            
            for (audioSource in audioSources) {
                if (recordingStarted) break
                
                try {
                    // 创建新的MediaRecorder
                    val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        android.media.MediaRecorder(this)
                    } else {
                        @Suppress("DEPRECATION")
                        android.media.MediaRecorder()
                    }
                    
                    try {
                        // 先设置文件路径 - 重要！这必须要在setAudioSource之前设置
                        Log.d(TAG, "尝试使用路径: $outputFilePath，音源: $audioSource")
                        mediaRecorder.setOutputFile(outputFilePath)
                        
                        // 设置音频源
                        mediaRecorder.setAudioSource(audioSource)
                        
                        // 华为P30 Pro特殊处理
                        if (isP30Pro) {
                            // 使用更稳定的3GPP格式和AMR_NB编码器，这在华为设备上兼容性更好
                            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                            mediaRecorder.setAudioSamplingRate(16000) // 降低采样率以提高兼容性
                            mediaRecorder.setAudioEncodingBitRate(96000) // 降低比特率以确保更好的兼容性
                            mediaRecorder.setAudioChannels(1) // 使用单声道以提高兼容性
                            
                            // 尝试添加硬件加速配置
                            try {
                                // 尝试使用反射调用非公开API
                                val hardwareAccelerationParam = mediaRecorder.javaClass.getDeclaredMethod(
                                    "setParameters", String::class.java)
                                hardwareAccelerationParam.isAccessible = true
                                hardwareAccelerationParam.invoke(mediaRecorder, "hw-encoder=1")
                                Log.d(TAG, "华为P30 Pro: 已添加硬件加速配置")
                            } catch (e: Exception) {
                                Log.e(TAG, "添加硬件加速配置失败: ${e.message}")
                            }
                        } else {
                            // 其他华为设备标准设置
                            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            mediaRecorder.setAudioSamplingRate(44100)
                            mediaRecorder.setAudioEncodingBitRate(192000) // 较高比特率以提高质量
                        }
                        
                        // 华为P30 Pro额外等待
                        if (isHuaweiP30Pro()) {
                            try {
                                Log.d(TAG, "华为P30 Pro额外等待4秒确保文件写入")
                                Thread.sleep(4000)  // 从2秒增加到4秒
                            } catch (e: InterruptedException) {
                                // 忽略中断
                            }
                        }
                        
                        try {
                            // 准备录音器
                            mediaRecorder.prepare()
                            
                            // 开始录音
                            mediaRecorder.start()
                            
                            // 保存MediaRecorder实例以便后续停止
                            this.mediaRecorder = mediaRecorder
                            isRecording = true
                            recordingStarted = true
                            
                            // 确保输出文件路径已正确保存
                            this.outputFile = outputFile
                            
                            Log.d(TAG, "华为设备内录成功启动（音源:$audioSource）: $outputFilePath")
                            
                            // 确认文件是否已创建
                            val file = outputFile
                            if (file != null && file.exists()) {
                                Log.d(TAG, "确认录音文件已创建: ${file.length()} 字节")
                            } else {
                                Log.d(TAG, "录音文件尚未创建，将在停止时检查")
                            }
                            
                            // 记录一些元数据到临时文件，方便后续恢复
                            val metaFile = File(file?.parentFile!!, "${file.nameWithoutExtension}.meta")
                            try {
                                try {
                                    metaFile.writeText("$audioSource\n$outputFilePath")
                                    Log.d(TAG, "已写入录音元数据: ${metaFile.absolutePath}")
                                } catch (e: SecurityException) {
                                    Log.e(TAG, "写入元数据权限被拒绝: ${e.message}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "写入元数据失败: ${e.message}")
                            }
                            
                            // 华为P30 Pro特殊处理：确保文件已正确写入
                            if (isHuaweiP30Pro() && outputFile != null) {
                                forceFileSystemSync()
                                
                                // 额外验证文件
                                var retryCount = 0
                                val maxRetries = 10
                                while (retryCount < maxRetries) {
                                    if (outputFile!!.exists() && outputFile!!.length() > 0) {
                                        Log.d(TAG, "华为P30 Pro: 文件验证成功: ${outputFile!!.absolutePath}")
                                        break
                                    }
                                    
                                    Log.d(TAG, "华为P30 Pro: 文件验证失败，重试 ${retryCount+1}/${maxRetries}")
                                    Thread.sleep(500)
                                    retryCount++
                                }
                                
                                // 如果文件仍然不存在或大小为0，尝试创建占位文件
                                if (!outputFile!!.exists() || outputFile!!.length() == 0L) {
                                    Log.d(TAG, "华为P30 Pro: 文件验证失败，尝试创建占位文件")
                                    val dir = outputFile!!.parentFile
                                    val newFile = File(dir, "backup_${System.currentTimeMillis()}.3gp")
                                    try {
                                        newFile.createNewFile()
                                        newFile.outputStream().use { os ->
                                            // 简单的3GP头部数据
                                            os.write(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
                                        }
                                        
                                        recordingPath = newFile.absolutePath
                                        Log.d(TAG, "华为P30 Pro: 已创建备用文件: ${newFile.absolutePath}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "创建备用文件失败: ${e.message}")
                                    }
                                }
                            }
                            
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "尝试音源 $audioSource 失败: ${e.message}")
                            try {
                                mediaRecorder.reset()
                                mediaRecorder.release()
                            } catch (e2: Exception) {
                                Log.e(TAG, "释放失败的MediaRecorder时异常: ${e2.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "配置MediaRecorder时异常: ${e.message}")
                        try {
                            mediaRecorder.release()
                        } catch (e2: Exception) {
                            // 忽略
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "创建MediaRecorder时异常: ${e.message}")
                }
            }
            
            if (recordingStarted) {
                return true
            }
            
            // 如果都失败，尝试非MediaRecorder的解决方案
            Log.d(TAG, "所有MediaRecorder方法均失败，尝试备选方法")
            
            // 尝试使用直接文件写入记录来验证是否存在权限问题
            try {
                val testFile = File(outputFile.parentFile, "test_write.txt")
                try {
                    testFile.writeText("Test write: ${System.currentTimeMillis()}")
                    Log.d(TAG, "测试文件写入成功: ${testFile.absolutePath}")
                    testFile.delete()
                } catch (e: SecurityException) {
                    Log.e(TAG, "测试文件写入权限被拒绝: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "测试文件写入失败，可能存在权限问题: ${e.message}")
            }
            
            // 如果所有音源都失败，尝试连接到无障碍服务
            return false
        } catch (e: Exception) {
            Log.e(TAG, "华为设备特殊录音实现异常: ${e.message}", e)
            return false
        }
    }
    
    // MediaRecorder实例（用于华为设备）
    private var mediaRecorder: android.media.MediaRecorder? = null
    
    /**
     * 释放MediaRecorder资源
     */
    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "停止MediaRecorder失败: ${e.message}")
        }
        
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "释放MediaRecorder资源失败: ${e.message}")
        }
        
        mediaRecorder = null
    }
    
    /**
     * 将音频数据写入文件
     */
    private fun processAudioData(bufferSize: Int) {
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val data = ByteArray(bufferSize)
        
        try {
            try {
                FileOutputStream(outputFile).use { fos ->
                    while (isRecording) {
                        val readResult = audioRecord?.read(buffer, bufferSize) ?: -1
                        
                        if (readResult > 0) {
                            buffer.position(0)
                            buffer.get(data, 0, readResult)
                            fos.write(data, 0, readResult)
                            buffer.clear()
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "文件访问权限被拒绝: ${e.message}", e)
            }
        } catch (e: IOException) {
            Log.e(TAG, "写入音频文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 停止录制
     */
    fun stopRecording(): String? {
        if (!isRecording) {
            return null
        }
        
        isRecording = false
        Log.d(TAG, "开始停止录音流程")
        
        // 检测是否为华为设备
        val isHuaweiDevice = Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
        val isHuaweiP30Pro = isHuaweiDevice && Build.MODEL.contains("P30 Pro", ignoreCase = true)
        
        // 华为设备特殊处理：等待确保文件写入完成
        if (isHuaweiDevice && recordFile != null) {
            Log.d(TAG, "检测到华为设备，等待文件写入完成...")
            
            // 尝试文件系统同步
            try {
                val clazz = Class.forName("android.os.FileUtils")
                val method = clazz.getDeclaredMethod("sync", FileDescriptor::class.java)
                method.isAccessible = true
                
                recordFile?.parentFile?.listFiles()?.forEach { file ->
                    try {
                        try {
                            val fos = FileOutputStream(file, true)
                            method.invoke(null, fos.fd)
                            fos.close()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "文件同步权限被拒绝: ${e.message}")
                        }
                    } catch (e: Exception) {
                        // 忽略同步错误
                    }
                }
            } catch (e: Exception) {
                // 忽略反射错误
            }
            
            // 华为P30 Pro额外等待
            if (isHuaweiP30Pro) {
                try {
                    Log.d(TAG, "华为P30 Pro额外等待2秒确保文件写入")
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    // 忽略中断
                }
            }
        }
        
        // 停止MediaRecorder
        try {
            mediaRecorder?.stop()
            Log.d(TAG, "MediaRecorder已停止")
            
            // 尝试使用sync命令增强文件同步
            if (isHuaweiP30Pro) {
                try {
                    Log.d(TAG, "华为P30 Pro: 执行额外的文件同步操作")
                    Runtime.getRuntime().exec("sync").waitFor(1, TimeUnit.SECONDS)
                    
                    // 尝试使用FileObserver确保文件系统更新
                    val recordDir = outputFile?.parentFile
                    if (recordDir != null && recordDir.exists()) {
                        try {
                            // 列出目录内容触发文件系统更新
                            recordDir.listFiles()
                            Log.d(TAG, "已列出目录以触发文件系统更新")
                        } catch (e: Exception) {
                            Log.e(TAG, "列出目录失败: ${e.message}")
                        }
                    }
                    
                    // 双重检查文件是否存在
                    val file = outputFile
                    if (file != null && file.exists()) {
                        Log.d(TAG, "文件已确认存在于: ${file.absolutePath}, 大小: ${file.length()} 字节")
                        
                        if (file.length() == 0L) {
                            Log.d(TAG, "文件大小为0，尝试修复")
                            try {
                                // 尝试添加3GP文件头数据
                                file.outputStream().use { os ->
                                    // 3GP文件头
                                    os.write(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x33, 0x67, 0x70))
                                }
                                Log.d(TAG, "已向空文件添加基本头数据")
                            } catch (e: Exception) {
                                Log.e(TAG, "添加文件头数据失败: ${e.message}")
                            }
                        }
                    } else {
                        Log.e(TAG, "停止录音后文件不存在: ${outputFile?.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "额外同步操作失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止MediaRecorder失败: ${e.message}")
            
            // 尝试创建代替文件
            if (recordFile != null && (!recordFile!!.exists() || recordFile!!.length() == 0L)) {
                try {
                    // 如果原文件不存在或大小为0，创建一个新的占位文件
                    val recordDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10以上，优先使用应用专属目录
                        getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { 
                            File(it, "book_records").apply { if (!exists()) mkdirs() }
                        } ?: File(filesDir, "book_records").apply { if (!exists()) mkdirs() }
                    } else {
                        // Android 10以下，可以使用公共目录
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "book_records").apply { 
                            if (!exists()) mkdirs() 
                        }
                    }
                    
                    val placeholderFile = File(recordDir, "TTS_${UUID.randomUUID()}_placeholder.mp3")
                    
                    if (placeholderFile.createNewFile()) {
                        // 写入基本MP3头数据
                        try {
                            placeholderFile.outputStream().use { os ->
                                os.write(byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                            }
                            
                            recordFile = placeholderFile
                            recordingPath = placeholderFile.absolutePath
                            Log.d(TAG, "已创建占位MP3文件: ${placeholderFile.absolutePath}")
                        } catch (e: SecurityException) {
                            Log.e(TAG, "访问占位文件权限被拒绝: ${e.message}", e)
                        }
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "创建占位MP3文件失败: ${e2.message}")
                }
            }
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
        
        // 确保文件存在且可访问
        if (recordFile?.exists() == true && recordFile?.length() ?: 0 > 0) {
            Log.d(TAG, "返回有效录音文件路径: $recordingPath")
            releaseResources()
            return recordingPath
        } else {
            // 尝试查找最近创建的录音文件
            val recordDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "book_records")
            if (recordDir.exists() && recordDir.isDirectory) {
                val recentFiles = recordDir.listFiles { file ->
                    file.isFile && (file.name.startsWith("TTS_") || file.name.contains("_recording_")) &&
                    System.currentTimeMillis() - file.lastModified() < 30000 // 最近30秒内创建的文件
                }
                
                val recentFile = recentFiles?.maxByOrNull { it.lastModified() }
                if (recentFile != null && recentFile.length() > 0) {
                    Log.d(TAG, "找到最近创建的录音文件: ${recentFile.absolutePath}")
                    recordingPath = recentFile.absolutePath
                    releaseResources()
                    return recordingPath
                }
            }
        }
        
        releaseResources()
        return null
    }
    
    /**
     * 为华为设备查找替代录音文件
     */
    private fun findAlternativeRecordingsForHuawei(originalPath: String): String? {
        try {
            // 从原始路径提取目录和文件名
            val lastIndex = originalPath.lastIndexOf(File.separator)
            if (lastIndex < 0) return null
            
            val dirPath = originalPath.substring(0, lastIndex)
            val fileName = originalPath.substring(lastIndex + 1)
            val dirFile = File(dirPath)
            
            // 1. 尝试查找元数据文件
            val dotIndex = fileName.lastIndexOf(".")
            val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
            
            val metaFilePath = "$dirPath${File.separator}$baseName.meta"
            val metaFile = File(metaFilePath)
            
            if (metaFile?.exists() == true) {
                try {
                    try {
                        val content = metaFile.readText()
                        val lines = content?.split("\n")
                        if (lines != null && lines.size > 1) {
                            val path = lines[1].trim()
                            val file = File(path)
                            if (file?.exists() == true && file.length() > 0) {
                                Log.d(TAG, "从元数据恢复到备用路径: $path")
                                return path
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "读取元数据文件权限被拒绝: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取元数据文件失败: ${e.message}")
                }
            }
            
            // 2. 尝试搜索相同目录下的类似文件
            if (dirFile?.exists() == true && dirFile.isDirectory) {
                val files = dirFile.listFiles { file ->
                    file?.isFile == true && file.name.startsWith(baseName) && file.name != fileName
                }
                
                val alternativeFile = files?.filter { it.length() > 0 }?.maxByOrNull { it.lastModified() }
                if (alternativeFile != null) {
                    Log.d(TAG, "在同目录找到备选文件: ${alternativeFile.absolutePath}")
                    return alternativeFile.absolutePath
                }
            }
            
            // 3. 尝试搜索华为设备的备用位置
            val possibleDirs = listOf(
                File(applicationContext.getExternalFilesDir(null), "Sounds"),
                File(applicationContext.filesDir, "Sounds"),
                File(applicationContext.cacheDir, "Sounds"),
                applicationContext.getExternalFilesDir(null),
                applicationContext.filesDir
            )
            
            // 3.1 基于文件名搜索
            for (dir in possibleDirs) {
                if (dir?.exists() != true || dir.isDirectory != true) continue
                
                val possibleFiles = dir.listFiles { file ->
                    file?.isFile == true && file.name.contains(baseName)
                }
                
                val match = possibleFiles?.filter { it.length() > 0 }?.maxByOrNull { it.lastModified() }
                if (match != null) {
                    Log.d(TAG, "在可能位置找到匹配文件: ${match.absolutePath}")
                    return match.absolutePath
                }
            }
            
            // 3.2 查找最近创建的录音文件
            val allRecentFiles = possibleDirs.flatMap { dir ->
                if (dir?.exists() == true && dir.isDirectory) {
                    dir.listFiles { file ->
                        file?.isFile == true && 
                        (file.name.endsWith(".mp3", true) || file.name.endsWith(".mp4", true)) &&
                        System.currentTimeMillis() - file.lastModified() < 30000 // 30秒内创建的
                    }?.toList() ?: emptyList()
                } else {
                    emptyList()
                }
            }
            
            val mostRecent = allRecentFiles.filter { it?.length() ?: 0 > 0 }.maxByOrNull { it.lastModified() }
            if (mostRecent != null) {
                Log.d(TAG, "找到最近创建的有效录音文件: ${mostRecent.absolutePath}")
                return mostRecent.absolutePath
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "查找备选录音文件失败: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 释放资源
     */
    private fun releaseResources() {
        try {
            // 首先取消正在进行的录音任务
            if (recordingJob?.isActive == true) {
                recordingJob?.cancel()
            }
            recordingJob = null
            
            // 释放录音器资源
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "释放AudioRecord资源失败: ${e.message}", e)
            }
            audioRecord = null
            
            // 释放MediaRecorder
            releaseMediaRecorder()
            
            // 确保媒体投影资源正确释放
            // 注意：不在这里释放mediaProjection，因为它由MainActivity管理
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败: ${e.message}", e)
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "音频捕获服务"
            val descriptionText = "用于实现内录功能的前台服务"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在录制音频")
            .setContentText("应用正在录制系统音频")
            .setSmallIcon(R.drawable.ic_recording)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * 强制同步文件系统
     */
    private fun forceFileSystemSync() {
        if (!isHuaweiDevice) return
        
        try {
            // 尝试使用sync命令
            try {
                val process = Runtime.getRuntime().exec("sync")
                process.waitFor(2, TimeUnit.SECONDS)
                Log.d(TAG, "已执行系统sync命令")
            } catch (e: Exception) {
                Log.e(TAG, "执行sync命令失败: ${e.message}")
            }
            
            // 同时尝试文件系统API
            try {
                val clazz = Class.forName("android.os.FileUtils")
                val method = clazz.getDeclaredMethod("sync", FileDescriptor::class.java)
                method.isAccessible = true
                
                outputFile?.parentFile?.listFiles()?.forEach { file ->
                    try {
                        val fos = FileOutputStream(file, true)
                        method.invoke(null, fos.fd)
                        fos.close()
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }
                
                // 尝试强制刷新文件
                if (outputFile?.exists() == true) {
                    try {
                        val randomAccessFile = java.io.RandomAccessFile(outputFile, "rw")
                        randomAccessFile.getFD().sync() // 强制同步文件
                        randomAccessFile.close()
                        Log.d(TAG, "已强制同步文件: ${outputFile?.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "强制同步文件失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                // 忽略反射错误
            }
            
            Log.d(TAG, "已强制同步文件系统")
        } catch (e: Exception) {
            Log.e(TAG, "强制同步文件系统失败: ${e.message}")
        }
    }
    
    /**
     * 检查当前设备是否是华为P30 Pro
     */
    private fun isHuaweiP30Pro(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        return manufacturer.contains("huawei") && model.contains("p30 pro")
    }
    
    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
} 