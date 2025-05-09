package com.wanderreads.ebook.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.wanderreads.ebook.R
import com.wanderreads.ebook.util.PageDirection
import com.wanderreads.ebook.util.WeChatShareUtil
import com.wanderreads.ebook.util.reader.model.ReaderConfig
import kotlinx.coroutines.launch
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay

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
    
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
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
    
    // TTS状态
    var isTtsActive by remember { mutableStateOf(false) }
    
    // 录音状态
    var isRecording by remember { mutableStateOf(false) }
    
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
    
    // WebView引用 (用于EPUB)
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    // 当前阅读配置
    var currentConfig by remember { mutableStateOf(ReaderConfig()) }
    
    // 检测手势
    var startTouchX by remember { mutableStateOf(0f) }
    val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels
    
    // 初始化TTS
    LaunchedEffect(Unit) {
        viewModel.initTts { status ->
            // TTS初始化完成
        }
    }
    
    // 初始化 - 检查当前录音状态
    LaunchedEffect(Unit) {
        isRecording = viewModel.isRecordingActive()
    }
    
    // 定期检查录音状态 - 以处理自动停止录音的情况（例如静音检测）
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)  // 每秒检查一次
            val currentRecordingState = viewModel.isRecordingActive()
            if (isRecording != currentRecordingState) {
                isRecording = currentRecordingState
            }
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
    
    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
            ) {
                TopAppBar(
                    title = { Text(uiState.chapterTitle, color = whiteText) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = whiteText)
                        }
                    },
                    actions = {
                        // TTS按钮
                        IconButton(
                            onClick = {
                                isTtsActive = viewModel.toggleTts()
                            }
                        ) {
                            Icon(
                                imageVector = if (isTtsActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (isTtsActive) "停止朗读" else "开始朗读",
                                tint = whiteText
                            )
                        }
                        
                        // 手动录音按钮
                        IconButton(
                            onClick = { 
                                isRecording = viewModel.toggleManualRecording()
                            }
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = if (isRecording) "停止录音" else "开始录音",
                                tint = if (isRecording) Color.Red else whiteText,
                                modifier = if (isRecording) Modifier.scale(scale.value) else Modifier
                            )
                        }
                        
                        // 录音文件按钮 - 更新图标以区分手动录音按钮
                        IconButton(
                            onClick = { showRecords = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "录音文件列表",
                                tint = whiteText
                            )
                        }
                        
                        // 目录按钮
                        IconButton(
                            onClick = { showToc = true }
                        ) {
                            Icon(Icons.Default.List, contentDescription = "目录", tint = whiteText)
                        }
                        
                        // 分享按钮 - 使用系统分享功能
                        IconButton(
                            onClick = { 
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
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "分享", tint = whiteText)
                        }
                        
                        // 设置按钮
                        IconButton(
                            onClick = { showSettings = true }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "设置", tint = whiteText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeBlue
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeBlue)
                        .padding(8.dp)
                ) {
                    Column {
                        // 阅读进度条
                        LinearProgressIndicator(
                            progress = { uiState.readingProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 页码信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${uiState.currentPage + 1}/${uiState.totalPages}",
                                style = MaterialTheme.typography.bodySmall,
                                color = whiteText
                            )
                            
                            Text(
                                text = "${(uiState.readingProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = whiteText
                            )
                        }
                        
                        // 翻页按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
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
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(navyBlueBackground)
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            // 点击左侧1/3区域，向前翻页
                            if (offset.x < size.width / 3) {
                                viewModel.navigatePage(PageDirection.PREVIOUS)
                            } 
                            // 点击右侧1/3区域，向后翻页
                            else if (offset.x > 2 * size.width / 3) {
                                viewModel.navigatePage(PageDirection.NEXT)
                            } 
                            // 点击中间区域，显示/隐藏控制栏
                            else {
                                showControls = !showControls
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
                                        // 区分左右点击，用于翻页
                                        if (x < width / 3) {
                                            coroutineScope.launch {
                                                viewModel.navigatePage(PageDirection.PREVIOUS)
                                            }
                                        } else if (x > 2 * width / 3) {
                                            coroutineScope.launch {
                                                viewModel.navigatePage(PageDirection.NEXT)
                                            }
                                        } else {
                                            showControls = !showControls
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
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            formattedText.forEach { paragraph ->
                                if (paragraph.isNotBlank()) {
                                    Text(
                                        text = paragraph,
                                        fontSize = currentConfig.fontSize.sp,
                                        lineHeight = (currentConfig.fontSize * 1.5).sp,
                                        color = whiteText,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
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
    }
    
    // 显示录音文件列表
    if (showRecords) {
        RecordsScreen(
            records = uiState.records,
            onDismiss = { showRecords = false },
            onPlayRecord = { record ->
                viewModel.playRecord(record)
            },
            onPauseRecord = { record ->
                viewModel.pauseRecord(record)
            },
            currentPlayingRecordId = uiState.currentPlayingRecordId
        )
    }
    
    // 显示无障碍服务引导对话框
    if (uiState.showAccessibilityGuide) {
        AlertDialog(
            onDismissRequest = { 
                viewModel.dismissAccessibilityGuide() 
            },
            title = { 
                Text(
                    text = stringResource(R.string.huawei_accessibility_guide_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { 
                Text(
                    text = stringResource(R.string.huawei_accessibility_guide_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissAccessibilityGuide()
                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text(stringResource(R.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        viewModel.dismissAccessibilityGuide() 
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { webView ->
                webView.loadUrl("about:blank")
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
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
    // 首先按段落分割（空行分隔）
    val paragraphs = text.split("\n\n", "\r\n\r\n")
    
    // 如果只有一个段落，可能没有空行分隔，尝试按单个换行符分割
    if (paragraphs.size <= 1 && text.contains("\n")) {
        return text.split("\n", "\r\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    
    return paragraphs.map { it.trim() }
} 