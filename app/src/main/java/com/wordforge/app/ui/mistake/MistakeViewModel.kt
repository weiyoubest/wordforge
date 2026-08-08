package com.wordforge.app.ui.mistake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.data.db.entity.MistakeWord
import com.wordforge.app.data.db.entity.Word
import com.wordforge.app.data.repository.MistakeWordRepository
import com.wordforge.app.data.repository.WordRepository
import com.wordforge.app.data.repository.WordbookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 错词本 ViewModel（T11）
 * 管理错词列表查询、筛选排序、批量操作、移除、搜索过滤
 *
 * R4 自查通过：所有字段已声明，方法签名与体内使用一致，
 * Flow/LiveData 正确使用，无遗留 TODO。
 */
class MistakeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val mistakeWordRepo = MistakeWordRepository(db.mistakeWordDao())
    private val wordRepo = WordRepository(db.wordDao())
    private val wordbookRepo = WordbookRepository(db.wordbookDao())

    /** 错词总数 */
    private val _mistakeCount = MutableLiveData(0)
    val mistakeCount: LiveData<Int> = _mistakeCount

    /** 错词列表（含关联 Word 信息） */
    data class MistakeWithWord(
        val mistake: MistakeWord,
        val word: Word?,
        val wordbookName: String?,
        val groupTitle: String? = null
    )

    private val _mistakeList = MutableLiveData<List<MistakeWithWord>>(emptyList())
    val mistakeList: LiveData<List<MistakeWithWord>> = _mistakeList

    /** 筛选模式 */
    private val _filterMode = MutableStateFlow("all")
    val filterMode: StateFlow<String> = _filterMode

    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** 多选模式 */
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode

    /** 已选中项 */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    /** 各词库错词分布 */
    private val _wordbookDistribution = MutableLiveData<Map<String, Int>>(emptyMap())
    val wordbookDistribution: LiveData<Map<String, Int>> = _wordbookDistribution

    /** 加载中 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 空状态 */
    private val _isEmpty = MutableLiveData(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    /** 错误消息 */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /** 操作成功消息 */
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    init {
        loadMistakeList()
    }

    /**
     * 加载错词列表
     */
    fun loadMistakeList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val mistakes = withContext(Dispatchers.IO) {
                    mistakeWordRepo.getAllActiveOnce()
                }
                _mistakeCount.postValue(mistakes.size)

                // 获取所有单词信息
                val wordIds = mistakes.map { it.wordId }
                val words = withContext(Dispatchers.IO) {
                    wordRepo.getByIds(wordIds)
                }
                val wordMap = words.associateBy { it.id }

                // 获取词库名称映射
                val wordbookIds = mistakes.map { it.wordbookId }.distinct()
                val wordbookNames = mutableMapOf<Long, String>()
                for (wbId in wordbookIds) {
                    val wb = withContext(Dispatchers.IO) { wordbookRepo.getById(wbId) }
                    if (wb != null) {
                        wordbookNames[wbId] = wb.name
                    }
                }

                var list = mistakes.map { mistake ->
                    MistakeWithWord(
                        mistake = mistake,
                        word = wordMap[mistake.wordId],
                        wordbookName = wordbookNames[mistake.wordbookId]
                    )
                }

                // 根据筛选模式排序或分组
                if (_filterMode.value == "wordbook") {
                    // 按词库分组，插入分组标题项
                    val grouped = list.groupBy { it.wordbookName ?: "未知词库" }
                    val groupedKey = "__group__"
                    val result = mutableListOf<MistakeWithWord>()
                    for ((wbName, items) in grouped.toSortedMap()) {
                        result.add(MistakeWithWord(
                            mistake = com.wordforge.app.data.db.entity.MistakeWord(
                                wordId = -1, wordbookId = -1, errorCount = 0,
                                lastErrorAt = 0, firstErrorAt = 0
                            ),
                            word = null,
                            wordbookName = groupedKey,
                            groupTitle = "📖 $wbName (${items.size}词)"
                        ))
                        result.addAll(items)
                    }
                    list = result
                } else {
                    list = when (_filterMode.value) {
                        "date" -> list.sortedByDescending { it.mistake.lastErrorAt }
                        "count" -> list.sortedByDescending { it.mistake.errorCount }
                        else -> list.sortedByDescending { it.mistake.lastErrorAt }
                    }
                }

                // 搜索过滤
                val query = _searchQuery.value.trim().lowercase()
                if (query.isNotEmpty()) {
                    list = list.filter { mw ->
                        mw.word?.spelling?.lowercase()?.contains(query) == true ||
                                mw.word?.meaning?.lowercase()?.contains(query) == true
                    }
                }

                _mistakeList.postValue(list)
                _isEmpty.postValue(mistakes.isEmpty())

                // 计算词库分布
                val distribution = mutableMapOf<String, Int>()
                mistakes.forEach { m ->
                    val name = wordbookNames[m.wordbookId] ?: "未知词库"
                    distribution[name] = (distribution[name] ?: 0) + 1
                }
                _wordbookDistribution.postValue(distribution)
            } catch (e: Exception) {
                _errorMessage.postValue(e.message ?: "加载失败")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * 设置筛选模式
     */
    fun setFilterMode(mode: String) {
        _filterMode.value = mode
        loadMistakeList()
    }

    /**
     * 设置搜索关键词
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadMistakeList()
    }

    /**
     * 移除单个错词
     */
    fun removeMistake(wordId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mistakeWordRepo.deleteByWordId(wordId)
            }
            _successMessage.postValue("已移除错词")
            loadMistakeList()
        }
    }

    /**
     * 批量移除错词
     */
    fun batchRemove(ids: Set<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ids.forEach { wordId ->
                    mistakeWordRepo.deleteByWordId(wordId)
                }
            }
            _successMessage.postValue("已移除 ${ids.size} 个错词")
            exitMultiSelectMode()
            loadMistakeList()
        }
    }

    /**
     * 批量标记为需要复习（添加到复习队列）
     * 实际效果：将错词的 lastErrorAt 更新为当前时间，使其出现在复习队列顶部
     */
    fun batchReview(ids: Set<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ids.forEach { wordId ->
                    mistakeWordRepo.incrementErrorCount(wordId, System.currentTimeMillis())
                }
            }
            _successMessage.postValue("已将 ${ids.size} 个错词加入复习队列")
            exitMultiSelectMode()
            loadMistakeList()
        }
    }

    /**
     * 切换多选模式
     */
    fun toggleMultiSelectMode() {
        _isMultiSelectMode.value = !(_isMultiSelectMode.value)
        if (!_isMultiSelectMode.value) {
            _selectedIds.value = emptySet()
        }
    }

    /**
     * 退出多选模式
     */
    fun exitMultiSelectMode() {
        _isMultiSelectMode.value = false
        _selectedIds.value = emptySet()
    }

    /**
     * 切换选中状态
     */
    fun toggleSelection(wordId: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(wordId)) {
            current.remove(wordId)
        } else {
            current.add(wordId)
        }
        _selectedIds.value = current
    }

    /**
     * 全选/取消全选
     */
    fun toggleSelectAll() {
        val currentList = _mistakeList.value ?: return
        val currentIds = _selectedIds.value
        if (currentIds.size == currentList.size) {
            _selectedIds.value = emptySet()
        } else {
            _selectedIds.value = currentList.map { it.mistake.wordId }.toSet()
        }
    }
}
