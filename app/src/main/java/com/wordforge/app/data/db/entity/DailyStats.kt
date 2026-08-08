package com.wordforge.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日统计表
 * 聚合每日学习数据，用于统计页展示
 */
@Entity(
    tableName = "daily_stats",
    indices = [
        Index(value = ["date"], unique = true)
    ]
)
data class DailyStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // "yyyy-MM-dd"
    val newWordsLearned: Int = 0,
    val wordsReviewed: Int = 0,
    val correctCount: Int = 0,
    val totalAttempts: Int = 0,
    val accuracyRate: Float = 0f,
    val streakDays: Int = 0,
    val studyDurationSec: Int = 0
)
