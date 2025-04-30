package com.example.ebook.ui.settings

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
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ebook.R
import com.example.ebook.ui.theme.Primary

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
                )
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
            
            // 关于
            item {
                SettingsCategoryTitle(title = "关于")
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于应用",
                    subtitle = "查看应用信息",
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
                "繁体中文",
                "英文"
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
                "跟随系统",
                "亮色主题",
                "暗色主题"
            ),
            selectedOption = settingsState.theme,
            onOptionSelected = { settingsViewModel.setTheme(it) },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    // 字体大小对话框
    if (showFontSizeDialog) {
        FontSizeDialog(
            currentSize = settingsState.fontSize,
            onSizeChanged = { settingsViewModel.setFontSize(it) },
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
 * 选项设置对话框
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
        title = { Text(title) },
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
                            .padding(vertical = 8.dp),
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
 * 字体大小设置对话框
 */
@Composable
fun FontSizeDialog(
    currentSize: Int,
    onSizeChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderPosition by remember { mutableStateOf(currentSize.toFloat()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字体大小") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "示例文本",
                    style = when (sliderPosition.toInt()) {
                        0 -> MaterialTheme.typography.bodySmall
                        1 -> MaterialTheme.typography.bodyMedium
                        2 -> MaterialTheme.typography.bodyLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.bodyMedium
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 0f..3f,
                    steps = 2,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("小", style = MaterialTheme.typography.bodySmall)
                    Text("中", style = MaterialTheme.typography.bodySmall)
                    Text("大", style = MaterialTheme.typography.bodySmall)
                    Text("特大", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSizeChanged(sliderPosition.toInt())
                    onDismiss()
                }
            ) {
                Text("确定")
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
 * 颜色选择对话框
 */
@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }
    
    // 预定义的颜色列表
    val colorOptions = listOf(
        Color(0xFF3F51B5), // Indigo
        Color(0xFF2196F3), // Blue
        Color(0xFF03A9F4), // Light Blue
        Color(0xFF00BCD4), // Cyan
        Color(0xFF009688), // Teal
        Color(0xFF4CAF50), // Green
        Color(0xFF8BC34A), // Light Green
        Color(0xFFCDDC39), // Lime
        Color(0xFFFFEB3B), // Yellow
        Color(0xFFFFC107), // Amber
        Color(0xFFFF9800), // Orange
        Color(0xFFFF5722), // Deep Orange
        Color(0xFFF44336), // Red
        Color(0xFFE91E63), // Pink
        Color(0xFF9C27B0)  // Purple
    )
    
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
                    for (i in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (j in 0 until 5) {
                                val index = i * 5 + j
                                if (index < colorOptions.size) {
                                    val color = colorOptions[index]
                                    ColorOption(
                                        color = color,
                                        isSelected = selectedColor == color,
                                        onClick = { selectedColor = color }
                                    )
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
                    TextButton(onClick = onDismiss) {
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
 * 单个颜色选项
 */
@Composable
fun ColorOption(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * 关于对话框
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(40.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "漫阅",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wander Reads",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Primary,  // 使用应用主题的蓝色
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "版本: 0.10",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "作者: HuntLong",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "联系: hunt2035@qq.com",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "感谢您使用本应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 获取语言文本
 */
private fun getLanguageText(language: Int): String {
    return when (language) {
        SettingsViewModel.LANGUAGE_SYSTEM -> "跟随系统"
        SettingsViewModel.LANGUAGE_SIMPLIFIED_CHINESE -> "简体中文"
        SettingsViewModel.LANGUAGE_TRADITIONAL_CHINESE -> "繁体中文"
        SettingsViewModel.LANGUAGE_ENGLISH -> "英文"
        else -> "跟随系统"
    }
}

/**
 * 获取主题文本
 */
private fun getThemeText(theme: Int): String {
    return when (theme) {
        SettingsViewModel.THEME_SYSTEM -> "跟随系统"
        SettingsViewModel.THEME_LIGHT -> "亮色主题"
        SettingsViewModel.THEME_DARK -> "暗色主题"
        else -> "跟随系统"
    }
}

/**
 * 获取字体大小文本
 */
private fun getFontSizeText(fontSize: Int): String {
    return when (fontSize) {
        SettingsViewModel.FONT_SIZE_SMALL -> "小"
        SettingsViewModel.FONT_SIZE_MEDIUM -> "中"
        SettingsViewModel.FONT_SIZE_LARGE -> "大"
        SettingsViewModel.FONT_SIZE_XLARGE -> "特大"
        else -> "中"
    }
}

/**
 * 获取封面样式文本
 */
private fun getCoverStyleText(coverStyle: Int): String {
    return when (coverStyle) {
        SettingsViewModel.COVER_STYLE_DEFAULT -> "默认样式"
        SettingsViewModel.COVER_STYLE_CARD -> "卡片样式"
        SettingsViewModel.COVER_STYLE_MATERIAL -> "材质样式"
        else -> "默认样式"
    }
} 