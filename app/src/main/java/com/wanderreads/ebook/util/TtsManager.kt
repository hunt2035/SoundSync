package com.wanderreads.ebook.util

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * TTS管理器
 * 管理应用内的TTS功能，提供全局状态管理
 */
class TtsManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"
        
        // TTS状态常量
        const val STATUS_STOPPED = 0  // 停止状态
        const val STATUS_PLAYING = 1  // 朗读状态
        const val STATUS_PAUSED = 2   // 暂停状态
        
        @Volatile
        private var INSTANCE: TtsManager? = null
        
        fun getInstance(context: Context): TtsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TtsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // TTS引擎
    private var tts: TextToSpeech? = null
    
    // TTS状态
    private val _ttsState = MutableStateFlow(TtsState())
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()
    
    // 当前朗读的页面信息
    private var currentBookId: String? = null
    private var currentPageIndex: Int = 0
    private var currentText: String = ""
    
    // 当前朗读的句子位置
    private var currentSentenceIndex: Int = 0
    
    // 初始化标志
    private var isInitialized = false
    
    /**
     * 初始化TTS引擎
     */
    suspend fun initialize(): Boolean = suspendCoroutine { continuation ->
        if (isInitialized) {
            continuation.resume(true)
            return@suspendCoroutine
        }
        
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS引擎初始化成功")
                tts?.language = Locale.CHINESE
                
                // 设置TTS进度监听器
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        Log.d(TAG, "开始朗读: $utteranceId")
                        _ttsState.value = _ttsState.value.copy(status = STATUS_PLAYING)
                    }
                    
                    override fun onDone(utteranceId: String) {
                        Log.d(TAG, "朗读完成: $utteranceId")
                        
                        // 如果是句子朗读完成，继续读下一句
                        if (utteranceId.startsWith("TTS_SENTENCE_") && _ttsState.value.status == STATUS_PLAYING) {
                            currentSentenceIndex++
                            // 更精确的句子分隔正则表达式，处理点+空格的情况，同时增加中英文冒号和省略号
                            val sentences = currentText.split(
                                Regex(
                            // "([.!?;:][\\s\\n])|" +  // 英文标点后跟空白或换行
                                    "([.][\\s\\n])|" +  // 英文标点后跟空白或换行

                                    "([.!?;:]$)|" +         // 英文标点在行尾
                                    "[。！？；：]|" +        // 中文标点
                                    "\\.{3,}|…{1,}"         // 英文省略号和中文省略号
                                )
                            ).filter { it.isNotBlank() }    // 过滤空白句子
                            
                            if (currentSentenceIndex < sentences.size) {
                                // 继续朗读下一句
                                speakSentence(sentences[currentSentenceIndex], currentSentenceIndex)
                            } else {
                                // 所有句子朗读完成，但不改变状态，让ViewModel决定是否继续
                                _ttsState.value = _ttsState.value.copy(
                                    pageCompleted = true
                                )
                            }
                        } else if (utteranceId.startsWith("TTS_FULL_")) {
                            // 全文朗读完成，但不改变状态，让ViewModel决定是否继续
                            _ttsState.value = _ttsState.value.copy(
                                pageCompleted = true
                            )
                        }
                    }
                    
                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "朗读错误: $utteranceId")
                        _ttsState.value = _ttsState.value.copy(status = STATUS_STOPPED)
                    }
                    
                    // API 23以下的错误回调
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String, errorCode: Int) {
                        onError(utteranceId)
                    }
                    
                    // API 21+的进度回调
                    override fun onRangeStart(
                        utteranceId: String,
                        start: Int,
                        end: Int,
                        frame: Int
                    ) {
                        // 当前正在朗读的文本范围
                        Log.v(TAG, "朗读范围 $start-$end")
                    }
                })
                
                isInitialized = true
                continuation.resume(true)
            } else {
                Log.e(TAG, "TTS引擎初始化失败，状态码: $status")
                continuation.resume(false)
            }
        }
    }
    
    /**
     * 开始从指定页面朗读
     */
    fun startReading(bookId: String, pageIndex: Int, text: String) {
        if (!isInitialized) {
            Log.e(TAG, "TTS引擎未初始化")
            return
        }
        
        // 保存当前朗读信息
        currentBookId = bookId
        currentPageIndex = pageIndex
        currentText = text
        currentSentenceIndex = 0
        
        // 更新状态
        _ttsState.value = _ttsState.value.copy(
            status = STATUS_PLAYING,
            bookId = bookId,
            currentPage = pageIndex
        )
        
        // 开始朗读
        speakText(text)
    }
    
    /**
     * 暂停朗读
     */
    fun pauseReading() {
        if (!isInitialized) return
        
        if (tts?.isSpeaking == true) {
            tts?.stop()
            _ttsState.value = _ttsState.value.copy(status = STATUS_PAUSED)
        }
    }
    
    /**
     * 继续朗读
     */
    fun resumeReading() {
        if (!isInitialized) return
        
        if (_ttsState.value.status == STATUS_PAUSED) {
            _ttsState.value = _ttsState.value.copy(status = STATUS_PLAYING)
            speakText(currentText)
        }
    }
    
    /**
     * 停止朗读
     */
    fun stopReading() {
        if (!isInitialized) return
        
        tts?.stop()
        _ttsState.value = _ttsState.value.copy(
            status = STATUS_STOPPED,
            bookId = null,
            currentPage = 0
        )
        currentBookId = null
        currentPageIndex = 0
        currentText = ""
        currentSentenceIndex = 0
    }
    
    /**
     * 朗读文本
     */
    private fun speakText(text: String) {
        if (text.isEmpty()) return
        
        val params = Bundle()
        val utteranceId = "TTS_FULL_${UUID.randomUUID()}"
        val result = tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )
        
        Log.d(TAG, "TTS.speak调用结果: $result")
    }
    
    /**
     * 朗读单个句子
     */
    private fun speakSentence(sentence: String, index: Int) {
        if (sentence.isEmpty()) return
        
        val params = Bundle()
        val utteranceId = "TTS_SENTENCE_${index}_${UUID.randomUUID()}"
        val result = tts?.speak(
            sentence,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )
        
        Log.d(TAG, "TTS.speakSentence调用结果: $result, 句子: $sentence")
    }
    
    /**
     * 重置页面完成标志
     */
    fun resetPageCompletedFlag() {
        _ttsState.value = _ttsState.value.copy(pageCompleted = false)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        INSTANCE = null
    }
    
    /**
     * TTS状态数据类
     */
    data class TtsState(
        val status: Int = STATUS_STOPPED,  // TTS状态
        val bookId: String? = null,        // 当前朗读的书籍ID
        val currentPage: Int = 0,          // 当前朗读的页面
        val pageCompleted: Boolean = false // 当前页朗读是否完成
    )
} 