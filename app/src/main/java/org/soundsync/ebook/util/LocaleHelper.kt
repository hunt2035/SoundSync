package org.soundsync.ebook.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import org.soundsync.ebook.ui.settings.SettingsViewModel
import java.util.Locale

/**
 * 语言设置帮助类
 * 用于管理应用的语言设置和切换
 */
object LocaleHelper {

    /**
     * 更新应用的语言配置
     * 
     * @param context Context对象
     * @param languageCode 语言代码，使用SettingsViewModel中的常量值
     * @return 应用了新语言设置的Context对象
     */
    fun updateLocale(context: Context, languageCode: Int): Context {
        val locale = when (languageCode) {
            SettingsViewModel.LANGUAGE_CHINESE -> Locale.SIMPLIFIED_CHINESE
            SettingsViewModel.LANGUAGE_ENGLISH -> Locale.ENGLISH
            else -> getSystemLocale()
        }
        
        return updateConfiguration(context, locale)
    }
    
    /**
     * 获取系统当前的Locale
     * 
     * @return 系统Locale
     */
    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val locales = Resources.getSystem().configuration.locales
            if (locales.isEmpty) Locale.getDefault() else locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
    }
    
    /**
     * 更新Context的语言配置
     * 
     * @param context Context对象
     * @param locale 要应用的Locale
     * @return 应用了新语言设置的Context对象
     */
    private fun updateConfiguration(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
} 