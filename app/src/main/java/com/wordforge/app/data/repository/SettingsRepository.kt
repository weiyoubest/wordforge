package com.wordforge.app.data.repository

import com.wordforge.app.data.db.dao.AppSettingsDao
import com.wordforge.app.data.db.entity.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 设置仓库
 * 封装 AppSettingsDao 的所有操作
 */
class SettingsRepository(private val appSettingsDao: AppSettingsDao) {

    suspend fun insert(setting: AppSettings) = withContext(Dispatchers.IO) {
        appSettingsDao.insert(setting)
    }

    suspend fun insertAll(settings: List<AppSettings>) = withContext(Dispatchers.IO) {
        appSettingsDao.insertAll(settings)
    }

    suspend fun getByKey(key: String): AppSettings? = withContext(Dispatchers.IO) {
        appSettingsDao.getByKey(key)
    }

    fun getByKeyFlow(key: String): Flow<AppSettings?> {
        return appSettingsDao.getByKeyFlow(key)
    }

    suspend fun getAll(): List<AppSettings> = withContext(Dispatchers.IO) {
        appSettingsDao.getAll()
    }

    fun getAllFlow(): Flow<List<AppSettings>> {
        return appSettingsDao.getAllFlow()
    }

    suspend fun deleteByKey(key: String) = withContext(Dispatchers.IO) {
        appSettingsDao.deleteByKey(key)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        appSettingsDao.deleteAll()
    }

    /**
     * 获取设置值，支持默认值
     */
    suspend fun getString(key: String, defaultValue: String = ""): String =
        withContext(Dispatchers.IO) {
            appSettingsDao.getByKey(key)?.value ?: defaultValue
        }

    /**
     * 获取设置值（Int类型），支持默认值
     */
    suspend fun getInt(key: String, defaultValue: Int = 0): Int =
        withContext(Dispatchers.IO) {
            appSettingsDao.getByKey(key)?.value?.toIntOrNull() ?: defaultValue
        }

    /**
     * 获取设置值（Boolean类型），支持默认值
     */
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            appSettingsDao.getByKey(key)?.value?.toBooleanStrictOrNull() ?: defaultValue
        }

    /**
     * 保存字符串设置值
     */
    suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        appSettingsDao.insert(AppSettings(key = key, value = value))
    }

    /**
     * 保存Int设置值
     */
    suspend fun putInt(key: String, value: Int) = withContext(Dispatchers.IO) {
        appSettingsDao.insert(AppSettings(key = key, value = value.toString()))
    }

    /**
     * 保存Boolean设置值
     */
    suspend fun putBoolean(key: String, value: Boolean) = withContext(Dispatchers.IO) {
        appSettingsDao.insert(AppSettings(key = key, value = value.toString()))
    }
}
