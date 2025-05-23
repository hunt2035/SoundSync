package com.wanderreads.ebook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wanderreads.ebook.domain.model.SynthesisParams
import com.wanderreads.ebook.domain.model.SynthesisRange

/**
 * 语音合成对话框
 * 用于设置语音合成参数
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynthesisDialog(
    onDismiss: () -> Unit,
    onStartSynthesis: (SynthesisParams) -> Unit,
    initialParams: SynthesisParams = SynthesisParams(),
    hasContent: Boolean = true // 新增参数，表示是否有可合成的内容
) {
    var synthesisRange by remember { mutableStateOf(initialParams.synthesisRange) }
    var speechRate by remember { mutableFloatStateOf(initialParams.speechRate) }
    var pitch by remember { mutableFloatStateOf(initialParams.pitch) }
    var volume by remember { mutableFloatStateOf(initialParams.volume) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语音合成") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 提示信息（如果没有内容）
                if (!hasContent) {
                    Text(
                        text = "注意：当前页面内容为空，合成可能会失败",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            
                // 合成范围选择
                Text(
                    text = "合成范围:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // 当前页
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = synthesisRange == SynthesisRange.CURRENT_PAGE,
                        onClick = { synthesisRange = SynthesisRange.CURRENT_PAGE }
                    )
                    Text("当前页")
                }
                
                // 当前章节
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = synthesisRange == SynthesisRange.CURRENT_CHAPTER,
                        onClick = { synthesisRange = SynthesisRange.CURRENT_CHAPTER }
                    )
                    Text("当前章节")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 语音设置
                Text(
                    text = "语音设置:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // 语速
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "语速:",
                        modifier = Modifier.width(45.dp)
                    )
                    Slider(
                        value = speechRate,
                        onValueChange = { speechRate = it },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.1f", speechRate),
                        modifier = Modifier.width(30.dp)
                    )
                }
                
                // 音调
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "音调:",
                        modifier = Modifier.width(45.dp)
                    )
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.1f", pitch),
                        modifier = Modifier.width(30.dp)
                    )
                }
                
                // 音量
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "音量:",
                        modifier = Modifier.width(45.dp)
                    )
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.1f", volume),
                        modifier = Modifier.width(30.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val params = SynthesisParams(
                        speechRate = speechRate,
                        pitch = pitch,
                        volume = volume,
                        synthesisRange = synthesisRange
                    )
                    onStartSynthesis(params)
                }
            ) {
                Text("开始合成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 语音合成进度对话框
 * 用于显示语音合成进度
 */
@Composable
fun SynthesisProgressDialog(
    progress: Int,
    message: String,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    // 判断消息是否包含错误相关词语或状态异常
    val isErrorState = message.contains("失败") || message.contains("错误") || progress < 0
    // 判断是否合成完成
    val isCompleted = message.contains("完成") || progress >= 100
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                when {
                    isErrorState -> "合成失败"
                    isCompleted -> "合成成功"
                    else -> "语音合成"
                }
            ) 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(message)
                Spacer(modifier = Modifier.height(16.dp))
                if (!isErrorState && !isCompleted) {
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$progress%")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = when {
                    isErrorState || isCompleted -> onDismiss
                    else -> onCancel
                }
            ) {
                Text(
                    when {
                        isErrorState -> "关闭"
                        isCompleted -> "确定"
                        else -> "取消"
                    }
                )
            }
        }
    )
} 