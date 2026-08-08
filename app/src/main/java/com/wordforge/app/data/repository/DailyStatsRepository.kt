package com.wordforge.app.data.repository

import com.wordforge.app.data.db.dao.DailyStatsDao
import com.wordforge.app.data.db.entity.DailyStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 每日统计仓库
 * 封装 DailyStatsDao 的所有操作
 */
class DailyStatsRepository(private val dailyStatsDao: DailyStatsDao) {

    suspend fun insert(stats: DailyStats): Long = withContext(Dispatchers.IO) {
        dailyStatsDao.insert(stats)
    }

    suspend fun incrementStats(
        date: String,
        newWords: Int = 0,
        reviewed: Int = 0,
        correct: Int = 0,
        total: Int = 0,
        accuracyRate: Float = 0f,
        duration: Int = 0
    ) = withContext(Dispatchers.IO) {
        dailyStatsDao.incrementStats(date, newWords, reviewed, correct, total, accuracyRate, duration)
    }

    suspend fun getByDate(date: String): DailyStats? = withContext(Dispatchers.IO) {
        dailyStatsDao.getByDate(date)
    }

    fun getByDateFlow(date: String): Flow<DailyStats?> {
        return dailyStatsDao.getByDateFlow(date)
    }

    suspend fun getByDateRange(startDate: String, endDate: String): List<DailyStats> =
        withContext(Dispatchers.IO) {
            dailyStatsDao.getByDateRange(startDate, endDate)
        }

    fun getByDateRangeFlow(startDate: String, endDate: String): Flow<List<DailyStats>> {
        return dailyStatsDao.getByDateRangeFlow(startDate, endDate)
    }

    fun getAllFlow(): Flow<List<DailyStats>> {
        return dailyStatsDao.getAllFlow()
    }

    suspend fun getAllOnce(): List<DailyStats> = withContext(Dispatchers.IO) {
        dailyStatsDao.getAllOnce()
    }

    suspend fun countStudyDays(): Int = withContext(Dispatchers.IO) {
        dailyStatsDao.countStudyDays()
    }

    suspend fun getCurrentStreak(): Int = withContext(Dispatchers.IO) {
        dailyStatsDao.getCurrentStreak()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dailyStatsDao.deleteAll()
    }
}
