package com.wordforge.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wordforge.app.data.db.entity.Word
import kotlinx.coroutines.flow.Flow

/**
 * 单词数据访问对象
 * 提供单词的查询、搜索、随机获取等操作
 */
@Dao
interface WordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: Word): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<Word>): List<Long>

    @Update
    suspend fun update(word: Word)

    @Delete
    suspend fun delete(word: Word)

    @Query("DELETE FROM word")
    suspend fun deleteAll()

    @Query("DELETE FROM word WHERE wordbookId = :wordbookId")
    suspend fun deleteAllByWordbook(wordbookId: Long)

    @Query("SELECT * FROM word WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Word?

    @Query("SELECT * FROM word WHERE spelling = :spelling LIMIT 1")
    suspend fun getBySpelling(spelling: String): Word?

    @Query("SELECT * FROM word WHERE wordbookId = :wordbookId ORDER BY id ASC")
    fun getByWordbook(wordbookId: Long): Flow<List<Word>>

    @Query("SELECT * FROM word WHERE wordbookId = :wordbookId ORDER BY id ASC")
    suspend fun getByWordbookOnce(wordbookId: Long): List<Word>

    @Query("SELECT COUNT(*) FROM word WHERE wordbookId = :wordbookId")
    suspend fun countByWordbook(wordbookId: Long): Int

    @Query("SELECT COUNT(*) FROM word")
    suspend fun countAll(): Int

    @Query("""
        SELECT * FROM word 
        WHERE spelling LIKE '%' || :query || '%' 
           OR meaning LIKE '%' || :query || '%'
        ORDER BY spelling ASC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 50): List<Word>

    @Query("""
        SELECT * FROM word 
        WHERE wordbookId = :wordbookId
          AND id NOT IN (
              SELECT DISTINCT wordId FROM learning_record WHERE wordbookId = :wordbookId
          )
        ORDER BY RANDOM()
        LIMIT :count
    """)
    suspend fun getRandomUnlearned(wordbookId: Long, count: Int): List<Word>

    @Query("""
        SELECT * FROM word 
        WHERE spelling LIKE '%' || :query || '%' 
           OR meaning LIKE '%' || :query || '%'
        ORDER BY spelling ASC
        LIMIT :limit
    """)
    fun searchFlow(query: String, limit: Int = 50): Flow<List<Word>>

    @Query("SELECT * FROM word WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Word>
}
