package com.example.ebook.ui.components

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
import com.example.ebook.data.repository.BookRepository
import com.example.ebook.data.repository.BookRepositoryImpl
import com.example.ebook.domain.model.Book
import com.example.ebook.domain.model.BookType
import com.example.ebook.util.TextProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 新建文本对话框
 * 
 * @param onDismiss 取消回调
 * @param onSaveComplete 保存完成回调
 * @param bookRepository 书籍仓库，用于导入生成的文本文件
 */
@Composable
fun NewTextDialog(
    onDismiss: () -> Unit,
    onSaveComplete: (Book) -> Unit,
    bookRepository: BookRepository,
    coroutineScope: CoroutineScope
) {
    var text by remember { mutableStateOf("") }
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
                    text = "新建文本",
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
                        .height(dialogWidth * 1.3f) // 高度是宽度的1.3倍
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
                                saveTextToFile(text, onSaveComplete, bookRepository, coroutineScope)
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
 * 保存文本到文件并导入书架
 */
private fun saveTextToFile(
    text: String,
    onSaveComplete: (Book) -> Unit,
    bookRepository: BookRepository,
    coroutineScope: CoroutineScope
) {
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            try {
                // 使用TextProcessor处理文本并提取标题
                val processedText = TextProcessor.processText(text)
                val title = TextProcessor.extractTitle(processedText)
                
                // 创建文件名
                val fileName = "${System.currentTimeMillis()}_${title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")}"
                
                // 使用TextProcessor保存文本到文件
                val context = (bookRepository as? BookRepositoryImpl)?.getContext() 
                    ?: return@withContext
                
                TextProcessor.saveTextToFile(context, processedText, fileName)
                    .onSuccess { file ->
                        // 创建书籍对象
                        val book = Book(
                            title = title,
                            filePath = file.absolutePath,
                            type = BookType.TXT,
                            addedDate = System.currentTimeMillis()
                        )
                        
                        // 导入书籍
                        bookRepository.addBook(book)
                        
                        // 返回主线程通知完成
                        withContext(Dispatchers.Main) {
                            onSaveComplete(book)
                        }
                    }
                    .onFailure { e ->
                        e.printStackTrace()
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
} 