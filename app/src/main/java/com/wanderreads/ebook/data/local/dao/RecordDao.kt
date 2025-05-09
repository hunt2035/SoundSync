package com.wanderreads.ebook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wanderreads.ebook.data.local.entity.RecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 录音数据访问对象
 */
@Dao
interface RecordDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordEntity): Long
    
    @Update
    suspend fun updateRecord(record: RecordEntity)
    
    @Delete
    suspend fun deleteRecord(record: RecordEntity)
    
    @Query("SELECT * FROM records WHERE rec_id = :recordId")
    suspend fun getRecordById(recordId: String): RecordEntity?
    
    @Query("SELECT * FROM records WHERE book_id = :bookId ORDER BY createdAt DESC")
    fun getRecordsByBookId(bookId: String): Flow<List<RecordEntity>>
    
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<RecordEntity>>
    
    @Query("DELETE FROM records WHERE book_id = :bookId")
    suspend fun deleteAllRecordsForBook(bookId: String)
} 