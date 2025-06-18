package org.soundsync.ebook.ui.reader

import android.speech.tts.TextToSpeech
import android.os.Bundle
import java.util.Locale
import java.util.UUID
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width

/**
 * 阅读器屏幕组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookId: String,
    onBackClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // 控制顶部和底部工具栏的显示
    var showControls by remember { mutableStateOf(false) }
    
    // 日志记录布局创建
    LaunchedEffect(Unit) {
        Log.d("ReaderScreen", "布局初始化，显示底部状态栏")
    }
    
    // TTS状态
    val ttsEngine = remember { mutableStateOf<TextToSpeech?>(null) }
    val isTtsActive = remember { mutableStateOf(false) }
    
    // 初始化TTS引擎 - 简化版本，移除回调
    LaunchedEffect(Unit) {
        ttsEngine.value = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 设置语言、语速和音调
                ttsEngine.value?.language = Locale.getDefault()
                ttsEngine.value?.setPitch(1.0f)
                ttsEngine.value?.setSpeechRate(1.0f)
            } else {
                Log.e("TextToSpeech", "TTS初始化失败，状态码: $status")
            }
        }
    }
    
    // 清理TTS
    DisposableEffect(Unit) {
        onDispose {
            ttsEngine.value?.stop()
            ttsEngine.value?.shutdown()
        }
    }
    
    // 跟踪手势方向和距离
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    val dragThreshold = 50f // 触发翻页的最小拖动距离
    
    // 点击屏幕时切换控制栏显示状态
    val toggleControls = {
        showControls = !showControls
    }
    
    if (showControls) {
        // 显示全功能控制界面
        Box(modifier = Modifier.fillMaxSize()) {
            // 内容区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { position ->
                                val screenWidth = size.width
                                val screenHeight = size.height
                                
                                // 定义中间区域的边界
                                val leftBound = screenWidth * 0.25f
                                val rightBound = screenWidth * 0.75f
                                val topBound = screenHeight * 0.33f
                                val bottomBound = screenHeight * 0.67f
                                
                                when {
                                    position.x < leftBound -> {
                                        viewModel.previousPage()
                                    }
                                    position.x > rightBound -> {
                                        viewModel.nextPage()
                                    }
                                    position.y > topBound && position.y < bottomBound -> {
                                        toggleControls()
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartX = offset.x
                                dragStartY = offset.y
                            },
                            onDragEnd = {
                                dragStartX = 0f
                                dragStartY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                val dragX = change.position.x - dragStartX
                                val dragY = change.position.y - dragStartY
                                
                                if (Math.abs(dragX) > Math.abs(dragY) && Math.abs(dragX) > dragThreshold) {
                                    if (dragX > 0) {
                                        viewModel.previousPage()
                                    } else {
                                        viewModel.nextPage()
                                    }
                                    dragStartX = change.position.x
                                    dragStartY = change.position.y
                                }
                            }
                        )
                    }
            ) {
                ReaderContent(uiState)
            }
            
            // 顶部工具栏
                TopAppBar(
                    title = { 
                        Text(
                            text = uiState.book?.title ?: "阅读器",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // 底部工具栏
                EnhancedBottomControls(
                    currentPage = uiState.currentPage + 1,
                    totalPages = uiState.totalPages,
                    isTtsActive = isTtsActive.value,
                    onPreviousPage = { viewModel.previousPage() },
                    onNextPage = { viewModel.nextPage() },
                    onToggleTts = { 
                        if (isTtsActive.value) {
                            // 停止朗读
                            ttsEngine.value?.stop()
                            isTtsActive.value = false
                        } else {
                            // 开始朗读当前页
                            isTtsActive.value = true
                            val currentPageContent = viewModel.getContentForCurrentPage()
                            if (currentPageContent.isNotEmpty()) {
                                val params = Bundle()
                                val utteranceId = UUID.randomUUID().toString()
                                ttsEngine.value?.speak(currentPageContent, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                            } else {
                                isTtsActive.value = false
                                Log.d("TextToSpeech", "当前页没有内容，无法朗读")
                            }
                        }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    } else {
        // 显示默认阅读界面
        Log.d("ReaderScreen", "显示默认阅读界面，包含底部状态栏")
        
        // 使用Column而非Box，确保底部状态栏显示
        Column(modifier = Modifier.fillMaxSize()) {
            // 内容区域 - 使用更小的weight(0.9f)确保让出足够的空间给底部状态栏
        Box(
            modifier = Modifier
                    .weight(0.9f)
                    .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { position ->
                            val screenWidth = size.width
                            val screenHeight = size.height
                            
                            // 定义中间区域的边界
                            val leftBound = screenWidth * 0.25f
                            val rightBound = screenWidth * 0.75f
                            val topBound = screenHeight * 0.33f
                            val bottomBound = screenHeight * 0.67f
                            
                            when {
                                position.x < leftBound -> {
                                    viewModel.previousPage()
                                }
                                position.x > rightBound -> {
                                    viewModel.nextPage()
                                }
                                position.y > topBound && position.y < bottomBound -> {
                                    toggleControls()
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            dragStartY = offset.y
                        },
                        onDragEnd = {
                            dragStartX = 0f
                            dragStartY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            val dragX = change.position.x - dragStartX
                            val dragY = change.position.y - dragStartY
                            
                            if (Math.abs(dragX) > Math.abs(dragY) && Math.abs(dragX) > dragThreshold) {
                                if (dragX > 0) {
                                    viewModel.previousPage()
                                } else {
                                    viewModel.nextPage()
                                }
                                dragStartX = change.position.x
                                dragStartY = change.position.y
                            }
                        }
                    )
                }
        ) {
                ReaderContent(uiState)
            }
            
            // 为底部状态栏增加一个固定的容器高度，确保显示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                // 底部状态栏
                VeryVisibleReaderStatusBar(
                    bookTitle = uiState.book?.title ?: "阅读书籍",
                    currentPage = uiState.currentPage + 1,
                    totalPages = uiState.totalPages
                )
            }
        }
    }
}

/**
 * 阅读内容 - 提取为独立组件以提高代码重用性
 */
