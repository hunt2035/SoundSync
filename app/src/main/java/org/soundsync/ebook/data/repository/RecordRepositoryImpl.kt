package org.soundsync.ebook.data.repository

import android.content.Context
import org.soundsync.ebook.data.local.dao.RecordDao
import org.soundsync.ebook.data.local.entity.RecordEntity
import org.soundsync.ebook.domain.model.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 录音仓库实现
 */
class RecordRepositoryImpl(
    private val context: Context,
    private val recordDao: RecordDao
) : RecordRepository {
    
    /**
     * 获取Context对象
     */
    fun getContext(): Context {
        return context
    }
    
    override suspend fun addRecord(record: Record): Long {
        return recordDao.insertRecord(RecordEntity.fromRecord(record))
    }
    
    override suspend fun saveRecord(record: Record): Long {
        // 确保录音文件存在
        val file = File(record.voiceFilePath)
        if (!file.exists()) {
            // 如果文件不存在，尝试创建
            try {
                file.parentFile?.mkdirs()
                file.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return addRecord(record)
    }
    
    override suspend fun updateRecord(record: Record) {
        recordDao.updateRecord(RecordEntity.fromRecord(record))
    }
    
    override suspend fun deleteRecord(record: Record) {
        recordDao.deleteRecord(RecordEntity.fromRecord(record))
    }
    
    override suspend fun getRecordById(recordId: String): Record? {
        return recordDao.getRecordById(recordId)?.toRecord()
    }
    
    override fun getRecordsByBookId(bookId: String): Flow<List<Record>> {
        return recordDao.getRecordsByBookId(bookId).map { entities ->
            entities.map { it.toRecord() }
        }
    }
    
    override fun getAllRecords(): Flow<List<Record>> {
        return recordDao.getAllRecords().map { entities ->
            entities.map { it.toRecord() }
        }
    }
    
    override suspend fun deleteAllRecordsForBook(bookId: String) {
        return recordDao.deleteAllRecordsForBook(bookId)
    }
    
    /**
     * 创建录音目录
     */
    suspend fun createRecordDirectory(): File = withContext(Dispatchers.IO) {
        val recordDir = File(context.filesDir, "book_records")
        if (!recordDir.exists()) {
            recordDir.mkdirs()
        }
        return@withContext recordDir
    }
    
    /**
     * 获取录音目录
     */
    fun getRecordDirectory(): File {
        val recordDir = File(context.filesDir, "book_records")
        if (!recordDir.exists()) {
            recordDir.mkdirs()
        }
        return recordDir
    }
} 