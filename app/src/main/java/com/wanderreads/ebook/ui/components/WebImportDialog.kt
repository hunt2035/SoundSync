package com.wanderreads.ebook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * 网址导入对话框
 */
@Composable
fun WebImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // 默认显示的网址
    val defaultUrl = "http://wanderreads.com"
    
    // 使用空字符串作为初始值，但在UI中显示默认网址
    var url by remember { mutableStateOf("") }
    var isPlaceholderVisible by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("请输入有效的网址") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    
    // 自动获取焦点
    DisposableEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Log.e("WebImportDialog", "焦点请求失败: ${e.message}")
        }
        onDispose { }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "网址导入",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "请输入要导入的网页地址",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = url,
                    onValueChange = { 
                        url = it
                        isError = false
                        isPlaceholderVisible = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("网页地址") },
                    placeholder = { 
                        if (isPlaceholderVisible) {
                            Text(
                                text = defaultUrl,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            ) 
                        }
                    },
                    leadingIcon = { 
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = null
                        )
                    },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            validateAndSubmit(
                                url = if (url.isEmpty() && isPlaceholderVisible) defaultUrl else url,
                                context = context,
                                onConfirm = {
                                    focusManager.clearFocus()
                                    onConfirm(it)
                                },
                                onError = { message ->
                                    errorMessage = message
                                    isError = true
                                }
                            )
                        }
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TextButton(
                        onClick = {
                            validateAndSubmit(
                                url = if (url.isEmpty() && isPlaceholderVisible) defaultUrl else url,
                                context = context,
                                onConfirm = {
                                    focusManager.clearFocus()
                                    onConfirm(it)
                                },
                                onError = { message ->
                                    errorMessage = message
                                    isError = true
                                }
                            )
                        }
                    ) {
                        Text("导入")
                    }
                }
            }
        }
    }
}

private fun validateAndSubmit(
    url: String,
    context: Context,
    onConfirm: (String) -> Unit,
    onError: (String) -> Unit
) {
    // 1. 检查网址是否为空
    if (url.isBlank()) {
        onError("请输入网址")
        return
    }
    
    // 2. 检查网址格式
    val formattedUrl = if (!(url.startsWith("http://") || url.startsWith("https://"))) {
        "https://$url"
    } else {
        url
    }
    
    // 3. 检查网络连接
    if (!isNetworkAvailable(context)) {
        onError("网络连接不可用，请检查网络设置")
        return
    }
    
    // 4. 网址验证成功，确认提交
    onConfirm(formattedUrl)
}

/**
 * 检查网络连接是否可用
 */
private fun isNetworkAvailable(context: Context): Boolean {
    try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        Log.e("WebImportDialog", "检查网络连接失败: ${e.message}")
        // 如果无法检查，则默认为有连接
        return true
    }
} 