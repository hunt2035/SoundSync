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
        private const val KEY_SPEECH_VOLUME = "speech_volume"
        private const val KEY_SENTENCES_PER_CHUNK = "sentences_per_chunk"
        private const val KEY_SILENCE_DURATION = "silence_duration"
        
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
     * 获取音量
     * @return 音量值，范围0.0f-1.0f，默认1.0f
     */
    fun getSpeechVolume(): Float {
        return preferences.getFloat(KEY_SPEECH_VOLUME, 1.0f)
    }
    
    /**
     * 设置音量
     * @param volume 音量值，范围0.0f-1.0f
     */
    fun setSpeechVolume(volume: Float) {
        preferences.edit().putFloat(KEY_SPEECH_VOLUME, volume).apply()
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
     * 获取句子间的停顿时间
     * @return 停顿时间，范围0-100，默认为10
     */
    fun getSilenceDuration(): Int {
        return preferences.getInt(KEY_SILENCE_DURATION, 10)
    }
    
    /**
     * 设置句子间的停顿时间
     * @param duration 停顿时间，范围0-100
     */
    fun setSilenceDuration(duration: Int) {
        preferences.edit().putInt(KEY_SILENCE_DURATION, duration).apply()
    }
    
    /**
     * 应用设置到TTS参数Bundle
     * @param params TTS参数Bundle
     */
    fun applyToParams(params: android.os.Bundle) {
        // 在Android TTS中，语速和音量不是通过Bundle参数设置的
        // 而是通过TextToSpeech实例的setSpeechRate和setVolume方法设置
        // 这里我们只设置句子间停顿时间参数
        params.putString("silence", getSilenceDuration().toString())
    }
    
    /**
     * 应用设置到TTS实例
     * @param tts TextToSpeech实例
     */
    fun applyToTts(tts: android.speech.tts.TextToSpeech) {
        // 设置语速
        tts.setSpeechRate(getSpeechRate())
        
        // 设置音量（注意：Android TTS没有直接设置音量的API，音量通常由系统控制）
        // 如果需要控制音量，可以考虑使用AudioManager
    }
} 