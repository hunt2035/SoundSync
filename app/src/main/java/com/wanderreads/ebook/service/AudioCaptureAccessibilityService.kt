package com.wanderreads.ebook.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference

/**
 * 无障碍服务用于实现华为设备的内录功能
 * 这种方法需要用户开启无障碍服务权限
 */
class AudioCaptureAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AudioCaptureA11yService"
        private var instance: WeakReference<AudioCaptureAccessibilityService>? = null
        
        fun getInstance(): AudioCaptureAccessibilityService? {
            return instance?.get()
        }
    }
    
    // 本地绑定器
    private val binder = LocalBinder()
    
    // 录音相关
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: File? = null
    private var recordingJob: Job? = null
    
    /**
     * 本地绑定器，用于与Activity通信
     */
    inner class LocalBinder : Binder() {
        fun getService(): AudioCaptureAccessibilityService = this@AudioCaptureAccessibilityService
    }
    
    // AccessibilityService中的onBind是final的，不能被重写
    // 以下方法用于获取binder，在服务连接时使用
    fun getLocalBinder(): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = WeakReference(this)
        Log.d(TAG, "无障碍服务已创建")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // 配置无障碍服务
        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        
        serviceInfo = info
        
        Log.d(TAG, "无障碍服务已连接并配置")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 监听系统事件，可用于控制录音
    }
    
    override fun onInterrupt() {
        // 服务中断时处理
        stopRecording()
    }
    
    /**
     * 开始录制内录
     */
    fun startRecording(outputFilePath: String): Boolean {
        if (isRecording) {
            Log.e(TAG, "已经在录音中")
            return false
        }
        
        try {
            outputFile = File(outputFilePath)
            Log.d(TAG, "准备创建内录文件: ${outputFile?.absolutePath}")
            
            // 初始化录音器
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            // 尝试多种华为特殊音源
            // 参考: https://developer.huawei.com/consumer/cn/forum/topic/0202480730432430050
            val audioSources = listOf(
                1998, // 华为特殊音源
                1999, // 另一种华为特殊音源
                MediaRecorder.AudioSource.REMOTE_SUBMIX, // REMOTE_SUBMIX (8)
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // VOICE_RECOGNITION (7)
                MediaRecorder.AudioSource.VOICE_COMMUNICATION // VOICE_COMMUNICATION (0)
            )
            
            var recordingStarted = false
            
            for (audioSource in audioSources) {
                if (recordingStarted) break
                
                try {
                    // 释放之前的mediaRecorder实例（如果有）
                    releaseRecorder(false)
                    
                    // 重新创建mediaRecorder
                    mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(this)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    
                    mediaRecorder?.apply {
                        setAudioSource(audioSource)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setOutputFile(outputFilePath)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(44100)
                        setAudioEncodingBitRate(128000)
                        
                        try {
                            prepare()
                            start()
                            
                            isRecording = true
                            recordingStarted = true
                            
                            Log.d(TAG, "通过无障碍服务成功开始内录 (音源: $audioSource): $outputFilePath")
                        } catch (e: Exception) {
                            Log.e(TAG, "尝试音源 $audioSource 失败: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "设置音源 $audioSource 失败: ${e.message}")
                }
            }
            
            if (recordingStarted) {
                // 启动协程处理录音数据
                recordingJob = CoroutineScope(Dispatchers.IO).launch {
                    // 监控录音状态
                    monitorRecording()
                }
                return true
            } else {
                Log.e(TAG, "所有尝试均失败，无法启动内录")
                releaseRecorder()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "无障碍服务内录失败: ${e.message}", e)
            releaseRecorder()
            return false
        }
    }
    
    /**
     * 监控录音状态
     */
    private suspend fun monitorRecording() {
        try {
            // 这里可以添加逻辑来确保录音正常进行
            // 例如检查文件大小是否增长
        } catch (e: Exception) {
            Log.e(TAG, "监控录音时出错: ${e.message}", e)
        }
    }
    
    /**
     * 停止录制
     */
    fun stopRecording(): String? {
        if (!isRecording) {
            return null
        }
        
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            
            mediaRecorder = null
            isRecording = false
            recordingJob?.cancel()
            recordingJob = null
            
            val filePath = outputFile?.absolutePath
            Log.d(TAG, "通过无障碍服务停止内录: $filePath")
            
            return filePath
        } catch (e: Exception) {
            Log.e(TAG, "停止无障碍服务内录失败: ${e.message}", e)
            releaseRecorder()
            return null
        }
    }
    
    /**
     * 释放录音资源
     */
    private fun releaseRecorder(resetIsRecording: Boolean = true) {
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
        
        if (resetIsRecording) {
            isRecording = false
        }
    }
    
    override fun onDestroy() {
        stopRecording()
        instance = null
        super.onDestroy()
    }
} 