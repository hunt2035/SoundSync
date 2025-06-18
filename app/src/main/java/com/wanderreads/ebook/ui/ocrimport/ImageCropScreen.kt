package com.wanderreads.ebook.ui.ocrimport

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView

/**
 * 图片裁剪界面
 * 使用 Android-Image-Cropper 库实现照片裁剪功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(
    imageUri: Uri,
    onNavigateBack: () -> Unit,
    onImageCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    
    // 使用 Activity 结果 API 处理裁剪结果
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                try {
                    isLoading = true
                    // 获取裁剪结果
                    val cropResult = CropImage.getActivityResult(data)
                    val croppedUri = cropResult.uri
                    
                    // 将 Uri 转换为 Bitmap
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, croppedUri)
                        android.graphics.ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, croppedUri)
                    }
                    
                    // 回调裁剪后的图片
                    onImageCropped(bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            onNavigateBack()
        }
    }
    
    // 启动裁剪 Activity
    LaunchedEffect(imageUri) {
        val intent = CropImage.activity(imageUri)
            .setGuidelines(CropImageView.Guidelines.ON)
            .setFixAspectRatio(false) // 允许自由裁剪
            .setBorderLineColor(android.graphics.Color.WHITE)
            .setBorderLineThickness(3f)
            .setGuidelinesColor(android.graphics.Color.WHITE)
            .setGuidelinesThickness(1f)
            .getIntent(context)
        
        cropImageLauncher.launch(intent)
    }
    
    // 加载指示器
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
} 