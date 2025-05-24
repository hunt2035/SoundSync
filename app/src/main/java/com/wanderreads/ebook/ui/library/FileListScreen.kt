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
                        IconButton(onClick = { viewModel.deleteSelectedFiles() }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除选中")
                        }
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
                            } else null
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
    onSetAlarmClick: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    
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
                                onDeleteClick()
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