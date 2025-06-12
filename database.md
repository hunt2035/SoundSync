# 电子书应用数据库设计文档

## 数据库概述

本应用使用 Room 数据库作为本地数据存储方案，数据库名称为 `ebook_database`。数据库采用单例模式设计，确保整个应用中只有一个数据库实例。

## 表结构设计

### 1. 电子书表 (books)

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | String | 电子书唯一标识符 | PRIMARY KEY |
| title | String | 电子书标题 | NOT NULL |
| author | String | 作者 | NOT NULL |
| filePath | String | 文件路径 | NOT NULL |
| coverPath | String | 封面图片路径 | NULL |
| fileHash | String | 文件哈希值 | NOT NULL |
| type | String | 电子书类型 | NOT NULL |
| lastReadPage | Integer | 最后阅读页码 | NOT NULL |
| lastReadPosition | Float | 最后阅读位置 | NOT NULL |
| totalPages | Integer | 总页数 | NOT NULL |
| addedDate | Long | 添加时间 | NOT NULL |
| lastOpenedDate | Long | 最后打开时间 | NOT NULL |
| urlPath | String | 网页导入的URL | NULL |
| originalFilePath | String | 原始文件路径，例如PDF转TXT时保存原PDF路径 | NULL |

### 2. 录音表 (records)

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| rec_id | String | 录音唯一标识符 | PRIMARY KEY |
| book_id | String | 关联电子书ID | FOREIGN KEY (books.id), NOT NULL |
| title | String | 录音标题 | NOT NULL |
| voiceFilePath | String | 录音文件路径 | NOT NULL |
| addedDate | Long | 录音添加时间 | NOT NULL |
| voiceLength | Integer | 录音时长（秒） | NOT NULL |
| chapterIndex | Long | 章节索引 | NULL |
| pageIndex | Long | 页码索引 | NULL |

- book_id 字段为外键，关联 books 表的 id 字段，且设置级联删除（CASCADE）。
- 对 book_id 字段建立索引以提升查询性能。

## 数据访问对象 (DAO)

### BookDao 接口

提供以下主要操作：

1. 基础CRUD操作
   - `insertBook(book: BookEntity)`: 插入新电子书
   - `updateBook(book: BookEntity)`: 更新电子书信息
   - `deleteBook(book: BookEntity)`: 删除电子书
   - `getBookById(bookId: String)`: 根据ID获取电子书

2. 查询操作
   - `getAllBooks()`: 获取所有电子书
   - `getRecentBooks(limit: Int)`: 获取最近阅读的电子书
   - `searchBooks(query: String)`: 搜索电子书
   - `getBookByHash(fileHash: String)`: 根据文件哈希获取电子书

3. 更新操作
   - `updateReadingProgress(bookId: String, lastReadPage: Int, lastReadPosition: Float, timestamp: Long)`: 更新阅读进度
   - `updateLastOpenedDate(bookId: String, lastOpenedDate: Long)`: 更新最后打开时间

### RecordDao 接口

提供以下主要操作：

1. 基础CRUD操作
   - `insertRecord(record: RecordEntity)`: 插入新录音
   - `updateRecord(record: RecordEntity)`: 更新录音信息
   - `deleteRecord(record: RecordEntity)`: 删除录音
   - `getRecordById(recordId: String)`: 根据ID获取录音

2. 查询操作
   - `getRecordsByBookId(bookId: String)`: 获取指定书籍的所有录音，按添加时间倒序排列
   - `getAllRecords()`: 获取所有录音，按添加时间倒序排列

3. 批量删除
   - `deleteAllRecordsForBook(bookId: String)`: 删除指定书籍的所有录音

## 数据模型

### Book 领域模型

```kotlin
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String?,
    val fileHash: String,
    val type: BookType,
    val lastReadPage: Int,
    val lastReadPosition: Float,
    val totalPages: Int,
    val addedDate: Long,
    val lastOpenedDate: Long,
    val urlPath: String?,
    val originalFilePath: String?
)
```

### Record 领域模型

```kotlin
data class Record(
    val id: String,
    val bookId: String,
    val title: String,
    val voiceFilePath: String,
    val addedDate: Long,
    val voiceLength: Int,
    val chapterIndex: Long?,
    val pageIndex: Long?
)
```

### BookType 枚举

```kotlin
enum class BookType {
    PDF, EPUB, TXT, MOBI, UNKNOWN
}
```

## 数据库版本

- 当前版本：6
- 导出Schema：false

## 注意事项

1. 数据库采用 Room 持久化库实现，确保数据的一致性和可靠性
2. 所有数据库操作都在协程中异步执行，避免阻塞主线程
3. 使用 Flow 实现响应式数据流，支持实时数据更新
4. 文件哈希值用于防止重复导入相同的电子书
5. 阅读进度和位置信息用于实现断点续读功能
6. 录音表与电子书表通过外键关联，删除电子书时自动删除其所有录音 