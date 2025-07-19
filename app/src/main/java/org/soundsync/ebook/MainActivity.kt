package org.soundsync.ebook

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import java.lang.ref.WeakReference
import java.io.File
import org.soundsync.ebook.ui.theme.EbookTheme
import org.soundsync.ebook.ui.navigation.AppNavigation
import org.soundsync.ebook.ui.navigation.Screen
import org.soundsync.ebook.util.FileUtil
import org.soundsync.ebook.ui.settings.SettingsViewModel
import org.soundsync.ebook.util.LocaleHelper
import org.soundsync.ebook.data.local.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * 主Activity
 */
class MainActivity : ComponentActivity() {
    
    // 是否显示存储权限对话框（仅用于Android 11+的MANAGE_EXTERNAL_STORAGE权限）
    private val showStoragePermissionDialogState = mutableStateOf(false)
    
    // 保存NavController的引用
    private var navController: NavController? = null
    
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
        
        // 全局阅读位置变量
        var readBookId: String? = null
        var readCurrentPage: Int = 0
        var readTotalPages: Int = 0
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
            Toast.makeText(this, "应用需要存储权限才能正常工作", Toast.LENGTH_LONG).show()
        } else {
            // 在 Android 10 上创建必要的目录结构
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                createDirectoryStructure()
            }
        }
    }
    
    /**
     * 在创建Activity时，应用已保存的语言设置
     */
    override fun attachBaseContext(newBase: Context) {
        // 获取保存的语言设置并应用
        var languageCode = SettingsViewModel.LANGUAGE_SYSTEM
        
        try {
            // 从DataStore同步读取语言设置
            val preferences = runBlocking { newBase.dataStore.data.first() }
            val savedLanguage = preferences[SettingsViewModel.LANGUAGE_KEY]
            languageCode = savedLanguage?.toIntOrNull() ?: SettingsViewModel.LANGUAGE_SYSTEM
        } catch (e: Exception) {
            Log.e(TAG, "读取语言设置失败: ${e.message}", e)
        }
        
        // 应用语言设置
        val context = LocaleHelper.updateLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 保存单例实例
        instance = WeakReference(this)
        
        // 检查并请求权限
        checkAndRequestPermissions()
        
        // 处理从TTS通知点击过来的情况
        handleTtsNotificationIntent(intent)
        
        // 添加系统返回键监听
        onBackPressedDispatcher.addCallback(this) {
            // 获取当前路由
            val currentRoute = navController?.currentDestination?.route
            
            // 如果当前在阅读器屏幕，重置readBookId
            if (currentRoute?.contains("reader") == true) {
                updateReadingPosition(null, 0, 0)
                
                // 更新TTS同步状态
                val ttsManager = org.soundsync.ebook.util.TtsManager.getInstance(this@MainActivity)
                ttsManager.updateSyncPageState()
                
                Log.d(TAG, "系统返回键：从阅读界面返回，重置readBookId为null，更新IsSyncPageState=${ttsManager.isSyncPageState.value}")
            }
            
            // 继续默认的返回行为
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
        
        setContent {
            // 读取主题设置
            val themeFlow = dataStore.data.map { preferences ->
                preferences[SettingsViewModel.THEME_KEY]?.toIntOrNull() ?: SettingsViewModel.THEME_DARK
            }
            val themeMode by themeFlow.collectAsState(initial = SettingsViewModel.THEME_DARK)

            // 根据主题设置确定是否使用深色模式
            val darkTheme = when (themeMode) {
                SettingsViewModel.THEME_LIGHT -> false
                SettingsViewModel.THEME_DARK -> true
                SettingsViewModel.THEME_SYSTEM -> isSystemInDarkTheme()
                else -> true // 默认深色模式
            }

            EbookTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()

                // 保存NavController引用
                this.navController = navController

                // 显示存储权限对话框
                if (showStoragePermissionDialogState.value) {
                    StoragePermissionDialog(
                        onDismiss = { showStoragePermissionDialogState.value = false },
                        onConfirm = {
                            showStoragePermissionDialogState.value = false
                            openAndroidSettings()
                        }
                    )
                }

                // 应用导航
                AppNavigation(
                    onNavControllerReady = { controller ->
                        this.navController = controller
                    }
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 处理新的Intent，例如从通知点击过来的情况
        handleTtsNotificationIntent(intent)
    }
    
    /**
     * 处理从TTS通知点击过来的Intent
     */
    private fun handleTtsNotificationIntent(intent: Intent?) {
        intent?.let {
            if (it.getBooleanExtra("FROM_TTS_NOTIFICATION", false)) {
                val bookId = it.getStringExtra("TTS_BOOK_ID")
                val pageIndex = it.getIntExtra("TTS_PAGE_INDEX", 0)
                
                if (bookId != null) {
                    Log.d(TAG, "从TTS通知跳转到书籍: bookId=$bookId, page=$pageIndex")
                    // 延迟执行导航，确保UI已经准备好
                    Handler(Looper.getMainLooper()).postDelayed({
                        navigateToBook(bookId, pageIndex)
                    }, 300)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 更新单例引用（确保引用的是最新的实例）
        instance = WeakReference(this)
        Log.d(TAG, "MainActivity实例已在onResume中更新")
        
        // 在onResume中再次检查外部存储权限，但仅针对Android 11+设备
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 仅在用户主动使用需要此权限的功能时才显示（不在启动时就显示）
                // showStoragePermissionDialogState.value = true
            }
        }
    }
    
    /**
     * 更新当前阅读位置
     */
    fun updateReadingPosition(bookId: String?, currentPage: Int, totalPages: Int) {
        try {
            val oldBookId = readBookId
            val oldPage = readCurrentPage
            
            readBookId = bookId
            readCurrentPage = currentPage
            readTotalPages = totalPages
            
            Log.d(TAG, "更新阅读位置: 从(bookId=$oldBookId, page=$oldPage) 到 (bookId=$bookId, page=$currentPage/$totalPages)")
            
            // 更新TTS同步状态
            try {
                val ttsManager = org.soundsync.ebook.util.TtsManager.getInstance(this)
                ttsManager?.updateSyncPageState()
            } catch (e: Exception) {
                Log.e(TAG, "更新TTS同步状态失败: ${e.message}", e)
                // 即使更新同步状态失败，也不影响阅读位置的更新
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新阅读位置失败: ${e.message}", e)
            // 确保即使出错，全局变量也会被更新
            readBookId = bookId
            readCurrentPage = currentPage
            readTotalPages = totalPages
        }
    }
    
    /**
     * 获取当前阅读位置与TTS朗读位置是否同步
     * 用于判断是否显示"同步朗读中"或"边听边看"
     */
    fun isReadingPositionSyncWithTts(): Boolean {
        val ttsManager = org.soundsync.ebook.util.TtsManager.getInstance(this)
        return readBookId == ttsManager.bookId && readCurrentPage == ttsManager.currentPage
    }
    
    /**
     * 导航到指定的书籍
     * 用于在点击"边听边看"按钮时切换到正在朗读的书籍
     */
    fun navigateToBook(bookId: String, pageIndex: Int) {
        // 首先更新全局阅读位置
        updateReadingPosition(bookId, pageIndex, 0) // 总页数暂时设为0，稍后会更新
        
        // 使用NavController导航到UnifiedReader屏幕
        navController?.let { controller ->
            // 导航到统一阅读器，并传递目标页码作为参数
            val route = Screen.UnifiedReader.createRoute(bookId, pageIndex)
            controller.navigate(route) {
                // 避免创建多个相同目标的副本
                launchSingleTop = true
                
                // 如果已经在阅读器屏幕上，则弹出当前阅读器
                popUpTo(Screen.UnifiedReader.route) {
                    inclusive = true
                }
            }
            
            Log.d(TAG, "导航到书籍: bookId=$bookId, page=$pageIndex")
        } ?: run {
            Log.e(TAG, "NavController为空，无法导航")
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
                        
                        val appRootDir = File(externalDocumentsDir, "SoundSync")
                        if (!appRootDir.exists()) {
                            Log.d(TAG, "创建应用根目录: ${appRootDir.mkdirs()}")
                        }
                        
                        // 创建txtfiles目录
                        val txtFilesDir = File(appRootDir, "txtfiles")
                        if (!txtFilesDir.exists()) {
                            Log.d(TAG, "创建txtfiles目录: ${txtFilesDir.mkdirs()}")
                        }
                        
                        // 创建webbook目录
                        val webBookDir = File(appRootDir, "webbook")
                        if (!webBookDir.exists()) {
                            Log.d(TAG, "创建webbook目录: ${webBookDir.mkdirs()}")
                        }
                        
                        // 创建books目录
                        val booksDir = File(appRootDir, "books")
                        if (!booksDir.exists()) {
                            Log.d(TAG, "创建books目录: ${booksDir.mkdirs()}")
                        }
                        
                        // 创建voices目录
                        val voicesDir = File(appRootDir, "voices")
                        if (!voicesDir.exists()) {
                            Log.d(TAG, "创建voices目录: ${voicesDir.mkdirs()}")
                        }
                    }
                    
                    // 2. 始终创建应用专属目录
                    val appSpecificExternalDir = getExternalFilesDir(null)
                    
                    val appTxtFilesDir = File(appSpecificExternalDir, "txtfiles")
                    if (!appTxtFilesDir.exists()) {
                        Log.d(TAG, "创建应用专属txtfiles目录: ${appTxtFilesDir.mkdirs()}")
                    }
                    
                    val appWebBookDir = File(appSpecificExternalDir, "webbook")
                    if (!appWebBookDir.exists()) {
                        Log.d(TAG, "创建应用专属webbook目录: ${appWebBookDir.mkdirs()}")
                    }
                    
                    val appVoicesDir = File(appSpecificExternalDir, "voices")
                    if (!appVoicesDir.exists()) {
                        Log.d(TAG, "创建应用专属voices目录: ${appVoicesDir.mkdirs()}")
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
            // 但不在启动时就请求，而是在用户需要时再请求
            val hasStoragePermission = Environment.isExternalStorageManager()
            if (hasStoragePermission) {
                Log.d(TAG, "已有 MANAGE_EXTERNAL_STORAGE 权限")
            } else {
                Log.d(TAG, "没有 MANAGE_EXTERNAL_STORAGE 权限，但不在启动时请求")
                // 不在启动时就显示权限请求对话框
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
     * 请求所有文件访问权限（仅适用于Android 11+）
     */
    private fun requestAllFilesAccessPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 需要请求特殊权限
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(this, "请授予应用访问所有文件的权限，以便保存电子书到Documents目录", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求所有文件访问权限失败: ${e.message}", e)
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
     * 显示Android 11+设备的外部存储权限请求对话框
     * 此方法应该只在用户尝试使用需要此权限的功能时调用
     */
    fun showAllFilesAccessPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showStoragePermissionDialogState.value = true
        }
    }
    
    override fun onDestroy() {
        // 清除单例引用
        if (instance?.get() == this) {
            instance = null
            Log.d(TAG, "MainActivity单例引用已清除")
        }
        
        super.onDestroy()
    }
    
    /**
     * 打开Android系统设置
     */
    private fun MainActivity.openAndroidSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 需要请求特殊权限
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(this, "请授予应用访问所有文件的权限", Toast.LENGTH_LONG).show()
            } else {
                // 对于Android 10及以下版本，跳转到应用详情页
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(this, "请在设置中允许应用访问存储", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "跳转到设置页面失败: ${e.message}", e)
            Toast.makeText(this, "请在系统设置中手动授予本应用存储权限", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * 显示存储权限请求对话框
 */
@Composable
private fun StoragePermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要存储权限") },
        text = { Text("为了能够导入和管理电子书，应用需要访问您的文件存储。请在下一步中授予存储权限。") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("前往设置")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}