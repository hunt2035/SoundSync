package com.wanderreads.ebook.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wanderreads.ebook.util.PageDirection
import java.util.Locale
import java.util.UUID

/**
 * EPUB阅读器屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    viewModel: EpubReaderViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 控制顶部和底部工具栏的显示
    var showControls by remember { mutableStateOf(false) }
    // 控制目录显示
    var showToc by remember { mutableStateOf(false) }
    
    // 控制设置面板显示
    val settingsSheetState = rememberModalBottomSheetState()
    var showSettings by remember { mutableStateOf(false) }
    
    // 设置面板当前选项卡
    var currentSettingsTab by remember { mutableIntStateOf(0) }

    // 跟踪手势方向和距离
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    val dragThreshold = 50f // 触发翻页的最小拖动距离
    
    // WebView引用
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    // TTS状态
    var isTtsActive by remember { mutableStateOf(false) }
    
    // TTS引擎
    val ttsEngine = remember { 
        mutableStateOf<TextToSpeech?>(null) 
    }
    
    // 初始化TTS引擎
    LaunchedEffect(Unit) {
        ttsEngine.value = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine.value?.language = Locale.CHINESE
            }
        }
    }
    
    // 设置TTS完成监听器
    DisposableEffect(ttsEngine.value) {
        val tts = ttsEngine.value
        
        if (tts != null) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // 朗读开始
                }
                
                override fun onDone(utteranceId: String?) {
                    // 朗读完成，翻到下一页
                    if (isTtsActive) {
                        viewModel.navigatePage(PageDirection.NEXT)
                        
                        // 获取HTML内容并执行JS提取可见文本
                        webViewInstance?.evaluateJavascript(
                            """
                            (function() {
                                var text = '';
                                var content = document.body.textContent || document.body.innerText;
                                if (content) {
                                    text = content.trim().substring(0, 10000); // 限制长度
                                }
                                return text;
                            })();
                            """.trimIndent(),
                            { result: String ->
                                // 处理引号
                                var textContent = result
                                if (textContent.startsWith("\"") && textContent.endsWith("\"")) {
                                    textContent = textContent.substring(1, textContent.length - 1)
                                }
                                // 处理转义字符
                                textContent = textContent.replace("\\n", "\n")
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                                
                                // 朗读提取的文本
                                if (textContent.isNotEmpty() && isTtsActive) {
                                    val params = Bundle()
                                    tts.speak(
                                        textContent,
                                        TextToSpeech.QUEUE_FLUSH,
                                        params,
                                        "TTS_${UUID.randomUUID()}"
                                    )
                                } else {
                                    isTtsActive = false
                                }
                            }
                        )
                    }
                }
                
                override fun onError(utteranceId: String?) {
                    // 朗读错误
                    isTtsActive = false
                }
            })
        }
        
        onDispose {
            ttsEngine.value?.stop()
            ttsEngine.value?.shutdown()
        }
    }
    
    // 开始或停止TTS朗读
    val toggleTts = {
        val tts = ttsEngine.value
        
        if (isTtsActive) {
            // 停止朗读
            tts?.stop()
            isTtsActive = false
        } else if (tts != null) {
            // 开始朗读当前页
            isTtsActive = true
            
            // 获取HTML内容并执行JS提取可见文本
            webViewInstance?.evaluateJavascript(
                """
                (function() {
                    var text = '';
                    var content = document.body.textContent || document.body.innerText;
                    if (content) {
                        text = content.trim().substring(0, 10000); // 限制长度
                    }
                    return text;
                })();
                """.trimIndent(),
                { result: String ->
                    // 处理引号
                    var textContent = result
                    if (textContent.startsWith("\"") && textContent.endsWith("\"")) {
                        textContent = textContent.substring(1, textContent.length - 1)
                    }
                    // 处理转义字符
                    textContent = textContent.replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                    
                    // 朗读提取的文本
                    if (textContent.isNotEmpty()) {
                        val params = Bundle()
                        tts.speak(
                            textContent,
                            TextToSpeech.QUEUE_FLUSH,
                            params,
                            "TTS_${UUID.randomUUID()}"
                        )
                    } else {
                        isTtsActive = false
                    }
                }
            )
        }
    }

    // 点击屏幕时切换控制栏显示状态
    val toggleControls: () -> Unit = {
        showControls = !showControls
    }

    // JS接口
    val jsInterface = remember { EpubReaderJsInterface(viewModel, toggleControls) }

    // 应用主题或字体修改
    LaunchedEffect(
        uiState.fontSize, 
        uiState.isDarkMode, 
        uiState.lineHeight, 
        uiState.fontFamily
    ) {
        webViewInstance?.let { view ->
            // 应用字体大小
            view.loadUrl(viewModel.getFontSizeJs())
            // 应用行高
            view.loadUrl(viewModel.getLineHeightJs())
            // 应用主题
            view.loadUrl(viewModel.getThemeJs())
            // 应用字体
            view.loadUrl(viewModel.getFontFamilyJs())
        }
    }
    
    // 应用页面导航
    LaunchedEffect(uiState.pageNavigationJs) {
        uiState.pageNavigationJs?.let { js ->
            webViewInstance?.loadUrl(js)
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                        slideInVertically(animationSpec = tween(durationMillis = 200)) { -it },
                exit = fadeOut(animationSpec = tween(durationMillis = 200)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 200)) { -it }
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.book?.title ?: "电子书阅读器",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        // 目录按钮
                        IconButton(onClick = { showToc = true }) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "目录"
                            )
                        }
                        
                        // 暗色/亮色模式快速切换
                        IconButton(onClick = { viewModel.toggleTheme(!uiState.isDarkMode) }) {
                            Icon(
                                imageVector = if (uiState.isDarkMode) 
                                    Icons.Filled.Brightness7 else Icons.Filled.Brightness4,
                                contentDescription = if (uiState.isDarkMode) 
                                    "亮色模式" else "暗色模式"
                            )
                        }
                        
                        // 设置按钮
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                        slideInVertically(animationSpec = tween(durationMillis = 200)) { it },
                exit = fadeOut(animationSpec = tween(durationMillis = 200)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 200)) { it }
            ) {
                EnhancedBottomEpubControls(
                    currentChapter = uiState.currentChapterIndex + 1,
                    totalChapters = uiState.totalChapters,
                    currentPage = uiState.currentPage + 1,
                    totalPages = uiState.totalPages,
                    isTtsActive = isTtsActive,
                    onOpenToc = { showToc = true },
                    onOpenSettings = { showSettings = true },
                    onToggleTts = toggleTts,
                    onPreviousPage = { viewModel.navigatePage(PageDirection.PREVIOUS) },
                    onNextPage = { viewModel.navigatePage(PageDirection.NEXT) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // 手势检测
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val screenWidth = size.width
                            // 左侧区域 - 上一页
                            if (offset.x < screenWidth * 0.3f) {
                                viewModel.navigatePage(PageDirection.PREVIOUS)
                            }
                            // 右侧区域 - 下一页
                            else if (offset.x > screenWidth * 0.7f) {
                                viewModel.navigatePage(PageDirection.NEXT)
                            }
                            // 中间区域 - 控制栏
                            else {
                                toggleControls()
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
                                    viewModel.navigatePage(PageDirection.PREVIOUS)
                                } else {
                                    // 向左拖动 - 下一页
                                    viewModel.navigatePage(PageDirection.NEXT)
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
                        CircularProgressIndicator()
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
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "未知错误",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                uiState.currentChapterHtml != null -> {
                    // 内容显示 - 使用WebView
                    EnhancedEpubWebView(
                        html = uiState.currentChapterHtml!!,
                        backgroundColor = if (uiState.isDarkMode) 
                            Color(0xFF121212)
                        else 
                            Color(0xFFFAFAFA),
                        onWebViewCreated = { 
                            webViewInstance = it 
                            // 设置JavaScript接口 - 确保传入的是有效的带有JavascriptInterface注解的接口
                            it?.addJavascriptInterface(jsInterface as EpubReaderJsInterface, "EpubReader")
                        },
                        onPageLoadFinished = viewModel::onPageLoadFinished,
                        jsInterface = jsInterface
                    )
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
        
        // 目录底部弹窗
        if (showToc) {
            EnhancedTocBottomSheet(
                chapters = uiState.chapters,
                currentChapterIndex = uiState.currentChapterIndex,
                onChapterSelected = { index ->
                    viewModel.navigateToChapter(index)
                    showToc = false
                },
                onDismiss = { showToc = false }
            )
        }
        
        // 设置底部弹窗
        if (showSettings) {
            EnhancedSettingsBottomSheet(
                currentTab = currentSettingsTab,
                onTabSelected = { currentSettingsTab = it },
                fontSize = uiState.fontSize,
                onFontSizeChange = viewModel::changeFontSize,
                lineHeight = uiState.lineHeight,
                onLineHeightChange = viewModel::changeLineHeight,
                isDarkMode = uiState.isDarkMode,
                onDarkModeChange = viewModel::toggleTheme,
                fontFamily = uiState.fontFamily,
                onFontFamilyChange = viewModel::changeFont,
                margin = uiState.margin,
                onMarginChange = viewModel::setMargin,
                onDismiss = { showSettings = false }
            )
        }
    }
}

/**
 * 增强的EPUB WebView 组件
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EnhancedEpubWebView(
    html: String,
    backgroundColor: Color,
    onWebViewCreated: (WebView?) -> Unit,
    onPageLoadFinished: () -> Unit,
    jsInterface: EpubReaderJsInterface
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    // 在组件销毁时清理WebView
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            // 告知外部WebView已被销毁
            onWebViewCreated(null)
        }
    }
    
    AndroidView(
        factory = { context ->
            createWebView(context, onPageLoadFinished, jsInterface).also {
                webViewInstance = it
                onWebViewCreated(it)
            }
        },
        update = { webView ->
            // 当需要更新时，重新加载HTML内容
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )
            webView.setBackgroundColor(backgroundColor.hashCode())
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 创建配置好的WebView
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    onPageLoadFinished: () -> Unit,
    jsInterface: EpubReaderJsInterface
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // 配置WebView
        settings.apply {
            javaScriptEnabled = true
            
            // API 7+ 设置DOM存储
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
                domStorageEnabled = true
            }
            
            // API 3+ 设置文件访问
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                allowFileAccess = true
            }
            
            // 允许文件内容访问
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                allowContentAccess = true
            }
            
            // API 21+ 设置混合内容模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // 设置缓存模式
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // 设置字体缩放
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                textZoom = 100
            }
            
            // 允许CSS的媒体查询
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mediaPlaybackRequiresUserGesture = false
            }
            
            // 支持CSS的viewport属性
            useWideViewPort = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
                loadWithOverviewMode = true
            }
            
            // 支持字体缩放
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                builtInZoomControls = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                displayZoomControls = false
            }
        }
        
        // 设置WebViewClient
        webViewClient = object : WebViewClient() {
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // 旧版API的URL处理
                return false
            }
            
            override fun shouldOverrideUrlLoading(
                view: WebView, 
                request: WebResourceRequest
            ): Boolean {
                // 新版API的URL处理，需要API 21+
                return false
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                onPageLoadFinished()
            }
        }
        
        // 允许调试
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        // 添加JavaScript接口
        addJavascriptInterface(jsInterface, "EpubReader")
    }
}

/**
 * 增强的底部控制栏 (EPUB版本)
 */
