package com.wanderreads.ebook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wanderreads.ebook.R
import com.wanderreads.ebook.ui.theme.Primary

/**
 * 设置屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    // 创建ViewModel
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(context) }
    val settingsState by settingsViewModel.uiState.collectAsState()
    
    // 对话框状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showThemeStyleDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showCoverStyleDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.height(32.dp)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 语言设置
            item {
                SettingsCategoryTitle(title = "语言设置")
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "语言",
                    subtitle = getLanguageText(settingsState.language),
                    onClick = { showLanguageDialog = true }
                )
            }
            
            // 主题模式
            item {
                SettingsCategoryTitle(title = "主题模式")
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "主题",
                    subtitle = getThemeText(settingsState.theme),
                    onClick = { showThemeDialog = true }
                )
            }
            
            // 主题样式
            item {
                SettingsCategoryTitle(title = "主题样式")
                SettingsItem(
                    icon = Icons.Default.FormatSize,
                    title = "字体大小",
                    subtitle = getFontSizeText(settingsState.fontSize),
                    onClick = { showFontSizeDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.Style,
                    title = "封面设置",
                    subtitle = getCoverStyleText(settingsState.coverStyle),
                    onClick = { showCoverStyleDialog = true }
                )
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "主色调",
                    subtitle = "自定义应用主色调",
                    onClick = { showColorDialog = true },
                    endContent = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(settingsState.primaryColor)))
                        )
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Contrast,
                    title = "背景色",
                    subtitle = "自定义应用背景色",
                    onClick = { showBackgroundDialog = true },
                    endContent = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(settingsState.backgroundColor)))
                        )
                    }
                )
            }
            
            // TTS设置
            item {
                SettingsCategoryTitle(title = "文本朗读设置")
                // TTS设置项将在未来添加
            }
            
            // 关于
            item {
                SettingsCategoryTitle(title = "其他")
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于",
                    subtitle = "应用版本和使用条款",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }
    
    // 语言选择对话框
    if (showLanguageDialog) {
        SettingsOptionDialog(
            title = "选择语言",
            options = listOf(
                "跟随系统",
                "简体中文",
                "英语"
            ),
            selectedOption = settingsState.language,
            onOptionSelected = { settingsViewModel.setLanguage(it) },
            onDismiss = { showLanguageDialog = false }
        )
    }
    
    // 主题选择对话框
    if (showThemeDialog) {
        SettingsOptionDialog(
            title = "选择主题",
            options = listOf(
                "浅色模式",
                "深色模式",
                "跟随系统"
            ),
            selectedOption = settingsState.theme,
            onOptionSelected = { settingsViewModel.setTheme(it) },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    // 字体大小对话框
    if (showFontSizeDialog) {
        SettingsOptionDialog(
            title = "字体大小",
            options = listOf(
                "小",
                "中",
                "大"
            ),
            selectedOption = settingsState.fontSize,
            onOptionSelected = { settingsViewModel.setFontSize(it) },
            onDismiss = { showFontSizeDialog = false }
        )
    }
    
    // 封面样式对话框
    if (showCoverStyleDialog) {
        SettingsOptionDialog(
            title = "封面样式",
            options = listOf(
                "默认样式",
                "卡片样式",
                "材质样式"
            ),
            selectedOption = settingsState.coverStyle,
            onOptionSelected = { settingsViewModel.setCoverStyle(it) },
            onDismiss = { showCoverStyleDialog = false }
        )
    }
    
    // 颜色选择对话框
    if (showColorDialog) {
        ColorPickerDialog(
            title = "选择主色调",
            currentColor = Color(android.graphics.Color.parseColor(settingsState.primaryColor)),
            onColorSelected = { settingsViewModel.setPrimaryColor("#%06X".format(0xFFFFFF and it.toArgb())) },
            onDismiss = { showColorDialog = false }
        )
    }
    
    // 背景色选择对话框
    if (showBackgroundDialog) {
        ColorPickerDialog(
            title = "选择背景色",
            currentColor = Color(android.graphics.Color.parseColor(settingsState.backgroundColor)),
            onColorSelected = { settingsViewModel.setBackgroundColor("#%06X".format(0xFFFFFF and it.toArgb())) },
            onDismiss = { showBackgroundDialog = false }
        )
    }
    
    // 关于对话框
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

/**
 * 设置分类标题
 */
