package org.soundsync.ebook.ui.reader

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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.soundsync.ebook.MainActivity
import org.soundsync.ebook.R
import org.soundsync.ebook.data.local.dataStore
import org.soundsync.ebook.data.repository.BookRepository
import org.soundsync.ebook.data.repository.RecordRepository
import org.soundsync.ebook.domain.model.Book
import org.soundsync.ebook.domain.model.Record
import org.soundsync.ebook.domain.model.SynthesisParams
import org.soundsync.ebook.domain.model.SynthesisRange
import org.soundsync.ebook.service.TtsSynthesisService
import org.soundsync.ebook.service.TtsService
import org.soundsync.ebook.ui.settings.SettingsViewModel
import org.soundsync.ebook.util.PageDirection
import org.soundsync.ebook.util.reader.BookReaderEngine
import org.soundsync.ebook.util.reader.SearchResult
import org.soundsync.ebook.util.reader.model.BookChapter
import org.soundsync.ebook.util.reader.model.ReaderConfig
import org.soundsync.ebook.util.reader.model.ReaderContent
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
import org.soundsync.ebook.util.TtsManager
import android.content.BroadcastReceiver
import android.content.IntentFilter

/**
 * 统一的阅读器ViewModel
 * 支持所有格式的电子书，依赖于BookReaderEngine接口进行实际处理
 */
class UnifiedReaderViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val recordRepository: RecordRepository,
    private val bookId: String,
    private val initialPage: Int = 0
) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "UnifiedReaderViewModel"
    }

    // 阅读引擎
    private var readerEngine: BookReaderEngine? = null
    
    // UI状态
    private val _uiState = MutableStateFlow(UnifiedReaderUiState())
    val uiState: StateFlow<UnifiedReaderUiState> = _uiState.asStateFlow()
    
    // TTS管理器
    private val ttsManager = TtsManager.getInstance(getApplication())
    
    // TTS状态
    val ttsState = ttsManager.ttsState
    
    // TTS高亮状态
    val highlightState = ttsManager.highlightState
    
    // 静音检测相关
    private var silenceDetectionJob: kotlinx.coroutines.Job? = null
    private var lastAudioActivity = 0L
    
    // TTS服务相关
    private var ttsService: TtsService? = null
    private var ttsServiceBound = false
    
    // TTS服务连接
    private val ttsServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TtsService.LocalBinder
            ttsService = binder.getService()
            ttsServiceBound = true
            Log.d(TAG, "TTS服务已连接")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            ttsService = null
            ttsServiceBound = false
            Log.d(TAG, "TTS服务已断开")
        }
    }
    
    // 语音合成服务
    private var ttsSynthesisService: TtsSynthesisService? = null
    private var serviceBound = false
    
    // 语音合成状态
    private val _synthesisState = MutableStateFlow<TtsSynthesisService.SynthesisState?>(null)
    val synthesisState: StateFlow<TtsSynthesisService.SynthesisState?> = _synthesisState.asStateFlow()
    
    // 语音合成服务连接
    private val synthServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TtsSynthesisService.LocalBinder
            ttsSynthesisService = binder.getService()
            serviceBound = true
            Log.d(TAG, "语音合成服务已连接")
            
            // 开始观察服务的合成状态
            startObservingSynthesisState()
            
            // 如果有待处理的合成任务，可以在这里执行
            if (_uiState.value.message == "正在准备语音合成...") {
                // 通知用户服务已连接
                _uiState.update { it.copy(message = "语音合成服务已连接，正在准备...") }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            ttsSynthesisService = null
            serviceBound = false
            Log.d(TAG, "语音合成服务已断开")
        }
    }
    
    // 媒体播放器相关
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingRecord: Record? = null
    private var playbackProgressHandler: Handler? = null
    private val playbackUpdateInterval = 1000L // 1秒更新一次播放进度
    private var currentPlaybackPosition = 0
    private var totalAudioDuration = 0
    
    // TTS翻页广播接收器
    private val ttsPageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "org.soundsync.ebook.TTS_PAGE_CHANGED") {
                val receivedBookId = intent.getStringExtra("bookId")
                val pageIndex = intent.getIntExtra("pageIndex", -1)
                
                // 只有当广播中的bookId与当前ViewModel的bookId匹配时才处理
                if (receivedBookId == bookId && pageIndex >= 0) {
                    // 更新UI，跳转到指定页面
                    viewModelScope.launch {
                        goToPage(pageIndex)
                        Log.d(TAG, "收到TTS翻页广播，更新UI到页面: $pageIndex")
                    }
                }
            }
        }
    }
    
    init {
        loadBook()
        bindSynthesisService()
        loadSynthesizedAudioList()
        
        // 注册TTS翻页广播接收器
        registerTtsPageChangedReceiver()
        
        // 观察TTS状态 - 不再处理自动翻页，这部分逻辑已移至TtsService
        viewModelScope.launch {
            ttsState.collect { state ->
                // 仅记录状态变化，不处理自动翻页
                if (state.status == TtsManager.STATUS_STOPPED) {
                    Log.d(TAG, "TTS状态变为停止")
                } else if (state.status == TtsManager.STATUS_PLAYING) {
                    Log.d(TAG, "TTS状态变为朗读")
                } else if (state.status == TtsManager.STATUS_PAUSED) {
                    Log.d(TAG, "TTS状态变为暂停")
                }
            }
        }
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

                    // 确定初始页码：如果传入了具体页码则使用传入的，否则使用数据库中保存的lastReadPage
                    val targetPage = if (initialPage > 0) initialPage else book.lastReadPage
                    Log.d(TAG, "确定初始页码: initialPage=$initialPage, book.lastReadPage=${book.lastReadPage}, targetPage=$targetPage")

                    // 初始化引擎
                    readerEngine?.initialize(book, targetPage)
                    
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
                    
                    // 更新全局阅读位置
                    val mainActivity = org.soundsync.ebook.MainActivity.getInstance()
                    mainActivity?.updateReadingPosition(book.id, targetPage, readerEngine?.getTotalPages() ?: 0)

                    // 更新TTS同步状态
                    ttsManager.updateSyncPageState()
                    Log.d(TAG, "书籍加载完成后更新TTS同步状态: bookId=${book.id}, page=${targetPage}, IsSyncPageState=${ttsManager.isSyncPageState.value}")
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
                    
                    // 每次状态更新时，更新全局阅读位置
                    val mainActivity = org.soundsync.ebook.MainActivity.getInstance()
                    mainActivity?.updateReadingPosition(bookId, state.currentPage, state.totalPages)
                    
                    // 更新TTS同步状态
                    ttsManager.updateSyncPageState()
                    Log.d(TAG, "引擎状态更新后更新TTS同步状态: bookId=$bookId, page=${state.currentPage}, IsSyncPageState=${ttsManager.isSyncPageState.value}")
                }
            }
        }
    }
    
    /**
     * 初始化TTS
     */
    fun initTts(onInitComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val success = ttsManager.initialize()
            if (success) {
                // 初始化成功，绑定TTS服务
                bindTtsService()
                onInitComplete(TextToSpeech.SUCCESS)
            } else {
                // 初始化失败
                onInitComplete(TextToSpeech.ERROR)
            }
        }
    }
    
    /**
     * 绑定TTS服务
     */
    private fun bindTtsService() {
        val context = getApplication<Application>().applicationContext
        val serviceIntent = Intent(context, TtsService::class.java)
        
        // 传递书籍标题给服务
        _uiState.value.book?.let { book ->
            serviceIntent.putExtra("bookTitle", book.title)
        }
        
        // 启动服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        
        // 绑定服务 - 使用已有的ttsServiceConnection
        context.bindService(serviceIntent, ttsServiceConnection, Context.BIND_AUTO_CREATE)
        
        Log.d(TAG, "开始绑定TTS服务")
    }
    
    /**
     * 解绑TTS服务
     */
    private fun unbindTtsService() {
        if (ttsServiceBound) {
            try {
                getApplication<Application>().unbindService(ttsServiceConnection)
                ttsServiceBound = false
                Log.d(TAG, "解绑TTS服务")
            } catch (e: Exception) {
                Log.e(TAG, "解绑TTS服务失败", e)
            }
        }
    }
    
    /**
     * 开始或停止朗读
     */
    fun toggleTts(): Boolean {
        val currentState = ttsState.value.status
        
        return when (currentState) {
            TtsManager.STATUS_STOPPED -> {
                // 如果是停止状态，开始朗读
                startReading()
                true
            }
            TtsManager.STATUS_PLAYING -> {
                // 如果是朗读状态，暂停朗读
                ttsManager.pauseReading()
                false
            }
            TtsManager.STATUS_PAUSED -> {
                // 如果是暂停状态，继续朗读
                ttsManager.resumeReading()
                true
            }
            else -> false
        }
    }
    
    /**
     * 开始朗读当前页面
     */
    private fun startReading() {
        val textToSpeak = readerEngine?.getCurrentPageText() ?: return
        if (textToSpeak.isBlank()) return
        
        val currentPage = _uiState.value.currentPage
        val currentBookId = bookId
        
        // 确保在开始朗读前，先更新MainActivity的全局阅读位置
        val mainActivity = org.soundsync.ebook.MainActivity.getInstance()
        mainActivity?.updateReadingPosition(currentBookId, currentPage, _uiState.value.totalPages)
        Log.d(TAG, "开始朗读前更新全局阅读位置: bookId=$currentBookId, page=$currentPage")
        
        // 如果未绑定TTS服务，先启动并绑定服务
        if (!ttsServiceBound) {
            startAndBindTtsService()
        }
        
        // 重置页面完成标志
        ttsManager.resetPageCompletedFlag()
        
        // 开始朗读，使用当前页面的文本和书籍ID
        ttsManager.startReading(currentBookId, currentPage, textToSpeak)
        
        // 确保在开始朗读后，再次更新同步状态
        ttsManager.updateSyncPageState()
        Log.d(TAG, "开始朗读后再次更新同步状态，IsSyncPageState=${ttsManager.isSyncPageState.value}")
    }
    
    /**
     * 启动并绑定TTS服务
     */
    private fun startAndBindTtsService() {
        val serviceIntent = Intent(getApplication(), TtsService::class.java).apply {
            putExtra("bookTitle", _uiState.value.book?.title ?: "")
        }
        
        // 启动服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(serviceIntent)
        } else {
            getApplication<Application>().startService(serviceIntent)
        }
        
        // 绑定服务
        getApplication<Application>().bindService(
            serviceIntent,
            ttsServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
    
    /**
     * 暂停朗读
     */
    fun pauseTts() {
        ttsManager.pauseReading()
    }
    
    /**
     * 继续朗读
     */
    fun resumeTts() {
        ttsManager.resumeReading()
    }
    
    /**
     * 停止朗读
     */
    fun stopTts() {
        ttsManager.stopReading()
    }
    
    /**
     * 获取当前TTS状态
     */
    fun getTtsStatus(): Int {
        return ttsState.value.status
    }
    
    /**
     * 跳转到TTS正在朗读的页面
     */
    fun navigateToTtsPage() {
        val ttsPage = ttsState.value.currentPage
        if (ttsPage > 0) {
            goToPage(ttsPage)
        }
    }
    
    /**
     * 翻页
     */
    fun navigatePage(direction: PageDirection) {
        viewModelScope.launch {
            // 翻页不应该影响TTS朗读状态
            // TTS朗读是全局事件，翻页时不应该暂停或停止朗读
            
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
            
            // 更新全局阅读位置与TTS同步状态
            val mainActivity = org.soundsync.ebook.MainActivity.getInstance()
            mainActivity?.updateReadingPosition(bookId, _uiState.value.currentPage, _uiState.value.totalPages)
            ttsManager.updateSyncPageState()
            
            Log.d("UnifiedReaderViewModel", "用户翻页: direction=$direction, 当前页=${_uiState.value.currentPage}, TTS同步状态=${ttsManager.isSyncPageState.value}")
        }
    }
    
    /**
     * 跳转到指定页面
     */
    fun goToPage(page: Int) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "开始跳转到页面: $page")
                
                // 跳转页面不应该影响TTS朗读状态
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
                
                // 保存阅读进度到数据库
                saveReadingProgress()
                
                // 更新全局阅读位置与TTS同步状态
                try {
                    val mainActivity = org.soundsync.ebook.MainActivity.getInstance()
                    mainActivity?.updateReadingPosition(bookId, _uiState.value.currentPage, _uiState.value.totalPages)
                    ttsManager.updateSyncPageState()
                    
                    Log.d(TAG, "页面跳转完成: page=$page, 当前页=${_uiState.value.currentPage}, TTS同步状态=${ttsManager.isSyncPageState.value}")
                } catch (e: Exception) {
                    Log.e(TAG, "更新全局阅读位置或TTS同步状态失败: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "跳转到页面 $page 失败: ${e.message}", e)
                _uiState.update { it.copy(error = "跳转页面失败: ${e.message}") }
            }
        }
    }
    
    /**
     * 跳转到指定章节
     */
    fun goToChapter(chapterIndex: Int) {
        viewModelScope.launch {
            // 跳转章节不应该影响TTS朗读状态
            
            readerEngine?.goToChapter(chapterIndex)
            
            // 更新当前页内容和章节标题
            readerEngine?.let { engine ->
                _uiState.update { state ->
                    state.copy(
                        currentContent = engine.getCurrentPageContent(),
                        chapterTitle = engine.getCurrentChapterTitle()
                    )
                }
                
                // 更新全局阅读位置
                val mainActivity = org.soundsync.ebook.MainActivity.getInstance()
                mainActivity?.updateReadingPosition(bookId, _uiState.value.currentPage, _uiState.value.totalPages)
                
                // 更新TTS同步状态
                ttsManager.updateSyncPageState()
                Log.d(TAG, "跳转章节后更新TTS同步状态: bookId=$bookId, chapter=$chapterIndex, page=${_uiState.value.currentPage}, IsSyncPageState=${ttsManager.isSyncPageState.value}")
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
            ttsManager.stopReading()
            
            // 解绑TTS服务
            unbindTtsService()
            
            // 解除注册TTS翻页广播接收器
            unregisterTtsPageChangedReceiver()
            
            // 如果使用了@Composable层面的remember，注意这里不会被调用
            // 仅在ViewModel实际被清除时才会被调用
            if (serviceBound) {
                getApplication<Application>().unbindService(synthServiceConnection)
                serviceBound = false
            }
            
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
            synthServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }
    
    /**
     * 解绑合成服务
     */
    private fun unbindSynthesisService() {
        if (serviceBound) {
            getApplication<Application>().unbindService(synthServiceConnection)
            serviceBound = false
        }
    }

    /**
     * 开始观察合成服务的状态
     */
    private fun startObservingSynthesisState() {
        ttsSynthesisService?.let { service ->
            viewModelScope.launch {
                service.synthesisState.collect { state ->
                    // 更新本地合成状态
                    _synthesisState.value = state
                    
                    // 根据状态更新UI
                    when(state.status) {
                        TtsSynthesisService.STATUS_COMPLETED -> {
                            // 合成完成，格式化文件路径
                            val formattedPath = state.outputPath.replace("/storage/emulated/0/", "内部存储/")
                            val directory = formattedPath.substringBeforeLast("/")
                            
                            // 更新UI状态
                            _uiState.update { it.copy(
                                message = "语音合成成功，生成文件位于目录${directory}下"
                            ) }
                            
                            Log.d(TAG, "合成完成，显示成功消息")
                        }
                        TtsSynthesisService.STATUS_ERROR -> {
                            // 合成失败
                            _uiState.update { it.copy(
                                error = "语音合成失败: ${state.message}"
                            ) }
                            Log.e(TAG, "合成失败: ${state.message}")
                        }
                        TtsSynthesisService.STATUS_SYNTHESIZING -> {
                            // 合成进行中，更新进度
                            _uiState.update { it.copy(
                                message = "正在合成语音...（${state.progress}%）"
                            ) }
                        }
                    }
                }
            }
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
        
        // 确保开始观察合成状态
        startObservingSynthesisState()
        
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
                _uiState.update { it.copy(
                    message = "正在合成语音...（${progress}%）"
                ) }
            }
            
            override fun onCompleted(outputPath: String) {
                // 格式化文件路径，使其更易读
                val formattedPath = outputPath.replace("/storage/emulated/0/", "内部存储/")
                
                // 提取目录路径（去掉文件名）
                val directory = formattedPath.substringBeforeLast("/")
                
                // 合成完成，更新状态和显示成功消息
                _uiState.update { it.copy(
                    message = "语音合成成功，生成文件位于目录${directory}下"
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
     * 清除Snackbar消息
     */
    fun clearSnackbarMessage() {
        _uiState.update { it.copy(
            snackbarMessage = null
        ) }
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
     * 调整音频播放位置
     * 
     * @param record 要调整的音频记录
     * @param position 目标播放位置（毫秒）
     */
    fun seekToPosition(record: Record, position: Int) {
        try {
            // 确保是当前正在播放的记录
            if (record.id == currentPlayingRecord?.id && mediaPlayer != null) {
                // 调整播放位置
                mediaPlayer?.seekTo(position)
                
                // 更新UI状态
                currentPlaybackPosition = position
                _uiState.update { state ->
                    state.copy(currentPlaybackPosition = position)
                }
                
                // 如果当前是暂停状态，不需要做其他操作
                // 如果需要在调整位置后自动播放，可以在此处添加相应代码
            } else if (record.id != currentPlayingRecord?.id) {
                // 如果不是当前播放的记录，先播放该记录，然后再调整位置
                playAudioRecord(record)
                // 由于playAudioRecord是异步准备的，需要在准备完成后再调整位置
                mediaPlayer?.setOnPreparedListener { mp ->
                    mp.start()
                    mp.seekTo(position)
                    currentPlayingRecord = record
                    totalAudioDuration = mp.duration
                    currentPlaybackPosition = position
                    _uiState.update { it.copy(
                        currentPlayingRecordId = record.id,
                        currentPlaybackPosition = position,
                        totalAudioDuration = totalAudioDuration,
                        isAudioPlaying = true
                    ) }
                    startPlaybackProgressTracking()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "调整播放位置失败: ${e.message}")
            _uiState.update { it.copy(error = "调整播放位置失败: ${e.message}") }
        }
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

    /**
     * 注册TTS翻页广播接收器
     */
    private fun registerTtsPageChangedReceiver() {
        try {
            val filter = IntentFilter("org.soundsync.ebook.TTS_PAGE_CHANGED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(ttsPageChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                getApplication<Application>().registerReceiver(ttsPageChangedReceiver, filter)
            }
            Log.d(TAG, "TTS翻页广播接收器注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "注册TTS翻页广播接收器失败: ${e.message}", e)
        }
    }
    
    /**
     * 解除注册TTS翻页广播接收器
     */
    private fun unregisterTtsPageChangedReceiver() {
        try {
            getApplication<Application>().unregisterReceiver(ttsPageChangedReceiver)
            Log.d(TAG, "TTS翻页广播接收器解除注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "解除注册TTS翻页广播接收器失败: ${e.message}", e)
        }
    }
    
    /**
     * 显示提示消息
     */
    private fun showSnackbar(message: String) {
        _uiState.update { it.copy(
            snackbarMessage = message
        ) }
    }

    /**
     * 测试TTS语速效果
     */
    fun testTtsSpeechRate(testText: String) {
        // 停止当前可能正在进行的朗读
        ttsManager.stopReading()
        
        // 使用测试文本进行朗读
        ttsManager.startReading("test", 0, testText)
    }

    /**
     * 获取当前书籍的全部文本内容
     * 用于修改文本功能
     */
    fun getBookFullContent(): String {
        return readerEngine?.getAllContent() ?: ""
    }

    /**
     * 重新加载书籍内容
     * 用于修改文本后刷新显示
     */
    fun reloadContent() {
        viewModelScope.launch {
            try {
                readerEngine?.let { engine ->
                    // 重新加载内容
                    engine.loadContent()
                    
                    // 更新UI状态
                    _uiState.update { state ->
                        state.copy(
                            currentContent = engine.getCurrentPageContent(),
                            chapterTitle = engine.getCurrentChapterTitle()
                        )
                    }
                    
                    // 如果正在TTS朗读，重新开始朗读当前页
                    if (ttsManager.ttsState.value.status == TtsManager.STATUS_PLAYING) {
                        ttsManager.stopReading()
                        ttsManager.startReading(
                            getContentForCurrentPage(),
                            _uiState.value.currentPage,
                            _uiState.value.book?.title ?: ""
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "重新加载内容失败", e)
            }
        }
    }

    /**
     * 播放前一句
     */
    fun playPreviousSentence() {
        ttsManager.playPreviousSentence()
    }
    
    /**
     * 播放后一句
     */
    fun playNextSentence() {
        ttsManager.playNextSentence()
    }

    /**
     * 打开文件所在目录并定位到该文件
     */
    fun openFileLocation(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                _uiState.update { it.copy(error = "文件不存在") }
                return
            }
            
            val parentDir = file.parentFile
            if (parentDir == null || !parentDir.exists()) {
                _uiState.update { it.copy(error = "父目录不存在") }
                return
            }
            
            Log.d(TAG, "尝试打开文件所在目录: ${parentDir.absolutePath}")
            Log.d(TAG, "文件名: ${file.name}")
            
            // 获取相对路径
            val relativeDirPath = if (parentDir.absolutePath.contains("SoundSync")) {
                parentDir.absolutePath.substringAfter("SoundSync")
            } else {
                ""
            }
            
            // 优先使用FileProvider生成content URI
            try {
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    parentDir
                )
                
                val viewIntent = Intent(Intent.ACTION_VIEW)
                viewIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                viewIntent.putExtra("org.openintents.extra.ABSOLUTE_PATH", parentDir.absolutePath)
                viewIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(viewIntent)
                Log.d(TAG, "成功使用ACTION_VIEW和content URI打开文件所在目录")
                return
            } catch (e: Exception) {
                Log.e(TAG, "使用ACTION_VIEW和content URI打开失败: ${e.message}", e)
            }
            
            // 尝试方法2: 使用ACTION_GET_CONTENT打开文件浏览器
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                
                // 构建指向Documents/SoundSync目录的URI
                val documentPath = if (parentDir.absolutePath.contains("SoundSync")) {
                    "primary:Documents/SoundSync${relativeDirPath}"
                } else {
                    "primary:Documents/SoundSync${relativeDirPath}"
                }
                
                // 尝试设置初始目录
                val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentPath)
                } else {
                    Uri.parse("content://com.android.externalstorage.documents/document/$documentPath")
                }
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(Intent.createChooser(intent, "选择文件浏览器"))
                Log.d(TAG, "成功使用ACTION_GET_CONTENT打开文件浏览器")
                return
            } catch (e: Exception) {
                Log.e(TAG, "使用ACTION_GET_CONTENT打开文件浏览器失败: ${e.message}", e)
            }
            
            // 尝试使用外部应用选择器
            try {
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    parentDir
                )
                
                val finalIntent = Intent(Intent.ACTION_VIEW)
                finalIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                finalIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(Intent.createChooser(finalIntent, "选择文件浏览器打开目录"))
                Log.d(TAG, "成功使用文件浏览器选择器打开")
                return
            } catch (e: Exception) {
                Log.e(TAG, "使用文件浏览器选择器打开失败: ${e.message}", e)
            }
            
            // 尝试方法3: 使用特定文件管理器的Intent
            try {
                // 构建指向Documents/SoundSync目录的URI
                val documentPath = if (parentDir.absolutePath.contains("SoundSync")) {
                    "primary:Documents/SoundSync${relativeDirPath}"
                } else {
                    "primary:Documents/SoundSync${relativeDirPath}"
                }
                Log.d(TAG, "目标文档路径: $documentPath")
                
                // 尝试使用系统文件管理器打开
                val systemFileManagerPackages = arrayOf(
                    // 通用Android系统文件管理器
                    Pair("com.android.documentsui", "com.android.documentsui.files.FilesActivity"),
                    // 三星设备
                    Pair("com.sec.android.app.myfiles", "com.sec.android.app.myfiles.common.MainActivity"),
                    // 华为设备
                    Pair("com.huawei.filemanager", "com.huawei.filemanager.activity.MainActivity"),
                    // 小米设备
                    Pair("com.miui.fileexplorer", "com.miui.fileexplorer.FileExplorerTabActivity"),
                    // OPPO设备
                    Pair("com.coloros.filemanager", "com.coloros.filemanager.MainActivity"),
                    // VIVO设备
                    Pair("com.vivo.filemanager", "com.vivo.filemanager.activity.MainActivity")
                )
                
                var launched = false
                for ((packageName, className) in systemFileManagerPackages) {
                    try {
                        val contentUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            parentDir
                        )
                        
                        val fileManagerIntent = Intent(Intent.ACTION_VIEW)
                        fileManagerIntent.setDataAndType(contentUri, "vnd.android.document/directory")
                        fileManagerIntent.putExtra("org.openintents.extra.ABSOLUTE_PATH", parentDir.absolutePath)
                        fileManagerIntent.component = ComponentName(packageName, className)
                        fileManagerIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        
                        context.startActivity(fileManagerIntent)
                        launched = true
                        Log.d(TAG, "成功使用 $packageName 打开文件所在目录")
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "尝试使用 $packageName 打开文件所在目录失败: ${e.message}")
                    }
                }
                
                if (launched) return
            } catch (e: Exception) {
                Log.e(TAG, "使用特定文件管理器打开失败: ${e.message}", e)
            }
            
            // 如果以上方法都失败，尝试SAF方法
            try {
                // 构建指向Documents/SoundSync目录的URI
                val documentPath = if (parentDir.absolutePath.contains("SoundSync")) {
                    "primary:Documents/SoundSync${relativeDirPath}"
                } else {
                    "primary:Documents/SoundSync${relativeDirPath}"
                }
                
                val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentPath)
                } else {
                    Uri.parse("content://com.android.externalstorage.documents/document/$documentPath")
                }
                
                val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    treeIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                } else {
                    treeIntent.putExtra("android.provider.extra.INITIAL_URI", initialUri)
                    treeIntent.putExtra("android.content.extra.SHOW_ADVANCED", true)
                }
                
                treeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(treeIntent)
                Log.d(TAG, "成功使用ACTION_OPEN_DOCUMENT_TREE打开文件所在目录")
                return
            } catch (e: Exception) {
                Log.e(TAG, "使用ACTION_OPEN_DOCUMENT_TREE打开失败: ${e.message}", e)
                _uiState.update { it.copy(error = "无法打开文件所在目录: ${e.message}") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开文件所在目录失败", e)
            _uiState.update { it.copy(error = "无法打开文件所在目录: ${e.message}") }
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
    val totalAudioDuration: Int = 0,
    val snackbarMessage: String? = null
)