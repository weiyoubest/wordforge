package com.wordforge.app.data.repository

import com.wordforge.app.data.db.dao.MistakeWordDao
import com.wordforge.app.data.db.entity.MistakeWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 错词仓库
 * 封装 MistakeWordDao 的所有操作
 */
class MistakeWordRepository(private val mistakeWordDao: MistakeWordDao) {

    suspend fun insert(mistakeWord: MistakeWord): Long = withContext(Dispatchers.IO) {
        mistakeWordDao.insert(mistakeWord)
    }

    suspend fun insertAll(mistakeWords: List<MistakeWord>): List<Long> = withContext(Dispatchers.IO) {
        mistakeWordDao.insertAll(mistakeWords)
    }

    suspend fun incrementErrorCount(
        wordId: Long,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        mistakeWordDao.incrementErrorCount(wordId, timestamp)
    }

    suspend fun markResolved(wordId: Long) = withContext(Dispatchers.IO) {
        mistakeWordDao.markResolved(wordId)
    }

    suspend fun deleteByWordId(wordId: Long) = withContext(Dispatchers.IO) {
        mistakeWordDao.deleteByWordId(wordId)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        mistakeWordDao.deleteById(id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        mistakeWordDao.deleteAll()
    }

    fun getAllActive(): Flow<List<MistakeWord>> {
        return mistakeWordDao.getAllActive()
    }

    suspend fun getAllActiveOnce(): List<MistakeWord> = withContext(Dispatchers.IO) {
        mistakeWordDao.getAllActiveOnce()
    }

    fun getAll(): Flow<List<MistakeWord>> {
        return mistakeWordDao.getAll()
    }

    suspend fun getByWordId(wordId: Long): MistakeWord? = withContext(Dispatchers.IO) {
        mistakeWordDao.getByWordId(wordId)
    }

    suspend fun countActive(): Int = withContext(Dispatchers.IO) {
        mistakeWordDao.countActive()
    }

    fun countActiveFlow(): Flow<Int> {
        return mistakeWordDao.countActiveFlow()
    }

    fun getByWordbook(wordbookId: Long): Flow<List<MistakeWord>> {
        return mistakeWordDao.getByWordbook(wordbookId)
    }

    fun getByMinErrorCount(minCount: Int): Flow<List<MistakeWord>> {
        return mistakeWordDao.getByMinErrorCount(minCount)
    }

    fun getByDateDesc(): Flow<List<MistakeWord>> {
        return mistakeWordDao.getByDateDesc()
    }

    suspend fun getByWordIds(wordIds: List<Long>): List<MistakeWord> = withContext(Dispatchers.IO) {
        mistakeWordDao.getByWordIds(wordIds)
    }
}
