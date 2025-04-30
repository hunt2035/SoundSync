package com.example.ebook.ui.reader

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    
    // TTS状态
    val ttsEngine = remember { mutableStateOf<TextToSpeech?>(null) }
    val isTtsActive = remember { mutableStateOf(false) }
    val currentTtsPage = remember { mutableStateOf(0) }.value
    
    // 初始化TTS引擎
    LaunchedEffect(Unit) {
        ttsEngine.value = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 设置语言、语速和音调
                ttsEngine.value?.language = Locale.getDefault()
                ttsEngine.value?.setPitch(1.0f)
                ttsEngine.value?.setSpeechRate(1.0f)
                
                // 设置回调
                ttsEngine.value?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        Log.d("TextToSpeech", "开始朗读: $utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String) {
                        Log.d("TextToSpeech", "朗读完成: $utteranceId")
                        
                        // 当朗读完成时，自动翻到下一页并朗读
                        if (isTtsActive.value) {
                            coroutineScope.launch {
                                // 翻到下一页
                                if (uiState.currentPage < uiState.totalPages - 1) {
                                    viewModel.goToPage(uiState.currentPage + 1)
                                    
                                    // 等待页面加载
                                    delay(300)
                                    
                                    // 朗读新的页面内容
                                    val nextPageContent = viewModel.getContentForCurrentPage()
                                    if (nextPageContent.isNotEmpty()) {
                                        val params = Bundle()
                                        val utteranceId = UUID.randomUUID().toString()
                                        ttsEngine.value?.speak(nextPageContent, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                                    } else {
                                        // 如果下一页没有内容，停止TTS
                                        isTtsActive.value = false
                                        Log.d("TextToSpeech", "下一页没有内容，停止朗读")
                                    }
                                } else {
                                    // 已到达最后一页，停止TTS
                                    isTtsActive.value = false
                                    Log.d("TextToSpeech", "已到达最后一页，停止朗读")
                                }
                            }
                        }
                    }
                    
                    override fun onError(utteranceId: String) {
                        Log.e("TextToSpeech", "朗读错误: $utteranceId")
                        isTtsActive.value = false
                    }
                })
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
    
    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
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
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
                        slideInVertically(animationSpec = tween(durationMillis = 300)) { it },
                exit = fadeOut(animationSpec = tween(durationMillis = 300)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 300)) { it }
            ) {
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
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
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
                                // 点击左侧区域翻到上一页
                                position.x < leftBound -> {
                                    viewModel.previousPage()
                                }
                                // 点击右侧区域翻到下一页
                                position.x > rightBound -> {
                                    viewModel.nextPage()
                                }
                                // 点击中间区域切换控制栏显示状态
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
                            // 重置拖动状态
                            dragStartX = 0f
                            dragStartY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            // 计算水平和垂直拖动的绝对距离
                            val dragX = change.position.x - dragStartX
                            val dragY = change.position.y - dragStartY
                            
                            // 如果水平拖动大于垂直拖动，且超过阈值，则翻页
                            if (Math.abs(dragX) > Math.abs(dragY) && Math.abs(dragX) > dragThreshold) {
                                // 拖动方向决定翻页方向
                                if (dragX > 0) {
                                    // 向右拖动 - 上一页
                                    viewModel.previousPage()
                                } else {
                                    // 向左拖动 - 下一页
                                    viewModel.nextPage()
                                }
                                // 重置拖动状态，避免连续触发
                                dragStartX = change.position.x
                                dragStartY = change.position.y
                            }
                        }
                    )
                }
        ) {
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
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 当前页内容
                        val currentPageContent = uiState.pages.getOrNull(uiState.currentPage) ?: ""
                        
                        val scrollState = rememberScrollState()
                        
                        // 每次页面变化时重置滚动位置
                        LaunchedEffect(uiState.currentPage) {
                            scrollState.scrollTo(0)
                        }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 24.dp)
                                .verticalScroll(scrollState)
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
    onToggleTts: () -> Unit
) {
    Card(
        modifier = Modifier
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