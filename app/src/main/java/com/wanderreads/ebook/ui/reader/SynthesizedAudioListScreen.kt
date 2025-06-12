package com.wanderreads.ebook.ui.reader

import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wanderreads.ebook.domain.model.Record
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.provider.AlarmClock
import androidx.core.content.FileProvider
import android.net.Uri
import android.util.Log

/**
 * 合成语音列表屏幕
 * 
 * @param records 语音记录列表
 * @param onDismiss 关闭语音列表屏幕的回调，只在用户点击返回按钮时调用，不应在重命名和删除操作后自动调用
 * @param onPlayRecord 播放语音记录的回调
 * @param onPauseRecord 暂停语音记录的回调
 * @param onRenameRecord 重命名语音记录的回调，调用后应保留在语音列表页面
 * @param onDeleteRecord 删除语音记录的回调，调用后应保留在语音列表页面
 * @param currentPlayingRecordId 当前正在播放的语音记录ID
 * @param currentPlaybackPosition 当前播放位置（毫秒）
 * @param totalDuration 当前播放记录的总时长（毫秒）
 * @param onSeekTo 调整播放位置的回调
 * @param isAudioPlaying 当前是否正在播放音频
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynthesizedAudioListScreen(
    records: List<Record>,
    onDismiss: () -> Unit,
    onPlayRecord: (Record) -> Unit,
    onPauseRecord: (Record) -> Unit,
    onRenameRecord: (Record, String) -> Unit,
    onDeleteRecord: (Record) -> Unit,
    currentPlayingRecordId: String?,
    currentPlaybackPosition: Int = 0,
    totalDuration: Int = 0,
    onSeekTo: (Record, Int) -> Unit = { _, _ -> },
    isAudioPlaying: Boolean = false
) {
    val context = LocalContext.current
    var recordToRename by remember { mutableStateOf<Record?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<Record?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    // 用于安全执行重命名和删除操作的状态
    var pendingRenameOperation by remember { mutableStateOf<Pair<Record, String>?>(null) }
    var pendingDeleteOperation by remember { mutableStateOf<Record?>(null) }
    
    // 跟踪当前展开的记录ID
    var expandedRecordId by remember { mutableStateOf<String?>(currentPlayingRecordId) }
    
    // 当播放的记录变化时，自动展开该记录的控制面板
    LaunchedEffect(currentPlayingRecordId) {
        if (currentPlayingRecordId != null) {
            expandedRecordId = currentPlayingRecordId
        }
    }
    
    // 拦截系统返回键，确保返回时调用onDismiss而不是直接导航到书架
    BackHandler {
        onDismiss()
    }
    
    // 使用key为Unit的LaunchedEffect，确保只在组件首次进入组合时执行一次
    // 这样可以避免在重组时重新触发副作用
    LaunchedEffect(Unit) {
        // 在这里可以进行一些初始化工作，但不会在重组时重新执行
    }
    
    // 安全地执行重命名操作 - 使用try-catch和状态重置确保不会导致Activity退出
    DisposableEffect(pendingRenameOperation) {
        if (pendingRenameOperation != null) {
            val (record, newName) = pendingRenameOperation!!
            try {
                // 使用try-catch块捕获可能的异常，防止应用崩溃
                onRenameRecord(record, newName)
            } catch (e: Exception) {
                // 记录错误，但不中断UI流
                e.printStackTrace()
            } finally {
                // 确保无论如何都会清除挂起的操作
                pendingRenameOperation = null
            }
        }
        
        // DisposableEffect需要返回一个清理函数
        onDispose {
            // 清理资源
        }
    }
    
    // 安全地执行删除操作 - 使用try-catch和状态重置确保不会导致Activity退出
    DisposableEffect(pendingDeleteOperation) {
        if (pendingDeleteOperation != null) {
            val record = pendingDeleteOperation!!
            try {
                // 使用try-catch块捕获可能的异常，防止应用崩溃
                onDeleteRecord(record)
            } catch (e: Exception) {
                // 记录错误，但不中断UI流
                e.printStackTrace()
            } finally {
                // 确保无论如何都会清除挂起的操作
                pendingDeleteOperation = null
            }
        }
        
        // DisposableEffect需要返回一个清理函数
        onDispose {
            // 清理资源
        }
    }
    
    val sortedRecords = records
        .filter { it.isSynthesized }
        .sortedByDescending { it.addedDate }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // 顶部栏
        TopAppBar(
            title = { Text("合成语音列表", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onDismiss) {  // 这是唯一应该调用onDismiss的地方
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF222222)
            )
        )
        
        // 可用空间信息
        val totalSpaceText = remember { "40.3 GB 可用" }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = totalSpaceText,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        // 列表显示
        if (sortedRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无合成语音文件",
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedRecords) { record ->
                    val isExpanded = record.id == expandedRecordId
                    val isCurrentRecord = record.id == currentPlayingRecordId
                    val isPlaying = record.id == currentPlayingRecordId && isAudioPlaying
                    val audioFile = File(record.voiceFilePath)
                    val audioExists = audioFile.exists()
                    val duration = remember(record.id) {
                        if (audioExists) {
                            getAudioDuration(record.voiceFilePath)
                        } else {
                            record.voiceLength.toLong() * 1000
                        }
                    }
                    val fileSize = if (audioExists) {
                        val sizeInMB = audioFile.length() / 1024f / 1024f
                        String.format("%.1f MB", sizeInMB)
                    } else "未知大小"
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 点击列表项时，切换展开状态
                                expandedRecordId = if (expandedRecordId == record.id) null else record.id
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // 第1行：文件名和时长
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 文件名
                            Text(
                                text = truncateMiddle(record.title, 25),
                                color = Color.White,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // 总时长
                            Text(
                                text = formatDuration(duration),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // 第2行：生成时间、波形图和文件大小
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 左侧：生成时间和波形图
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 生成时间
                                Text(
                                    text = formatDate(record.addedDate),
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // 波形图标
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        for (i in 1..10) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height((3 + (i % 7) * 2).dp)
                                                    .background(Color.Gray.copy(alpha = 0.5f))
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // 右侧：文件大小
                            Text(
                                text = fileSize,
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        // 展开的控制面板
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            AudioControlPanel(
                                isPlaying = isPlaying,
                                currentPosition = if (isPlaying) currentPlaybackPosition else 0,
                                duration = if (isPlaying) totalDuration else duration.toInt(),
                                onPlayPause = {
                                    if (isPlaying) {
                                        // 当前正在播放，点击后暂停
                                        onPauseRecord(record)
                                    } else {
                                        // 当前未播放，点击后播放
                                        onPlayRecord(record)
                                    }
                                },
                                onRename = {
                                    recordToRename = record
                                    showRenameDialog = true
                                },
                                onDelete = {
                                    recordToDelete = record
                                    showDeleteConfirmation = true
                                },
                                onSeek = { seekPosition ->
                                    // 直接调用onSeekTo更新播放位置
                                    onSeekTo(record, seekPosition)
                                    
                                    // 如果当前不是播放状态，则开始播放
                                    if (!isPlaying) {
                                        onPlayRecord(record)
                                    }
                                },
                                onSliderSeek = { seekPosition ->
                                    // 直接调用onSeekTo更新播放位置
                                    onSeekTo(record, seekPosition)
                                    
                                    // 如果当前不是播放状态，不自动开始播放
                                    // 这样可以让用户在拖动滑杆时预览不同位置，而不会立即开始播放
                                },
                                onSliderSeekFinished = { seekPosition ->
                                    // 拖动结束后，确保播放位置已更新
                                    onSeekTo(record, seekPosition)
                                    
                                    // 如果当前不是播放状态，则开始播放
                                    if (!isPlaying) {
                                        onPlayRecord(record)
                                    }
                                },
                                onOpenFileLocation = {
                                    // 打开文件所在目录
                                    val file = File(record.voiceFilePath)
                                    if (file.exists()) {
                                        try {
                                            // 使用LibraryViewModel中的方法打开文件所在目录
                                            // 这里我们直接在UI层实现类似功能
                                            val intent = Intent(Intent.ACTION_VIEW)
                                            val parentDir = file.parentFile
                                            if (parentDir != null && parentDir.exists()) {
                                                val uri = Uri.fromFile(parentDir)
                                                intent.setDataAndType(uri, "resource/folder")
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                context.startActivity(Intent.createChooser(intent, "选择文件浏览器打开目录"))
                                            }
                                        } catch (e: Exception) {
                                            Log.e("SynthesizedAudioListScreen", "打开文件所在目录失败", e)
                                        }
                                    }
                                },
                                onSetAlarm = {
                                    // 设置闹钟
                                    val file = File(record.voiceFilePath)
                                    if (file.exists()) {
                                        try {
                                            // 使用FileProvider获取Uri
                                            val fileUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            
                                            // 创建闹钟设置Intent
                                            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                                // 添加铃声URI
                                                putExtra(AlarmClock.EXTRA_RINGTONE, fileUri.toString())
                                                
                                                // 设置其他闹钟参数
                                                putExtra(AlarmClock.EXTRA_MESSAGE, record.title) // 闹钟标签
                                                putExtra(AlarmClock.EXTRA_HOUR, 8) // 默认时间8点
                                                putExtra(AlarmClock.EXTRA_MINUTES, 0) // 默认0分
                                                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // 显示闹钟设置界面
                                                
                                                // 添加授权标志
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            }
                                            
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
                                                    Log.w("SynthesizedAudioListScreen", "授予 $packageName 权限失败: ${e.message}")
                                                    // 继续尝试下一个包名
                                                }
                                            }
                                            
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e("SynthesizedAudioListScreen", "设置闹钟失败: ${e.message}", e)
                                        }
                                    }
                                }
                            )
                        }
                        
                        Divider(
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
    
    // 重命名对话框
    if (showRenameDialog && recordToRename != null) {
        var newName by remember { mutableStateOf(recordToRename?.title ?: "") }
        
        Dialog(
            onDismissRequest = {
                showRenameDialog = false
                recordToRename = null
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "修改文件名",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("文件名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showRenameDialog = false
                                recordToRename = null
                            }
                        ) {
                            Text("取消")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = {
                                val currentRecord = recordToRename
                                val currentNewName = newName
                                
                                // 先关闭对话框，再设置挂起操作
                                showRenameDialog = false
                                recordToRename = null
                                
                                // 确保有效的记录和名称
                                if (currentRecord != null && currentNewName.isNotBlank()) {
                                    // 设置挂起操作，让DisposableEffect安全地执行
                                    pendingRenameOperation = Pair(currentRecord, currentNewName)
                                }
                            }
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteConfirmation && recordToDelete != null) {
        Dialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                recordToDelete = null
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "删除文件",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "确定要删除文件 \"${recordToDelete?.title}\" 吗？此操作不可撤销。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showDeleteConfirmation = false
                                recordToDelete = null
                            }
                        ) {
                            Text("取消")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = {
                                val currentRecord = recordToDelete
                                
                                // 先关闭对话框，再设置挂起操作
                                showDeleteConfirmation = false
                                recordToDelete = null
                                
                                // 确保有效的记录
                                if (currentRecord != null) {
                                    // 设置挂起操作，让DisposableEffect安全地执行
                                    pendingDeleteOperation = currentRecord
                                }
                            }
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 音频控制面板
 */
