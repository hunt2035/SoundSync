package org.soundsync.ebook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.soundsync.ebook.MainActivity
import org.soundsync.ebook.util.TtsManager

/**
 * 书架界面的音频播放控件
 * 在底部导航栏上方显示，用于控制TTS朗读
 */
@Composable
fun BookshelfAudioPlayerControl(
    onSyncPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsManager = TtsManager.getInstance(context)
    
    // 收集TTS状态
    val ttsState by ttsManager.ttsState.collectAsState()
    val isSyncPageState by ttsManager.isSyncPageState.collectAsState()
    
    // 只有当TTS状态为播放或暂停时才显示控件
    val shouldShowControl = ttsState.status == TtsManager.STATUS_PLAYING || 
                            ttsState.status == TtsManager.STATUS_PAUSED
    
    AnimatedVisibility(
        visible = shouldShowControl,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AudioPlayerControl(
                ttsStatus = ttsState.status,
                isPositionSynced = isSyncPageState == 1,
                onPlayPause = {
                    if (ttsState.status == TtsManager.STATUS_PLAYING) {
                        ttsManager.pauseReading()
                    } else {
                        ttsManager.resumeReading()
                    }
                },
                onStop = {
                    ttsManager.stopReading()
                },
                onSyncPosition = onSyncPosition,
                onOffsetChange = { /* 在书架界面不需要处理拖动位置 */ },
                onPrevSentence = { ttsManager.playPreviousSentence() },
                onNextSentence = { ttsManager.playNextSentence() }
            )
        }
    }
} 