package com.wanderreads.ebook.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.speech.tts.TextToSpeech
import androidx.compose.material3.BottomAppBar
import java.util.Locale

/**
 * WebView页面，用于显示网页内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onNavigateBack: () -> Unit
) {
    // 定义颜色
    val navyBlueBackground = Color(0xFF0A1929) // 墨蓝色背景
    val themeBlue = Color(0xFF1976D2) // 主题蓝色（工具栏）
    val whiteText = Color.White // 白色文字
    
    // 控制顶部和底部工具栏的显示
    var showControls by remember { mutableStateOf(true) }
    
    // 页面加载状态
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("") }
    
    // TTS状态
    var isTtsActive by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val context = LocalContext.current
    
    // 网页内容
    var webContent by remember { mutableStateOf("") }
    
    // 初始化TTS
    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
            }
        }
    }
    
    // 释放TTS资源
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
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
                    title = { Text(if (pageTitle.isNotEmpty()) pageTitle else "网页浏览", color = whiteText) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = whiteText)
                        }
                    },
                    actions = {
                        // TTS按钮
                        IconButton(
                            onClick = {
                                if (isTtsActive) {
                                    tts?.stop()
                                    isTtsActive = false
                                } else {
                                    if (!webContent.isNullOrEmpty()) {
                                        tts?.speak(webContent, TextToSpeech.QUEUE_FLUSH, null, "web_content")
                                        isTtsActive = true
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isTtsActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (isTtsActive) "停止朗读" else "开始朗读",
                                tint = whiteText
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                BottomAppBar(
                    containerColor = themeBlue,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLoading) "加载中..." else "完成",
                            color = whiteText
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clickable {
                    // 点击时切换控制栏的显示状态
                    showControls = !showControls
                }
        ) {
            // WebView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                pageTitle = view?.title ?: ""
                                
                                // 提取网页内容用于TTS
                                view?.evaluateJavascript(
                                    "(function() { return document.body.innerText; })();"
                                ) { result ->
                                    // 移除引号并处理转义字符
                                    webContent = result?.let {
                                        it.substring(1, it.length - 1)
                                            .replace("\\n", "\n")
                                            .replace("\\\"", "\"")
                                    } ?: ""
                                }
                            }
                        }
                        
                        loadUrl(url)
                    }
                }
            )
            
            // 加载指示器
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
} 