package com.wordforge.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordforge.app.data.db.entity.MistakeWord
import kotlinx.coroutines.flow.Flow

/**
 * 错词数据访问对象
 * 管理错词的增删改查和统计
 */
@Dao
interface MistakeWordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mistakeWord: MistakeWord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mistakeWords: List<MistakeWord>): List<Long>

    @Query("""
        UPDATE mistake_word 
        SET errorCount = errorCount + 1, lastErrorAt = :timestamp, isResolved = 0 
        WHERE wordId = :wordId
    """)
    suspend fun incrementErrorCount(wordId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE mistake_word 
        SET isResolved = 1 
        WHERE wordId = :wordId
    """)
    suspend fun markResolved(wordId: Long)

    @Query("DELETE FROM mistake_word WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: Long)

    @Query("DELETE FROM mistake_word WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mistake_word")
    suspend fun deleteAll()

    @Query("SELECT * FROM mistake_word WHERE isResolved = 0 ORDER BY lastErrorAt DESC")
    fun getAllActive(): Flow<List<MistakeWord>>

    @Query("SELECT * FROM mistake_word WHERE isResolved = 0 ORDER BY lastErrorAt DESC")
    suspend fun getAllActiveOnce(): List<MistakeWord>

    @Query("SELECT * FROM mistake_word ORDER BY lastErrorAt DESC")
    fun getAll(): Flow<List<MistakeWord>>

    @Query("SELECT * FROM mistake_word WHERE wordId = :wordId LIMIT 1")
    suspend fun getByWordId(wordId: Long): MistakeWord?

    @Query("SELECT COUNT(*) FROM mistake_word WHERE isResolved = 0")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM mistake_word WHERE isResolved = 0")
    fun countActiveFlow(): Flow<Int>

    @Query("""
        SELECT * FROM mistake_word 
        WHERE isResolved = 0 AND wordbookId = :wordbookId 
        ORDER BY lastErrorAt DESC
    """)
    fun getByWordbook(wordbookId: Long): Flow<List<MistakeWord>>

    @Query("""
        SELECT * FROM mistake_word 
        WHERE isResolved = 0 AND errorCount >= :minCount 
        ORDER BY errorCount DESC, lastErrorAt DESC
    """)
    fun getByMinErrorCount(minCount: Int): Flow<List<MistakeWord>>

    @Query("""
        SELECT * FROM mistake_word 
        WHERE isResolved = 0 
        ORDER BY firstErrorAt DESC
    """)
    fun getByDateDesc(): Flow<List<MistakeWord>>

    @Query("SELECT * FROM mistake_word WHERE wordId IN (:wordIds)")
    suspend fun getByWordIds(wordIds: List<Long>): List<MistakeWord>

    @Delete
    suspend fun delete(mistakeWord: MistakeWord)
}
