package com.wordforge.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 学习记录表
 * 记录每次学习/复习行为，用于追踪学习进度和复习调度
 */
@Entity(
    tableName = "learning_record",
    indices = [
        Index(value = ["wordId"]),
        Index(value = ["nextReviewAt"]),
        Index(value = ["sessionDate"])
    ]
)
data class LearningRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wordId: Long,
    val recordType: String, // "new", "review", "mistake_review"
    val cognitionLevel: String, // "familiar", "vague", "unknown"
    val reviewedAt: Long,
    val nextReviewAt: Long? = null,
    val reviewStage: Int = 0,
    val wordbookId: Long,
    val sessionDate: String // "yyyy-MM-dd"
)
