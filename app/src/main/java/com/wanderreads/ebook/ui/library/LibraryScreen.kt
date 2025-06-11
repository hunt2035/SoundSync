package com.wanderreads.ebook.ui.library

import android.content.Intent
import android.provider.AlarmClock
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.wanderreads.ebook.domain.model.BookFile
import androidx.core.content.FileProvider

/**
 * 书库屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModelFactory(context.applicationContext as android.app.Application)
    )
    val uiState by viewModel.uiState.collectAsState()
    
    // 当前选中的类别
    var selectedCategory by remember { mutableStateOf<LibraryCategory?>(null) }
    
    // 确保每次进入书库界面时，重置readBookId并更新TTS同步状态
    LaunchedEffect(Unit) {
        // 重置全局阅读位置
        val mainActivity = com.wanderreads.ebook.MainActivity.getInstance()
        mainActivity?.updateReadingPosition(null, 0, 0)
        
        // 更新TTS同步状态
        val ttsManager = com.wanderreads.ebook.util.TtsManager.getInstance(context)
        ttsManager.updateSyncPageState()
        
        Log.d("LibraryScreen", "进入书库界面，重置readBookId为null，更新IsSyncPageState=${ttsManager.isSyncPageState.value}")
    }
    
    // 加载类别
    LaunchedEffect(Unit) {
        viewModel.loadCategories()
    }
    
    // 错误提示
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
    
    // 显示设置闹钟对话框
    var showAlarmDialog by remember { mutableStateOf(false) }
    var selectedVoiceFile by remember { mutableStateOf<BookFile?>(null) }
    
    if (showAlarmDialog && selectedVoiceFile != null) {
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("设置闹钟") },
            text = { 
                Text("将语音文件 \"${selectedVoiceFile?.fileName}\" 设置为闹钟铃声？") 
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        // 调用系统闹钟设置
                        try {
                            val file = selectedVoiceFile
                            if (file != null) {
                                // 使用FileProvider获取Uri
                                val fileObj = java.io.File(file.filePath)
                                val fileUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    fileObj
                                )
                                
                                // 创建闹钟设置Intent - 使用标准的ACTION_SET_ALARM操作
                                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                    // 添加铃声URI
                                    putExtra(AlarmClock.EXTRA_RINGTONE, fileUri.toString())
                                    
                                    // 设置其他闹钟参数
                                    putExtra(AlarmClock.EXTRA_MESSAGE, file.fileName) // 闹钟标签
                                    putExtra(AlarmClock.EXTRA_HOUR, 8) // 默认时间8点
                                    putExtra(AlarmClock.EXTRA_MINUTES, 0) // 默认0分
                                    putExtra(AlarmClock.EXTRA_SKIP_UI, false) // 显示闹钟设置界面
                                    
                                    // 添加授权标志
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                
                                // 添加临时权限
                                // 为常见的闹钟应用授予权限
                                val commonClockPackages = arrayOf(
                                    "com.android.deskclock", // 原生Android闹钟
                                    "com.google.android.deskclock", // Google闹钟
                                    "com.sec.android.app.clockpackage", // 三星闹钟
                                    "com.huawei.deskclock", // 华为闹钟
                                    "com.android.alarmclock", // 其他常见闹钟
                                    "com.oneplus.deskclock", // 一加闹钟
                                    "com.oppo.alarmclock", // OPPO闹钟
                                    "com.vivo.alarmclock" // vivo闹钟
                                )
                                
                                // 为所有可能的闹钟应用授予权限
                                for (packageName in commonClockPackages) {
                                    try {
                                        context.grantUriPermission(
                                            packageName, 
                                            fileUri, 
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    } catch (e: Exception) {
                                        Log.w("LibraryScreen", "授予 $packageName 权限失败: ${e.message}")
                                        // 继续尝试下一个包名
                                    }
                                }
                                
                                context.startActivity(intent)
                                Log.d("LibraryScreen", "启动系统闹钟设置: ${fileUri}")
                            }
                        } catch (e: Exception) {
                            Log.e("LibraryScreen", "设置闹钟失败: ${e.message}", e)
                            // 显示错误提示
                            viewModel.setError("设置闹钟失败: ${e.message}")
                        }
                        showAlarmDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 处理系统返回键
    BackHandler(enabled = selectedCategory != null) {
        // 如果当前在二级界面，返回到一级界面
        selectedCategory = null
    }
    
    // 根据是否选择了类别显示不同的界面
    if (selectedCategory != null) {
        // 显示文件列表界面
        FileListScreen(
            viewModel = viewModel,
            category = selectedCategory!!,
            onBackClick = { selectedCategory = null },
            onSetAlarm = { file ->
                selectedVoiceFile = file
                showAlarmDialog = true
            }
        )
    } else {
        // 显示类别列表界面
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("书库") }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.categories.isEmpty()) {
                    Text(
                        text = "没有找到类别",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(LibraryCategory.values()) { category ->
                            CategoryItem(
                                category = category,
                                fileCount = uiState.categories[category] ?: 0,
                                onClick = { selectedCategory = category },
                                onExplorerClick = { viewModel.openFileExplorer(context, category) }
                            )
                        }
                    }
                }
                
                // 错误提示
                uiState.error?.let { error ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text(error)
                    }
                }
            }
        }
    }
}

/**
 * 类别项组件
 */
@Composable
fun CategoryItem(
    category: LibraryCategory,
    fileCount: Int,
    onClick: () -> Unit,
    onExplorerClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类别图标
            Icon(
                imageVector = when (category.icon) {
                    "text_fields" -> Icons.Default.TextFields
                    "language" -> Icons.Default.Language
                    "book" -> Icons.Default.Book
                    "record_voice_over" -> Icons.Default.RecordVoiceOver
                    else -> Icons.Default.Folder
                },
                contentDescription = category.displayName,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 类别名称
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // 文件数量
            Text(
                text = "$fileCount 个文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 文件浏览器图标
            IconButton(onClick = onExplorerClick) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "打开文件浏览器",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
} 