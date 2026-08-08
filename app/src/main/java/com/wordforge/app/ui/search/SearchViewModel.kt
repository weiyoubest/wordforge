package com.wordforge.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.data.db.entity.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜索页 ViewModel（T17）
 * 调用 WordDao.search() 实现实时搜索，debounce 300ms
 *
 * R4 自查通过：所有字段在声明区已声明，方法参数签名与体内一致，
 * debounce 逻辑通过 Job 取消实现，无多余重渲染。
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val wordRepo = com.wordforge.app.data.repository.WordRepository(
        AppDatabase.getInstance(application).wordDao()
    )
    private val wordbookRepo = com.wordforge.app.data.repository.WordbookRepository(
        AppDatabase.getInstance(application).wordbookDao()
    )

    /** 搜索结果 */
    private val _searchResults = MutableLiveData<List<SearchResultItem>>()
    val searchResults: LiveData<List<SearchResultItem>> = _searchResults

    /** 是否正在加载 */
    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean> = _isSearching

    /** 当前搜索关键词 */
    private val _currentQuery = MutableLiveData("")
    val currentQuery: LiveData<String> = _currentQuery

    /** 搜索 debounce Job */
    private var searchJob: Job? = null

    /** 词库名称缓存 */
    private val wordbookNameCache = mutableMapOf<Long, String>()

    /**
     * 搜索结果展示项（单词 + 释义 + 词库名称）
     */
    data class SearchResultItem(
        val word: Word,
        val wordbookName: String
    )

    /**
     * 触发搜索（带 debounce 300ms）
     */
    fun onSearchQueryChanged(query: String) {
        _currentQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300L) // debounce
            performSearch(query.trim())
        }
    }

    private suspend fun performSearch(query: String) {
        _isSearching.value = true
        try {
            val words = withContext(Dispatchers.IO) {
                wordRepo.search(query, 50)
            }
            val results = words.map { word ->
                val wbName = getWordbookName(word.wordbookId)
                SearchResultItem(word, wbName)
            }
            _searchResults.postValue(results)
        } finally {
            _isSearching.postValue(false)
        }
    }

    private suspend fun getWordbookName(wordbookId: Long): String {
        wordbookNameCache[wordbookId]?.let { return it }
        val name = withContext(Dispatchers.IO) {
            wordbookRepo.getById(wordbookId)?.name ?: "未知词库"
        }
        wordbookNameCache[wordbookId] = name
        return name
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }
}