@Composable
fun AudioControlPanel(
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    onPlayPause: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSeek: (Int) -> Unit = {},
    onSliderSeek: (Int) -> Unit = onSeek,
    onSliderSeekFinished: (Int) -> Unit = onSeek,
    onOpenFileLocation: () -> Unit = {},
    onSetAlarm: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFF252525), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 播放进度条和时间显示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formattedCurrentTime = formatDuration(currentPosition.toLong())
            val formattedRemainingTime = "-" + formatDuration((duration - currentPosition).toLong().coerceAtLeast(0))
            
            // 当前播放时间
            Text(
                text = formattedCurrentTime,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
            
            // 进度条
            var sliderPosition by remember(currentPosition) { mutableFloatStateOf(if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f) }
            var isChangingSlider by remember { mutableStateOf(false) }
            
            Slider(
                value = if (isChangingSlider) sliderPosition else if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { 
                    sliderPosition = it
                    isChangingSlider = true
                    // 实时更新播放位置，提供即时反馈
                    if (duration > 0) {
                        onSliderSeek((sliderPosition * duration).toInt())
                    }
                },
                onValueChangeFinished = {
                    isChangingSlider = false
                    // 拖动结束后，确保播放位置已更新
                    if (duration > 0) {
                        onSliderSeekFinished((sliderPosition * duration).toInt())
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF8AFFDD),
                    activeTrackColor = Color(0xFF8AFFDD),
                    inactiveTrackColor = Color.Gray
                )
            )
            
            // 剩余时间
            Text(
                text = formattedRemainingTime,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // 控制按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 倒退15秒按钮
            ControlButton(
                icon = Icons.Default.KeyboardArrowLeft,
                description = "倒退15秒",
                onClick = { 
                    // 计算新的播放位置（当前位置减去15秒，但不小于0）
                    val newPosition = (currentPosition - 15000).coerceAtLeast(0)
                    // 调用onSeek回调，传递新的播放位置
                    onSeek(newPosition)
                }
            )
            
            // 播放/暂停按钮 - 根据isPlaying状态显示不同的图标
            ControlButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                description = if (isPlaying) "暂停" else "播放",
                onClick = onPlayPause,
                isHighlighted = true
            )
            
            // 快进15秒按钮
            ControlButton(
                icon = Icons.Default.KeyboardArrowLeft,
                description = "快进15秒",
                onClick = { 
                    // 计算新的播放位置（当前位置加上15秒，但不超过总时长）
                    val newPosition = (currentPosition + 15000).coerceAtMost(duration)
                    // 调用onSeek回调，传递新的播放位置
                    onSeek(newPosition)
                },
                modifier = Modifier.rotate(180f)
            )
            
            // 重命名按钮
            ControlButton(
                icon = Icons.Default.Edit,
                description = "重命名",
                onClick = onRename
            )
            
            // 删除按钮
            ControlButton(
                icon = Icons.Default.Delete,
                description = "删除",
                onClick = onDelete
            )
            
            // 添加三个点菜单按钮
            var showMenu by remember { mutableStateOf(false) }
            Box {
                ControlButton(
                    icon = Icons.Default.MoreVert,
                    description = "更多选项",
                    onClick = { showMenu = true }
                )
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // 所属目录选项
                    DropdownMenuItem(
                        text = { Text("所属目录") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        onClick = {
                            onOpenFileLocation()
                            showMenu = false
                        }
                    )
                    
                    // 设置闹钟选项
                    DropdownMenuItem(
                        text = { Text("设置闹钟") },
                        leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        onClick = {
                            onSetAlarm()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 控制按钮组件
 */
@Composable
fun ControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isHighlighted) Color(0xFF8AFFDD) else Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 格式化日期
 */
fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

/**
 * 格式化时长
 */
fun formatDuration(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * 在字符串中间截断并添加省略号
 * @param text 需要截断的文本
 * @param maxLength 最大长度
 * @return 截断后的文本
 */
fun truncateMiddle(text: String, maxLength: Int): String {
    if (text.length <= maxLength) return text
    
    val startLength = (maxLength - 3) / 2
    val endLength = maxLength - 3 - startLength
    
    return text.take(startLength) + "..." + text.takeLast(endLength)
}

/**
 * 获取音频文件时长
 */
fun getAudioDuration(filePath: String): Long {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        durationStr?.toLong() ?: 0
    } catch (e: Exception) {
        e.printStackTrace()
        0
    }
} 