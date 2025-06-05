package com.wanderreads.ebook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
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
    initialParams: SynthesisParams = SynthesisParams(synthesisRange = SynthesisRange.CURRENT_PAGE),
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
    // 判断是否合成完成，检查进度值或消息内容
    val isCompleted = message.contains("成功") || message.contains("完成") || progress >= 100
    
    // 提取目录信息
    val directory = if (message.contains("目录")) {
        try {
            message.substringAfter("目录").substringBefore("下").trim()
        } catch (e: Exception) {
            null
        }
    } else null
    
    // 提取错误信息
    val errorMessage = if (isErrorState) {
        // 尝试提取更具体的错误原因
        when {
            message.contains("权限") -> "存储权限不足，请在设置中授予存储权限"
            message.contains("空间") -> "存储空间不足，请清理设备存储空间后重试"
            message.contains("超时") -> "合成超时，请检查网络连接或重试"
            message.contains("初始化") -> "TTS引擎初始化失败，请重启应用后重试"
            else -> message // 使用原始错误消息
        }
    } else null
    
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
                // 错误状态直接显示错误信息
                if (isErrorState) {
                    // 显示错误图标
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = errorMessage ?: "语音合成失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } 
                // 完成状态显示成功消息和文件位置
                else if (isCompleted) {
                    // 显示成功图标
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = "语音合成成功",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 显示目录路径（如果有）
                    if (directory != null) {
                        Text(
                            text = "生成文件位于目录：",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = directory,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Start
                            )
                        }
                    } else if (message.contains("成功")) {
                        // 如果消息包含"成功"但没有具体目录，仍然显示完整的消息
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                // 进行中状态显示进度条和百分比
                else {
                    // 显示加载图标
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 16.dp),
                        strokeWidth = 4.dp
                    )
                    
                    Text(
                        text = "正在合成语音...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LinearProgressIndicator(
                        // 即使进度为0也显示一点点进度（至少1%）
                        progress = (progress / 100f).coerceAtLeast(0.01f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        // 如果进度为0，显示为"准备中..."，否则显示实际百分比
                        if (progress <= 0) "准备中... 0%" else "$progress%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            if (isCompleted || isErrorState) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("关闭")
                }
            } else {
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("取消")
                }
            }
        }
    )
} 