@Composable
private fun ReaderContent(uiState: ReaderUiState) {
            when {
                uiState.isLoading -> {
                    // 加载状态
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                uiState.error != null -> {
                    // 错误状态
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "加载失败",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "未知错误",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                uiState.pages.isNotEmpty() -> {
                    // 内容显示
                        val currentPageContent = uiState.pages.getOrNull(uiState.currentPage) ?: ""
                        val scrollState = rememberScrollState()
                        
                        // 每次页面变化时重置滚动位置
                        LaunchedEffect(uiState.currentPage) {
                            scrollState.scrollTo(0)
                        }
                        
            Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
                        ) {
                            Text(
                                text = currentPageContent,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 18.sp,
                                    lineHeight = 28.sp,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                    }
                }
                else -> {
                    // 空状态
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "无内容可显示",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
    }
}

/**
 * 高可见度阅读器底部状态栏
 * 完全符合需求并确保可见：32dp高度，左边2/3显示书名，右边1/3显示页码和百分比
 */
@Composable
fun VeryVisibleReaderStatusBar(
    bookTitle: String,
    currentPage: Int,
    totalPages: Int
) {
    Log.d("ReaderStatusBar", "渲染高可见度底部状态栏: $bookTitle, $currentPage/$totalPages")
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)  // 固定高度为32dp
            .background(Color.Red)  // 纯红色背景，确保绝对可见
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左边2/3：书名
            Text(
                text = bookTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,  // 纯白色文字
                modifier = Modifier
                    .weight(2f)
            )
            
            // 右边1/3：页码和百分比
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 页码
                Text(
                    text = "$currentPage/$totalPages",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White  // 纯白色文字
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 百分比 - 保留一位小数
                val percent = if (totalPages > 0) (currentPage.toFloat() / totalPages * 100f) else 0f
                Text(
                    text = String.format("%.1f%%", percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White  // 纯白色文字
                )
            }
        }
    }
}

/**
 * 增强版底部控制栏
 */
@Composable
fun EnhancedBottomControls(
    currentPage: Int,
    totalPages: Int,
    isTtsActive: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 进度条
            LinearProgressIndicator(
                progress = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 底部功能按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 目录按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { /* 打开目录 */ }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "目录",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "目录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // 翻页控制
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPreviousPage) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "上一页",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Text(
                        text = "$currentPage / $totalPages",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    IconButton(onClick = onNextPage) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "下一页",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // 朗读按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = onToggleTts) {
                        Icon(
                            imageVector = if (isTtsActive) 
                                Icons.Default.VolumeOff 
                            else 
                                Icons.Default.VolumeUp,
                            contentDescription = if (isTtsActive) "停止朗读" else "朗读",
                            tint = if (isTtsActive) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isTtsActive) "停止" else "朗读",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // 设置按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { /* 打开设置 */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
} 