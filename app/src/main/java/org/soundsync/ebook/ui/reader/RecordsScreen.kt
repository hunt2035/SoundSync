package org.soundsync.ebook.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.soundsync.ebook.domain.model.Record
import java.text.SimpleDateFormat
import java.util.*

/**
 * 录音文件列表弹出框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    records: List<Record>,
    onDismiss: () -> Unit,
    onPlayRecord: (Record) -> Unit,
    onPauseRecord: (Record) -> Unit,
    currentPlayingRecordId: String?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A1929), // 墨蓝色背景
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "录音文件",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 录音文件列表
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无录音文件",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(records) { record ->
                        val isPlaying = currentPlayingRecordId == record.id
                        RecordItem(
                            record = record,
                            isPlaying = isPlaying,
                            onPlayPause = {
                                if (isPlaying) {
                                    onPauseRecord(record)
                                } else {
                                    onPlayRecord(record)
                                }
                            }
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
 * 单个录音文件项
 */
@Composable
private fun RecordItem(
    record: Record,
    isPlaying: Boolean,
    onPlayPause: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(record.addedDate))
    
    // 格式化录音时长
    val totalSeconds = record.voiceLength
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val duration = String.format("%02d:%02d", minutes, seconds)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2C3A) // 深蓝色卡片背景
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 录音信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${formattedDate} · ${duration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            // 播放按钮
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = Color(0xFF1976D2), // 蓝色按钮背景
                        shape = MaterialTheme.shapes.small
                    )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White
                )
            }
        }
    }
} 