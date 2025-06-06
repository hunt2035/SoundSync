package com.wanderreads.ebook.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wanderreads.ebook.data.local.AppDatabase
import com.wanderreads.ebook.data.repository.BookRepositoryImpl
import com.wanderreads.ebook.ui.bookshelf.BookshelfScreen
import com.wanderreads.ebook.ui.bookshelf.BookshelfViewModel
import com.wanderreads.ebook.ui.bookshelf.BookshelfViewModelFactory
import com.wanderreads.ebook.ui.components.WebImportDialog
import com.wanderreads.ebook.ui.components.WebViewScreen
import com.wanderreads.ebook.ui.history.HistoryScreen
import com.wanderreads.ebook.ui.history.HistoryViewModel
import com.wanderreads.ebook.ui.history.HistoryViewModelFactory
import com.wanderreads.ebook.ui.library.LibraryScreen
import com.wanderreads.ebook.ui.settings.SettingsScreen
import com.wanderreads.ebook.ui.importbook.ImportBookScreen
import com.wanderreads.ebook.ui.reader.ReaderScreen
import com.wanderreads.ebook.ui.reader.ReaderViewModel
import com.wanderreads.ebook.ui.reader.ReaderViewModelFactory
import com.wanderreads.ebook.ui.reader.EpubReaderScreen
import com.wanderreads.ebook.ui.reader.EpubReaderViewModel
import com.wanderreads.ebook.ui.reader.EpubReaderViewModelFactory
import com.wanderreads.ebook.ui.settings.SettingsViewModelFactory
import com.wanderreads.ebook.ui.settings.SettingsViewModel
import com.wanderreads.ebook.ui.settings.TtsSettingsScreen
import com.wanderreads.ebook.ui.reader.UnifiedReaderScreen
import com.wanderreads.ebook.ui.reader.UnifiedReaderViewModel
import com.wanderreads.ebook.ui.reader.UnifiedReaderViewModelFactory
import com.wanderreads.ebook.domain.model.BookType
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 应用导航路由
 */
