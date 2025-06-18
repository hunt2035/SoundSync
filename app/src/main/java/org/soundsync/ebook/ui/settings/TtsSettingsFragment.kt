package org.soundsync.ebook.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.soundsync.ebook.R
import org.soundsync.ebook.util.TtsManager
import org.soundsync.ebook.util.TtsSettings

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
    }
    
    /**
     * 测试TTS朗读
     */
    private fun testTts() {
        val testText = "这是一个TTS朗读测试。您可以调整语速，以获得最佳的朗读效果。"
        ttsManager.startReading("test", 0, testText)
    }
} 