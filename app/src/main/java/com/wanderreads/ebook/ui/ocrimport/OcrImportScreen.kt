package com.wanderreads.ebook.ui.ocrimport

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.wanderreads.ebook.domain.model.Book
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * OCR导入界面
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OcrImportScreen(
    viewModel: OcrImportViewModel,
    onNavigateBack: () -> Unit,
    onBookCreated: (Book) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // 相机权限状态
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // 临时图片文件URI
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.extractTextFromImage(context, it)
        }
    }
    
    // 相机拍照
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempImageUri != null) {
            tempImageUri?.let { uri ->
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    viewModel.extractTextFromBitmap(context, bitmap)
                } catch (e: Exception) {
                    Log.e("OcrImportScreen", "无法处理相机图片", e)
                }
            }
        }
    }
    
    // 创建临时图片文件
    fun createTempImageFile(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = context.getExternalFilesDir("Pictures")
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }
    
    // 处理成功保存的情况
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess && uiState.savedBook != null) {
            onBookCreated(uiState.savedBook!!)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "拍照导入",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1565C0),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.showTextPreview) {
                    // 文本预览和编辑
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "识别结果",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = uiState.extractedText,
                            onValueChange = { viewModel.updateExtractedText(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            label = { Text("识别的文本") },
                            placeholder = { Text("这里将显示从图片中识别的文本") }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { viewModel.hideTextPreview() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("重新选择")
                            }
                            
                            Button(
                                onClick = { viewModel.saveExtractedText(context) },
                                enabled = !uiState.isSaving && uiState.extractedText.isNotBlank()
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("保存文本")
                            }
                        }
                    }
                } else if (uiState.showImportMethodSelection) {
                    // 选择导入方式
                    Text(
                        "选择导入方式",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 相机拍照按钮
                    Button(
                        onClick = {
                            if (cameraPermissionState.status.isGranted) {
                                // 已有权限，直接拍照
                                tempImageUri = createTempImageFile()
                                cameraLauncher.launch(tempImageUri)
                            } else {
                                // 请求相机权限
                                cameraPermissionState.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("相机拍照")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 从相册选择按钮
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从相册选择")
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        "选择图片后，应用将自动识别图片中的文字，并可以保存为电子书",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 相机权限请求结果处理
            LaunchedEffect(cameraPermissionState.status.isGranted) {
                if (cameraPermissionState.status.isGranted && uiState.showImportMethodSelection) {
                    // 权限获取后，自动启动相机
                    tempImageUri = createTempImageFile()
                    cameraLauncher.launch(tempImageUri)
                }
            }
            
            // 加载指示器
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(200.dp)
                            .height(120.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在识别文字...")
                        }
                    }
                }
            }
            
            // 错误提示
            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }
    }
} 