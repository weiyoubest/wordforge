package com.wordforge.app.data.repository

import com.wordforge.app.data.db.dao.WordbookDao
import com.wordforge.app.data.db.entity.Wordbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 词库仓库
 * 封装 WordbookDao 的所有操作
 */
class WordbookRepository(private val wordbookDao: WordbookDao) {

    suspend fun insert(wordbook: Wordbook): Long = withContext(Dispatchers.IO) {
        wordbookDao.insert(wordbook)
    }

    suspend fun insertAll(wordbooks: List<Wordbook>): List<Long> = withContext(Dispatchers.IO) {
        wordbookDao.insertAll(wordbooks)
    }

    suspend fun update(wordbook: Wordbook) = withContext(Dispatchers.IO) {
        wordbookDao.update(wordbook)
    }

    suspend fun delete(wordbook: Wordbook) = withContext(Dispatchers.IO) {
        wordbookDao.delete(wordbook)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        wordbookDao.deleteById(id)
    }

    fun getAll(): Flow<List<Wordbook>> {
        return wordbookDao.getAll()
    }

    suspend fun getAllOnce(): List<Wordbook> = withContext(Dispatchers.IO) {
        wordbookDao.getAllOnce()
    }

    suspend fun getById(id: Long): Wordbook? = withContext(Dispatchers.IO) {
        wordbookDao.getById(id)
    }

    suspend fun getActiveWordbook(): Wordbook? = withContext(Dispatchers.IO) {
        wordbookDao.getActiveWordbook()
    }

    fun getActiveWordbookFlow(): Flow<Wordbook?> {
        return wordbookDao.getActiveWordbookFlow()
    }

    suspend fun setActive(id: Long) = withContext(Dispatchers.IO) {
        wordbookDao.deactivateAll()
        wordbookDao.setActive(id)
    }

    fun getByType(type: String): Flow<List<Wordbook>> {
        return wordbookDao.getByType(type)
    }

    suspend fun deleteCustomWordbook(id: Long) = withContext(Dispatchers.IO) {
        wordbookDao.deleteCustomWordbook(id)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        wordbookDao.count()
    }

    suspend fun getByName(name: String): Wordbook? = withContext(Dispatchers.IO) {
        wordbookDao.getByName(name)
    }
}
