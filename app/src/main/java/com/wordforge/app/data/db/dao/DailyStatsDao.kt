package com.wordforge.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordforge.app.data.db.entity.DailyStats
import kotlinx.coroutines.flow.Flow

/**
 * 每日统计数据访问对象
 * 管理每日学习统计的读写
 */
@Dao
interface DailyStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: DailyStats): Long

    @Query("""
        UPDATE daily_stats 
        SET newWordsLearned = newWordsLearned + :newWords,
            wordsReviewed = wordsReviewed + :reviewed,
            correctCount = correctCount + :correct,
            totalAttempts = totalAttempts + :total,
            accuracyRate = :accuracyRate,
            studyDurationSec = studyDurationSec + :duration
        WHERE date = :date
    """)
    suspend fun incrementStats(
        date: String,
        newWords: Int = 0,
        reviewed: Int = 0,
        correct: Int = 0,
        total: Int = 0,
        accuracyRate: Float = 0f,
        duration: Int = 0
    )

    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyStats?

    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    fun getByDateFlow(date: String): Flow<DailyStats?>

    @Query("""
        SELECT * FROM daily_stats 
        WHERE date >= :startDate AND date <= :endDate 
        ORDER BY date ASC
    """)
    suspend fun getByDateRange(startDate: String, endDate: String): List<DailyStats>

    @Query("""
        SELECT * FROM daily_stats 
        WHERE date >= :startDate AND date <= :endDate 
        ORDER BY date ASC
    """)
    fun getByDateRangeFlow(startDate: String, endDate: String): Flow<List<DailyStats>>

    @Query("SELECT * FROM daily_stats ORDER BY date DESC")
    fun getAllFlow(): Flow<List<DailyStats>>

    @Query("SELECT * FROM daily_stats ORDER BY date DESC")
    suspend fun getAllOnce(): List<DailyStats>

    @Query("SELECT COUNT(*) FROM daily_stats")
    suspend fun countStudyDays(): Int

    @Query("""
        SELECT streakDays FROM daily_stats 
        ORDER BY date DESC LIMIT 1
    """)
    suspend fun getCurrentStreak(): Int

    @Query("DELETE FROM daily_stats")
    suspend fun deleteAll()
}
