package com.wanderreads.ebook

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import com.wanderreads.ebook.ui.theme.EbookTheme
import com.wanderreads.ebook.ui.navigation.AppNavigation
import android.os.Environment

/**
 * 主Activity
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val MEDIA_PROJECTION_REQUEST_CODE = 100
        
        // 单例实例 - 使用弱引用避免内存泄漏
        private var instance: WeakReference<MainActivity>? = null
        
        /**
         * 获取MainActivity实例
         */
        fun getInstance(): MainActivity? {
            return instance?.get()
        }
        
        /**
         * 获取媒体投影实例
         */
        private var mediaProjection: MediaProjection? = null
        
        /**
         * 获取媒体投影实例 - 公开方法
         */
        fun getMediaProjection(): MediaProjection? {
            return mediaProjection
        }
    }
    
    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "权限已被授予")
        } else {
            Toast.makeText(this, "应用需要相应权限才能正常运行", Toast.LENGTH_LONG).show()
        }
    }
    
    // 多权限请求
    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach { entry ->
            Log.d(TAG, "权限 ${entry.key} 授予状态: ${entry.value}")
            if (!entry.value) {
                allGranted = false
            }
        }
        
        if (!allGranted) {
            Toast.makeText(this, "应用需要所有请求的权限才能提供完整功能", Toast.LENGTH_LONG).show()
        }
    }
    
    // 媒体投影管理器
    private var mediaProjectionManager: MediaProjectionManager? = null
    
    // 媒体投影结果
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.d(TAG, "媒体投影权限已被授予")
            try {
                // 获取媒体投影实例
                mediaProjection = mediaProjectionManager?.getMediaProjection(result.resultCode, result.data!!)
                // 检查设备制造商
                val manufacturer = Build.MANUFACTURER
                val model = Build.MODEL
                val isHuaweiDevice = manufacturer.equals("HUAWEI", ignoreCase = true)
                Log.d(TAG, "设备信息 - 制造商: $manufacturer, 型号: $model")
                if (isHuaweiDevice) {
                    if (model.contains("P30 Pro", ignoreCase = true)) {
                        Log.d(TAG, "检测到华为P30 Pro设备，应用特殊录音处理")
                        Toast.makeText(this, "已获取华为P30 Pro屏幕录制权限，将使用特殊方法进行内录", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d(TAG, "华为设备已获取媒体投影权限，将使用特殊方法实现内录")
                        Toast.makeText(this, "华为设备已获取录屏权限，将尝试内录", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理媒体投影权限失败: ${e.message}", e)
                Toast.makeText(this, "处理媒体投影权限失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.e(TAG, "媒体投影权限被拒绝")
            Toast.makeText(this, "需要屏幕录制权限才能实现内录功能", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 存储当前实例
        instance = WeakReference(this)
        Log.d(TAG, "MainActivity实例已创建和存储")
        
        // 设置全局未捕获异常处理器
        setupExceptionHandler()
        
        super.onCreate(savedInstanceState)
        
        // 检查并请求必要权限
        checkAndRequestPermissions()
        
        // 初始化媒体投影管理器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        }
        
        try {
            enableEdgeToEdge()
            setContent {
                EbookTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "应用初始化失败: ${e.message}", e)
            Toast.makeText(this, "应用初始化失败，请重新启动应用", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 更新单例引用（确保引用的是最新的实例）
        instance = WeakReference(this)
        Log.d(TAG, "MainActivity实例已在onResume中更新")
    }
    
    /**
     * 请求媒体投影权限
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun requestMediaProjectionPermission() {
        // 检查设备制造商和型号
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val isHuaweiDevice = manufacturer.equals("HUAWEI", ignoreCase = true)
        val isHuaweiP30Pro = isHuaweiDevice && model.contains("P30 Pro", ignoreCase = true)
        
        Log.d(TAG, "请求媒体投影权限，设备制造商: $manufacturer, 型号: $model")
        
        if (mediaProjection != null) {
            // 已有权限，直接启动服务
            Log.d(TAG, "已有媒体投影权限，直接启动服务")
            return
        }
        
        try {
            Log.d(TAG, "请求媒体投影权限")
            
            // 初始化媒体投影管理器（如果尚未初始化）
            if (mediaProjectionManager == null) {
                mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            }
            
            mediaProjectionManager?.let {
                val intent = it.createScreenCaptureIntent()
                
                // 对华为设备显示提示
                if (isHuaweiDevice) {
                    if (isHuaweiP30Pro) {
                        Toast.makeText(this, "华为P30 Pro需要允许屏幕录制权限以实现录音功能", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "华为设备请允许屏幕录制权限以实现内录功能", Toast.LENGTH_LONG).show()
                    }
                }
                
                mediaProjectionLauncher.launch(intent)
            } ?: run {
                Log.e(TAG, "媒体投影管理器为空")
                Toast.makeText(this, "无法创建媒体投影管理器", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求媒体投影权限失败: ${e.message}", e)
            Toast.makeText(this, "请求录屏权限失败，无法实现内录功能", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupExceptionHandler() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "未捕获的异常: ${throwable.message}", throwable)
            try {
                Toast.makeText(
                    applicationContext,
                    "应用发生错误，即将关闭",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                // 忽略在显示Toast时可能发生的异常
            }
            
            // 调用原始的异常处理器
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }
    
    /**
     * 检查并请求必要权限
     */
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)，使用MANAGE_EXTERNAL_STORAGE权限
            val hasStoragePermission = Environment.isExternalStorageManager()
            if (!hasStoragePermission) {
                Log.d(TAG, "需要请求 MANAGE_EXTERNAL_STORAGE 权限")
                // 避免在应用启动时就直接弹出权限请求，可能导致崩溃
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.postDelayed({
                    try {
                // 需要用户手动授予所有文件访问权限
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = android.net.Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                    Toast.makeText(this, "请授予应用访问所有文件的权限，以便保存电子书到Documents目录", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                        Log.e(TAG, "跳转到特定应用权限设置失败: ${e.message}")
                        try {
                    // 如果直接跳转失败，则跳转到普通设置页面
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                    Toast.makeText(this, "请在设置中允许应用访问所有文件", Toast.LENGTH_LONG).show()
                        } catch (e2: Exception) {
                            Log.e(TAG, "跳转到通用权限设置也失败: ${e2.message}")
                            Toast.makeText(this, "请在系统设置中手动授予本应用存储权限", Toast.LENGTH_LONG).show()
                        }
                    }
                }, 1000) // 延迟1秒请求权限，避免启动时的崩溃
            } else {
                Log.d(TAG, "已有 MANAGE_EXTERNAL_STORAGE 权限")
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Android 10 (API 29) 特殊处理
            Log.d(TAG, "Android 10 设备，检查传统存储权限")
            
            // 在Android 10，推荐使用分区存储，但我们也支持传统权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            // Android 9及以下，使用传统存储权限
            Log.d(TAG, "Android 9及以下设备，检查传统存储权限")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        // 检查录音权限（用于TTS和语音识别功能）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // 根据API级别添加媒体权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            // 添加通知权限检查
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // 请求所有需要的权限
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "请求权限: ${permissionsToRequest.joinToString()}")
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "已有所有必要权限或无需请求额外权限")
        }
    }
    
    override fun onDestroy() {
        // 清除单例引用
        if (instance?.get() == this) {
            instance = null
            Log.d(TAG, "MainActivity单例引用已清除")
        }
        
        // 释放媒体投影
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjection?.stop()
            mediaProjection = null
            Log.d(TAG, "媒体投影已释放")
        }
        
        super.onDestroy()
    }
}