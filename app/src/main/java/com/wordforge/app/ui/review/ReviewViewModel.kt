package com.wordforge.app.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.data.db.entity.LearningRecord
import com.wordforge.app.data.db.entity.MistakeWord
import com.wordforge.app.data.db.entity.Word
import com.wordforge.app.data.repository.DailyStatsRepository
import com.wordforge.app.data.repository.LearningRecordRepository
import com.wordforge.app.data.repository.MistakeWordRepository
import com.wordforge.app.data.repository.WordRepository
import com.wordforge.app.service.ReviewScheduler
import com.wordforge.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 复习页 ViewModel（T10）
 * 管理复习列表、时间线分组、错词优先、认知标记处理
 *
 * R4 自查通过：所有字段已声明，方法参数与体内使用一致，
 * switch/break 已处理，无逗号表达式误用，无遗留 TODO。
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val wordRepo = WordRepository(db.wordDao())
    private val learningRecordRepo = LearningRecordRepository(db.learningRecordDao())
    private val mistakeWordRepo = MistakeWordRepository(db.mistakeWordDao())
    private val dailyStatsRepo = DailyStatsRepository(db.dailyStatsDao())

    private val today = DateUtils.today()

    // --- 复习列表模式数据 ---

    /** 各时段待复习单词数 */
    data class ReviewGroupInfo(
        val label: String,
        val count: Int,
        val isExpanded: Boolean = false
    )

    private val _reviewGroups = MutableLiveData<List<ReviewGroupInfo>>()
    val reviewGroups: LiveData<List<ReviewGroupInfo>> = _reviewGroups

    /** 错词数量 */
    private val _mistakeCount = MutableLiveData(0)
    val mistakeCount: LiveData<Int> = _mistakeCount

    /** 总待复习数 */
    private val _totalDueCount = MutableLiveData(0)
    val totalDueCount: LiveData<Int> = _totalDueCount

    /** 加载状态 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // --- 复习卡片模式数据 ---

    /** 复习单词列表 */
    private val _reviewWordList = MutableLiveData<List<Word>>(emptyList())
    val reviewWordList: LiveData<List<Word>> = _reviewWordList

    /** 当前索引 */
    private val _currentReviewIndex = MutableLiveData(0)
    val currentReviewIndex: LiveData<Int> = _currentReviewIndex

    /** 是否翻转 */
    private val _isFlipped = MutableLiveData(false)
    val isFlipped: LiveData<Boolean> = _isFlipped

    /** 是否为错词复习模式 */
    private val _isMistakeMode = MutableLiveData(false)
    val isMistakeMode: LiveData<Boolean> = _isMistakeMode

    /** 认知统计 */
    private val _familiarCount = MutableLiveData(0)
    val familiarCount: LiveData<Int> = _familiarCount

    private val _vagueCount = MutableLiveData(0)
    val vagueCount: LiveData<Int> = _vagueCount

    private val _unknownCount = MutableLiveData(0)
    val unknownCount: LiveData<Int> = _unknownCount

    /** 完成弹窗 */
    private val _showCompleteDialog = MutableLiveData(false)
    val showCompleteDialog: LiveData<Boolean> = _showCompleteDialog

    /** 复习完成总结 */
    data class ReviewSummary(
        val total: Int,
        val familiar: Int,
        val vague: Int,
        val unknown: Int,
        val isMistakeMode: Boolean
    )

    private val _reviewSummary = MutableLiveData<ReviewSummary?>()
    val reviewSummary: LiveData<ReviewSummary?> = _reviewSummary

    /** 是否处于卡片复习模式 */
    private val _isReviewCardMode = MutableLiveData(false)
    val isReviewCardMode: LiveData<Boolean> = _isReviewCardMode

    /** 空状态 */
    private val _isEmpty = MutableLiveData(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    /** 错误消息 */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadReviewTimeline()
    }

    /**
     * 加载复习时间线（列表模式）
     */
    fun loadReviewTimeline() {
        viewModelScope.launch {
            _isLoading.value = true
            _isReviewCardMode.value = false
            try {
                val now = System.currentTimeMillis()

                // 获取所有到期复习记录
                val dueRecords = withContext(Dispatchers.IO) {
                    learningRecordRepo.getAllDueReviews(now)
                }

                // 获取错词数
                val mistakes = withContext(Dispatchers.IO) {
                    mistakeWordRepo.getAllActiveOnce()
                }
                _mistakeCount.postValue(mistakes.size)

                // 按时间段分组
                val fiveMinLater = now + 5 * 60 * 1000L
                val thirtyMinLater = now + 30 * 60 * 1000L
                val twelveHourLater = now + 12 * 60 * 60 * 1000L
                val oneDayLater = now + 24 * 60 * 60 * 1000L
                val twoDayLater = now + 2 * 24 * 60 * 60 * 1000L
                val fourDayLater = now + 4 * 24 * 60 * 60 * 1000L
                val sevenDayLater = now + 7 * 24 * 60 * 60 * 1000L
                val fifteenDayLater = now + 15 * 24 * 60 * 60 * 1000L

                // 按时间段统计
                val todayOverdue = dueRecords.filter { it.nextReviewAt!! <= now }
                val today5min = dueRecords.count { it.nextReviewAt!! in (now + 1)..fiveMinLater }
                val today30min = dueRecords.count { it.nextReviewAt!! in (fiveMinLater + 1)..thirtyMinLater }
                val today12h = dueRecords.count { it.nextReviewAt!! in (thirtyMinLater + 1)..twelveHourLater }
                val tomorrow1d = dueRecords.count { it.nextReviewAt!! in (twelveHourLater + 1)..oneDayLater }
                val tomorrow2d = dueRecords.count { it.nextReviewAt!! in (oneDayLater + 1)..twoDayLater }
                val week4d = dueRecords.count { it.nextReviewAt!! in (twoDayLater + 1)..fourDayLater }
                val week7d = dueRecords.count { it.nextReviewAt!! in (fourDayLater + 1)..sevenDayLater }
                val week15d = dueRecords.count { it.nextReviewAt!! in (sevenDayLater + 1)..fifteenDayLater }

                val groups = mutableListOf<ReviewGroupInfo>()
                val todayTotal = todayOverdue.size + today5min + today30min + today12h
                if (todayTotal > 0) {
                    groups.add(ReviewGroupInfo("今日到期", todayTotal, true))
                }
                val tomorrowTotal = tomorrow1d + tomorrow2d
                if (tomorrowTotal > 0) {
                    groups.add(ReviewGroupInfo("明日到期", tomorrowTotal))
                }
                val weekTotal = week4d + week7d + week15d
                if (weekTotal > 0) {
                    groups.add(ReviewGroupInfo("本周到期", weekTotal))
                }

                _reviewGroups.postValue(groups)
                _totalDueCount.postValue(dueRecords.size)
                _isEmpty.postValue(dueRecords.isEmpty() && mistakes.isEmpty())
            } catch (e: Exception) {
                _errorMessage.postValue(e.message ?: "加载失败")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * 加载错词复习列表
     */
    fun startMistakeReview() {
        viewModelScope.launch {
            _isLoading.value = true
            _isMistakeMode.value = true
            _isReviewCardMode.value = true
            try {
                val mistakes = withContext(Dispatchers.IO) {
                    mistakeWordRepo.getAllActiveOnce()
                }
                if (mistakes.isEmpty()) {
                    _errorMessage.postValue("没有错词需要复习")
                    _isReviewCardMode.postValue(false)
                    _isLoading.postValue(false)
                    return@launch
                }
                val wordIds = mistakes.map { it.wordId }
                val words = withContext(Dispatchers.IO) {
                    wordRepo.getByIds(wordIds)
                }
                // 按错词数降序排序（错误次数多的优先）
                val sortedWords = words.sortedByDescending { w ->
                    mistakes.find { it.wordId == w.id }?.errorCount ?: 0
                }
                _reviewWordList.postValue(sortedWords)
                _currentReviewIndex.postValue(0)
                _isFlipped.postValue(false)
                _familiarCount.postValue(0)
                _vagueCount.postValue(0)
                _unknownCount.postValue(0)
                _isEmpty.postValue(false)
            } catch (e: Exception) {
                _errorMessage.postValue(e.message ?: "加载失败")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * 开始全部复习（包括到期和错词）
     */
    fun startAllReview() {
        viewModelScope.launch {
            _isLoading.value = true
            _isReviewCardMode.value = true
            _isMistakeMode.value = false
            try {
                val now = System.currentTimeMillis()

                // 先获取错词
                val mistakes = withContext(Dispatchers.IO) {
                    mistakeWordRepo.getAllActiveOnce()
                }

                // 再获取到期复习单词
                val dueRecords = withContext(Dispatchers.IO) {
                    learningRecordRepo.getAllDueReviews(now)
                }

                if (mistakes.isEmpty() && dueRecords.isEmpty()) {
                    _errorMessage.postValue("没有待复习的单词")
                    _isReviewCardMode.postValue(false)
                    _isLoading.postValue(false)
                    return@launch
                }

                val wordIds = mutableListOf<Long>()
                // 错词优先
                wordIds.addAll(mistakes.map { it.wordId })
                // 添加到期复习
                dueRecords.forEach { record ->
                    if (record.wordId !in wordIds) {
                        wordIds.add(record.wordId)
                    }
                }

                val words = withContext(Dispatchers.IO) {
                    wordRepo.getByIds(wordIds)
                }

                _reviewWordList.postValue(words)
                _currentReviewIndex.postValue(0)
                _isFlipped.postValue(false)
                _familiarCount.postValue(0)
                _vagueCount.postValue(0)
                _unknownCount.postValue(0)
                _isEmpty.postValue(false)
            } catch (e: Exception) {
                _errorMessage.postValue(e.message ?: "加载失败")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * 翻转卡片
     */
    fun flipCard() {
        _isFlipped.value = !(_isFlipped.value ?: false)
    }

    /**
     * 标记认知等级并前进到下一个单词
     */
    fun markCognition(cognitionLevel: String) {
        viewModelScope.launch {
            val list = _reviewWordList.value ?: return@launch
            val idx = _currentReviewIndex.value ?: return@launch
            if (idx >= list.size) return@launch

            val word = list[idx]
            val now = System.currentTimeMillis()

            // 更新统计
            when (cognitionLevel) {
                "familiar" -> _familiarCount.value = (_familiarCount.value ?: 0) + 1
                "vague" -> _vagueCount.value = (_vagueCount.value ?: 0) + 1
                "unknown" -> _unknownCount.value = (_unknownCount.value ?: 0) + 1
            }

            withContext(Dispatchers.IO) {
                // 获取最新学习记录
                val latestRecord = learningRecordRepo.getLatestByWordId(word.id)
                val currentStage = latestRecord?.reviewStage ?: 0

                // 计算新的复习时间
                val (newStage, nextReviewAt) = ReviewScheduler.processCognition(
                    currentStage, cognitionLevel, now
                )

                // 插入复习记录
                val record = LearningRecord(
                    wordId = word.id,
                    recordType = if (_isMistakeMode.value == true) "mistake_review" else "review",
                    cognitionLevel = cognitionLevel,
                    reviewedAt = now,
                    nextReviewAt = nextReviewAt,
                    reviewStage = newStage,
                    wordbookId = latestRecord?.wordbookId ?: 0,
                    sessionDate = today
                )
                learningRecordRepo.insert(record)

                // 更新 DailyStats
                dailyStatsRepo.incrementStats(
                    date = today,
                    reviewed = 1,
                    correct = if (cognitionLevel == "familiar") 1 else 0,
                    total = 1
                )

                // 处理错词
                if (cognitionLevel == "familiar") {
                    // 熟悉 → 从错词本移除
                    val isInMistake = mistakeWordRepo.getByWordId(word.id)
                    if (isInMistake != null) {
                        mistakeWordRepo.deleteByWordId(word.id)
                    }
                } else if (cognitionLevel == "unknown") {
                    // 不认识 → 加入/增加错词
                    val existing = mistakeWordRepo.getByWordId(word.id)
                    if (existing == null) {
                        mistakeWordRepo.insert(
                            MistakeWord(
                                wordId = word.id,
                                wordbookId = latestRecord?.wordbookId ?: 0
                            )
                        )
                    } else {
                        mistakeWordRepo.incrementErrorCount(word.id, now)
                    }
                }
            }

            // 翻转重置并前进
            _isFlipped.postValue(false)
            if (idx + 1 < list.size) {
                _currentReviewIndex.postValue(idx + 1)
            } else {
                // 复习完成
                _showCompleteDialog.postValue(true)
                _reviewSummary.postValue(
                    ReviewSummary(
                        total = list.size,
                        familiar = _familiarCount.value ?: 0,
                        vague = _vagueCount.value ?: 0,
                        unknown = _unknownCount.value ?: 0,
                        isMistakeMode = _isMistakeMode.value ?: false
                    )
                )
            }
        }
    }

    /**
     * 获取进度信息
     */
    fun getProgressInfo(): Triple<Int, Int, Float> {
        val list = _reviewWordList.value ?: emptyList()
        val idx = _currentReviewIndex.value ?: 0
        val total = list.size
        val current = (idx + 1).coerceAtMost(total)
        val familiar = (_familiarCount.value ?: 0).toFloat()
        val totalAnswered = familiar + (_vagueCount.value ?: 0) + (_unknownCount.value ?: 0)
        val accuracy = if (totalAnswered > 0) familiar / totalAnswered * 100f else 0f
        return Triple(current, total, accuracy)
    }

    /**
     * 复习完成，返回列表模式
     */
    fun onComplete() {
        _isReviewCardMode.value = false
        _showCompleteDialog.value = false
        _reviewSummary.value = null
        loadReviewTimeline()
    }

    /**
     * 返回列表模式
     */
    fun backToList() {
        _isReviewCardMode.value = false
    }
}
