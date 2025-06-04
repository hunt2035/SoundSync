package com.wanderreads.ebook.ui.bookshelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FormatTextdirectionLToR
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wanderreads.ebook.domain.model.BookType
import com.wanderreads.ebook.ui.components.NewTextDialog
import com.wanderreads.ebook.ui.theme.AppAnimationSpec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.WindowInsets

// 使用类型别名解决命名冲突
typealias EbookModel = com.wanderreads.ebook.domain.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onBookClick: (EbookModel) -> Unit,
    onImportClick: () -> Unit,
    onWebImportClick: () -> Unit = {},
    onOpenWebUrl: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showNewTextDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // 显示新建文本对话框
    if (showNewTextDialog) {
        NewTextDialog(
            onDismiss = { showNewTextDialog = false },
            onSaveComplete = { book -> 
                // 处理保存完成的回调，可以根据需要跳转到书籍详情页
                onBookClick(book)
            },
            bookRepository = viewModel.bookRepository,
            coroutineScope = coroutineScope
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "我的书架",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // 搜索图标按钮
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                // 导航到搜索页面的逻辑
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    // 三点菜单按钮
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多选项",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    // 下拉菜单
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("新建文本") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FormatTextdirectionLToR,
                                    contentDescription = null
                                )
                            },
                            onClick = { 
                                showNewTextDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("网址导入") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null
                                )
                            },
                            onClick = { 
                                onWebImportClick()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("本地导入") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Storage,
                                    contentDescription = null
                                )
                            },
                            onClick = { 
                                onImportClick()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("书架管理") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LibraryBooks,
                                    contentDescription = null
                                )
                            },
                            onClick = { 
                                // 书架管理逻辑
                                showMenu = false
                            }
                        )
                        
                        // 替换原来的排序菜单项
                        var showSortMenu by remember { mutableStateOf(false) }
                        DropdownMenuItem(
                            text = { Text("书籍排序") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Sort,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowRight,
                                    contentDescription = null
                                )
                            },
                            onClick = { 
                                showSortMenu = true
                            }
                        )
                        
                        // 子菜单：排序选项
                        if (showSortMenu) {
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.width(200.dp)
                            ) {
                                val sortOptions = listOf(
                                    BookSort.ADDED_DATE to "添加时间",
                                    BookSort.LAST_OPENED to "最近阅读",
                                    BookSort.TITLE to "书名",
                                    BookSort.AUTHOR to "作者"
                                )
                                
                                sortOptions.forEach { (sort, label) ->
                                    DropdownMenuItem(
                                        text = { 
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(label)
                                                if (uiState.currentSort == sort) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        },
                                        onClick = { 
                                            viewModel.sortBooks(sort)
                                            showSortMenu = false
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1565C0),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = Modifier
                    .height(64.dp)
                    .shadow(elevation = 8.dp),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(color = MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            } else if (uiState.books.isEmpty()) {
                EmptyBookshelf(onImportClick)
            } else {
                BookList(
                    books = uiState.books,
                    onBookClick = onBookClick
                )
            }
        }
    }
}

@Composable
fun EmptyBookshelf(onImportClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "书架上还没有电子书",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "点击右上角菜单导入电子书",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BookList(
    books: List<EbookModel>,
    onBookClick: (EbookModel) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(books) { book ->
            BookListItem(book = book, onClick = { onBookClick(book) })
        }
    }
}

@Composable
fun BookListItem(
    book: EbookModel,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // 格式化日期显示
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val lastOpenedDate = remember(book.lastOpenedDate) { 
        dateFormat.format(Date(book.lastOpenedDate))
    }
    
    // 格式化文件大小
    val fileSize = remember(book.filePath) {
        val file = File(book.filePath)
        when {
            file.length() < 1024 -> "${file.length()} B"
            file.length() < 1024 * 1024 -> "${file.length() / 1024} KB"
            else -> "%.1f MB".format(file.length() / (1024.0 * 1024.0))
        }
    }
    
    // Animate elevation on hover
    val elevation by animateFloatAsState(
        targetValue = if (isHovered) 4f else 1f,
        animationSpec = AppAnimationSpec.defaultHoverEffect,
        label = "Card Elevation Animation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 书籍封面 - 调整为长方形
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(102.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                if (book.coverPath != null && File(book.coverPath).exists()) {
                    AsyncImage(
                        model = book.coverPath,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 格式特定的占位符背景颜色
                    val backgroundColor = when (book.type) {
                        BookType.EPUB -> MaterialTheme.colorScheme.primaryContainer
                        BookType.PDF -> MaterialTheme.colorScheme.secondaryContainer
                        BookType.TXT -> MaterialTheme.colorScheme.surfaceVariant
                        BookType.MD -> MaterialTheme.colorScheme.surfaceVariant
                        BookType.UNKNOWN -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = book.type.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 书籍信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 作者
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = book.author.ifBlank { "未知作者" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 上次阅读时间和进度
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$lastOpenedDate · ${(book.readingProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 文件大小
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${book.type.name} · $fileSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BookCover(
    book: EbookModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
    ) {
        // If there's a cover image, display it; otherwise show a format-specific placeholder
        if (book.coverPath != null && File(book.coverPath).exists()) {
            AsyncImage(
                model = book.coverPath,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Format-specific placeholder background colors
            val backgroundColor = when (book.type) {
                BookType.EPUB -> MaterialTheme.colorScheme.primaryContainer
                BookType.PDF -> MaterialTheme.colorScheme.secondaryContainer
                BookType.TXT -> MaterialTheme.colorScheme.surfaceVariant
                BookType.MD -> MaterialTheme.colorScheme.surfaceVariant
                BookType.UNKNOWN -> MaterialTheme.colorScheme.tertiaryContainer
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = book.type.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
} 