package org.soundsync.ebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import org.soundsync.ebook.domain.model.Record
import org.soundsync.ebook.domain.model.SynthesisParams

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
    val pageIndex: Long?,
    val isSynthesized: Boolean = false, // 是否为合成语音
    val synthParamsJson: String? = null // 语音合成参数JSON字符串
) {
    fun toRecord(): Record {
        val synthParams = if (!synthParamsJson.isNullOrEmpty()) {
            try {
                Gson().fromJson(synthParamsJson, SynthesisParams::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
        
        return Record(
            id = rec_id,
            bookId = book_id,
            title = title,
            voiceFilePath = voiceFilePath,
            addedDate = addedDate,
            voiceLength = voiceLength,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            isSynthesized = isSynthesized,
            synthParams = synthParams
        )
    }
    
    companion object {
        fun fromRecord(record: Record): RecordEntity {
            val synthParamsJson = record.synthParams?.let {
                try {
                    Gson().toJson(it)
                } catch (e: Exception) {
                    null
                }
            }
            
            return RecordEntity(
                rec_id = record.id,
                book_id = record.bookId,
                title = record.title,
                voiceFilePath = record.voiceFilePath,
                addedDate = record.addedDate,
                voiceLength = record.voiceLength,
                chapterIndex = record.chapterIndex,
                pageIndex = record.pageIndex,
                isSynthesized = record.isSynthesized,
                synthParamsJson = synthParamsJson
            )
        }
    }
} 