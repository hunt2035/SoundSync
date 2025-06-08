package com.wanderreads.ebook.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanderreads.ebook.data.local.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 设置视图模型
 */
class SettingsViewModel(private val context: Context) : ViewModel() {
    // 设置状态
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // 初始化
    init {
        loadSettings()
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            // 从DataStore中读取设置
            context.dataStore.data.map { preferences ->
                SettingsUiState(
                    language = preferences[LANGUAGE_KEY]?.toInt() ?: LANGUAGE_SYSTEM,
                    theme = preferences[THEME_KEY]?.toInt() ?: THEME_DARK,
                    fontSize = preferences[FONT_SIZE_KEY]?.toInt() ?: FONT_SIZE_MEDIUM,
                    coverStyle = preferences[COVER_STYLE_KEY]?.toInt() ?: COVER_STYLE_DEFAULT,
                    primaryColor = preferences[PRIMARY_COLOR_KEY] ?: PRIMARY_COLOR_DEFAULT,
                    backgroundColor = preferences[BACKGROUND_COLOR_KEY] ?: BACKGROUND_COLOR_DEFAULT,
                    batteryOptimizationDisabled = preferences[BATTERY_OPTIMIZATION_KEY] ?: false,
                    backgroundRunningEnabled = preferences[BACKGROUND_RUNNING_KEY] ?: false
                )
            }.first().let { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * 设置语言
     */
    fun setLanguage(language: Int) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[LANGUAGE_KEY] = language.toString()
            }
            _uiState.value = _uiState.value.copy(language = language)
        }
    }

    /**
     * 设置主题
     */
    fun setTheme(theme: Int) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[THEME_KEY] = theme.toString()
            }
            _uiState.value = _uiState.value.copy(theme = theme)
        }
    }

    /**
     * 设置字体大小
     */
    fun setFontSize(fontSize: Int) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FONT_SIZE_KEY] = fontSize.toString()
            }
            _uiState.value = _uiState.value.copy(fontSize = fontSize)
        }
    }

    /**
     * 设置封面样式
     */
    fun setCoverStyle(coverStyle: Int) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[COVER_STYLE_KEY] = coverStyle.toString()
            }
            _uiState.value = _uiState.value.copy(coverStyle = coverStyle)
        }
    }

    /**
     * 设置主色调
     */
    fun setPrimaryColor(color: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PRIMARY_COLOR_KEY] = color
            }
            _uiState.value = _uiState.value.copy(primaryColor = color)
        }
    }

    /**
     * 设置背景色
     */
    fun setBackgroundColor(color: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[BACKGROUND_COLOR_KEY] = color
            }
            _uiState.value = _uiState.value.copy(backgroundColor = color)
        }
    }
    
    /**
     * 设置电池优化状态
     */
    fun setBatteryOptimization(disabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[BATTERY_OPTIMIZATION_KEY] = disabled
            }
            _uiState.value = _uiState.value.copy(batteryOptimizationDisabled = disabled)
            
            // 如果用户选择禁用电池优化，则跳转到系统设置
            if (disabled) {
                requestIgnoreBatteryOptimization()
            }
        }
    }
    
    /**
     * 设置后台运行状态
     */
    fun setBackgroundRunning(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[BACKGROUND_RUNNING_KEY] = enabled
            }
            _uiState.value = _uiState.value.copy(backgroundRunningEnabled = enabled)
            
            // 如果用户选择启用后台运行，则跳转到系统设置
            if (enabled) {
                requestBackgroundRunningPermission()
            }
        }
    }
    
    /**
     * 请求忽略电池优化
     */
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val packageName = context.packageName
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "请求忽略电池优化失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 请求后台运行权限
     * 根据不同厂商设备打开相应的设置页面
     */
    private fun requestBackgroundRunningPermission() {
        try {
            val packageName = context.packageName
            val manufacturer = Build.MANUFACTURER.lowercase()
            val intent = Intent()
            
            when {
                // 华为设备
                manufacturer.contains("huawei") -> {
                    intent.component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                // 小米设备
                manufacturer.contains("xiaomi") -> {
                    intent.component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                // OPPO设备
                manufacturer.contains("oppo") -> {
                    intent.component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                // VIVO设备
                manufacturer.contains("vivo") -> {
                    intent.component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
                // 三星设备
                manufacturer.contains("samsung") -> {
                    intent.component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
                // 联想设备
                manufacturer.contains("lenovo") -> {
                    intent.component = ComponentName(
                        "com.lenovo.security",
                        "com.lenovo.security.purebackground.PureBackgroundActivity"
                    )
                }
                // 其他设备尝试使用通用的电池优化设置
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.data = Uri.parse("package:$packageName")
                    } else {
                        intent.action = Settings.ACTION_APPLICATION_SETTINGS
                    }
                }
            }
            
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d("SettingsViewModel", "打开后台运行权限设置页面: 设备=${manufacturer}")
            
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "打开后台运行权限设置页面失败: ${e.message}", e)
            // 如果特定厂商的设置页面打开失败，尝试打开应用详情页
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e("SettingsViewModel", "打开应用详情页失败: ${e2.message}", e2)
            }
        }
    }
    
    /**
     * 检查电池优化状态
     */
    fun checkBatteryOptimizationStatus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = context.packageName
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            // 低于Android M的设备不支持此功能，默认返回true
            true
        }
    }

    companion object {
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val THEME_KEY = stringPreferencesKey("theme")
        val FONT_SIZE_KEY = stringPreferencesKey("font_size")
        val COVER_STYLE_KEY = stringPreferencesKey("cover_style")
        val PRIMARY_COLOR_KEY = stringPreferencesKey("primary_color")
        val BACKGROUND_COLOR_KEY = stringPreferencesKey("background_color")
        val BATTERY_OPTIMIZATION_KEY = booleanPreferencesKey("battery_optimization_disabled")
        val BACKGROUND_RUNNING_KEY = booleanPreferencesKey("background_running_enabled")
        
        const val LANGUAGE_SYSTEM = 0
        const val LANGUAGE_CHINESE = 1
        const val LANGUAGE_ENGLISH = 2
        
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
        
        const val FONT_SIZE_SMALL = 0
        const val FONT_SIZE_MEDIUM = 1
        const val FONT_SIZE_LARGE = 2
        
        const val COVER_STYLE_DEFAULT = 0
        const val COVER_STYLE_CARD = 1
        const val COVER_STYLE_MATERIAL = 2
        
        const val PRIMARY_COLOR_DEFAULT = "#0091EA"
        const val BACKGROUND_COLOR_DEFAULT = "#FFFFFF"
    }
}

/**
 * 设置UI状态
 */
data class SettingsUiState(
    val language: Int = SettingsViewModel.LANGUAGE_SYSTEM,
    val theme: Int = SettingsViewModel.THEME_DARK,
    val fontSize: Int = SettingsViewModel.FONT_SIZE_MEDIUM,
    val coverStyle: Int = SettingsViewModel.COVER_STYLE_DEFAULT,
    val primaryColor: String = SettingsViewModel.PRIMARY_COLOR_DEFAULT,
    val backgroundColor: String = SettingsViewModel.BACKGROUND_COLOR_DEFAULT,
    val batteryOptimizationDisabled: Boolean = false,
    val backgroundRunningEnabled: Boolean = false
) 