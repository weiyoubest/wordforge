package com.wordforge.app.data.repository

import com.wordforge.app.data.db.dao.LearningRecordDao
import com.wordforge.app.data.db.entity.LearningRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 学习记录仓库
 * 封装 LearningRecordDao 的所有操作
 */
class LearningRecordRepository(private val learningRecordDao: LearningRecordDao) {

    suspend fun insert(record: LearningRecord): Long = withContext(Dispatchers.IO) {
        learningRecordDao.insert(record)
    }

    suspend fun insertAll(records: List<LearningRecord>): List<Long> = withContext(Dispatchers.IO) {
        learningRecordDao.insertAll(records)
    }

    suspend fun updateReviewSchedule(
        id: Long,
        nextReviewAt: Long,
        stage: Int,
        cognitionLevel: String
    ) = withContext(Dispatchers.IO) {
        learningRecordDao.updateReviewSchedule(id, nextReviewAt, stage, cognitionLevel)
    }

    suspend fun getByDate(date: String): List<LearningRecord> = withContext(Dispatchers.IO) {
        learningRecordDao.getByDate(date)
    }

    fun getByDateFlow(date: String): Flow<List<LearningRecord>> {
        return learningRecordDao.getByDateFlow(date)
    }

    suspend fun getByWordId(wordId: Long): List<LearningRecord> = withContext(Dispatchers.IO) {
        learningRecordDao.getByWordId(wordId)
    }

    suspend fun getLatestByWordId(wordId: Long): LearningRecord? = withContext(Dispatchers.IO) {
        learningRecordDao.getLatestByWordId(wordId)
    }

    suspend fun getDueReviews(currentTime: Long, wordbookId: Long): List<LearningRecord> =
        withContext(Dispatchers.IO) {
            learningRecordDao.getDueReviews(currentTime, wordbookId)
        }

    suspend fun getAllDueReviews(currentTime: Long): List<LearningRecord> =
        withContext(Dispatchers.IO) {
            learningRecordDao.getAllDueReviews(currentTime)
        }

    fun getDueReviewsFlow(currentTime: Long): Flow<List<LearningRecord>> {
        return learningRecordDao.getDueReviewsFlow(currentTime)
    }

    suspend fun countNewWordsOnDate(date: String): Int = withContext(Dispatchers.IO) {
        learningRecordDao.countNewWordsOnDate(date)
    }

    suspend fun countReviewsOnDate(date: String): Int = withContext(Dispatchers.IO) {
        learningRecordDao.countReviewsOnDate(date)
    }

    suspend fun countCorrectOnDate(date: String): Int = withContext(Dispatchers.IO) {
        learningRecordDao.countCorrectOnDate(date)
    }

    suspend fun countTotalOnDate(date: String): Int = withContext(Dispatchers.IO) {
        learningRecordDao.countTotalOnDate(date)
    }

    suspend fun getAllStudyDates(): List<String> = withContext(Dispatchers.IO) {
        learningRecordDao.getAllStudyDates()
    }

    suspend fun deleteByWordbook(wordbookId: Long) = withContext(Dispatchers.IO) {
        learningRecordDao.deleteByWordbook(wordbookId)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        learningRecordDao.deleteAll()
    }
}