@Composable
fun EnhancedBottomEpubControls(
    currentChapter: Int,
    totalChapters: Int,
    currentPage: Int,
    totalPages: Int,
    isTtsActive: Boolean,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTts: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
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
                progress = if (totalChapters > 0) 
                    (currentChapter - 1 + (currentPage.toFloat() / totalPages.coerceAtLeast(1))) / totalChapters 
                else 
                    0f,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 章节和页面信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第 $currentChapter/$totalChapters 章",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "$currentPage / $totalPages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
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
                    IconButton(onClick = onOpenToc) {
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
                    IconButton(onClick = onOpenSettings) {
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

/**
 * 增强的目录底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTocBottomSheet(
    chapters: List<com.wanderreads.ebook.util.ChapterInfo>,
    currentChapterIndex: Int,
    onChapterSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(400.dp)  // 固定高度避免全屏
        ) {
            Text(
                text = "目录",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (chapters.isEmpty()) {
                Text(
                    text = "没有可用的章节",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                // 章节列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(chapters) { chapter ->
                        EnhancedChapterItem(
                            title = chapter.title,
                            level = chapter.level,
                            isSelected = chapter.index.toString() == currentChapterIndex.toString(),
                            onClick = { onChapterSelected(chapter.index.toInt()) }
                        )
                    }
                }
            }
            
            // 底部间距
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 增强的章节列表项
 */
@Composable
fun EnhancedChapterItem(
    title: String,
    level: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // 根据层级计算左边距
    val leftPadding = (16 + level * 16).dp
    
    Surface(
        onClick = onClick,
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (level == 0) FontWeight.Medium else FontWeight.Normal,
                fontSize = if (level == 0) 16.sp else 14.sp
            ),
            color = if (isSelected) 
                MaterialTheme.colorScheme.onPrimaryContainer 
            else 
                MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = leftPadding,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                )
        )
    }
}

