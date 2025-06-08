# 样式信息

## 文字

### 通用字体
| UI中的位置 | 字体 | 大小 | 所在文件名 | 第xx行 | 其它备注 |
|------------|------|------|------------|--------|----------|
| 全局字体 | Inter (Google Font) | 不同组件大小不同 | Type.kt | 17-25 | 使用GoogleFont.Provider加载，如果不可用则回退到系统默认字体 |

### 标题样式
| UI中的位置 | 字体 | 大小 | 所在文件名 | 第xx行 | 其它备注 |
|------------|------|------|------------|--------|----------|
| 大号标题 (displayLarge) | Inter | 36sp | Type.kt | 36-42 | 行高44sp，字间距-0.25sp，粗体 |
| 中号标题 (displayMedium) | Inter | 32sp | Type.kt | 43-49 | 行高40sp，字间距-0.25sp，粗体 |
| 小号标题 (displaySmall) | Inter | 28sp | Type.kt | 50-56 | 行高36sp，字间距-0.25sp，粗体 |
| 大号标题 (headlineLarge) | Inter | 26sp | Type.kt | 59-65 | 行高32sp，字间距0sp，半粗体 |
| 中号标题 (headlineMedium) | Inter | 22sp | Type.kt | 66-72 | 行高28sp，字间距0sp，半粗体 |
| 小号标题 (headlineSmall) | Inter | 18sp | Type.kt | 73-79 | 行高24sp，字间距0sp，半粗体 |
| 大号标题 (titleLarge) | Inter | 20sp | Type.kt | 82-88 | 行高24sp，字间距0sp，半粗体 |
| 中号标题 (titleMedium) | Inter | 16sp | Type.kt | 89-95 | 行高20sp，字间距0.15sp，中等粗体 |
| 小号标题 (titleSmall) | Inter | 14sp | Type.kt | 96-102 | 行高18sp，字间距0.1sp，中等粗体 |
| 书架标题 | Inter | headlineMedium | BookshelfScreen.kt | 167-173 | 白色文字，粗体 |
| 章节标题 | Inter | 默认 | UnifiedReaderScreen.kt | 324 | 白色文字 |

### 正文样式
| UI中的位置 | 字体 | 大小 | 所在文件名 | 第xx行 | 其它备注 |
|------------|------|------|------------|--------|----------|
| 大号正文 (bodyLarge) | Inter | 16sp | Type.kt | 105-111 | 行高24sp (1.5x)，字间距0.15sp，常规字重 |
| 中号正文 (bodyMedium) | Inter | 14sp | Type.kt | 112-118 | 行高21sp (1.5x)，字间距0.25sp，常规字重 |
| 小号正文 (bodySmall) | Inter | 12sp | Type.kt | 119-125 | 行高18sp (1.5x)，字间距0.4sp，常规字重 |
| 阅读界面状态栏 | Inter | bodySmall | README.md | 158 | 半透明白色(alpha = 0.7f) |

### 标签样式
| UI中的位置 | 字体 | 大小 | 所在文件名 | 第xx行 | 其它备注 |
|------------|------|------|------------|--------|----------|
| 大号标签 (labelLarge) | Inter | 14sp | Type.kt | 128-134 | 行高18sp，字间距0.1sp，中等字重 |
| 中号标签 (labelMedium) | Inter | 12sp | Type.kt | 135-141 | 行高16sp，字间距0.5sp，中等字重 |
| 小号标签 (labelSmall) | Inter | 10sp | Type.kt | 142-148 | 行高14sp，字间距0.5sp，中等字重 |
| 书籍标签 | Inter | labelSmall | AppStyles.kt | 237-246 | 用于显示书籍相关标签 |

## 颜色

### 主题颜色
| UI中的位置 | 前景色/背景色 | 颜色值 | 所在文件名 | 第xx行 | 其它备注 |
|------------|--------------|--------|------------|--------|----------|
| 主色调 (Primary) | 背景色 | #1E3A5F | Color.kt | 6 | 深蓝色主题 |
| 主色调亮色 (PrimaryLight) | 背景色 | #3A5A88 | Color.kt | 7 | 深蓝色主题的亮色版本 |
| 主色调暗色 (PrimaryDark) | 背景色 | #0A233A | Color.kt | 8 | 深蓝色主题的暗色版本 |
| 次级色调 (Secondary) | 背景色 | #64A5AD | Color.kt | 11 | 轻微的强调色 |
| 次级色调亮色 (SecondaryLight) | 背景色 | #8DD5DE | Color.kt | 12 | 次级色调的亮色版本 |
| 次级色调暗色 (SecondaryDark) | 背景色 | #3F7880 | Color.kt | 13 | 次级色调的暗色版本 |

