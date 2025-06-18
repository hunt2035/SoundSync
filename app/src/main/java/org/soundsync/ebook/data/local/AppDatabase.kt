package org.soundsync.ebook.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.soundsync.ebook.data.local.dao.BookDao
import org.soundsync.ebook.data.local.dao.RecordDao
import org.soundsync.ebook.data.local.entity.BookEntity
import org.soundsync.ebook.data.local.entity.RecordEntity

/**
 * 应用数据库
 */
@Database(
    entities = [BookEntity::class, RecordEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun bookDao(): BookDao
    abstract fun recordDao(): RecordDao
    
    companion object {
        private const val DATABASE_NAME = "ebook_database"
        private const val TAG = "AppDatabase"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    )
                        .fallbackToDestructiveMigration()
                        // 使用允许主线程查询来避免在主线程上的一些异步操作问题
                        // 注意：仅在开发阶段使用，生产环境应移除此选项并使用协程
                        .allowMainThreadQueries()
                        .build()
                    
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    // 记录错误，并创建一个内存数据库作为备用
                    Log.e(TAG, "数据库初始化失败，使用内存数据库: ${e.message}")
                    
                    Room.inMemoryDatabaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java
                    )
                        .fallbackToDestructiveMigration()
                        .allowMainThreadQueries()
                        .build()
                }
            }
        }
    }
} 