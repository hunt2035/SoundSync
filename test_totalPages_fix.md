# totalPages字段修复验证测试

## 问题描述
导入文本文件后，数据库表books的totalPages字段为0，导致书架界面显示阅读进度为0%。

## 修复内容

### 1. 网页导入功能修复
**文件**: `app/src/main/java/org/soundsync/ebook/ui/bookshelf/BookshelfViewModel.kt`
**修复**: 在创建Book对象时添加totalPages字段计算
```kotlin
// 计算总页数 (每页约2000字符)
val totalPages = (file.length() / 2000).toInt().coerceAtLeast(1)

val book = Book(
    title = firstLine,
    filePath = file.absolutePath,
    type = BookType.TXT,
    urlPath = urlPath,
    totalPages = totalPages,  // 设置总页数
    addedDate = System.currentTimeMillis(),
    fileHash = generateFileHash(file)
)
```

### 2. OCR导入功能修复
**文件**: `app/src/main/java/org/soundsync/ebook/ui/ocrimport/OcrImportViewModel.kt`
**修复**: 在保存OCR识别文本时添加totalPages字段计算
```kotlin
// 计算总页数 (每页约2000字符)
val totalPages = (file.length() / 2000).toInt().coerceAtLeast(1)

val book = Book(
    title = title,
    filePath = file.absolutePath,
    type = BookType.TXT,
    totalPages = totalPages,  // 设置总页数
    addedDate = System.currentTimeMillis(),
    fileHash = generateFileHash(file)
)
```

### 3. 新建文本功能修复
**文件**: `app/src/main/java/org/soundsync/ebook/ui/components/NewTextDialog.kt`
**修复**: 在新建文本时添加totalPages字段计算
```kotlin
// 计算总页数 (每页约2000字符)
val totalPages = (file.length() / 2000).toInt().coerceAtLeast(1)

val book = Book(
    title = title,
    filePath = file.absolutePath,
    type = BookType.TXT,
    totalPages = totalPages,  // 设置总页数
    addedDate = System.currentTimeMillis()
)
```

### 4. BookshelfViewModel.addBook方法修复
**文件**: `app/src/main/java/org/soundsync/ebook/ui/bookshelf/BookshelfViewModel.kt`
**修复**: 添加estimateBookPages方法并在addBook中使用
```kotlin
// 估算页数
val totalPages = estimateBookPages(file, bookType)

val book = Book(
    title = title,
    author = "",
    filePath = filePath,
    coverPath = coverPath,
    fileHash = fileHash,
    type = bookType,
    totalPages = totalPages,  // 设置总页数
    addedDate = System.currentTimeMillis(),
    lastOpenedDate = System.currentTimeMillis()
)
```

### 5. 添加页数估算方法
**文件**: `app/src/main/java/org/soundsync/ebook/ui/bookshelf/BookshelfViewModel.kt`
**新增**: estimateBookPages方法
```kotlin
private fun estimateBookPages(file: File, bookType: BookType): Int {
    if (!file.exists()) return 1

    return when (bookType) {
        BookType.TXT -> {
            // TXT文件：每2000字符算一页
            (file.length() / 2000).toInt().coerceAtLeast(1)
        }
        BookType.PDF -> {
            // PDF文件：按文件大小估算，每50KB一页
            (file.length() / (50 * 1024)).toInt().coerceAtLeast(1)
        }
        BookType.EPUB -> {
            // EPUB文件：按文件大小估算，每100KB一章
            (file.length() / (100 * 1024)).toInt().coerceAtLeast(1)
        }
        BookType.MD -> {
            // Markdown文件：每3000字符算一页
            (file.length() / 3000).toInt().coerceAtLeast(1)
        }
        else -> {
            // 其他格式：按文件大小估算
            (file.length() / 10000).toInt().coerceAtLeast(1)
        }
    }
}
```

## 测试验证

### 测试步骤
1. **网页导入测试**
   - 在书架界面点击"+"按钮
   - 选择"网页导入"
   - 输入一个网页URL并导入
   - 检查导入后的书籍在书架界面是否显示正确的阅读进度百分比

2. **OCR导入测试**
   - 在书架界面点击"+"按钮
   - 选择"OCR导入"
   - 拍照或选择图片进行OCR识别
   - 保存识别的文本为书籍
   - 检查导入后的书籍在书架界面是否显示正确的阅读进度百分比

3. **新建文本测试**
   - 在书架界面点击"+"按钮
   - 选择"新建文本"
   - 输入一些文本内容并保存
   - 检查创建后的书籍在书架界面是否显示正确的阅读进度百分比

4. **数据库验证**
   - 使用数据库查看工具检查books表
   - 确认新导入的书籍totalPages字段不为0
   - 确认readingProgress计算正确：lastReadPage / totalPages

### 预期结果
- 所有新导入的文本文件totalPages字段都不为0
- 书架界面正确显示阅读进度百分比（不再显示0%）
- 阅读进度计算公式正确：progress = (lastReadPage / totalPages) * 100%

## 相关文件
- `app/src/main/java/org/soundsync/ebook/ui/bookshelf/BookshelfViewModel.kt`
- `app/src/main/java/org/soundsync/ebook/ui/ocrimport/OcrImportViewModel.kt`
- `app/src/main/java/org/soundsync/ebook/ui/components/NewTextDialog.kt`
- `app/src/main/java/org/soundsync/ebook/domain/model/Book.kt`
- `app/src/main/java/org/soundsync/ebook/util/MetadataExtractor.kt`
- `app/src/main/java/org/soundsync/ebook/worker/BookImportWorker.kt`