### 中性颜色
| UI中的位置 | 前景色/背景色 | 颜色值 | 所在文件名 | 第xx行 | 其它备注 |
|------------|--------------|--------|------------|--------|----------|
| 中性色10 (Neutral10) | 背景色 | #FAFBFC | Color.kt | 16 | 最亮的中性色，近白色 |
| 中性色20 (Neutral20) | 背景色 | #F0F4F8 | Color.kt | 17 | 亮中性色 |
| 中性色90 (Neutral90) | 背景色 | #1A2637 | Color.kt | 18 | 深中性色 |
| 中性色95 (Neutral95) | 背景色 | #0F1824 | Color.kt | 19 | 更深的中性色 |
| 中性色99 (Neutral99) | 背景色 | #050A12 | Color.kt | 20 | 最深的中性色，近黑色 |

### 阅读背景颜色
| UI中的位置 | 前景色/背景色 | 颜色值 | 所在文件名 | 第xx行 | 其它备注 |
|------------|--------------|--------|------------|--------|----------|
| 暖白色 (WarmWhite) | 背景色 | #F9F6F0 | Color.kt | 23 | 阅读模式的暖白色背景 |
| 夜间模式 (NightMode) | 背景色 | #0F1824 | Color.kt | 24 | 阅读的深蓝色夜间模式 |
| 深蓝夜间模式 (DeepBlueNight) | 背景色 | #091018 | Color.kt | 25 | 更深的蓝色夜间模式 |
| 褐色调 (SepiaTone) | 背景色 | #F2E8D9 | Color.kt | 26 | 阅读的褐色调背景 |
| 阅读界面背景色 | 背景色 | #0A1929 | UnifiedReaderScreen.kt | 135 | 墨蓝色背景 |
| 工具栏颜色 | 背景色 | #1976D2 | UnifiedReaderScreen.kt | 136 | 主题蓝色（工具栏） |
| 阅读界面文本颜色 | 前景色 | #FFFFFF | UnifiedReaderScreen.kt | 137 | 白色文字 |
| 状态栏背景色 | 背景色 | #FFFFFF (alpha = 0.7f) | UnifiedReaderScreen.kt | 138 | 半透明白色背景 |

### 错误和成功颜色
| UI中的位置 | 前景色/背景色 | 颜色值 | 所在文件名 | 第xx行 | 其它备注 |
|------------|--------------|--------|------------|--------|----------|
| 错误颜色 (ErrorColor) | 前景色 | #B3261E | Color.kt | 29 | 错误提示颜色 |
| 暗模式错误颜色 (ErrorColorDark) | 前景色 | #F2B8B5 | Color.kt | 30 | 暗模式下的错误提示颜色 |
| 成功颜色 (SuccessColor) | 前景色 | #386A20 | Color.kt | 33 | 成功提示颜色 |
| 暗模式成功颜色 (SuccessColorDark) | 前景色 | #ADDC8D | Color.kt | 34 | 暗模式下的成功提示颜色 |

### 主题配色方案
| UI中的位置 | 前景色/背景色 | 颜色值 | 所在文件名 | 第xx行 | 其它备注 |
|------------|--------------|--------|------------|--------|----------|
| 状态栏颜色 | 背景色 | #1565C0 | Theme.kt | 95 | 固定的深蓝色状态栏颜色 |
| 暗色主题配色方案 | 多种用途 | 多种颜色 | Theme.kt | 39-56 | 暗色主题的完整配色方案 |
| 亮色主题配色方案 | 多种用途 | 多种颜色 | Theme.kt | 58-75 | 亮色主题的完整配色方案 |

### XML资源颜色
| UI中的位置 | 前景色/背景色 | 颜色值 | 所在文件名 | 第xx行 | 其它备注 |
|------------|--------------|--------|------------|--------|----------|
| purple_200 | 背景色 | #FFBB86FC | colors.xml | 3 | XML资源中定义的紫色 |
| purple_500 | 背景色 | #FF6200EE | colors.xml | 4 | XML资源中定义的紫色 |
| purple_700 | 背景色 | #FF3700B3 | colors.xml | 5 | XML资源中定义的深紫色 |
| teal_200 | 背景色 | #FF03DAC5 | colors.xml | 6 | XML资源中定义的蓝绿色 |
| teal_700 | 背景色 | #FF018786 | colors.xml | 7 | XML资源中定义的深蓝绿色 |
| black | 前景色/背景色 | #FF000000 | colors.xml | 8 | 黑色 |
| white | 前景色/背景色 | #FFFFFFFF | colors.xml | 9 | 白色 | 