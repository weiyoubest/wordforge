package com.wordforge.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wordforge.app.data.db.entity.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * 应用设置数据访问对象
 * key-value 形式的设置存储
 */
@Dao
interface AppSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: AppSettings)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<AppSettings>)

    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): AppSettings?

    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    fun getByKeyFlow(key: String): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSettings>

    @Query("SELECT * FROM app_settings")
    fun getAllFlow(): Flow<List<AppSettings>>

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM app_settings")
    suspend fun deleteAll()
}
