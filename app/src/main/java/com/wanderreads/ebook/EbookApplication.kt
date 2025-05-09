package com.wanderreads.ebook

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.wanderreads.ebook.data.local.AppDatabase
import com.wanderreads.ebook.data.repository.BookRepository
import com.wanderreads.ebook.data.repository.BookRepositoryImpl
import com.wanderreads.ebook.data.repository.RecordRepository
import com.wanderreads.ebook.data.repository.RecordRepositoryImpl
import com.wanderreads.ebook.util.WeChatShareUtil
import java.net.HttpURLConnection
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

/**
 * 电子书应用主类
 */
class EbookApplication : Application(), Configuration.Provider {
    
    companion object {
        private const val TAG = "EbookApplication"
        private var isGmsAvailable = false
        
        fun isGooglePlayServicesAvailable(): Boolean {
            return false // 始终返回 false，表示不依赖 Google Play 服务
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "应用启动，初始化...")
        
        // 初始化Tom Roush PdfBox库
        PDFBoxResourceLoader.init(applicationContext)
        Log.d(TAG, "PdfBox库已初始化")
        
        // 初始化WorkManager
        WorkManager.initialize(this, workManagerConfiguration)
        
        // 设置全局网络请求超时时间
        configureNetworkSettings()
        
        // 检测并记录设备信息
        logDeviceInfo()
        
        // 直接配置TLS，不依赖Google服务
        configureTLS()
        
        // 初始化微信SDK
        WeChatShareUtil.registerWeChatAPI(this)
        Log.d(TAG, "微信SDK已初始化")
    }
    
    /**
     * 记录设备信息
     */
    private fun logDeviceInfo() {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val version = Build.VERSION.SDK_INT
        val versionRelease = Build.VERSION.RELEASE
        
        Log.d(TAG, "设备信息: 制造商=$manufacturer, 型号=$model, Android版本=$versionRelease (API $version)")
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
    
    /**
     * 配置全局网络设置
     */
    private fun configureNetworkSettings() {
        // 设置默认的HTTP连接超时
        val defaultTimeoutMs = 60000 // 60秒
        System.setProperty("sun.net.client.defaultConnectTimeout", defaultTimeoutMs.toString())
        System.setProperty("sun.net.client.defaultReadTimeout", defaultTimeoutMs.toString())
        
        // 配置HttpURLConnection默认超时
        try {
            val originalFactory = HttpURLConnection.getFollowRedirects()
            HttpURLConnection.setFollowRedirects(true)
            // 注意：这不会应用于已经创建的连接，仅对新创建的连接生效
        } catch (e: Exception) {
            Log.e(TAG, "配置网络请求默认值失败", e)
        }
    }
    
    /**
     * 配置TLS安全设置
     */
    private fun configureTLS() {
        try {
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, null, null)
            SSLContext.setDefault(sslContext)
            Log.d(TAG, "TLS 1.2已成功配置")
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "无法配置TLS 1.2，设备可能不支持", e)
        } catch (e: Exception) {
            Log.e(TAG, "配置TLS失败", e)
        }
    }

    class AppDependencies(
        val bookRepository: BookRepository,
        val recordRepository: RecordRepository
    )
    
    fun provideDependencies(): AppDependencies {
        return AppDependencies(
            bookRepository = BookRepositoryImpl(
                context = applicationContext,
                bookDao = AppDatabase.getInstance(applicationContext).bookDao()
            ),
            recordRepository = RecordRepositoryImpl(
                context = applicationContext,
                recordDao = AppDatabase.getInstance(applicationContext).recordDao()
            )
        )
    }
} 