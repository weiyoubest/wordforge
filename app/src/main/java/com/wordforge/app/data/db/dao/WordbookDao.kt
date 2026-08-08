package com.wordforge.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wordforge.app.data.db.entity.Wordbook
import kotlinx.coroutines.flow.Flow

/**
 * 词库数据访问对象
 * 管理词库的增删改查
 */
@Dao
interface WordbookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wordbook: Wordbook): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wordbooks: List<Wordbook>): List<Long>

    @Update
    suspend fun update(wordbook: Wordbook)

    @Delete
    suspend fun delete(wordbook: Wordbook)

    @Query("DELETE FROM wordbook WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM wordbook ORDER BY id ASC")
    fun getAll(): Flow<List<Wordbook>>

    @Query("SELECT * FROM wordbook ORDER BY id ASC")
    suspend fun getAllOnce(): List<Wordbook>

    @Query("SELECT * FROM wordbook WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Wordbook?

    @Query("SELECT * FROM wordbook WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveWordbook(): Wordbook?

    @Query("SELECT * FROM wordbook WHERE isActive = 1 LIMIT 1")
    fun getActiveWordbookFlow(): Flow<Wordbook?>

    @Query("UPDATE wordbook SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()

    @Query("UPDATE wordbook SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("SELECT * FROM wordbook WHERE type = :type ORDER BY id ASC")
    fun getByType(type: String): Flow<List<Wordbook>>

    @Query("DELETE FROM wordbook WHERE type = 'custom' AND id = :id")
    suspend fun deleteCustomWordbook(id: Long)

    @Query("SELECT COUNT(*) FROM wordbook")
    suspend fun count(): Int

    @Query("SELECT * FROM wordbook WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Wordbook?
}
