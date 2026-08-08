package com.wordforge.app.data.repository

import com.wordforge.app.data.db.dao.WordDao
import com.wordforge.app.data.db.entity.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 单词仓库
 * 封装 WordDao 的所有操作，统一在 IO 线程执行
 */
class WordRepository(private val wordDao: WordDao) {

    suspend fun insert(word: Word): Long = withContext(Dispatchers.IO) {
        wordDao.insert(word)
    }

    suspend fun insertAll(words: List<Word>): List<Long> = withContext(Dispatchers.IO) {
        wordDao.insertAll(words)
    }

    suspend fun update(word: Word) = withContext(Dispatchers.IO) {
        wordDao.update(word)
    }

    suspend fun delete(word: Word) = withContext(Dispatchers.IO) {
        wordDao.delete(word)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        wordDao.deleteAll()
    }

    suspend fun deleteAllByWordbook(wordbookId: Long) = withContext(Dispatchers.IO) {
        wordDao.deleteAllByWordbook(wordbookId)
    }

    suspend fun getById(id: Long): Word? = withContext(Dispatchers.IO) {
        wordDao.getById(id)
    }

    suspend fun getBySpelling(spelling: String): Word? = withContext(Dispatchers.IO) {
        wordDao.getBySpelling(spelling)
    }

    fun getByWordbook(wordbookId: Long): Flow<List<Word>> {
        return wordDao.getByWordbook(wordbookId)
    }

    suspend fun getByWordbookOnce(wordbookId: Long): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getByWordbookOnce(wordbookId)
    }

    suspend fun countByWordbook(wordbookId: Long): Int = withContext(Dispatchers.IO) {
        wordDao.countByWordbook(wordbookId)
    }

    suspend fun search(query: String, limit: Int = 50): List<Word> = withContext(Dispatchers.IO) {
        wordDao.search(query, limit)
    }

    fun searchFlow(query: String, limit: Int = 50): Flow<List<Word>> {
        return wordDao.searchFlow(query, limit)
    }

    suspend fun getRandomUnlearned(wordbookId: Long, count: Int): List<Word> =
        withContext(Dispatchers.IO) {
            wordDao.getRandomUnlearned(wordbookId, count)
        }

    suspend fun getByIds(ids: List<Long>): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getByIds(ids)
    }
}
