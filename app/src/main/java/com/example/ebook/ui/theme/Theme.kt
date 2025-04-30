package com.example.ebook.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Custom animation specifications for hover effects
object AppAnimationSpec {
    val defaultHoverEffect = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val subtleHoverEffect = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

// Composition Local for custom theme values
data class AppThemeExtras(
    val bookCoverElevation: Float = 4f,
    val cardHoverAlpha: Float = 0.08f,
    val readingBackground: Color = DeepBlueNight
)

val LocalAppThemeExtras = staticCompositionLocalOf { AppThemeExtras() }

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = PrimaryLight,
    secondary = SecondaryLight,
    onSecondary = Color.Black,
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = SecondaryLight,
    tertiary = SecondaryLight,
    background = Neutral95,
    surface = Neutral95,
    onBackground = Neutral10,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral20,
    error = ErrorColorDark,
    onError = Neutral95
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = PrimaryDark,
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = SecondaryDark,
    tertiary = Secondary,
    background = Neutral10,
    surface = Neutral10,
    onBackground = Neutral99,
    onSurface = Neutral99,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    error = ErrorColor,
    onError = Color.White
)

@Composable
fun EbookTheme(
    darkTheme: Boolean = true, // 设置默认使用深蓝色暗色主题
    // 禁用动态颜色，以保证在华为P30 Pro上的兼容性
    dynamicColor: Boolean = false, 
    content: @Composable () -> Unit
) {
    // 不使用动态颜色功能，直接使用我们的固定颜色方案
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val window = (view.context as Activity).window
                window.statusBarColor = colorScheme.primary.toArgb()
                
                // 兼容性处理，避免在低版本Android上调用不支持的API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                } else {
                    // 旧版本API处理状态栏
                    @Suppress("DEPRECATION")
                    if (!darkTheme) {
                        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or 
                                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    } else {
                        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and 
                                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    }
                }
                
                window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))
            } catch (e: Exception) {
                // 捕获可能的异常，避免应用崩溃
            }
        }
    }
    
    // Provide custom theme extras
    val appThemeExtras = if (darkTheme) {
        AppThemeExtras(readingBackground = DeepBlueNight)
    } else {
        AppThemeExtras(readingBackground = WarmWhite)
    }
    
    CompositionLocalProvider(LocalAppThemeExtras provides appThemeExtras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}