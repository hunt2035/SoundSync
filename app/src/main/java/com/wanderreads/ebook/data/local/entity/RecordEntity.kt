package com.wanderreads.ebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wanderreads.ebook.domain.model.Record

/**
 * 录音数据库实体
 */
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("book_id")
    ]
)
data class RecordEntity(
    @PrimaryKey
    val rec_id: String,
    val book_id: String,
    val title: String,
    val voiceFilePath: String,
    val addedDate: Long,
    val voiceLength: Int,
    val chapterIndex: Long?,
    val pageIndex: Long?
) {
    fun toRecord(): Record {
        return Record(
            id = rec_id,
            bookId = book_id,
            title = title,
            voiceFilePath = voiceFilePath,
            addedDate = addedDate,
            voiceLength = voiceLength,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex
        )
    }
    
    companion object {
        fun fromRecord(record: Record): RecordEntity {
            return RecordEntity(
                rec_id = record.id,
                book_id = record.bookId,
                title = record.title,
                voiceFilePath = record.voiceFilePath,
                addedDate = record.addedDate,
                voiceLength = record.voiceLength,
                chapterIndex = record.chapterIndex,
                pageIndex = record.pageIndex
            )
        }
    }
} 