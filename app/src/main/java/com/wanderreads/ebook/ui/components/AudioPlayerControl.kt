package com.wanderreads.ebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wanderreads.ebook.util.TtsManager
import kotlin.math.roundToInt

/**
 * 音频播放控件
 * 在阅读界面显示TTS朗读控制
 */
@Composable
fun AudioPlayerControl(
    ttsStatus: Int, // TTS状态
    isPositionSynced: Boolean, // 当前阅读位置是否与TTS朗读位置同步
    onPlayPause: () -> Unit, // 播放/暂停回调
    onStop: () -> Unit, // 停止回调
    onSyncPosition: () -> Unit, // 同步位置回调（点击"边听边看"时）
    onOffsetChange: (IntOffset) -> Unit, // 位置变化回调
    onPrevSentence: () -> Unit = {}, // 播放前一句回调
    onNextSentence: () -> Unit = {}, // 播放后一句回调
    modifier: Modifier = Modifier
) {
    val isDragging = remember { mutableStateOf(false) }
    val dragOffset = remember { mutableStateOf(IntOffset(0, 0)) }
    val isPlaying = ttsStatus == TtsManager.STATUS_PLAYING

    // 定义颜色
    val lightBlue = Color(0xFF2196F3) // 浅蓝色（同步朗读中）
    val darkBlue = Color(0xFF0D47A1) // 深蓝色（边听边看）

    Surface(
        modifier = modifier
            .shadow(4.dp, shape = RoundedCornerShape(32.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging.value = true },
                    onDragEnd = { isDragging.value = false },
                    onDragCancel = { isDragging.value = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset.value = IntOffset(
                            dragOffset.value.x + dragAmount.x.roundToInt(),
                            dragOffset.value.y + dragAmount.y.roundToInt()
                        )
                        onOffsetChange(dragOffset.value)
                    }
                )
            },
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF1A2635) // 更深的不透明蓝色背景
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面图标（圆形）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D47A1)), // 更深的不透明蓝色
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = "封面",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // "边听边看"或"同步朗读中"文字按钮
            Button(
                onClick = { 
                    // 只有在位置不同步时才能点击
                    if (!isPositionSynced) {
                        onSyncPosition()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    // 根据同步状态使用不同的背景色
                    containerColor = if (isPositionSynced) lightBlue else darkBlue
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp),
                // 在同步状态下禁用按钮
                enabled = !isPositionSynced
            ) {
                Text(
                    // 根据同步状态显示不同的文本
                    text = if (isPositionSynced) "同步中..." else "边听边看",
                    color = Color.White
                )
            }
            
            // 播放前一句按钮
            IconButton(
                onClick = onPrevSentence,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "播放前一句",
                    tint = Color.White
                )
            }
            
            // 播放/暂停按钮
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White
                )
            }
            
            // 播放后一句按钮
            IconButton(
                onClick = onNextSentence,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "播放后一句",
                    tint = Color.White
                )
            }
            
            // 停止按钮
            IconButton(
                onClick = onStop,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "停止",
                    tint = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AudioPlayerControlPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.Gray),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 预览"同步中..."状态
            AudioPlayerControl(
                ttsStatus = TtsManager.STATUS_PLAYING,
                isPositionSynced = true,
                onPlayPause = { },
                onStop = { },
                onSyncPosition = { },
                onOffsetChange = { },
                onPrevSentence = { },
                onNextSentence = { }
            )
            
            // 预览"边听边看"状态
            AudioPlayerControl(
                ttsStatus = TtsManager.STATUS_PLAYING,
                isPositionSynced = false,
                onPlayPause = { },
                onStop = { },
                onSyncPosition = { },
                onOffsetChange = { },
                onPrevSentence = { },
                onNextSentence = { }
            )
        }
    }
} 