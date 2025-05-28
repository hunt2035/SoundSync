package com.wanderreads.ebook.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.wanderreads.ebook.util.PageDirection
import com.wanderreads.ebook.util.reader.model.ReaderConfig
import kotlinx.coroutines.launch
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.delay
import com.wanderreads.ebook.domain.model.SynthesisParams
import com.wanderreads.ebook.domain.model.SynthesisRange
import com.wanderreads.ebook.ui.components.SynthesisDialog
import com.wanderreads.ebook.ui.components.SynthesisProgressDialog
import com.wanderreads.ebook.service.TtsSynthesisService
import com.wanderreads.ebook.service.TtsSynthesisService.SynthesisState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.WindowInsets
import android.util.Log
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import com.wanderreads.ebook.ui.components.AudioPlayerControl
import com.wanderreads.ebook.util.TtsManager
import com.wanderreads.ebook.ui.components.HighlightedText
import com.wanderreads.ebook.util.AppTextUtils

/**
 * 统一阅读器屏幕
 * 支持所有格式的电子书
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedReaderScreen(
    viewModel: UnifiedReaderViewModel,
    onNavigateBack: () -> Unit
) {
    // 定义颜色
    val navyBlueBackground = Color(0xFF0A1929) // 墨蓝色背景
    val themeBlue = Color(0xFF1976D2) // 主题蓝色（工具栏）
    val whiteText = Color.White // 白色文字
    val statusBarBackground = Color.White.copy(alpha = 0.7f) // 半透明白色背景
    
    val uiState by viewModel.uiState.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // 获取屏幕宽度(像素)
    val screenWidthPx = LocalContext.current.resources.displayMetrics.widthPixels
    val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels
    
    // 计算屏幕宽度(dp)
    val density = LocalContext.current.resources.displayMetrics.density
    val screenWidthDp = (screenWidthPx / density).toInt()
    
    // 音频控件宽度(屏幕宽度的65%)
    val audioControlWidth = (screenWidthDp * 0.65f).toInt()
    
    // 控制顶部和底部工具栏的显示
    var showControls by remember { mutableStateOf(false) }
    
    // 控制目录显示
    var showToc by remember { mutableStateOf(false) }
    
    // 控制设置面板显示
    val settingsSheetState = rememberModalBottomSheetState()
    var showSettings by remember { mutableStateOf(false) }
    
    // 控制录音文件列表显示
    var showRecords by remember { mutableStateOf(false) }
    
    // 控制菜单显示
    var showMenu by remember { mutableStateOf(false) }
    
    // 设置面板当前选项卡
    var currentSettingsTab by remember { mutableIntStateOf(0) }
    
    // 创建无限循环的动画效果用于录音图标
    val infiniteTransition = rememberInfiniteTransition(label = "recording animation")
    val scale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording scale animation"
    )
    
    // 音频播放控件的显示
    var showAudioControl by remember { mutableStateOf(false) }
    
    // 音频播放控件的位置 - 默认显示在距离屏幕底部270dp处，左右居中
    var audioControlPosition by remember { 
        mutableStateOf(IntOffset(
            ((screenWidthPx - (audioControlWidth * density)) / 2).toInt(), 
            (screenHeightPx - 270 * density).toInt() // 距离屏幕底部270dp
        ).toOffset()) 
    }
    
    // WebView引用 (用于EPUB)
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    // 当前阅读配置
    var currentConfig by remember { mutableStateOf(ReaderConfig()) }
    
    // 检测手势
    var startTouchX by remember { mutableStateOf(0f) }
    
    // 添加TTS活动状态变量
    var isTtsActive by remember { mutableStateOf(false) }
    
    // 收集高亮状态
    val highlightState by viewModel.highlightState.collectAsState()
    
    // 初始化TTS
    LaunchedEffect(Unit) {
        viewModel.initTts { status ->
            // TTS初始化完成
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(context, "TTS引擎初始化失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 监听TTS状态变化
    LaunchedEffect(ttsState) {
        // 根据TTS状态决定是否显示控件
        val ttsIsActive = ttsState.status != TtsManager.STATUS_STOPPED
        
        // 更新isTtsActive变量，保持与全局TTS状态一致
        isTtsActive = ttsIsActive
        
        // 只有当控制栏显示且TTS活跃时才显示音频控件
        showAudioControl = showControls && ttsIsActive
        
        // 如果正在某页朗读，但当前页面不是朗读页，可以添加提示或自动跳转
        if (ttsIsActive && ttsState.currentPage > 0 && ttsState.currentPage != uiState.currentPage) {
            // 可以选择自动跳转到朗读页面
            // viewModel.navigateToTtsPage()
            
            // 或者显示提示
            Toast.makeText(
                context, 
                "正在第${ttsState.currentPage + 1}页朗读", 
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // 监听TTS活动状态变化
    LaunchedEffect(isTtsActive) {
        if (isTtsActive && showControls) {
            showAudioControl = true
        }
    }
    
    // HTML样式（添加CSS样式，设置文字为白色，背景为墨蓝色）
    val cssStyle = """
        <style>
            body {
                background-color: #0A1929;
                color: white;
                font-family: system-ui, -apple-system, sans-serif;
                line-height: 1.5;
                padding: 16px;
                margin: 0;
            }
            a {
                color: #64B5F6;
            }
            img {
                max-width: 100%;
            }
        </style>
    """.trimIndent()
    
    // 处理内容变化
    LaunchedEffect(uiState.currentContent) {
        // 如果是HTML内容且WebView已初始化，加载内容
        if (uiState.currentContent?.html != null && webViewInstance != null) {
            // 添加CSS样式到HTML内容中
            val htmlWithStyle = uiState.currentContent?.html?.let { html ->
                if (html.contains("<head>")) {
                    html.replace("<head>", "<head>$cssStyle")
                } else {
                    "<html><head>$cssStyle</head><body>$html</body></html>"
                }
            } ?: ""
            
            webViewInstance?.loadDataWithBaseURL(
                "file:///android_asset/",
                htmlWithStyle,
                "text/html",
                "UTF-8",
                null
            )
        }
    }
    
    // 语音合成对话框状态
    var showSynthesisDialog by remember { mutableStateOf(false) }
    
    // 语音合成进度对话框状态
    var showSynthesisProgressDialog by remember { mutableStateOf(false) }
    
    // 收集合成状态
    val synthesisState by viewModel.synthesisState.collectAsState()
    
    // 监听合成状态变化
    LaunchedEffect(synthesisState) {
        synthesisState?.let { state ->
            // 如果出现错误，更新错误状态
            if (state.status == TtsSynthesisService.STATUS_ERROR) {
                // 显示错误信息
                state.message?.let { errorMsg ->
                    if (errorMsg.isNotBlank()) {
                        viewModel.clearError() // 先清除旧的错误
                        viewModel.handleSynthesisError("语音合成失败: $errorMsg")
                    }
                }
            }
            
            // 不再在这里控制对话框的显示与隐藏
            // 只有在合成完成、出错或取消时，才允许用户手动关闭对话框
        }
    }
    
    // 显示错误对话框
    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("提示") },
            text = { Text(uiState.error ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
            ) {
                TopAppBar(
                    title = { 
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(uiState.chapterTitle, color = whiteText)
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = whiteText)
                            }
                        }
                    },
                    actions = {
                        // 三个点菜单按钮
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showMenu = !showMenu }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = whiteText
                                )
                            }
                        }
                        
                        // 下拉菜单
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(navyBlueBackground)
                        ) {
                            DropdownMenuItem(
                                text = { Text("查看目录", color = whiteText) },
                                onClick = { 
                                    showMenu = false
                                    showToc = true 
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = "目录",
                                        tint = whiteText
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isTtsActive) "停止朗读" else "朗读本页", color = whiteText) },
                                onClick = { 
                                    showMenu = false
                                    isTtsActive = viewModel.toggleTts() 
                                    if (isTtsActive && showControls) {
                                        showAudioControl = true
                                    } else {
                                        showAudioControl = false
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isTtsActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = if (isTtsActive) "停止朗读" else "朗读本页",
                                        tint = whiteText
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("合成语音", color = whiteText) },
                                onClick = { 
                                    showMenu = false
                                    showSynthesisDialog = true  // 显示语音合成对话框
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "合成语音",
                                        tint = whiteText
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("语音列表", color = whiteText) },
                                onClick = { 
                                    showMenu = false
                                    viewModel.showSynthesizedAudioList()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.MicNone,
                                        contentDescription = "语音列表",
                                        tint = whiteText
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("分享内容", color = whiteText) },
                                onClick = { 
                                    showMenu = false
                                    // 获取当前页文本内容
                                    val currentText = uiState.currentContent?.text ?: ""
                                    if (currentText.isNotEmpty()) {
                                        // 准备分享文本，包含书名和当前阅读内容
                                        val bookTitle = uiState.book?.title ?: "电子书"
                                        val shareText = "我正在阅读《$bookTitle》，分享一段内容：\n\n${
                                            if (currentText.length > 300) 
                                                currentText.substring(0, 300) + "..." 
                                            else 
                                                currentText
                                        }"
                                        
                                        // 使用系统分享功能
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        
                                        context.startActivity(Intent.createChooser(shareIntent, "分享到"))
                                    } else {
                                        Toast.makeText(context, "当前页面没有可分享的文本内容", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "分享",
                                        tint = whiteText
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("阅读设置", color = whiteText) },
                                onClick = { 
                                    showMenu = false
                                    showSettings = true 
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "阅读设置",
                                        tint = whiteText
                                    )
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeBlue
                    ),
                    modifier = Modifier.height(64.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeBlue)
                        .padding(8.dp)
                ) {
                    // 底部工具栏第一行：翻页控制
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 上一页按钮
                        IconButton(
                            onClick = { viewModel.navigatePage(PageDirection.PREVIOUS) },
                            enabled = uiState.currentPage > 0
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowLeft,
                                contentDescription = "上一页",
                                tint = whiteText
                            )
                        }
                        
                        // 滑杆控制翻页进度
                        Slider(
                            value = uiState.readingProgress,
                            onValueChange = { progress ->
                                val targetPage = (progress * (uiState.totalPages - 1)).toInt()
                                viewModel.goToPage(targetPage)
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = whiteText,
                                activeTrackColor = whiteText,
                                inactiveTrackColor = whiteText.copy(alpha = 0.3f)
                            )
                        )
                        
                        // 下一页按钮
                        IconButton(
                            onClick = { viewModel.navigatePage(PageDirection.NEXT) },
                            enabled = uiState.currentPage < uiState.totalPages - 1
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "下一页",
                                tint = whiteText
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 底部工具栏第二行：功能图标
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 目录图标
                        IconButton(onClick = { showToc = true }) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = "目录",
                                tint = whiteText
                            )
                        }
                        
                        // 朗读本页按钮 - 从图标按钮改为文字按钮
                        androidx.compose.material3.Button(
                            onClick = { 
                                when (ttsState.status) {
                                    TtsManager.STATUS_STOPPED -> {
                                        // 从停止状态开始朗读本页
                                        viewModel.toggleTts()
                                    }
                                    TtsManager.STATUS_PAUSED -> {
                                        // 从暂停状态重新开始朗读本页（先停止再开始）
                                        viewModel.stopTts()
                                        viewModel.toggleTts()
                                    }
                                    else -> {
                                        // 其他状态不应该触发点击
                                    }
                                }
                            },
                            enabled = ttsState.status != TtsManager.STATUS_PLAYING, // 如果正在朗读状态，禁用按钮
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (ttsState.status != TtsManager.STATUS_STOPPED) Color(0xFF64B5F6) else themeBlue,
                                contentColor = whiteText,
                                disabledContainerColor = Color(0xFF808080) // 明显的灰色背景
                            ),
                            modifier = Modifier.graphicsLayer {
                                // 当处于朗读状态时，降低按钮透明度，增强灰色效果
                                alpha = if (ttsState.status == TtsManager.STATUS_PLAYING) 0.7f else 1.0f
                            }
                        ) {
                            Text(
                                text = when (ttsState.status) {
                                    TtsManager.STATUS_PLAYING -> "朗读中..."
                                    TtsManager.STATUS_PAUSED -> "朗读本页"
                                    else -> "朗读本页"
                                },
                                color = if (ttsState.status == TtsManager.STATUS_PLAYING) Color.LightGray else whiteText
                            )
                        }
                        
                        // 合成语音图标
                        IconButton(onClick = { 
                            showSynthesisDialog = true  // 显示语音合成对话框
                        }) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "合成语音",
                                tint = whiteText
                            )
                        }
                        
                        // 语音列表图标
                        IconButton(onClick = { 
                            viewModel.showSynthesizedAudioList()
                        }) {
                            Icon(
                                Icons.Default.MicNone,
                                contentDescription = "语音列表",
                                tint = whiteText
                            )
                        }
                        
                        // 阅读设置图标
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "阅读设置",
                                tint = whiteText
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // 使用Column替代Box作为主布局
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(navyBlueBackground)
                .padding(paddingValues)
        ) {
            // 内容区域 - 占据90%的空间
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (showControls) {
                                    // 当控制栏显示时，任何区域点击都会隐藏控制栏和朗读悬浮窗
                                    showControls = false
                                    if (showAudioControl) {
                                        showAudioControl = false
                                    }
                                } else {
                                    // 控制栏隐藏时
                                    // 点击左侧1/3区域，向前翻页
                                    if (offset.x < size.width / 3) {
                                        viewModel.navigatePage(PageDirection.PREVIOUS)
                                    } 
                                    // 点击右侧1/3区域，向后翻页
                                    else if (offset.x > 2 * size.width / 3) {
                                        viewModel.navigatePage(PageDirection.NEXT)
                                    } 
                                    // 点击中间区域，同时显示控制栏和朗读控制悬浮窗
                                    else {
                                        showControls = true
                                        // 如果TTS处于朗读状态或暂停状态，显示音频控制悬浮窗
                                        if (ttsState.status == TtsManager.STATUS_PLAYING || 
                                            ttsState.status == TtsManager.STATUS_PAUSED) {
                                            showAudioControl = true
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                // 内容加载指示器
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = whiteText
                    )
                } 
                // 错误信息
                else if (uiState.error != null) {
                    Text(
                        text = uiState.error ?: "未知错误",
                        color = Color.Red,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                } 
                // 内容显示
                else {
                    // 检查是否为HTML内容（通常是EPUB格式）
                    if (uiState.currentContent?.html != null) {
                        // 使用WebView渲染HTML内容
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.textZoom = currentConfig.fontSize * 5
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.domStorageEnabled = true
                                    settings.defaultTextEncodingName = "UTF-8"
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            // 注入JavaScript以支持点击事件
                                            view?.loadUrl("""
                                                javascript:(function() {
                                                    document.body.addEventListener('click', function(e) {
                                                        var x = e.clientX;
                                                        var width = window.innerWidth;
                                                        Android.onPageTap(x, width);
                                                    });
                                                })()
                                            """.trimIndent())
                                        }
                                    }
                                    webChromeClient = WebChromeClient()
                                    setBackgroundColor(android.graphics.Color.parseColor("#0A1929"))
                                    
                                    // 添加JavaScript接口
                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onPageTap(x: Float, width: Float) {
                                            coroutineScope.launch {
                                                if (showControls) {
                                                    // 当控制栏显示时，任何区域点击都会隐藏控制栏和朗读悬浮窗
                                                    showControls = false
                                                    showAudioControl = false
                                                } else {
                                                    // 控制栏隐藏时
                                                    // 点击左侧1/3区域，向前翻页
                                                    if (x < width / 3) {
                                                        viewModel.navigatePage(PageDirection.PREVIOUS)
                                                    } 
                                                    // 点击右侧1/3区域，向后翻页
                                                    else if (x > 2 * width / 3) {
                                                        viewModel.navigatePage(PageDirection.NEXT)
                                                    } 
                                                    // 点击中间区域，同时显示控制栏和朗读控制悬浮窗
                                                    else {
                                                        showControls = true
                                                        // 如果TTS处于朗读状态或暂停状态，显示音频控制悬浮窗
                                                        if (ttsState.status == TtsManager.STATUS_PLAYING || 
                                                            ttsState.status == TtsManager.STATUS_PAUSED) {
                                                            showAudioControl = true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        @JavascriptInterface
                                        fun getTextContent(): String {
                                            return uiState.currentContent?.text ?: ""
                                        }
                                    }, "Android")
                                    
                                    // 加载初始内容（带样式）
                                    uiState.currentContent?.html?.let { html ->
                                        val enhancedStyle = """
                                            <style>
                                                body {
                                                    background-color: #0A1929;
                                                    color: white;
                                                    font-family: system-ui, -apple-system, sans-serif;
                                                    line-height: 1.5;
                                                    padding: 16px;
                                                    margin: 0;
                                                }
                                                p {
                                                    margin-bottom: 1em;
                                                    text-indent: 2em;
                                                    line-height: 1.5;
                                                }
                                                h1, h2, h3, h4, h5, h6 {
                                                    margin-top: 1.5em;
                                                    margin-bottom: 0.5em;
                                                    color: white;
                                                }
                                                a {
                                                    color: #64B5F6;
                                                    text-decoration: none;
                                                }
                                                img {
                                                    max-width: 100%;
                                                    height: auto;
                                                    display: block;
                                                    margin: 1em auto;
                                                }
                                            </style>
                                        """.trimIndent()
                                        
                                        val htmlWithStyle = if (html.contains("<head>")) {
                                            html.replace("<head>", "<head>$enhancedStyle")
                                        } else {
                                            "<html><head>$enhancedStyle</head><body>$html</body></html>"
                                        }
                                        
                                        loadDataWithBaseURL(
                                            "file:///android_asset/",
                                            htmlWithStyle,
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                    }
                                    
                                    // 保存WebView实例
                                    webViewInstance = this
                                }
                            },
                            update = { webView ->
                                // 更新内容
                                uiState.currentContent?.html?.let { html ->
                                    if (webView.url == null) {
                                        val enhancedStyle = """
                                            <style>
                                                body {
                                                    background-color: #0A1929;
                                                    color: white;
                                                    font-family: system-ui, -apple-system, sans-serif;
                                                    line-height: 1.5;
                                                    padding: 16px;
                                                    margin: 0;
                                                }
                                                p {
                                                    margin-bottom: 1em;
                                                    text-indent: 2em;
                                                    line-height: 1.5;
                                                }
                                                h1, h2, h3, h4, h5, h6 {
                                                    margin-top: 1.5em;
                                                    margin-bottom: 0.5em;
                                                    color: white;
                                                }
                                                a {
                                                    color: #64B5F6;
                                                    text-decoration: none;
                                                }
                                                img {
                                                    max-width: 100%;
                                                    height: auto;
                                                    display: block;
                                                    margin: 1em auto;
                                                }
                                            </style>
                                        """.trimIndent()
                                        
                                        val htmlWithStyle = if (html.contains("<head>")) {
                                            html.replace("<head>", "<head>$enhancedStyle")
                                        } else {
                                            "<html><head>$enhancedStyle</head><body>$html</body></html>"
                                        }
                                        
                                        webView.loadDataWithBaseURL(
                                            "file:///android_asset/",
                                            htmlWithStyle,
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                    }
                                }
                                
                                // 更新字体大小
                                webView.settings.textZoom = currentConfig.fontSize * 5
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 纯文本内容（TXT等格式）
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(navyBlueBackground)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            val textContent = uiState.currentContent?.text ?: ""
                            val formattedText = remember(textContent) {
                                formatTextContent(textContent)
                            }
                            
                            // 设置整个页面的句子序列
                            LaunchedEffect(textContent) {
                                viewModel.setPageSentences(textContent)
                            }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // 由于整个页面就是一个段落，所以这里只有一个元素
                                if (formattedText.isNotEmpty()) {
                                    val paragraph = formattedText[0]
                                    if (paragraph.isNotBlank()) {
                                        // 从整个页面句子序列中获取该段落对应的句子
                                        val pageSentences = viewModel.getSentencesForParagraph(paragraph)
                                        HighlightedText(
                                            text = paragraph,
                                            sentences = pageSentences,
                                            highlightIndex = highlightState.currentSentenceIndex,
                                            isHighlighting = highlightState.isHighlighting && 
                                                (ttsState.status == TtsManager.STATUS_PLAYING || ttsState.status == TtsManager.STATUS_PAUSED),
                                            ttsStatus = ttsState.status,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = currentConfig.fontSize.sp,
                                                lineHeight = (currentConfig.fontSize * 1.5).sp,
                                                fontFamily = FontFamily.Serif
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 添加AudioPlayerControl组件
                if (showAudioControl && showControls) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { 
                                IntOffset(
                                    audioControlPosition.x.roundToInt(), 
                                    audioControlPosition.y.roundToInt()
                                ) 
                            }
                    ) {
                        AudioPlayerControl(
                            ttsStatus = ttsState.status,
                            onPlayPause = {
                                if (ttsState.status == TtsManager.STATUS_PLAYING) {
                                    viewModel.pauseTts()
                                } else {
                                    viewModel.resumeTts()
                                }
                            },
                            onStop = {
                                viewModel.stopTts()
                            },
                            onOffsetChange = { offset ->
                                audioControlPosition = audioControlPosition.copy(
                                    x = (audioControlPosition.x + offset.x).coerceIn(0f, screenWidthPx - (audioControlWidth * density)),
                                    y = (audioControlPosition.y + offset.y).coerceIn(0f, screenHeightPx - 270 * density)
                                )
                            },
                            modifier = Modifier.width(audioControlWidth.dp)
                        )
                    }
                }
            }
            
            // 底部状态栏 - 固定高度32dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(statusBarBackground) // 使用半透明白色背景
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧2/3显示书名
                Text(
                    text = uiState.book?.title ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(2f)
                )
                
                // 右侧1/3显示页码和阅读百分比
                Text(
                    text = "${uiState.currentPage + 1}/${uiState.totalPages} (${(uiState.readingProgress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 目录底部弹窗
        if (showToc) {
            ModalBottomSheet(
                onDismissRequest = { showToc = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = navyBlueBackground
            ) {
                Text(
                    text = "目录",
                    style = MaterialTheme.typography.titleMedium,
                    color = whiteText,
                    modifier = Modifier.padding(16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(viewModel.getChapters()) { chapter ->
                        Text(
                            text = chapter.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable {
                                    coroutineScope.launch {
                                        viewModel.goToChapter(chapter.index)
                                        showToc = false
                                    }
                                },
                            color = if (chapter.index == uiState.currentChapter)
                                Color(0xFF64B5F6) // 亮蓝色
                            else
                                whiteText
                        )
                    }
                }
            }
        }
        
        // 设置面板底部弹窗
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = settingsSheetState,
                containerColor = navyBlueBackground
            ) {
                Text(
                    text = "阅读设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = whiteText,
                    modifier = Modifier.padding(16.dp)
                )
                
                // 字体大小设置
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "字体大小",
                        modifier = Modifier.width(80.dp),
                        color = whiteText
                    )
                    
                    Slider(
                        value = currentConfig.fontSize.toFloat(),
                        onValueChange = { 
                            currentConfig = currentConfig.copy(fontSize = it.toInt())
                        },
                        onValueChangeFinished = {
                            viewModel.updateConfig(currentConfig)
                        },
                        valueRange = 12f..30f,
                        steps = 9,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = "${currentConfig.fontSize}",
                        modifier = Modifier.width(30.dp),
                        textAlign = TextAlign.End,
                        color = whiteText
                    )
                }
                
                // 行高设置
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "行间距",
                        modifier = Modifier.width(80.dp),
                        color = whiteText
                    )
                    
                    Slider(
                        value = currentConfig.lineHeight,
                        onValueChange = { 
                            currentConfig = currentConfig.copy(lineHeight = it)
                        },
                        onValueChangeFinished = {
                            viewModel.updateConfig(currentConfig)
                        },
                        valueRange = 1.0f..2.0f,
                        steps = 4,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = "%.1f".format(currentConfig.lineHeight),
                        modifier = Modifier.width(30.dp),
                        textAlign = TextAlign.End,
                        color = whiteText
                    )
                }
                
                // 主题设置 (亮/暗)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "暗色模式",
                        modifier = Modifier.width(80.dp),
                        color = whiteText
                    )
                    
                    Switch(
                        checked = currentConfig.isDarkMode,
                        onCheckedChange = {
                            currentConfig = currentConfig.copy(isDarkMode = it)
                            viewModel.updateConfig(currentConfig)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        
        // 添加语音合成对话框
        if (showSynthesisDialog) {
            SynthesisDialog(
                onDismiss = { showSynthesisDialog = false },
                onStartSynthesis = { params ->
                    viewModel.startSynthesis(params)
                    showSynthesisDialog = false
                    // 立即显示进度对话框，不等待状态更新
                    showSynthesisProgressDialog = true
                },
                // 添加内容检查
                hasContent = !uiState.currentContent?.text.isNullOrBlank()
            )
        }
        
        // 添加语音合成进度对话框
        if (showSynthesisProgressDialog) {
            // 即使synthesisState为null，也显示一个初始的进度对话框
            val state = synthesisState ?: SynthesisState(
                status = TtsSynthesisService.STATUS_PREPARING,
                message = "正在准备语音合成...",
                progress = 0
            )
            
            SynthesisProgressDialog(
                progress = state.progress,
                message = state.message,
                onDismiss = {
                    // 允许在错误、完成或取消状态下点击外部关闭
                    if (state.status == TtsSynthesisService.STATUS_ERROR || 
                        state.status == TtsSynthesisService.STATUS_COMPLETED ||
                        state.status == TtsSynthesisService.STATUS_CANCELED) {
                        showSynthesisProgressDialog = false
                    }
                    // 在合成进行中，点击外部不关闭对话框
                },
                onCancel = {
                    viewModel.cancelSynthesis()
                    // 点击取消按钮时，直接关闭对话框
                    showSynthesisProgressDialog = false
                }
            )
        }
        
        // 合成语音列表界面
        if (uiState.showSynthesizedAudioList) {
            // 当显示语音列表时，强制隐藏顶部和底部菜单栏
            showControls = false
            
            SynthesizedAudioListScreen(
                records = uiState.synthesizedAudioList,
                onDismiss = { 
                    viewModel.hideSynthesizedAudioList() 
                    // 退出语音列表时，保持控制栏隐藏状态
                },
                onPlayRecord = { record ->
                    if (record.id == uiState.currentPlayingRecordId && uiState.isAudioPlaying) {
                        viewModel.pauseAudioPlayback()
                    } else if (record.id == uiState.currentPlayingRecordId && !uiState.isAudioPlaying) {
                        viewModel.resumeAudioPlayback()
                    } else {
                        viewModel.playAudioRecord(record)
                    }
                },
                onPauseRecord = { viewModel.pauseAudioPlayback() },
                onRenameRecord = { record, newName ->
                    viewModel.renameAudioRecord(record, newName)
                },
                onDeleteRecord = { record ->
                    viewModel.deleteAudioRecord(record)
                },
                onSeekTo = { record, position ->
                    viewModel.seekToPosition(record, position)
                },
                currentPlayingRecordId = uiState.currentPlayingRecordId,
                currentPlaybackPosition = uiState.currentPlaybackPosition,
                totalDuration = uiState.totalAudioDuration,
                isAudioPlaying = uiState.isAudioPlaying
            )
        }
    }
}

@Composable
private fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun textClickableModifier(onClick: () -> Unit): Modifier {
    return Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = { onClick() })
    }
}

/**
 * 格式化文本内容，将单个文本字符串分割成段落列表
 */
private fun formatTextContent(text: String): List<String> {
    // 将整个页面作为一个段落返回，不再细分段落
    return listOf(text)
}

// 将IntOffset转换为Offset的扩展函数
private fun IntOffset.toOffset(): Offset {
    return Offset(x.toFloat(), y.toFloat())
} 