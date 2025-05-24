package com.wanderreads.ebook.ui.reader

import android.Manifest
import android.app.Application
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderreads.ebook.MainActivity
import com.wanderreads.ebook.R
import com.wanderreads.ebook.data.local.dataStore
import com.wanderreads.ebook.data.repository.BookRepository
import com.wanderreads.ebook.data.repository.RecordRepository
import com.wanderreads.ebook.domain.model.Book
import com.wanderreads.ebook.domain.model.Record
import com.wanderreads.ebook.domain.model.SynthesisParams
import com.wanderreads.ebook.domain.model.SynthesisRange
import com.wanderreads.ebook.service.TtsSynthesisService
import com.wanderreads.ebook.ui.settings.SettingsViewModel
import com.wanderreads.ebook.util.PageDirection
import com.wanderreads.ebook.util.reader.BookReaderEngine
import com.wanderreads.ebook.util.reader.SearchResult
import com.wanderreads.ebook.util.reader.model.BookChapter
import com.wanderreads.ebook.util.reader.model.ReaderConfig
import com.wanderreads.ebook.util.reader.model.ReaderContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.Date
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import android.os.Environment

/**
 * 统一的阅读器ViewModel
 * 支持所有格式的电子书，依赖于BookReaderEngine接口进行实际处理
 */
class UnifiedReaderViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val recordRepository: RecordRepository,
    private val bookId: String
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "UnifiedReaderViewModel"
    }

    // 阅读引擎
    private var readerEngine: BookReaderEngine? = null
    
    // UI状态
    private val _uiState = MutableStateFlow(UnifiedReaderUiState())
    val uiState: StateFlow<UnifiedReaderUiState> = _uiState.asStateFlow()
    
    // TTS引擎
    private var tts: TextToSpeech? = null
    private var isTtsActive = false
    
    // 静音检测相关
    private var silenceDetectionJob: kotlinx.coroutines.Job? = null
    private var lastAudioActivity = 0L
    
    // 语音合成服务
    private var ttsSynthesisService: TtsSynthesisService? = null
    private var serviceBound = false
    
    // 语音合成状态
    private val _synthesisState = MutableStateFlow<TtsSynthesisService.SynthesisState?>(null)
    val synthesisState: StateFlow<TtsSynthesisService.SynthesisState?> = _synthesisState.asStateFlow()
    
    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TtsSynthesisService.LocalBinder
            ttsSynthesisService = binder.getService()
            serviceBound = true
            
            // 开始监听合成状态
            viewModelScope.launch {
                ttsSynthesisService?.synthesisState?.collect { state ->
                    _synthesisState.value = state
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            ttsSynthesisService = null
            serviceBound = false
        }
    }
    
    // 媒体播放器相关
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingRecord: Record? = null
    private var playbackProgressHandler: Handler? = null
    private val playbackUpdateInterval = 1000L // 1秒更新一次播放进度
    private var currentPlaybackPosition = 0
    private var totalAudioDuration = 0
    
    init {
        loadBook()
        bindSynthesisService()
        loadSynthesizedAudioList()
    }
    
    /**
     * 加载书籍
     */
    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 从仓库获取书籍信息
                val book = bookRepository.getBookById(bookId)
                
                if (book != null) {
                    // 创建适合此书籍格式的阅读引擎
                    readerEngine = BookReaderEngine.create(getApplication(), book.type)
                    
                    // 初始化引擎
                    readerEngine?.initialize(book, book.lastReadPage)
                    
                    // 加载内容
                    readerEngine?.loadContent()
                    
                    // 初始化UI状态
                    readerEngine?.let { engine ->
                        _uiState.update { state ->
                            state.copy(
                                currentContent = engine.getCurrentPageContent(),
                                chapterTitle = engine.getCurrentChapterTitle()
                            )
                        }
                    }
                    
                    // 订阅引擎状态更新
                    observeEngineState()
                    
                    // 更新书籍的最后打开时间
                    bookRepository.updateLastOpenedDate(book.id, System.currentTimeMillis())
                } else {
                    _uiState.update { it.copy(error = "找不到图书") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "加载图书失败: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * 观察引擎状态
     */
    private fun observeEngineState() {
        readerEngine?.let { engine ->
            viewModelScope.launch {
                engine.state.collect { state ->
                    _uiState.update { uiState ->
                        uiState.copy(
                            book = state.book,
                            currentPage = state.currentPage,
                            totalPages = state.totalPages,
                            currentChapter = state.currentChapter,
                            totalChapters = state.totalChapters,
                            readingProgress = state.readingProgress,
                            currentContent = engine.getCurrentPageContent(),
                            chapterTitle = engine.getCurrentChapterTitle()
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 初始化文本朗读
     */
    fun initTts(onInitListener: (status: Int) -> Unit) {
        if (tts == null) {
            tts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    
                    // 设置TTS进度监听器
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            Log.d(TAG, "开始朗读: $utteranceId")
                        }
                        
                        override fun onDone(utteranceId: String) {
                            Log.d(TAG, "朗读完成: $utteranceId")
                            
                            // 如果还在朗读模式，自动朗读下一页
                            if (isTtsActive) {
                                viewModelScope.launch {
                                    if (readerEngine?.hasNextPage() == true) {
                                        navigatePage(PageDirection.NEXT)
                                    } else {
                                        // 已到最后一页，停止朗读
                                        isTtsActive = false
                                    }
                                }
                            }
                        }
                        
                        override fun onError(utteranceId: String) {
                            Log.e(TAG, "朗读错误: $utteranceId")
                            isTtsActive = false
                        }
                    })
                    
                    readerEngine?.initTts(tts!!)
                }
                onInitListener(status)
            }
        } else {
            onInitListener(TextToSpeech.SUCCESS)
        }
    }
    
    /**
     * 开始或停止朗读
     */
    fun toggleTts(): Boolean {
        isTtsActive = !isTtsActive
        
        if (isTtsActive) {
            speakCurrentPage()
        } else {
            tts?.stop()
        }
        
        return isTtsActive
    }
    
    /**
     * 暂停朗读
     */
    fun pauseTts() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
    }
    
    /**
     * 继续朗读
     */
    fun resumeTts() {
        if (tts?.isSpeaking == false) {
            speakCurrentPage()
        }
    }
    
    /**
     * 停止朗读
     */
    fun stopTts() {
        isTtsActive = false
        tts?.stop()
    }
    
    /**
     * 朗读当前页面
     */
    private fun speakCurrentPage() {
        val textToSpeak = readerEngine?.getCurrentPageText() ?: return
        
        if (textToSpeak.isNotEmpty() && isTtsActive) {
            val params = Bundle()
            val utteranceId = "TTS_${UUID.randomUUID()}"
            tts?.speak(
                textToSpeak,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utteranceId
            )
        }
    }
    
    /**
     * 翻页
     */
    fun navigatePage(direction: PageDirection) {
        viewModelScope.launch {
            if (isTtsActive) {
                // 如果正在朗读，先停止当前页的朗读
                tts?.stop()
            }
            
            readerEngine?.navigatePage(direction)
            
            // 更新当前页内容和章节标题
            readerEngine?.let { engine ->
                _uiState.update { state ->
                    state.copy(
                        currentContent = engine.getCurrentPageContent(),
                        chapterTitle = engine.getCurrentChapterTitle()
                    )
                }
            }
            
            // 如果正在朗读，自动朗读新的页面
            if (isTtsActive) {
                speakCurrentPage()
            }
        }
    }
    
    /**
     * 跳转到指定页面
     */
    fun goToPage(page: Int) {
        viewModelScope.launch {
            if (isTtsActive) {
                // 如果正在朗读，先停止
                tts?.stop()
            }
            
            readerEngine?.goToPage(page)
            
            // 更新当前页内容和章节标题
            readerEngine?.let { engine ->
                _uiState.update { state ->
                    state.copy(
                        currentContent = engine.getCurrentPageContent(),
                        chapterTitle = engine.getCurrentChapterTitle()
                    )
                }
            }
            
            // 如果正在朗读，自动朗读新的页面
            if (isTtsActive) {
                speakCurrentPage()
            }
        }
    }
    
    /**
     * 跳转到指定章节
     */
    fun goToChapter(chapterIndex: Int) {
        viewModelScope.launch {
            if (isTtsActive) {
                // 如果正在朗读，先停止
                tts?.stop()
            }
            
            readerEngine?.goToChapter(chapterIndex)
            
            // 更新当前页内容和章节标题
            readerEngine?.let { engine ->
                _uiState.update { state ->
                    state.copy(
                        currentContent = engine.getCurrentPageContent(),
                        chapterTitle = engine.getCurrentChapterTitle()
                    )
                }
            }
            
            // 如果正在朗读，自动朗读新的页面
            if (isTtsActive) {
                speakCurrentPage()
            }
        }
    }
    
    /**
     * 获取当前页面内容
     */
    fun getContentForCurrentPage(): String {
        val pageText = readerEngine?.getCurrentPageText() ?: ""
        // 如果引擎获取的内容为空，尝试从uiState获取
        return if (pageText.isBlank()) {
            _uiState.value.currentContent?.text ?: ""
        } else {
            pageText
        }
    }
    
    /**
     * 获取章节列表
     */
    fun getChapters(): List<BookChapter> {
        return readerEngine?.getChapters() ?: emptyList()
    }
    
    /**
     * 更新阅读器配置
     */
    fun updateConfig(config: ReaderConfig) {
        viewModelScope.launch {
            readerEngine?.updateConfig(config)
        }
    }
    
    /**
     * 保存阅读进度
     */
    private suspend fun saveReadingProgress() {
        val book = uiState.value.book ?: return
        val currentPage = uiState.value.currentPage
        
        try {
            bookRepository.updateReadingProgress(
                bookId = book.id,
                lastReadPage = currentPage,
                lastReadPosition = uiState.value.readingProgress
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "保存进度失败: ${e.message}") }
        }
    }
    
    /**
     * 搜索文本
     */
    fun searchText(query: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSearching = true) }
                
                val results = readerEngine?.searchText(query) ?: emptyList()
                
                _uiState.update { state ->
                    state.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "搜索失败: ${e.message}",
                        isSearching = false
                    ) 
                }
            }
        }
    }
    
    /**
     * 获取书籍封面
     */
    fun getBookCover(): Bitmap? {
        return uiState.value.book?.coverPath?.let {
            try {
                android.graphics.BitmapFactory.decodeFile(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        
        viewModelScope.launch {
            saveReadingProgress()
            
            // 停止TTS
            tts?.stop()
            tts?.shutdown()
            tts = null
            
            // 解绑合成服务
            unbindSynthesisService()
            
            readerEngine?.close()
            readerEngine = null
        }
        stopAudioPlayback()
    }

    private fun formatProgressText(): String {
        val currentPage = uiState.value.currentPage + 1
        val totalPages = uiState.value.totalPages
        return "${currentPage}/${totalPages}"
    }

    /**
     * 绑定合成服务
     */
    private fun bindSynthesisService() {
        val intent = Intent(getApplication(), TtsSynthesisService::class.java)
        getApplication<Application>().startService(intent)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
    
    /**
     * 解绑合成服务
     */
    private fun unbindSynthesisService() {
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /**
     * 开始语音合成
     */
    fun startSynthesis(params: SynthesisParams) {
        // 先更新UI状态，避免用户感觉卡住
        _uiState.update { it.copy(message = "正在准备语音合成...") }
        
        if (!serviceBound || ttsSynthesisService == null) {
            bindSynthesisService()
            return
        }
        
        // 确保书名和章节名不会过长，并移除非法字符
        val bookTitle = (_uiState.value.book?.title ?: "未知书籍")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .let { if (it.length > 20) it.substring(0, 20) + "..." else it }
            
        val chapterTitle = (_uiState.value.chapterTitle ?: "未知章节")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .let { if (it.length > 20) it.substring(0, 20) + "..." else it }
        
        val title = "$bookTitle - $chapterTitle"
        
        // 获取合成内容
        val textToSynthesize = when (params.synthesisRange) {
            SynthesisRange.CURRENT_PAGE -> {
                // 获取当前页文本
                val pageText = readerEngine?.getCurrentPageText()
                if (pageText.isNullOrBlank()) {
                    Log.d(TAG, "当前页文本为空，使用uiState中的文本")
                    _uiState.value.currentContent?.text ?: ""
                } else {
                    Log.d(TAG, "使用引擎提供的当前页文本，长度: ${pageText.length}")
                    pageText
                }
            }
            SynthesisRange.CURRENT_CHAPTER -> {
                try {
                    // 获取当前章节全部文本
                    Log.d(TAG, "尝试获取当前章节文本")
                    val chapterText = readerEngine?.getCurrentChapterText()
                    
                    if (chapterText.isNullOrBlank()) {
                        // 如果引擎无法提供章节文本，尝试使用当前页文本
                        Log.d(TAG, "当前章节文本为空，使用当前页文本")
                        readerEngine?.getCurrentPageText() ?: _uiState.value.currentContent?.text ?: ""
                    } else {
                        // 章节文本可能过长，记录长度
                        Log.d(TAG, "使用引擎提供的当前章节文本，长度: ${chapterText.length}")
                        
                        // 预处理章节文本，移除不必要的特殊字符
                        preprocessChapterText(chapterText)
                    }
                } catch (e: Exception) {
                    // 如果获取章节文本出错，回退到当前页文本
                    Log.e(TAG, "获取章节文本异常，使用当前页文本", e)
                    _uiState.update { it.copy(
                        error = "获取章节文本失败，已改用当前页文本: ${e.message}"
                    ) }
                    
                    // 延迟清除错误消息
                    viewModelScope.launch {
                        delay(3000)
                        _uiState.update { it.copy(error = null) }
                    }
                    
                    // 返回当前页文本作为备选
                    readerEngine?.getCurrentPageText() ?: _uiState.value.currentContent?.text ?: ""
                }
            }
        }
        
        // 最终文本预处理
        val finalText = preprocessText(textToSynthesize)
        
        if (finalText.isBlank()) {
            _uiState.update { it.copy(error = "没有可合成的文本内容，请确保页面已加载并有内容") }
            return
        }
        
        Log.d(TAG, "开始合成文本，长度: ${finalText.length}, 范围: ${params.synthesisRange}")
        
        // 创建回调
        val callback = object : TtsSynthesisService.SynthesisCallback {
            override fun onProgress(progress: Int) {
                // 进度回调，更新UI
            }
            
            override fun onCompleted(outputPath: String) {
                // 合成完成，记录到文件列表
                _uiState.update { it.copy(
                    message = "语音合成完成，文件已保存到: $outputPath"
                ) }
            }
            
            override fun onError(message: String) {
                handleSynthesisError("语音合成失败: $message")
            }
            
            override fun onCanceled() {
                _uiState.update { it.copy(message = "语音合成已取消") }
            }
            
            override fun onSaveRecord(record: Record) {
                // 保存记录到数据库
                viewModelScope.launch {
                    try {
                        recordRepository.addRecord(record)
                    } catch (e: Exception) {
                        Log.e(TAG, "保存语音合成记录失败", e)
                    }
                }
            }
        }
        
        // 开始合成
        ttsSynthesisService?.addSynthesisTask(
            text = finalText,
            params = params,
            bookId = bookId,
            title = title,
            callback = callback
        )
    }
    
    /**
     * 预处理章节文本
     * 移除不必要的特殊字符，规范化文本格式
     */
    private fun preprocessChapterText(text: String): String {
        return try {
            // 1. 移除连续的空行
            var result = text.replace(Regex("\n{3,}"), "\n\n")
            
            // 2. 移除可能导致TTS引擎出错的特殊字符
            result = result.replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "")
            
            // 3. 替换特殊Unicode字符为常规字符
            result = result.replace(Regex("[\u2018\u2019]"), "'") // 智能单引号
                .replace(Regex("[\u201C\u201D]"), "\"") // 智能双引号
                .replace("\u2026", "...") // 省略号
                .replace("\u2014", "-") // 破折号
            
            // 4. 限制文本长度，避免过大（如果需要）
            if (result.length > 100000) {
                Log.w(TAG, "章节文本过长(${result.length})，截断到100000字符")
                result = result.substring(0, 100000)
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "预处理章节文本异常", e)
            // 如果预处理失败，返回原文本
            text
        }
    }
    
    /**
     * 通用文本预处理
     * 处理所有类型的文本（页面或章节）
     */
    private fun preprocessText(text: String): String {
        return try {
            // 1. 移除HTML标签（如果有）
            var result = text.replace(Regex("<[^>]*>"), " ")
            
            // 2. 规范化空白字符
            result = result.replace(Regex("\\s+"), " ")
            
            // 3. 处理常见HTML实体
            result = result
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
            
            // 4. 移除行首行尾空白
            result = result.trim()
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "预处理文本异常", e)
            // 如果预处理失败，返回原文本
            text
        }
    }
    
    /**
     * 取消语音合成
     */
    fun cancelSynthesis() {
        ttsSynthesisService?.cancelCurrentTask()
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 清除提示消息
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 处理合成错误
     */
    fun handleSynthesisError(errorMessage: String) {
        _uiState.update { it.copy(error = errorMessage) }
    }

    /**
     * 加载合成语音列表
     */
    fun loadSynthesizedAudioList() {
        viewModelScope.launch {
            try {
                recordRepository.getRecordsByBookId(bookId).collect { records ->
                    val synthesizedRecords = records.filter { it.isSynthesized }
                    _uiState.update { it.copy(
                        synthesizedAudioList = synthesizedRecords,
                        showSynthesizedAudioList = false
                    ) }
                    Log.d(TAG, "已加载 ${synthesizedRecords.size} 个合成语音文件")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载合成语音文件失败: ${e.message}")
            }
        }
    }
    
    /**
     * 显示合成语音列表
     */
    fun showSynthesizedAudioList() {
        _uiState.update { it.copy(showSynthesizedAudioList = true) }
    }
    
    /**
     * 隐藏合成语音列表
     */
    fun hideSynthesizedAudioList() {
        _uiState.update { it.copy(showSynthesizedAudioList = false) }
    }
    
    /**
     * 播放合成语音文件
     */
    fun playAudioRecord(record: Record) {
        try {
            // 如果有正在播放的录音，先停止
            stopAudioPlayback()
            
            val recordFile = File(record.voiceFilePath)
            if (!recordFile.exists()) {
                Log.e(TAG, "语音文件不存在: ${record.voiceFilePath}")
                _uiState.update { it.copy(error = "语音文件不存在") }
                return
            }
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(record.voiceFilePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    currentPlayingRecord = record
                    totalAudioDuration = mp.duration
                    _uiState.update { it.copy(
                        currentPlayingRecordId = record.id,
                        currentPlaybackPosition = 0,
                        totalAudioDuration = totalAudioDuration
                    ) }
                    startPlaybackProgressTracking()
                }
                setOnCompletionListener {
                    stopAudioPlayback()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放语音文件失败: ${e.message}")
            stopAudioPlayback()
            _uiState.update { it.copy(error = "播放失败: ${e.message}") }
        }
    }
    
    /**
     * 暂停播放语音
     */
    fun pauseAudioPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopPlaybackProgressTracking()
                _uiState.update { state -> 
                    state.copy(isAudioPlaying = false)
                }
            }
        }
    }
    
    /**
     * 继续播放语音
     */
    fun resumeAudioPlayback() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startPlaybackProgressTracking()
                _uiState.update { state -> 
                    state.copy(isAudioPlaying = true)
                }
            }
        }
    }
    
    /**
     * 停止播放语音
     */
    fun stopAudioPlayback() {
        stopPlaybackProgressTracking()
        
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "停止播放失败: ${e.message}")
            }
        }
        
        mediaPlayer = null
        currentPlayingRecord = null
        
        _uiState.update { 
            it.copy(
                currentPlayingRecordId = null,
                isAudioPlaying = false,
                currentPlaybackPosition = 0,
                totalAudioDuration = 0
            )
        }
    }
    
    /**
     * 开始追踪播放进度
     */
    private fun startPlaybackProgressTracking() {
        playbackProgressHandler = Handler(Looper.getMainLooper())
        
        playbackProgressHandler?.post(object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        currentPlaybackPosition = it.currentPosition
                        _uiState.update { state -> 
                            state.copy(
                                currentPlaybackPosition = currentPlaybackPosition,
                                isAudioPlaying = true
                            )
                        }
                    }
                }
                playbackProgressHandler?.postDelayed(this, playbackUpdateInterval)
            }
        })
    }
    
    /**
     * 停止追踪播放进度
     */
    private fun stopPlaybackProgressTracking() {
        playbackProgressHandler?.removeCallbacksAndMessages(null)
        playbackProgressHandler = null
    }
    
    /**
     * 重命名合成语音文件
     */
    fun renameAudioRecord(record: Record, newName: String) {
        if (newName.isBlank()) {
            _uiState.update { it.copy(error = "文件名不能为空") }
            return
        }
        
        viewModelScope.launch {
            try {
                val updatedRecord = record.copy(title = newName)
                recordRepository.updateRecord(updatedRecord)
                
                // 如果是当前播放的记录，更新当前播放记录
                if (record.id == currentPlayingRecord?.id) {
                    currentPlayingRecord = updatedRecord
                }
                
                _uiState.update { it.copy(message = "文件已重命名") }
                
                // 刷新列表
                loadSynthesizedAudioList()
            } catch (e: Exception) {
                Log.e(TAG, "重命名文件失败: ${e.message}")
                _uiState.update { it.copy(error = "重命名失败: ${e.message}") }
            }
        }
    }
    
    /**
     * 删除合成语音文件
     */
    fun deleteAudioRecord(record: Record) {
        // 如果正在播放，先停止
        if (record.id == currentPlayingRecord?.id) {
            stopAudioPlayback()
        }
        
        viewModelScope.launch {
            try {
                // 删除数据库记录
                recordRepository.deleteRecord(record)
                
                // 删除实际文件
                val file = File(record.voiceFilePath)
                if (file.exists()) {
                    file.delete()
                }
                
                _uiState.update { it.copy(message = "文件已删除") }
                
                // 刷新列表
                loadSynthesizedAudioList()
            } catch (e: Exception) {
                Log.e(TAG, "删除文件失败: ${e.message}")
                _uiState.update { it.copy(error = "删除失败: ${e.message}") }
            }
        }
    }
}

/**
 * 统一阅读器UI状态
 */
data class UnifiedReaderUiState(
    val book: Book? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val currentChapter: Int = 0,
    val totalChapters: Int = 0,
    val chapterTitle: String = "",
    val currentContent: ReaderContent? = null,
    val readingProgress: Float = 0f,
    val config: ReaderConfig = ReaderConfig(),
    val isSearching: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val showControls: Boolean = false,
    val synthesizedAudioList: List<Record> = emptyList(),
    val showSynthesizedAudioList: Boolean = false,
    val currentPlayingRecordId: String? = null,
    val isAudioPlaying: Boolean = false,
    val currentPlaybackPosition: Int = 0,
    val totalAudioDuration: Int = 0
)