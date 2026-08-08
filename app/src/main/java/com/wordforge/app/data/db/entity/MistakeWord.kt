package com.wordforge.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 错词表
 * 记录用户标记为"不认识"的单词，用于错词本功能
 */
@Entity(
    tableName = "mistake_word",
    indices = [
        Index(value = ["wordId"], unique = true)
    ]
)
data class MistakeWord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wordId: Long,
    val errorCount: Int = 1,
    val firstErrorAt: Long = System.currentTimeMillis(),
    val lastErrorAt: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val wordbookId: Long
)
