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
    val urlPath: String?
)
```

### BookType 枚举

```kotlin
enum class BookType {
    PDF, EPUB, TXT, MOBI, UNKNOWN
}
```

## 数据库版本

- 当前版本：1
- 导出Schema：false

## 注意事项

1. 数据库采用 Room 持久化库实现，确保数据的一致性和可靠性
2. 所有数据库操作都在协程中异步执行，避免阻塞主线程
3. 使用 Flow 实现响应式数据流，支持实时数据更新
4. 文件哈希值用于防止重复导入相同的电子书
5. 阅读进度和位置信息用于实现断点续读功能 