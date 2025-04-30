package com.example.ebook.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ebook.data.local.dataStore
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
                    backgroundColor = preferences[BACKGROUND_COLOR_KEY] ?: BACKGROUND_COLOR_DEFAULT
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

    companion object {
        // 语言
        const val LANGUAGE_SYSTEM = 0
        const val LANGUAGE_SIMPLIFIED_CHINESE = 1
        const val LANGUAGE_TRADITIONAL_CHINESE = 2
        const val LANGUAGE_ENGLISH = 3

        // 主题
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        // 字体大小
        const val FONT_SIZE_SMALL = 0
        const val FONT_SIZE_MEDIUM = 1
        const val FONT_SIZE_LARGE = 2
        const val FONT_SIZE_XLARGE = 3

        // 封面样式
        const val COVER_STYLE_DEFAULT = 0
        const val COVER_STYLE_CARD = 1
        const val COVER_STYLE_MATERIAL = 2

        // 默认颜色
        const val PRIMARY_COLOR_DEFAULT = "#1E3A5F" // 深蓝色
        const val BACKGROUND_COLOR_DEFAULT = "#0F1824" // 深蓝背景色

        // 设置键
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val THEME_KEY = stringPreferencesKey("theme")
        private val FONT_SIZE_KEY = stringPreferencesKey("font_size")
        private val COVER_STYLE_KEY = stringPreferencesKey("cover_style")
        private val PRIMARY_COLOR_KEY = stringPreferencesKey("primary_color")
        private val BACKGROUND_COLOR_KEY = stringPreferencesKey("background_color")
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
    val backgroundColor: String = SettingsViewModel.BACKGROUND_COLOR_DEFAULT
) 