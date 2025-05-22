package com.wanderreads.ebook

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import com.wanderreads.ebook.ui.theme.EbookTheme
import com.wanderreads.ebook.ui.navigation.AppNavigation
import com.wanderreads.ebook.util.FileUtil
import java.io.File

/**
 * 主Activity
 */
class MainActivity : ComponentActivity() {
    
    // 是否显示存储权限对话框
    private val showStoragePermissionDialogState = mutableStateOf(false)
    
    companion object {
        private const val TAG = "MainActivity"
        
        // 单例实例 - 使用弱引用避免内存泄漏
        private var instance: WeakReference<MainActivity>? = null
        
        /**
         * 获取MainActivity实例
         */
        fun getInstance(): MainActivity? {
            return instance?.get()
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
        } else {
            // 在 Android 10 上创建必要的目录结构
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                createDirectoryStructure()
            }
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
        
        // 对于 Android 10，预创建目录结构
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            createDirectoryStructure()
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
                        
                        // 显示权限对话框
                        if (showStoragePermissionDialogState.value) {
                            AlertDialog(
                                onDismissRequest = { showStoragePermissionDialogState.value = false },
                                title = { Text("需要存储权限") },
                                text = { Text("WanderReads需要存储权限才能保存书籍到您的设备。请在接下来的系统页面中授予权限。") },
                                confirmButton = {
                                    Button(onClick = {
                                        showStoragePermissionDialogState.value = false
                                        requestStoragePermission()
                                    }) {
                                        Text("授予权限")
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { showStoragePermissionDialogState.value = false }) {
                                        Text("取消")
                                    }
                                }
                            )
                        }
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
        
        // 在onResume中再次检查外部存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 显示权限提示对话框
                showStoragePermissionDialogState.value = true
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // 对于Android 10，检查传统存储权限
            val hasReadPermission = ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasWritePermission = ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasReadPermission || !hasWritePermission) {
                showStoragePermissionDialogState.value = true
            } else {
                // 确保目录结构已创建
                createDirectoryStructure()
            }
        }
    }
    
    /**
     * 为Android 10设备创建必要的目录结构
     */
    private fun createDirectoryStructure() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) return
        
        try {
            // 使用Thread避免UI线程IO操作
            Thread {
                try {
                    // 1. 尝试在公共目录创建结构
                    if (FileUtil.isExternalStorageWritable()) {
                        val externalDocumentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                        if (!externalDocumentsDir.exists()) {
                            Log.d(TAG, "创建外部Documents目录: ${externalDocumentsDir.mkdirs()}")
                        }
                        
                        val appRootDir = File(externalDocumentsDir, "WanderReads")
                        if (!appRootDir.exists()) {
                            Log.d(TAG, "创建应用根目录: ${appRootDir.mkdirs()}")
                        }
                        
                        // 创建books目录
                        val booksDir = File(appRootDir, "books")
                        if (!booksDir.exists()) {
                            Log.d(TAG, "创建books目录: ${booksDir.mkdirs()}")
                        }
                        
                        // 创建webbook目录
                        val webBookDir = File(appRootDir, "webbook")
                        if (!webBookDir.exists()) {
                            Log.d(TAG, "创建webbook目录: ${webBookDir.mkdirs()}")
                        }
                    }
                    
                    // 2. 始终创建应用专属目录
                    val appSpecificExternalDir = getExternalFilesDir(null)
                    
                    val appBooksDir = File(appSpecificExternalDir, "newtxt")
                    if (!appBooksDir.exists()) {
                        Log.d(TAG, "创建应用专属books目录: ${appBooksDir.mkdirs()}")
                    }
                    
                    val appWebBookDir = File(appSpecificExternalDir, "webbook")
                    if (!appWebBookDir.exists()) {
                        Log.d(TAG, "创建应用专属webbook目录: ${appWebBookDir.mkdirs()}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "创建目录结构失败: ${e.message}", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "启动目录创建线程失败: ${e.message}", e)
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
                // 显示权限提示对话框
                showStoragePermissionDialogState.value = true
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
    
    /**
     * 请求存储权限
     */
    private fun requestStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 需要请求特殊权限
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(this, "请授予应用访问所有文件的权限，以便保存电子书到Documents目录", Toast.LENGTH_LONG).show()
            } else {
                // Android 10及以下使用常规权限请求
                val permissions = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                requestMultiplePermissionsLauncher.launch(permissions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求存储权限失败: ${e.message}", e)
            try {
                // 如果直接跳转失败，则跳转到普通设置页面
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(this, "请在设置中允许应用访问存储", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Log.e(TAG, "跳转到应用设置也失败: ${e2.message}")
                Toast.makeText(this, "请在系统设置中手动授予本应用存储权限", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 显示外部存储权限请求对话框
     */
    private fun showStoragePermissionDialog() {
        showStoragePermissionDialogState.value = true
    }
    
    override fun onDestroy() {
        // 清除单例引用
        if (instance?.get() == this) {
            instance = null
            Log.d(TAG, "MainActivity单例引用已清除")
        }
        
        super.onDestroy()
    }
}