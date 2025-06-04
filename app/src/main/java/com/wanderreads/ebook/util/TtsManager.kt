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
    
    // 当前高亮的句子状态
    private val _highlightState = MutableStateFlow(SentenceHighlightState())
    val highlightState: StateFlow<SentenceHighlightState> = _highlightState.asStateFlow()
    
    // 当前朗读的页面信息（内部使用）
    private var currentBookId: String? = null
    private var currentPageIndex: Int = 0
    private var currentText: String = ""
    private var currentSentenceIndex: Int = 0
    
    // 当前朗读的页面信息（公共访问）
    val bookId: String? get() = currentBookId
    val currentPage: Int get() = currentPageIndex
    val pageCompleted: Boolean get() = _ttsState.value.pageCompleted
    
    // 当前页面的句子列表
    private var sentencesList: List<String> = emptyList()
    
    // 初始化标志
    private var isInitialized = false
    
    // 同步状态标志：-1表示TTS停止，1表示TTS朗读位置与用户当前阅读位置同步，0表示TTS活动但位置不同步
    private val _isSyncPageState = MutableStateFlow(-1)
    val isSyncPageState: StateFlow<Int> = _isSyncPageState.asStateFlow()
    
    // TTS设置
    private val ttsSettings by lazy { TtsSettings.getInstance(context) }
    
    /**
     * 更新同步状态
     * 检查TTS朗读位置是否与用户当前阅读位置同步
     * 
     * 规则：
     * - 如果TTS状态为停止，则IsSyncPageState=-1
     * - 如果TTS状态为朗读或暂停，且TTS朗读位置与用户当前阅读位置同步，则IsSyncPageState=1
     * - 如果TTS状态为朗读或暂停，但TTS朗读位置与用户当前阅读位置不同步，则IsSyncPageState=0
     */
    fun updateSyncPageState() {
        try {
            val mainActivity = com.wanderreads.ebook.MainActivity.getInstance()
            val readBookId = com.wanderreads.ebook.MainActivity.readBookId
            val readCurrentPage = com.wanderreads.ebook.MainActivity.readCurrentPage
            
            // 添加详细日志，记录比较的值
            Log.d(TAG, "同步状态比较: TTS状态=${_ttsState.value.status}, TTS位置(bookId=$currentBookId, page=$currentPageIndex), " +
                  "阅读位置(bookId=$readBookId, page=$readCurrentPage)")
            
            val newState = when (_ttsState.value.status) {
                STATUS_STOPPED -> -1
                STATUS_PLAYING, STATUS_PAUSED -> {
                    // 确保bookId非空并且页码有效
                    if (currentBookId != null && currentPageIndex >= 0 && readBookId != null && readCurrentPage >= 0) {
                        val isSynced = readBookId == currentBookId && readCurrentPage == currentPageIndex
                        if (isSynced) 1 else 0
                    } else {
                        // 如果有任何必要的值为空或无效，则设置为非同步状态
                        0
                    }
                }
                else -> -1 // 默认情况，不应该发生
            }
            
            // 记录状态变化
            val oldState = _isSyncPageState.value
            if (oldState != newState) {
                _isSyncPageState.value = newState
                Log.d(TAG, "同步状态已更新: 从${oldState}变为${newState} " +
                        "(${when(newState) {
                            -1 -> "TTS停止"
                            1 -> "TTS朗读位置与阅读位置同步"
                            0 -> "TTS朗读位置与阅读位置不同步"
                            else -> "未知状态"
                        }}), " +
                        "TTS状态=${_ttsState.value.status}, " +
                        "TTS位置: bookId=$currentBookId, page=$currentPageIndex, " +
                        "阅读位置: bookId=$readBookId, page=$readCurrentPage")
                
                // 如果状态从1变为0，记录更详细的信息，帮助调试
                if (oldState == 1 && newState == 0) {
                    Log.w(TAG, "同步状态从同步(1)变为不同步(0)! " +
                            "TTS位置: bookId=$currentBookId, page=$currentPageIndex, " +
                            "阅读位置: bookId=$readBookId, page=$readCurrentPage")
                }
                // 如果状态从0变为1，也记录详细信息
                else if (oldState == 0 && newState == 1) {
                    Log.d(TAG, "同步状态从不同步(0)变为同步(1)! " +
                            "TTS位置: bookId=$currentBookId, page=$currentPageIndex, " +
                            "阅读位置: bookId=$readBookId, page=$readCurrentPage")
                }
            }
        } catch (e: Exception) {
            // 捕获可能出现的异常，避免影响TTS功能
            Log.e(TAG, "更新同步状态时出错: ${e.message}", e)
            // 保持当前状态不变
        }
    }

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
                
                // 设置语言
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "不支持中文语言")
                    continuation.resume(false)
                    return@TextToSpeech
                }
                
                // 应用TTS设置
                ttsSettings.applyToTts(tts!!)
                
                // 设置TTS进度监听器
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        Log.d(TAG, "开始朗读: $utteranceId")
                        _ttsState.value = _ttsState.value.copy(status = STATUS_PLAYING)
                        
                        // 如果是句子朗读，更新高亮状态
                        if (utteranceId.startsWith("TTS_SENTENCE_")) {
                            try {
                                // 从utteranceId解析句子索引，格式为"TTS_SENTENCE_索引_UUID"
                                val indexPart = utteranceId.split("_")[2]
                                val sentenceIndex = indexPart.toIntOrNull() ?: 0
                                
                                // 更新高亮状态
                                if (sentenceIndex < sentencesList.size) {
                                    // 确保设置isHighlighting为true
                                    _highlightState.value = _highlightState.value.copy(
                                        isHighlighting = true,
                                        currentSentenceIndex = sentenceIndex,
                                        currentSentence = sentencesList[sentenceIndex]
                                    )
                                    
                                    // 记录日志，便于调试
                                    Log.d(TAG, "高亮句子: ${sentencesList[sentenceIndex]}, isHighlighting=true, sentenceIndex=$sentenceIndex")
                                } else {
                                    Log.e(TAG, "句子索引越界: $sentenceIndex >= ${sentencesList.size}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "解析utteranceId失败", e)
                            }
                        } else {
                            // 即使不是句子朗读，也设置高亮状态为true
                            _highlightState.value = _highlightState.value.copy(
                                isHighlighting = true,
                                currentSentenceIndex = 0
                            )
                            Log.d(TAG, "非句子朗读，但设置isHighlighting=true")
                        }
                        
                        // 更新同步状态
                        updateSyncPageState()
                    }
                    
                    override fun onDone(utteranceId: String) {
                        Log.d(TAG, "朗读完成: $utteranceId")
                        
                        // 如果是句子朗读完成，继续读下一句
                        if (utteranceId.startsWith("TTS_SENTENCE_") && _ttsState.value.status == STATUS_PLAYING) {
                            currentSentenceIndex++
                            
                            if (currentSentenceIndex < sentencesList.size) {
                                // 继续朗读下一句
                                speakSentence(sentencesList[currentSentenceIndex], currentSentenceIndex)
                            } else {
                                // 所有句子朗读完成，但不改变状态，让ViewModel决定是否继续
                                _ttsState.value = _ttsState.value.copy(
                                    pageCompleted = true
                                )
                                
                                // 清除高亮状态
                                _highlightState.value = _highlightState.value.copy(
                                    isHighlighting = false,
                                    currentSentenceIndex = -1,
                                    currentSentence = ""
                                )
                                
                                // 页面朗读完成时，记录日志
                                Log.d(TAG, "页面朗读完成: bookId=$currentBookId, page=$currentPageIndex")
                            }
                        } else if (utteranceId.startsWith("TTS_FULL_")) {
                            // 全文朗读完成，但不改变状态，让ViewModel决定是否继续
                            _ttsState.value = _ttsState.value.copy(
                                pageCompleted = true
                            )
                            
                            // 清除高亮状态
                            _highlightState.value = _highlightState.value.copy(
                                isHighlighting = false,
                                currentSentenceIndex = -1,
                                currentSentence = ""
                            )
                            
                            // 页面朗读完成时，记录日志
                            Log.d(TAG, "页面朗读完成: bookId=$currentBookId, page=$currentPageIndex")
                        }
                    }
                    
                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "朗读错误: $utteranceId")
                        _ttsState.value = _ttsState.value.copy(status = STATUS_STOPPED)
                        
                        // 清除高亮状态
                        _highlightState.value = _highlightState.value.copy(
                            isHighlighting = false,
                            currentSentenceIndex = -1,
                            currentSentence = ""
                        )
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
     * 
     * @param bookId 书籍ID
     * @param pageIndex 页码
     * @param text 页面文本
     */
    fun startReading(bookId: String, pageIndex: Int, text: String) {
        if (!isInitialized) {
            Log.e(TAG, "TTS引擎未初始化")
            return
        }
        
        try {
            // 记录调用前的同步状态
            val oldSyncState = _isSyncPageState.value
            
            // 保存当前朗读信息
            currentBookId = bookId
            currentPageIndex = pageIndex
            currentText = text
            currentSentenceIndex = 0
            
            // 分割句子 - 使用整个页面的文本
            sentencesList = com.wanderreads.ebook.util.AppTextUtils.splitTextIntoSentences(text)
            
            // 更新状态
            _ttsState.value = _ttsState.value.copy(
                status = STATUS_PLAYING,
                bookId = bookId,
                currentPage = pageIndex
            )
            
            // 更新同步状态 - 确保在设置完currentBookId和currentPageIndex之后调用
            updateSyncPageState()
            
            // 记录调用后的同步状态
            val newSyncState = _isSyncPageState.value
            Log.d(TAG, "startReading后更新同步状态: bookId=$bookId, page=$pageIndex, 同步状态从${oldSyncState}变为${newSyncState}")
            
            // 如果有句子，开始逐句朗读
            if (sentencesList.isNotEmpty()) {
                try {
                    speakSentence(sentencesList[0], 0)
                } catch (e: Exception) {
                    Log.e(TAG, "朗读第一句失败: ${e.message}", e)
                    // 如果逐句朗读失败，尝试朗读整个文本
                    speakText(text)
                }
            } else {
                // 没有有效句子，直接朗读整个文本
                speakText(text)
            }
            
            // 如果同步状态从1变为0，这可能是自动翻页导致的，尝试再次更新全局阅读位置
            if (oldSyncState == 1 && newSyncState == 0) {
                try {
                    Log.d(TAG, "检测到同步状态从1变为0，尝试恢复同步状态")
                    val mainActivity = com.wanderreads.ebook.MainActivity.getInstance()
                    mainActivity?.updateReadingPosition(bookId, pageIndex, 0) // 总页数暂时设为0，稍后会更新
                    
                    // 再次更新同步状态
                    updateSyncPageState()
                    Log.d(TAG, "尝试恢复同步状态后: IsSyncPageState=${_isSyncPageState.value}")
                } catch (e: Exception) {
                    Log.e(TAG, "尝试恢复同步状态失败: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startReading执行失败: ${e.message}", e)
            // 确保即使出错，TTS状态也会被正确设置
            _ttsState.value = _ttsState.value.copy(
                status = STATUS_PLAYING,
                bookId = bookId,
                currentPage = pageIndex
            )
        }
    }
    
    /**
     * 暂停朗读
     */
    fun pauseReading() {
        if (!isInitialized) return
        
        if (tts?.isSpeaking == true) {
            tts?.stop()
            _ttsState.value = _ttsState.value.copy(status = STATUS_PAUSED)
            // 在暂停状态下保持高亮状态，不做任何清除操作
            
            Log.d(TAG, "暂停朗读: bookId=$currentBookId, page=$currentPageIndex, IsSyncPageState=${_isSyncPageState.value}")
        }
    }
    
    /**
     * 继续朗读
     */
    fun resumeReading() {
        if (!isInitialized) return
        
        if (_ttsState.value.status == STATUS_PAUSED) {
            _ttsState.value = _ttsState.value.copy(status = STATUS_PLAYING)
            
            // 如果当前有高亮的句子，继续朗读该句
            val currentIndex = _highlightState.value.currentSentenceIndex
            if (currentIndex >= 0 && currentIndex < sentencesList.size) {
                speakSentence(sentencesList[currentIndex], currentIndex)
            } else {
                // 否则从头开始朗读
                if (sentencesList.isNotEmpty()) {
                    currentSentenceIndex = 0
                    speakSentence(sentencesList[0], 0)
                } else {
                    speakText(currentText)
                }
            }
            
            // 更新同步状态
            updateSyncPageState()
            
            Log.d(TAG, "继续朗读: bookId=$currentBookId, page=$currentPageIndex, IsSyncPageState=${_isSyncPageState.value}")
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
        sentencesList = emptyList()
        
        // 清除高亮状态
        _highlightState.value = _highlightState.value.copy(
            isHighlighting = false,
            currentSentenceIndex = -1,
            currentSentence = ""
        )
        
        // 更新同步状态
        _isSyncPageState.value = -1
        
        Log.d(TAG, "停止朗读: IsSyncPageState=-1")
    }
    
    /**
     * 朗读文本
     */
    private fun speakText(text: String) {
        if (text.isEmpty()) return
        
        // 应用TTS设置到TTS实例
        ttsSettings.applyToTts(tts!!)
        
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
        
        // 应用TTS设置到TTS实例
        ttsSettings.applyToTts(tts!!)
        
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
     * 获取当前页面的所有句子
     */
    fun getSentences(): List<String> {
        return sentencesList
    }
    
    /**
     * 重置页面完成标志
     */
    fun resetPageCompletedFlag() {
        _ttsState.value = _ttsState.value.copy(pageCompleted = false)
        Log.d(TAG, "重置pageCompleted标志")
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
        
        // 清除高亮状态
        _highlightState.value = _highlightState.value.copy(
            isHighlighting = false,
            currentSentenceIndex = -1,
            currentSentence = ""
        )
        
        // 重置同步状态
        _isSyncPageState.value = -1
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
    
    /**
     * 句子高亮状态数据类
     */
    data class SentenceHighlightState(
        val isHighlighting: Boolean = false,  // 是否正在高亮
        val currentSentenceIndex: Int = -1,   // 当前高亮的句子索引
        val currentSentence: String = ""      // 当前高亮的句子内容
    )
} 