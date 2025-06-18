package org.soundsync.ebook.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.soundsync.ebook.util.TtsManager

/**
 * TTS通知按钮点击事件接收器
 * 处理通知栏中播放/暂停和停止按钮的点击事件
 */
class TtsNotificationReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "TtsNotificationReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到通知操作: ${intent.action}")
        
        val ttsManager = TtsManager.getInstance(context)
        
        when (intent.action) {
            "org.soundsync.ebook.PAUSE_TTS" -> {
                Log.d(TAG, "执行暂停TTS操作")
                ttsManager.pauseReading()
            }
            "org.soundsync.ebook.RESUME_TTS" -> {
                Log.d(TAG, "执行继续TTS操作")
                ttsManager.resumeReading()
            }
            "org.soundsync.ebook.STOP_TTS" -> {
                Log.d(TAG, "执行停止TTS操作")
                ttsManager.stopReading()
            }
        }
    }
} 