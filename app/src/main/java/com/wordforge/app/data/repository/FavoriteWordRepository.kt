package com.wordforge.app.data.repository

import com.wordforge.app.data.db.dao.FavoriteWordDao
import com.wordforge.app.data.db.entity.FavoriteWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 收藏/生词本仓库
 * 封装 FavoriteWordDao 的所有操作
 */
class FavoriteWordRepository(private val favoriteWordDao: FavoriteWordDao) {

    suspend fun insert(favoriteWord: FavoriteWord): Long = withContext(Dispatchers.IO) {
        favoriteWordDao.insert(favoriteWord)
    }

    suspend fun insertAll(favoriteWords: List<FavoriteWord>): List<Long> = withContext(Dispatchers.IO) {
        favoriteWordDao.insertAll(favoriteWords)
    }

    suspend fun deleteByWordId(wordId: Long) = withContext(Dispatchers.IO) {
        favoriteWordDao.deleteByWordId(wordId)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        favoriteWordDao.deleteById(id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        favoriteWordDao.deleteAll()
    }

    fun getAll(): Flow<List<FavoriteWord>> {
        return favoriteWordDao.getAll()
    }

    suspend fun getAllOnce(): List<FavoriteWord> = withContext(Dispatchers.IO) {
        favoriteWordDao.getAllOnce()
    }

    suspend fun getByWordId(wordId: Long): FavoriteWord? = withContext(Dispatchers.IO) {
        favoriteWordDao.getByWordId(wordId)
    }

    fun isFavorited(wordId: Long): Flow<Boolean> {
        return favoriteWordDao.isFavorited(wordId)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        favoriteWordDao.count()
    }

    suspend fun getByWordIds(wordIds: List<Long>): List<FavoriteWord> = withContext(Dispatchers.IO) {
        favoriteWordDao.getByWordIds(wordIds)
    }
}
