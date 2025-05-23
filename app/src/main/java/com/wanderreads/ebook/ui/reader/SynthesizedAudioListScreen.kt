package com.wanderreads.ebook.ui.reader

import android.media.MediaMetadataRetriever
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * 合成语音列表屏幕
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
    totalDuration: Int = 0
) {
    val context = LocalContext.current
    var recordToRename by remember { mutableStateOf<Record?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<Record?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
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
                IconButton(onClick = onDismiss) {
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
                    .padding(16.dp),
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
                    val isExpanded = record.id == currentPlayingRecordId
                    val audioFile = File(record.voiceFilePath)
                    val audioExists = audioFile.exists()
                    val duration = remember(record.id) {
                        if (audioExists) {
                            getAudioDuration(record.voiceFilePath)
                        } else {
                            record.voiceLength.toLong() * 1000
                        }
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (record.id == currentPlayingRecordId) {
                                    onPauseRecord(record)
                                } else {
                                    onPlayRecord(record)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // 音频信息行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 收藏星标图标（可选功能）
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "收藏",
                                tint = if (false) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // 文件信息
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = record.title,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // 日期、波形、大小行
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 日期时间
                                    Text(
                                        text = formatDate(record.addedDate),
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // 波形图标（简化为静态图标）
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
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // 文件大小
                                    Text(
                                        text = if (audioExists) {
                                            "${audioFile.length() / 1024 / 1024.0f} MB"
                                        } else "未知大小",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // 总时长
                            Text(
                                text = formatDuration(duration),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        
                        // 展开的控制面板
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            AudioControlPanel(
                                isPlaying = record.id == currentPlayingRecordId,
                                currentPosition = currentPlaybackPosition,
                                duration = totalDuration,
                                onPlayPause = {
                                    if (record.id == currentPlayingRecordId) {
                                        onPauseRecord(record)
                                    } else {
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
        
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                recordToRename = null
            },
            title = { Text("修改文件名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToRename?.let { record ->
                            onRenameRecord(record, newName)
                        }
                        showRenameDialog = false
                        recordToRename = null
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        recordToRename = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // 删除确认对话框
    if (showDeleteConfirmation && recordToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                recordToDelete = null
            },
            title = { Text("删除文件") },
            text = { Text("确定要删除文件 \"${recordToDelete?.title}\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToDelete?.let { record ->
                            onDeleteRecord(record)
                        }
                        showDeleteConfirmation = false
                        recordToDelete = null
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        recordToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
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
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF252525), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 播放进度条和时间显示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
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
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { /* 需要实现拖动进度条更新播放位置的逻辑 */ },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
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
            ControlButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                description = if (isPlaying) "暂停" else "播放",
                onClick = onPlayPause
            )
            
            ControlButton(
                icon = Icons.Default.Edit,
                description = "重命名",
                onClick = onRename
            )
            
            ControlButton(
                icon = Icons.Default.Delete,
                description = "删除",
                onClick = onDelete
            )
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
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color(0xFF8AFFDD),
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