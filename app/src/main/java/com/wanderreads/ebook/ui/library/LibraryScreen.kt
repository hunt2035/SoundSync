package com.wanderreads.ebook.ui.library

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
import com.wanderreads.ebook.domain.model.BookFile

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
                Text("为语音文件 ${selectedVoiceFile?.fileName} 设置闹钟功能即将上线") 
            },
            confirmButton = {
                TextButton(onClick = { showAlarmDialog = false }) {
                    Text("确定")
                }
            }
        )
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