package com.wanderreads.ebook.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.wanderreads.ebook.R
import com.wanderreads.ebook.util.TtsManager
import com.wanderreads.ebook.util.TtsSettings

/**
 * TTS设置界面
 * 用于调整TTS朗读相关的设置
 */
class TtsSettingsFragment : Fragment() {
    
    private lateinit var ttsSettings: TtsSettings
    private lateinit var ttsManager: TtsManager
    
    // 语速相关控件
    private lateinit var speechRateSeekBar: SeekBar
    private lateinit var speechRateValueText: TextView
    
    // 音量相关控件
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeValueText: TextView
    
    // 句子间停顿时间相关控件
    private lateinit var silenceDurationSeekBar: SeekBar
    private lateinit var silenceDurationValueText: TextView
    
    // 测试按钮
    private lateinit var testButton: Button
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tts_settings, container, false)
        
        // 初始化TTS设置和管理器
        ttsSettings = TtsSettings.getInstance(requireContext())
        ttsManager = TtsManager.getInstance(requireContext())
        
        // 初始化控件
        initViews(view)
        
        // 加载当前设置
        loadCurrentSettings()
        
        return view
    }
    
    /**
     * 初始化控件
     */
    private fun initViews(view: View) {
        // 语速相关控件
        speechRateSeekBar = view.findViewById(R.id.speech_rate_seekbar)
        speechRateValueText = view.findViewById(R.id.speech_rate_value)
        
        // 音量相关控件
        volumeSeekBar = view.findViewById(R.id.volume_seekbar)
        volumeValueText = view.findViewById(R.id.volume_value)
        
        // 句子间停顿时间相关控件
        silenceDurationSeekBar = view.findViewById(R.id.silence_duration_seekbar)
        silenceDurationValueText = view.findViewById(R.id.silence_duration_value)
        
        // 测试按钮
        testButton = view.findViewById(R.id.test_tts_button)
        
        // 设置SeekBar监听器
        setupSeekBarListeners()
        
        // 设置测试按钮监听器
        testButton.setOnClickListener {
            testTts()
        }
    }
    
    /**
     * 设置SeekBar监听器
     */
    private fun setupSeekBarListeners() {
        // 语速SeekBar监听器
        speechRateSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // 将进度值转换为0.5-2.0范围的语速值
                val speechRate = 0.5f + progress * 1.5f / 100
                speechRateValueText.text = String.format("%.1f", speechRate)
                
                if (fromUser) {
                    ttsSettings.setSpeechRate(speechRate)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        
        // 音量SeekBar监听器
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // 将进度值转换为0.0-1.0范围的音量值
                val volume = progress / 100f
                volumeValueText.text = String.format("%.1f", volume)
                
                if (fromUser) {
                    ttsSettings.setSpeechVolume(volume)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        
        // 句子间停顿时间SeekBar监听器
        silenceDurationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // 将进度值转换为0-100范围的停顿时间
                val silenceDuration = progress
                silenceDurationValueText.text = silenceDuration.toString()
                
                if (fromUser) {
                    ttsSettings.setSilenceDuration(silenceDuration)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }
    
    /**
     * 加载当前设置
     */
    private fun loadCurrentSettings() {
        // 加载语速设置
        val speechRate = ttsSettings.getSpeechRate()
        val speechRateProgress = ((speechRate - 0.5f) * 100 / 1.5f).toInt()
        speechRateSeekBar.progress = speechRateProgress
        speechRateValueText.text = String.format("%.1f", speechRate)
        
        // 加载音量设置
        val volume = ttsSettings.getSpeechVolume()
        val volumeProgress = (volume * 100).toInt()
        volumeSeekBar.progress = volumeProgress
        volumeValueText.text = String.format("%.1f", volume)
        
        // 加载句子间停顿时间设置
        val silenceDuration = ttsSettings.getSilenceDuration()
        silenceDurationSeekBar.progress = silenceDuration
        silenceDurationValueText.text = silenceDuration.toString()
    }
    
    /**
     * 测试TTS朗读
     */
    private fun testTts() {
        val testText = "这是一个TTS朗读测试。您可以调整语速、音量和句子间停顿时间，以获得最佳的朗读效果。"
        ttsManager.startReading("test", 0, testText)
    }
} 