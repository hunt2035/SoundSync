package org.soundsync.ebook.data.repository

import org.soundsync.ebook.domain.model.Record
import kotlinx.coroutines.flow.Flow

/**
 * 录音仓库接口
 */
interface RecordRepository {
    suspend fun addRecord(record: Record): Long
    suspend fun saveRecord(record: Record): Long
    suspend fun updateRecord(record: Record)
    suspend fun deleteRecord(record: Record)
    suspend fun getRecordById(recordId: String): Record?
    fun getRecordsByBookId(bookId: String): Flow<List<Record>>
    fun getAllRecords(): Flow<List<Record>>
    suspend fun deleteAllRecordsForBook(bookId: String)
} 