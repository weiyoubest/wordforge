package com.wordforge.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordforge.app.data.db.entity.LearningRecord
import kotlinx.coroutines.flow.Flow

/**
 * 学习记录数据访问对象
 * 记录学习/复习行为，查询待复习单词和学习进度
 */
@Dao
interface LearningRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: LearningRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<LearningRecord>): List<Long>

    @Query("""
        UPDATE learning_record 
        SET nextReviewAt = :nextReviewAt, reviewStage = :stage, cognitionLevel = :cognitionLevel
        WHERE id = :id
    """)
    suspend fun updateReviewSchedule(
        id: Long,
        nextReviewAt: Long,
        stage: Int,
        cognitionLevel: String
    )

    @Query("SELECT * FROM learning_record WHERE sessionDate = :date ORDER BY reviewedAt DESC")
    suspend fun getByDate(date: String): List<LearningRecord>

    @Query("SELECT * FROM learning_record WHERE sessionDate = :date ORDER BY reviewedAt DESC")
    fun getByDateFlow(date: String): Flow<List<LearningRecord>>

    @Query("SELECT * FROM learning_record WHERE wordId = :wordId ORDER BY reviewedAt DESC")
    suspend fun getByWordId(wordId: Long): List<LearningRecord>

    @Query("SELECT * FROM learning_record WHERE wordId = :wordId ORDER BY reviewedAt DESC LIMIT 1")
    suspend fun getLatestByWordId(wordId: Long): LearningRecord?

    @Query("""
        SELECT * FROM learning_record 
        WHERE nextReviewAt IS NOT NULL 
          AND nextReviewAt <= :currentTime
          AND wordbookId = :wordbookId
        ORDER BY nextReviewAt ASC
    """)
    suspend fun getDueReviews(currentTime: Long, wordbookId: Long): List<LearningRecord>

    @Query("""
        SELECT * FROM learning_record 
        WHERE nextReviewAt IS NOT NULL 
          AND nextReviewAt <= :currentTime
        ORDER BY nextReviewAt ASC
    """)
    suspend fun getAllDueReviews(currentTime: Long): List<LearningRecord>

    @Query("""
        SELECT * FROM learning_record 
        WHERE nextReviewAt IS NOT NULL 
          AND nextReviewAt <= :currentTime
        ORDER BY nextReviewAt ASC
    """)
    fun getDueReviewsFlow(currentTime: Long): Flow<List<LearningRecord>>

    @Query("""
        SELECT COUNT(*) FROM learning_record 
        WHERE sessionDate = :date AND recordType = 'new'
    """)
    suspend fun countNewWordsOnDate(date: String): Int

    @Query("""
        SELECT COUNT(*) FROM learning_record 
        WHERE sessionDate = :date AND recordType IN ('review', 'mistake_review')
    """)
    suspend fun countReviewsOnDate(date: String): Int

    @Query("""
        SELECT COUNT(*) FROM learning_record 
        WHERE sessionDate = :date AND cognitionLevel = 'familiar'
    """)
    suspend fun countCorrectOnDate(date: String): Int

    @Query("""
        SELECT COUNT(*) FROM learning_record 
        WHERE sessionDate = :date
    """)
    suspend fun countTotalOnDate(date: String): Int

    @Query("SELECT DISTINCT sessionDate FROM learning_record ORDER BY sessionDate DESC")
    suspend fun getAllStudyDates(): List<String>

    @Query("DELETE FROM learning_record WHERE wordbookId = :wordbookId")
    suspend fun deleteByWordbook(wordbookId: Long)

    @Query("DELETE FROM learning_record")
    suspend fun deleteAll()
}