/**
 * 增强的设置底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSettingsBottomSheet(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    fontFamily: String,
    onFontFamilyChange: (String) -> Unit,
    margin: Int,
    onMarginChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text(
                text = "阅读设置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 设置选项卡
            TabRow(
                selectedTabIndex = currentTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text("文本") }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text("版式") }
                )
                Tab(
                    selected = currentTab == 2,
                    onClick = { onTabSelected(2) },
                    text = { Text("主题") }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 根据当前选项卡显示不同的设置
            when (currentTab) {
                0 -> TextSettings(
                    fontSize = fontSize,
                    onFontSizeChange = onFontSizeChange,
                    fontFamily = fontFamily,
                    onFontFamilyChange = onFontFamilyChange
                )
                1 -> LayoutSettings(
                    lineHeight = lineHeight,
                    onLineHeightChange = onLineHeightChange,
                    margin = margin,
                    onMarginChange = onMarginChange
                )
                2 -> ThemeSettings(
                    isDarkMode = isDarkMode,
                    onDarkModeChange = onDarkModeChange
                )
                else -> TextSettings( // 添加默认处理，以防currentTab是其他值
                    fontSize = fontSize,
                    onFontSizeChange = onFontSizeChange,
                    fontFamily = fontFamily,
                    onFontFamilyChange = onFontFamilyChange
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 文本设置面板
 */
