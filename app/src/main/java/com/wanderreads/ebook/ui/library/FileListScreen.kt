package com.wanderreads.ebook.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.wanderreads.ebook.domain.model.BookFile
import com.wanderreads.ebook.util.FileUtil
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件列表屏幕
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileListScreen(
    viewModel: LibraryViewModel,
    category: LibraryCategory,
    onBackClick: () -> Unit,
    onSetAlarm: ((BookFile) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    // 加载文件
    LaunchedEffect(category) {
        viewModel.loadFiles(category)
    }
    
    // 处理系统返回键
    BackHandler {
        if (uiState.isSelectionMode) {
            // 如果处于选择模式，先退出选择模式
            viewModel.toggleSelectionMode()
        } else {
            // 否则返回上一级
            onBackClick()
        }
    }
    
    // 重命名对话框
    if (uiState.isRenameDialogVisible && uiState.fileToRename != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRenameDialog() },
            title = { Text("重命名文件") },
            text = {
                TextField(
                    value = uiState.newFileName,
                    onValueChange = { viewModel.updateNewFileName(it) },
                    label = { Text("文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uiState.fileToRename?.let { file ->
                            viewModel.renameFile(file.filePath, uiState.newFileName)
                        }
                        viewModel.hideRenameDialog()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRenameDialog() }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 删除确认对话框
    if (uiState.isDeleteConfirmationVisible) {
        val isSingleFile = uiState.singleFileToDelete != null
        val selectedCount = if (isSingleFile) 1 else uiState.selectedFiles.size
        
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmation() },
            title = { Text("确认删除") },
            text = { 
                Text("选定的${selectedCount}个文件即将被删除，且不可恢复，是否确认删除？") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.executeDelete()
                    }
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirmation() }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 错误提示
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        // 删除图标
                        IconButton(
                            onClick = { 
                                if (uiState.selectedFiles.isNotEmpty()) {
                                    viewModel.showDeleteConfirmation()
                                }
                            },
                            enabled = uiState.selectedFiles.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete, 
                                contentDescription = "删除选中",
                                tint = if (uiState.selectedFiles.isEmpty()) 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else 
                                    LocalContentColor.current
                            )
                        }
                        
                        // 全选/取消全选按钮（在删除图标的右边）
                        IconButton(
                            onClick = { 
                                if (viewModel.isAllSelected()) {
                                    viewModel.deselectAllFiles()
                                } else {
                                    viewModel.selectAllFiles()
                                }
                            }
                        ) {
                            if (viewModel.isAllSelected()) {
                                // 已全选状态，显示取消全选图标
                                Icon(
                                    imageVector = Icons.Default.IndeterminateCheckBox,
                                    contentDescription = "取消全选"
                                )
                            } else {
                                // 未全选状态，显示全选图标
                                Icon(
                                    imageVector = Icons.Default.CheckBox,
                                    contentDescription = "全选"
                                )
                            }
                        }
                        
                        // 取消按钮
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Text("取消")
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Text("选择")
                        }
                    }
                }
            )
        },
        bottomBar = {
            // 音频播放控制器
            if (uiState.currentPlayingFilePath != null) {
                val currentFile = uiState.files.find { it.filePath == uiState.currentPlayingFilePath }
                if (currentFile != null) {
                    AudioPlayerControl(
                        file = currentFile,
                        isPlaying = uiState.isAudioPlaying,
                        currentPosition = uiState.currentPlaybackPosition,
                        totalDuration = uiState.totalAudioDuration,
                        onPlayPause = {
                            if (uiState.isAudioPlaying) {
                                viewModel.pauseAudioPlayback()
                            } else {
                                viewModel.resumeAudioPlayback()
                            }
                        },
                        onStop = { viewModel.stopAudioPlayback() },
                        onSeek = { position -> viewModel.seekToPosition(position) }
                    )
                }
            }
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
            } else if (uiState.files.isEmpty()) {
                Text(
                    text = "没有找到文件",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.files) { file ->
                        val isSelected = uiState.selectedFiles.contains(file.filePath)
                        
                        FileItem(
                            file = file,
                            dateFormat = dateFormat,
                            isSelected = isSelected,
                            isSelectionMode = uiState.isSelectionMode,
                            onItemClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleFileSelection(file.filePath)
                                } else if (category == LibraryCategory.VOICE_FILES) {
                                    // 点击语音文件时直接播放
                                    if (uiState.currentPlayingFilePath == file.filePath && uiState.isAudioPlaying) {
                                        viewModel.pauseAudioPlayback()
                                    } else if (uiState.currentPlayingFilePath == file.filePath) {
                                        viewModel.resumeAudioPlayback()
                                    } else {
                                        viewModel.playAudioFile(file)
                                    }
                                }
                            },
                            onItemLongClick = {
                                viewModel.toggleFileSelection(file.filePath)
                            },
                            onRenameClick = {
                                viewModel.showRenameDialog(file)
                            },
                            onDeleteClick = {
                                viewModel.deleteFile(file.filePath)
                            },
                            onSetAlarmClick = if (category == LibraryCategory.VOICE_FILES) {
                                { onSetAlarm?.invoke(file) }
                            } else null,
                            viewModel = viewModel
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

/**
 * 文件项组件
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    file: BookFile,
    dateFormat: SimpleDateFormat,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSetAlarmClick: (() -> Unit)? = null,
    viewModel: LibraryViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    val uiState = viewModel.uiState.collectAsState().value
    val isPlaying = uiState.currentPlayingFilePath == file.filePath && uiState.isAudioPlaying
    val isVoiceFile = onSetAlarmClick != null // 如果onSetAlarmClick不为null，说明是语音文件
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .combinedClickable(
                onClick = { onItemClick() },
                onLongClick = { onItemLongClick() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                            else if (isPlaying) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                // 文件名
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 创建时间
                Text(
                    text = "创建时间: ${dateFormat.format(Date(file.lastModified))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 文件大小
                Text(
                    text = "文件大小: ${FileUtil.getFormattedFileSize(file.fileSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 操作按钮
            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多操作"
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // 所属目录选项（对所有文件显示）
                        DropdownMenuItem(
                            text = { Text("所属目录") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                viewModel.openFileLocation(context, file.filePath)
                                showMenu = false
                            }
                        )
                        
                        // 播放语音选项（仅对语音文件显示）
                        if (isVoiceFile) {
                            DropdownMenuItem(
                                text = { Text("播放语音") },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    ) 
                                },
                                onClick = {
                                    if (isPlaying) {
                                        viewModel.pauseAudioPlayback()
                                    } else if (uiState.currentPlayingFilePath == file.filePath) {
                                        viewModel.resumeAudioPlayback()
                                    } else {
                                        viewModel.playAudioFile(file)
                                    }
                                    showMenu = false
                                }
                            )
                        }
                        
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                onRenameClick()
                                showMenu = false
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                viewModel.showSingleFileDeleteConfirmation(file.filePath)
                                showMenu = false
                            }
                        )
                        
                        if (onSetAlarmClick != null) {
                            DropdownMenuItem(
                                text = { Text("设置闹钟") },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null
                                    ) 
                                },
                                onClick = {
                                    onSetAlarmClick()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
            
            // 选择状态指示器
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onItemClick() }
                )
            }
        }
    }
}

/**
 * 音频播放控制器
 */
@Composable
fun AudioPlayerControl(
    file: BookFile,
    isPlaying: Boolean,
    currentPosition: Int,
    totalDuration: Int,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        // 文件名
        Text(
            text = file.fileName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 进度条
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // 当前时间
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.bodySmall
            )
            
            // 进度滑块
            Slider(
                value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                onValueChange = { value ->
                    val newPosition = (value * totalDuration).toInt()
                    onSeek(newPosition)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            
            // 总时长
            Text(
                text = formatDuration(totalDuration),
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // 控制按钮
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 播放/暂停按钮
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }
            
            // 停止按钮
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "停止"
                )
            }
        }
    }
}

/**
 * 格式化时长
 */
fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
} 