package com.example.ebook

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * 电子书应用主类
 */
class EbookApplication : Application(), Configuration.Provider {
    
    companion object {
        private const val TAG = "EbookApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "应用启动，初始化...")
        
        // 初始化Tom Roush PdfBox库
        PDFBoxResourceLoader.init(applicationContext)
        Log.d(TAG, "PdfBox库已初始化")
    }
    
    /**
     * 提供WorkManager配置
     * 由于在AndroidManifest.xml中禁用了默认的WorkManagerInitializer，
     * 需要在Application类中实现Configuration.Provider接口并提供配置
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
} 