package com.example.ebook.ui.navigation

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.example.ebook.data.local.AppDatabase
import com.example.ebook.data.repository.BookRepositoryImpl
import com.example.ebook.ui.bookshelf.BookshelfScreen
import com.example.ebook.ui.bookshelf.BookshelfViewModel
import com.example.ebook.ui.components.WebImportDialog
import com.example.ebook.ui.history.HistoryScreen
import com.example.ebook.ui.library.LibraryScreen
import com.example.ebook.ui.settings.SettingsScreen
import com.example.ebook.ui.importbook.ImportBookScreen
import com.example.ebook.ui.reader.ReaderScreen
import com.example.ebook.ui.reader.ReaderViewModel
import com.example.ebook.ui.reader.ReaderViewModelFactory
import com.example.ebook.ui.reader.EpubReaderScreen
import com.example.ebook.ui.reader.EpubReaderViewModel
import com.example.ebook.ui.reader.EpubReaderViewModelFactory
import com.example.ebook.ui.settings.SettingsViewModelFactory
import com.example.ebook.ui.settings.SettingsViewModel
import com.example.ebook.ui.reader.UnifiedReaderScreen
import com.example.ebook.ui.reader.UnifiedReaderViewModel
import com.example.ebook.ui.reader.UnifiedReaderViewModelFactory
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
        route = "unified_reader/{bookId}",
        title = "阅读器",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List,
        hasBottomBar = false
    ) {
        fun createRoute(bookId: String) = "unified_reader/$bookId"
    }
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
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
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
    
    // 网址导入对话框状态
    var showWebImportDialog by remember { mutableStateOf(false) }
    
    // 显示网址导入对话框
    if (showWebImportDialog) {
        WebImportDialog(
            onDismiss = { showWebImportDialog = false },
            onConfirm = { url ->
                bookshelfViewModel.importBookFromUrl(url)
                showWebImportDialog = false
            }
        )
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
                BookshelfScreen(
                    viewModel = bookshelfViewModel,
                    onBookClick = { book ->
                        // 使用统一阅读器打开所有格式的电子书
                        navController.navigate(Screen.UnifiedReader.createRoute(book.id))
                    },
                    onImportClick = {
                        navController.navigate(Screen.ImportBook.route)
                    },
                    onWebImportClick = {
                        showWebImportDialog = true
                    }
                )
            }
            
            // 书库屏幕
            composable(Screen.Library.route) {
                LibraryScreen()
            }
            
            // 历史屏幕
            composable(Screen.History.route) {
                HistoryScreen()
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
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                val application = context.applicationContext as android.app.Application
                val viewModelFactory = remember { 
                    UnifiedReaderViewModelFactory(
                        application = application,
                        bookRepository = bookRepository,
                        bookId = bookId
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
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
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
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
} 