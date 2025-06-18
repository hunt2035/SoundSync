package org.soundsync.ebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.soundsync.ebook.domain.model.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.util.Log

/**
 * 修改文本对话框
 * 
 * @param initialText 初始文本内容
 * @param book 当前书籍
 * @param onDismiss 取消回调
 * @param onSaveComplete 保存完成回调
 */
@Composable
fun EditTextDialog(
    initialText: String,
    book: Book,
    onDismiss: () -> Unit,
    onSaveComplete: () -> Unit,
    coroutineScope: CoroutineScope
) {
    var text by remember { mutableStateOf(initialText) }
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    
    // 对话框宽度略小于屏幕宽度
    val dialogWidth = (configuration.screenWidthDp * 0.92f).dp
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "修改文本",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 输入框高度是宽度的1.3倍
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dialogWidth * 1.15f) // 高度是宽度的1.15倍
                        .verticalScroll(scrollState), // 添加垂直滚动
                    placeholder = { 
                        Text(
                            "请输入文本内容...", 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ) 
                    },
                    label = { 
                        Text(
                            "文本内容",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ) 
                    },
                    colors = TextFieldDefaults.colors(
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 调整按钮布局
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(
                        onClick = { 
                            if (text.isNotBlank()) {
                                saveTextToFile(text, book, onSaveComplete, coroutineScope)
                                onDismiss()
                            }
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/**
 * 保存文本到文件
 */
private fun saveTextToFile(
    text: String,
    book: Book,
    onSaveComplete: () -> Unit,
    coroutineScope: CoroutineScope
) {
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            try {
                val file = File(book.filePath)
                
                if (file.exists()) {
                    // 写入文件
                    FileOutputStream(file).use { outputStream ->
                        outputStream.write(text.toByteArray())
                        outputStream.flush()
                    }
                    
                    // 返回主线程通知完成
                    withContext(Dispatchers.Main) {
                        onSaveComplete()
                    }
                } else {
                    Log.e("EditTextDialog", "文件不存在: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("EditTextDialog", "保存文本失败", e)
            }
        }
    }
} 