@Composable
fun TextSettings(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    fontFamily: String,
    onFontFamilyChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 字体大小设置
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "字体大小",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            IconButton(
                onClick = { onFontSizeChange((fontSize - 2).coerceAtLeast(12)) },
                enabled = fontSize > 12
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "减小字体"
                )
            }
            
            Text(
                text = "$fontSize",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )
            
            IconButton(
                onClick = { onFontSizeChange((fontSize + 2).coerceAtMost(32)) },
                enabled = fontSize < 32
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "增大字体"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 字体预览
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Text(
                text = "字体大小预览（当前 ${fontSize}px）",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSize.sp
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 字体选择
        Text(
            text = "字体选择",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // 字体选项
        val fontOptions = listOf(
            "默认" to "Default",
            "无衬线" to "Sans Serif",
            "衬线" to "Serif",
            "等宽" to "Monospace"
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            items(fontOptions) { (displayName, fontKey) ->
                FontOption(
                    name = displayName,
                    fontKey = fontKey,
                    isSelected = fontFamily.toString() == fontKey,
                    onSelected = { onFontFamilyChange(fontKey) }
                )
            }
        }
    }
}

/**
 * 字体选项
 */
@Composable
fun FontOption(
    name: String,
    fontKey: String,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val fontStyle = when (fontKey) {
        "Sans Serif" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    
    Surface(
        onClick = onSelected,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "字体示例",
                fontFamily = fontStyle,
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 版式设置面板
 */
@Composable
fun LayoutSettings(
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    margin: Int,
    onMarginChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 行高设置
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SpaceBar,
                contentDescription = "行高",
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "行高",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "%.1f".format(lineHeight),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Slider(
            value = lineHeight,
            onValueChange = { onLineHeightChange(it) },
            valueRange = 1.0f..2.5f,
            steps = 14,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 页面边距设置
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "页面边距",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "${margin}px",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Slider(
            value = margin.toFloat(),
            onValueChange = { onMarginChange(it.toInt()) },
            valueRange = 0f..60f,
            steps = 12,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        // 边距预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp)
                .height(120.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            // 外边框代表屏幕
            Box(
                modifier = Modifier
                    .padding(margin.dp)
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                // 内容区域
                Text(
                    text = "内容区域",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * 主题设置面板
 */
@Composable
fun ThemeSettings(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 暗色模式切换
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .clickable { onDarkModeChange(!isDarkMode) }
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Filled.Brightness7 else Icons.Filled.Brightness4,
                    contentDescription = if (isDarkMode) "亮色模式" else "暗色模式"
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = if (isDarkMode) "亮色模式" else "暗色模式",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Switch(
                checked = isDarkMode,
                onCheckedChange = onDarkModeChange
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        
        // 主题预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 主题预览卡片
            Surface(
                color = if (isDarkMode) Color(0xFF121212) else Color(0xFFFAFAFA),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "主题预览",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF333333)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "这是一段示例文字，展示在${if (isDarkMode) "暗色" else "亮色"}主题下的效果。您可以通过这个预览来决定哪种主题更适合您的阅读习惯。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color(0xFFCCCCCC) else Color(0xFF666666)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 示例链接
                    Text(
                        text = "示例链接",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color(0xFF90CAF9) else Color(0xFF1565C0)
                    )
                }
            }
        }
    }
} 