sealed class Screen( 
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val hasBottomBar: Boolean = true
) {
    data object Bookshelf : Screen(
        route = "bookshelf",
        title = "书架",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    
    data object Library : Screen(
        route = "library",
        title = "书库",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List
    )
    
    data object History : Screen(
        route = "history",
        title = "历史",
        selectedIcon = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.Star
    )
    
    data object Settings : Screen(
        route = "settings",
        title = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    
    data object Reader : Screen(
        route = "reader/{bookId}",
        title = "阅读器",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
        hasBottomBar = false
    ) {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
    
    data object EpubReader : Screen(
        route = "epub_reader/{bookId}",
        title = "EPUB阅读器",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
        hasBottomBar = false
    ) {
        fun createRoute(bookId: String) = "epub_reader/$bookId"
    }
    
    data object ImportBook : Screen(
        route = "import",
        title = "导入",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
        hasBottomBar = false
    )
    
    data object TTS : Screen(
        route = "tts/{bookId}",
        title = "朗读",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
        hasBottomBar = false
    ) {
        fun createRoute(bookId: String) = "tts/$bookId"
    }
    
    data object UnifiedReader : Screen(
        route = "unified_reader/{bookId}?pageIndex={pageIndex}",
        title = "阅读器",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
        hasBottomBar = false
    ) {
        fun createRoute(bookId: String) = "unified_reader/$bookId"
        fun createRoute(bookId: String, pageIndex: Int) = "unified_reader/$bookId?pageIndex=$pageIndex"
    }
    
    data object WebView : Screen(
        route = "webview?url={url}",
        title = "网页浏览",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
        hasBottomBar = false
    ) {
        fun createRoute(url: String) = "webview?url=${url}"
    }
    
    data object TtsSettings : Screen(
        route = "tts_settings",
        title = "TTS设置",
        selectedIcon = Icons.Filled.VolumeUp,
        unselectedIcon = Icons.Filled.VolumeUp,
        hasBottomBar = false
    )
}

/**
 * 底部导航项
 */
val bottomNavItems = listOf(
    Screen.Bookshelf,
    Screen.Library,
    Screen.History,
    Screen.Settings
)

/**
 * 应用导航组件
 */
@Composable
fun AppNavigation(
    onNavControllerReady: (NavController) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // 提供NavController的引用
    LaunchedEffect(navController) {
        onNavControllerReady(navController)
    }
    
    // 确定当前路由是否需要显示底部栏
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = remember(currentRoute) {
        bottomNavItems.any { screen -> 
            currentRoute == screen.route 
        }
    }
    
    // AppDatabase实例
    val context = LocalContext.current
    val database = remember { AppDatabase.getInstance(context) }
    
    // 仓库实例
    val bookRepository = remember { 
        BookRepositoryImpl(
            context = context,
            bookDao = database.bookDao()
        ) 
    }
    
    // 书架ViewModel
    val bookshelfViewModel = remember { 
        BookshelfViewModel(
            context = context,
            bookRepository = bookRepository
        ) 
    }
    
    // 收集UI状态
    val uiState by bookshelfViewModel.uiState.collectAsState()
    
    // 网址导入对话框状态
    var showWebImportDialog by remember { mutableStateOf(false) }
    // 导入错误状态
    var importError by remember { mutableStateOf<String?>(null) }
    
    // 显示导入错误提示
    importError?.let { error ->
        androidx.compose.material3.Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                androidx.compose.material3.TextButton(
                    onClick = { importError = null }
                ) {
                    Text("确定")
                }
            },
            dismissAction = {
                androidx.compose.material3.IconButton(onClick = { importError = null }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "关闭"
                    )
                }
            }
        ) {
            Text(error)
        }
        
        // 自动清除错误提示
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(5000)
            importError = null
        }
    }
    
    // 继续显示网址导入对话框
    if (showWebImportDialog) {
        WebImportDialog(
            onDismiss = { showWebImportDialog = false },
            onConfirm = { url ->
                try {
                    // 处理导入网页的逻辑
                    bookshelfViewModel.importBookFromUrl(url)
                    showWebImportDialog = false
                } catch (e: Exception) {
                    importError = "导入失败: ${e.message}"
                }
            }
        )
    }
    
    // 监听导入状态变化
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            importError = error
        }
    }
    
    // 用于处理底部导航栏的显示/隐藏动画
    var bottomBarVisible by rememberSaveable { mutableStateOf(showBottomBar) }
    
    // 根据导航目标更新底部栏可见性
    LaunchedEffect(currentDestination) {
        val shouldShowBottomBar = bottomNavItems.any { screen ->
            currentDestination?.hierarchy?.any { it.route == screen.route } == true
        }
        bottomBarVisible = shouldShowBottomBar
    }
    
    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentDestination = currentDestination,
                visible = bottomBarVisible
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Bookshelf.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 书架屏幕
            composable(Screen.Bookshelf.route) {
                val bookshelfViewModel = viewModel<BookshelfViewModel>(
                    factory = BookshelfViewModelFactory(
                        application = context.applicationContext as Application,
                        bookRepository = bookRepository
                    )
                )
                
                BookshelfScreen(
                    viewModel = bookshelfViewModel,
                    onBookClick = { book ->
                        when(book.type) {
                            BookType.EPUB -> navController.navigate(Screen.EpubReader.createRoute(book.id))
                            else -> navController.navigate(Screen.UnifiedReader.createRoute(book.id))
                        }
                    },
                    onImportClick = {
                        navController.navigate(Screen.ImportBook.route)
                    },
                    onWebImportClick = {
                        // 显示网址导入对话框
                        showWebImportDialog = true
                    },
                    onOpenWebUrl = { url ->
                        // 这一部分代码会保留但不再被调用
                        navController.navigate(Screen.WebView.createRoute(url))
                    }
                )
            }
            
            // 书库屏幕
            composable(Screen.Library.route) {
                LibraryScreen()
            }
            
            // 历史屏幕
            composable(Screen.History.route) {
                val application = context.applicationContext as Application
                val historyViewModelFactory = remember { 
                    HistoryViewModelFactory(
                        application = application,
                        bookRepository = bookRepository
                    )
                }
                
                val historyViewModel = viewModel<HistoryViewModel>(
                    factory = historyViewModelFactory
                )
                
                HistoryScreen(
                    viewModel = historyViewModel,
                    onBookClick = { book ->
                        when(book.type) {
                            BookType.EPUB -> navController.navigate(Screen.EpubReader.createRoute(book.id))
                            else -> navController.navigate(Screen.UnifiedReader.createRoute(book.id))
                        }
                    }
                )
            }
            
            // 设置屏幕
            composable(Screen.Settings.route) {
                val context = LocalContext.current
                val viewModelFactory = remember { SettingsViewModelFactory(context) }
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>(
                    factory = viewModelFactory
                )
                SettingsScreen()
            }
            
            // TTS设置屏幕
            composable(Screen.TtsSettings.route) {
                TtsSettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // 标准阅读器屏幕 (用于TXT和其他格式)
            composable(
                route = Screen.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                val context = LocalContext.current
                val readerViewModelFactory = ReaderViewModelFactory(
                    application = LocalContext.current.applicationContext as Application,
                    bookRepository = bookRepository,
                    bookId = bookId
                )
                
                val readerViewModel = viewModel<ReaderViewModel>(
                    factory = readerViewModelFactory
                )
                
                ReaderScreen(
                    viewModel = readerViewModel,
                    bookId = bookId,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onOpenSettings = {
                        // 打开设置，后续实现
                    }
                )
            }
            
            // EPUB专用阅读器屏幕
            composable(
                route = Screen.EpubReader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                val application = context.applicationContext as android.app.Application
                val ebookApplication = context.applicationContext as com.wanderreads.ebook.EbookApplication
                val dependencies = ebookApplication.provideDependencies()
                val bookRepository = dependencies.bookRepository
                val viewModelFactory = remember { 
                    EpubReaderViewModelFactory(
                        application = application,
                        bookRepository = bookRepository,
                        bookId = bookId
                    )
                }
                
                val epubReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel<EpubReaderViewModel>(
                    factory = viewModelFactory
                )
                
                EpubReaderScreen(
                    viewModel = epubReaderViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // 导入书籍屏幕
            composable(Screen.ImportBook.route) {
                ImportBookScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                        // 刷新书架
                        bookshelfViewModel.refreshBooks()
                    },
                    onBookImported = {
                        // 刷新书架
                        bookshelfViewModel.refreshBooks()
                    }
                )
            }
            
            // TTS屏幕
            composable(
                route = Screen.TTS.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                // TODO: 实现TTS屏幕
                // TTSScreen(bookId = bookId)
                // Temporary placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TTS朗读界面 - 图书ID: $bookId")
                }
            }
            
            // 统一阅读器屏幕
            composable(
                route = Screen.UnifiedReader.route,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("pageIndex") { 
                        type = NavType.IntType 
                        defaultValue = 0
                    }
                )
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                val pageIndex = backStackEntry.arguments?.getInt("pageIndex") ?: 0
                val application = context.applicationContext as android.app.Application
                val ebookApplication = context.applicationContext as com.wanderreads.ebook.EbookApplication
                val dependencies = ebookApplication.provideDependencies()
                val bookRepository = dependencies.bookRepository
                val recordRepository = dependencies.recordRepository
                val viewModelFactory = remember { 
                    UnifiedReaderViewModelFactory(
                        application = application,
                        bookRepository = bookRepository,
                        recordRepository = recordRepository,
                        bookId = bookId,
                        initialPage = pageIndex
                    )
                }
                
                val unifiedReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel<UnifiedReaderViewModel>(
                    factory = viewModelFactory
                )
                
                UnifiedReaderScreen(
                    viewModel = unifiedReaderViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // WebView 页面
            composable(
                route = Screen.WebView.route,
                arguments = listOf(
                    navArgument("url") {
                        type = NavType.StringType
                        nullable = false
                    }
                )
            ) { backStackEntry ->
                val url = backStackEntry.arguments?.getString("url") ?: ""
                WebViewScreen(
                    url = url,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        BottomAppBar(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            containerColor = Color(0xFF1565C0), // 使用深蓝色作为底部导航栏背景，与顶部导航栏保持一致
            contentColor = Color.White // 白色图标和文字以增强可见性
        ) {
            bottomNavItems.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                BottomNavigationItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(screen.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                            contentDescription = screen.title
                        )
                    },
                    label = { Text(text = screen.title) }
                )
            }
        }
    }
}

@Composable
fun RowScope.BottomNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp)
            ) {
                icon()
            }
        },
        label = label,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White, // 选中图标使用白色
            unselectedIconColor = Color.White.copy(alpha = 0.7f), // 未选中图标使用半透明白色
            selectedTextColor = Color.White, // 选中文本使用白色
            unselectedTextColor = Color.White.copy(alpha = 0.7f), // 未选中文本使用半透明白色
            indicatorColor = Color(0xFF64B5F6) // 指示器使用浅蓝色
        )
    )
} 