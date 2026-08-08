package com.wordforge.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordforge.app.data.db.entity.FavoriteWord
import kotlinx.coroutines.flow.Flow

/**
 * 收藏/生词本数据访问对象
 */
@Dao
interface FavoriteWordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favoriteWord: FavoriteWord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favoriteWords: List<FavoriteWord>): List<Long>

    @Query("DELETE FROM favorite_word WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: Long)

    @Query("DELETE FROM favorite_word WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM favorite_word")
    suspend fun deleteAll()

    @Query("SELECT * FROM favorite_word ORDER BY favoritedAt DESC")
    fun getAll(): Flow<List<FavoriteWord>>

    @Query("SELECT * FROM favorite_word ORDER BY favoritedAt DESC")
    suspend fun getAllOnce(): List<FavoriteWord>

    @Query("SELECT * FROM favorite_word WHERE wordId = :wordId LIMIT 1")
    suspend fun getByWordId(wordId: Long): FavoriteWord?

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_word WHERE wordId = :wordId LIMIT 1)")
    fun isFavorited(wordId: Long): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM favorite_word")
    suspend fun count(): Int

    @Query("SELECT * FROM favorite_word WHERE wordId IN (:wordIds)")
    suspend fun getByWordIds(wordIds: List<Long>): List<FavoriteWord>

    @Delete
    suspend fun delete(favoriteWord: FavoriteWord)
}
