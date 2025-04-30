package com.example.ebook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ebook.ui.navigation.AppNavigation
import com.example.ebook.ui.theme.EbookTheme

/**
 * 主Activity
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // 存储权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "存储权限已被授予")
        } else {
            Toast.makeText(this, "应用需要存储权限才能正常运行", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 设置全局未捕获异常处理器
        setupExceptionHandler()
        
        super.onCreate(savedInstanceState)
        
        // 检查并请求必要权限
        checkAndRequestPermissions()
        
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
    
    private fun checkAndRequestPermissions() {
        val requiredPermission = Manifest.permission.READ_EXTERNAL_STORAGE
        
        // 检查是否已有权限
        if (ContextCompat.checkSelfPermission(this, requiredPermission) != 
                PackageManager.PERMISSION_GRANTED) {
            // 请求权限
            requestPermissionLauncher.launch(requiredPermission)
        }
    }
}