@Composable
fun SettingsCategoryTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/**
 * 设置项
 */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    endContent: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (endContent != null) {
                endContent()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    Divider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/**
 * 带开关的设置项
 */
@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 选项对话框
 */
@Composable
fun SettingsOptionDialog(
    title: String,
    options: List<String>,
    selectedOption: Int,
    onOptionSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onOptionSelected(index)
                                onDismiss() 
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == index,
                            onClick = { 
                                onOptionSelected(index)
                                onDismiss() 
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(text = option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 颜色选择对话框
 */
@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    // 色彩选项
    val colorOptions = listOf(
        Color(0xFFE57373), // 红色
        Color(0xFFF06292), // 粉红色
        Color(0xFFBA68C8), // 紫色
        Color(0xFF9575CD), // 深紫色
        Color(0xFF7986CB), // 靛蓝色
        Color(0xFF64B5F6), // 蓝色
        Color(0xFF4FC3F7), // 浅蓝色
        Color(0xFF4DD0E1), // 青色
        Color(0xFF4DB6AC), // 蓝绿色
        Color(0xFF81C784), // 绿色
        Color(0xFFAED581), // 浅绿色
        Color(0xFFFF8A65), // 橙色
        Color(0xFF90A4AE), // 蓝灰色
        Color(0xFF0091EA), // 亮蓝色
        Color(0xFF1E3A5F), // 深蓝色
        Color(0xFF212121)  // 黑色
    )
    
    var selectedColor by remember { mutableStateOf(currentColor) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 颜色展示
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .padding(4.dp)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 颜色选项网格
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until 4) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (j in 0 until 4) {
                                val index = i * 4 + j
                                if (index < colorOptions.size) {
                                    val color = colorOptions[index]
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable {
                                                selectedColor = color
                                            }
                                            .padding(4.dp)
                                    )
                                } else {
                                    Box(modifier = Modifier.size(40.dp))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                    
                    TextButton(
                        onClick = {
                            onColorSelected(selectedColor)
                            onDismiss()
                        }
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/**
 * 关于对话框
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null, // 移除标题，使用自定义布局
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. LOGO图标（居中）
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "App Logo",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 2. 应用中文名
                Text(
                    text = "漫阅",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 3. 应用英文名和版本号
                Text(
                    text = "Wander Reads v0.19",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Divider(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 4. 应用简介
                Text(
                    text = "本应用提供电子书导入、文本输入和阅读功能，同时支持TTS朗读和合成语音功能，目前应用仍在不断升级和完善中，欢迎使用并提出改进意见或建议。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    lineHeight = 24.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 5. 联系作者
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "联系邮箱",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "hunt2035@qq.com",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
            ) {
                Text("确定", modifier = Modifier.padding(horizontal = 16.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp))
    )
}

/**
 * 获取语言文本
 */
fun getLanguageText(language: Int): String {
    return when (language) {
        SettingsViewModel.LANGUAGE_SYSTEM -> "跟随系统"
        SettingsViewModel.LANGUAGE_CHINESE -> "简体中文"
        SettingsViewModel.LANGUAGE_ENGLISH -> "英语"
        else -> "未知"
    }
}

/**
 * 获取主题文本
 */
fun getThemeText(theme: Int): String {
    return when (theme) {
        SettingsViewModel.THEME_LIGHT -> "浅色模式"
        SettingsViewModel.THEME_DARK -> "深色模式"
        SettingsViewModel.THEME_SYSTEM -> "跟随系统"
        else -> "未知"
    }
}

/**
 * 获取字体大小文本
 */
fun getFontSizeText(fontSize: Int): String {
    return when (fontSize) {
        SettingsViewModel.FONT_SIZE_SMALL -> "小"
        SettingsViewModel.FONT_SIZE_MEDIUM -> "中"
        SettingsViewModel.FONT_SIZE_LARGE -> "大"
        else -> "未知"
    }
}

/**
 * 获取封面样式文本
 */
fun getCoverStyleText(coverStyle: Int): String {
    return when (coverStyle) {
        SettingsViewModel.COVER_STYLE_DEFAULT -> "默认样式"
        SettingsViewModel.COVER_STYLE_CARD -> "卡片样式"
        SettingsViewModel.COVER_STYLE_MATERIAL -> "材质样式"
        else -> "未知"
    }
} 