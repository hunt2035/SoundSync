package com.wanderreads.ebook.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.wanderreads.ebook.MainActivity
import com.wanderreads.ebook.data.local.dataStore
import com.wanderreads.ebook.service.TtsService
import com.wanderreads.ebook.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 开机自启动接收器
 * 用于在设备重启后恢复应用服务
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "收到广播: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "android.intent.action.REBOOT") {
            
            // 使用线程池延迟启动，确保系统完全启动
            val executor = Executors.newSingleThreadScheduledExecutor()
            executor.schedule({
                try {
                    // 从DataStore读取配置
                    val backgroundRunningEnabled = runBlocking {
                        context.dataStore.data.map { preferences ->
                            preferences[SettingsViewModel.BACKGROUND_RUNNING_KEY] ?: false
                        }.first()
                    }
                    
                    if (backgroundRunningEnabled) {
                        Log.d(TAG, "启动TtsService")
                        
                        // 启动TtsService
                        val serviceIntent = Intent(context, TtsService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        
                        // 可选：启动MainActivity
                        val mainIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        // 取消下面的注释可以在开机时自动启动应用
                        // context.startActivity(mainIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启动服务失败: ${e.message}", e)
                }
            }, 30, TimeUnit.SECONDS)
        }
    }
} 