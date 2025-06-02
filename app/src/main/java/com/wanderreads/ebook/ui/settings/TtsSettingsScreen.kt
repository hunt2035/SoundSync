package com.wanderreads.ebook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderreads.ebook.util.TtsManager
import com.wanderreads.ebook.util.TtsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * TTS设置屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val ttsSettings = remember { TtsSettings.getInstance(context) }
    val ttsManager = remember { TtsManager.getInstance(context) }
    
    // 初始化TTS引擎
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            ttsManager.initialize()
        }
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            // 不要在这里释放ttsManager，因为它是单例，可能在其他地方还在使用
        }
    }
    
    // 语速设置
    var speechRate by remember { mutableFloatStateOf(ttsSettings.getSpeechRate()) }
    
    // 音量设置
    var speechVolume by remember { mutableFloatStateOf(ttsSettings.getSpeechVolume()) }
    
    // 句子间停顿时间设置
    var silenceDuration by remember { mutableIntStateOf(ttsSettings.getSilenceDuration()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TTS朗读设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 语速设置
            SettingSection(
                title = "语速",
                description = "调整语速可以改变朗读的速度（0.5-2.0）",
                value = speechRate.toString().take(3),
                onValueChange = { newValue ->
                    speechRate = newValue
                    ttsSettings.setSpeechRate(newValue)
                },
                valueRange = 0.5f..2.0f
            )
            
            // 音量设置
            SettingSection(
                title = "音量",
                description = "调整朗读的音量大小（0.0-1.0）",
                value = speechVolume.toString().take(3),
                onValueChange = { newValue ->
                    speechVolume = newValue
                    ttsSettings.setSpeechVolume(newValue)
                },
                valueRange = 0.0f..1.0f
            )
            
            // 句子间停顿时间设置
            SettingSection(
                title = "句子间停顿时间",
                description = "句子之间的停顿时间，值越小，停顿越短（0-100）",
                value = silenceDuration.toString(),
                onValueChange = { newValue ->
                    silenceDuration = newValue.toInt()
                    ttsSettings.setSilenceDuration(newValue.toInt())
                },
                valueRange = 0f..100f,
                steps = 100
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 测试按钮
            Button(
                onClick = {
                    val testText = "这是一个TTS朗读测试。您可以调整语速、音量和句子间停顿时间，以获得最佳的朗读效果。"
                    ttsManager.startReading("test", 0, testText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("测试朗读效果")
            }
        }
    }
}

/**
 * 设置部分组件
 */
@Composable
fun SettingSection(
    title: String,
    description: String,
    value: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = value.toFloatOrNull()?.coerceIn(valueRange) ?: valueRange.start,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = value,
                fontSize = 14.sp
            )
        }
        
        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
} 