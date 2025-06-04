package com.wanderreads.ebook.util

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech

/**
 * TTS设置管理类
 * 用于保存和管理TTS朗读相关的设置
 */
class TtsSettings private constructor(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "tts_settings"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_SENTENCES_PER_CHUNK = "sentences_per_chunk"
        
        @Volatile
        private var INSTANCE: TtsSettings? = null
        
        fun getInstance(context: Context): TtsSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TtsSettings(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 获取语速
     * @return 语速值，范围0.5f-2.0f，默认1.0f
     */
    fun getSpeechRate(): Float {
        return preferences.getFloat(KEY_SPEECH_RATE, 1.0f)
    }
    
    /**
     * 设置语速
     * @param rate 语速值，范围0.5f-2.0f
     */
    fun setSpeechRate(rate: Float) {
        preferences.edit().putFloat(KEY_SPEECH_RATE, rate).apply()
    }
    
    /**
     * 获取每个朗读块中包含的句子数
     * @return 句子数，默认为2
     */
    fun getSentencesPerChunk(): Int {
        return preferences.getInt(KEY_SENTENCES_PER_CHUNK, 2)
    }
    
    /**
     * 设置每个朗读块中包含的句子数
     * @param count 句子数，范围1-10
     */
    fun setSentencesPerChunk(count: Int) {
        preferences.edit().putInt(KEY_SENTENCES_PER_CHUNK, count).apply()
    }
    
    /**
     * 应用设置到TTS实例
     * @param tts TextToSpeech实例
     */
    fun applyToTts(tts: android.speech.tts.TextToSpeech) {
        // 设置语速
        tts.setSpeechRate(getSpeechRate())
    }
} 