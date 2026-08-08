package com.wordforge.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.data.db.entity.Wordbook
import com.wordforge.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 学习统计数据
 */
data class StudyStats(
    val totalDays: Int,
    val totalWords: Int,
    val todayWords: Int,
    val streakDays: Int
)

/**
 * 首页 ViewModel
 * 查询今日学习任务、待复习数、连续打卡天数、本周打卡数据、学习统计
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val wordbookRepo = com.wordforge.app.data.repository.WordbookRepository(db.wordbookDao())
    private val learningRecordRepo = com.wordforge.app.data.repository.LearningRecordRepository(db.learningRecordDao())
    private val dailyStatsRepo = com.wordforge.app.data.repository.DailyStatsRepository(db.dailyStatsDao())
    private val settingsRepo = com.wordforge.app.data.repository.SettingsRepository(db.appSettingsDao())
    private val mistakeWordRepo = com.wordforge.app.data.repository.MistakeWordRepository(db.mistakeWordDao())

    private val today = DateUtils.today()

    /** 当前激活的词库 */
    private val _activeWordbook = MutableLiveData<Wordbook?>()
    val activeWordbook: LiveData<Wordbook?> = _activeWordbook

    /** 今日新词进度 (已学/目标) */
    private val _newWordsProgress = MutableLiveData(Pair(0, 20))
    val newWordsProgress: LiveData<Pair<Int, Int>> = _newWordsProgress

    /** 今日复习进度 (已复习/待复习) */
    private val _reviewProgress = MutableLiveData(Pair(0, 0))
    val reviewProgress: LiveData<Pair<Int, Int>> = _reviewProgress

    /** 连续打卡天数 */
    private val _streakDays = MutableLiveData(0)
    val streakDays: LiveData<Int> = _streakDays

    /** 本周打卡日期列表 (Mon~Sun) */
    private val _weeklyCheckins = MutableLiveData<List<Boolean>>()
    val weeklyCheckins: LiveData<List<Boolean>> = _weeklyCheckins

    /** 是否有错词 */
    private val _hasMistakeWords = MutableLiveData(false)
    val hasMistakeWords: LiveData<Boolean> = _hasMistakeWords

    /** 加载状态 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 是否全部完成 */
    private val _allComplete = MutableLiveData(false)
    val allComplete: LiveData<Boolean> = _allComplete

    /** 学习统计数据 */
    private val _studyStats = MutableLiveData(StudyStats(0, 0, 0, 0))
    val studyStats: LiveData<StudyStats> = _studyStats

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadWordbook()
                loadTodayStats()
                loadStreak()
                loadWeeklyCheckins()
                loadMistakeCount()
                loadStudyStats()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadWordbook() {
        val wordbook = withContext(Dispatchers.IO) {
            wordbookRepo.getActiveWordbook()
        }
        _activeWordbook.postValue(wordbook)
        if (wordbook != null) {
            val dailyTarget = withContext(Dispatchers.IO) {
                settingsRepo.getInt("daily_new_words", 20)
            }
            val newToday = withContext(Dispatchers.IO) {
                learningRecordRepo.countNewWordsOnDate(today)
            }
            _newWordsProgress.postValue(Pair(newToday, dailyTarget))

            val dueReviews = withContext(Dispatchers.IO) {
                learningRecordRepo.getDueReviews(System.currentTimeMillis(), wordbook.id)
            }
            val reviewedToday = withContext(Dispatchers.IO) {
                learningRecordRepo.countReviewsOnDate(today)
            }
            _reviewProgress.postValue(Pair(reviewedToday, dueReviews.size))
            _allComplete.postValue(newToday >= dailyTarget && dueReviews.isEmpty())
        } else {
            _newWordsProgress.postValue(Pair(0, 20))
            _reviewProgress.postValue(Pair(0, 0))
        }
    }

    private suspend fun loadTodayStats() {
        // streak 已在 loadStreak 中处理
    }

    private suspend fun loadStreak() {
        val streak = withContext(Dispatchers.IO) {
            dailyStatsRepo.getCurrentStreak()
        }
        _streakDays.postValue(streak)
    }

    private suspend fun loadWeeklyCheckins() {
        val checkins = withContext(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val weekStart = DateUtils.formatDate(calendar.timeInMillis)
            val dates = (0..6).map { offset ->
                val c = Calendar.getInstance()
                c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                c.add(Calendar.DAY_OF_MONTH, offset)
                DateUtils.formatDate(c.timeInMillis)
            }
            val allStats = dailyStatsRepo.getByDateRange(weekStart, dates.last())
            val statsDates = allStats.map { it.date }.toSet()
            dates.map { statsDates.contains(it) }
        }
        _weeklyCheckins.postValue(checkins)
    }

    private suspend fun loadMistakeCount() {
        val count = withContext(Dispatchers.IO) {
            mistakeWordRepo.countActive()
        }
        _hasMistakeWords.postValue(count > 0)
    }

    /**
     * 加载学习统计数据
     * 尝试从 DailyStatsRepository 获取，失败则使用当前已有数据作为fallback
     */
    private suspend fun loadStudyStats() {
        val stats = withContext(Dispatchers.IO) {
            try {
                val totalDays = dailyStatsRepo.countStudyDays()
                val streak = dailyStatsRepo.getCurrentStreak()
                val todayCount = learningRecordRepo.countNewWordsOnDate(today) +
                    learningRecordRepo.countReviewsOnDate(today)
                // 总学习单词数：尝试从所有 dailyStats 中累加
                val allStats = dailyStatsRepo.getByDateRange("2020-01-01", today)
                val totalWords = allStats.sumOf { it.newWordsLearned + it.wordsReviewed }
                StudyStats(
                    totalDays = totalDays,
                    totalWords = totalWords,
                    todayWords = todayCount,
                    streakDays = streak
                )
            } catch (e: Exception) {
                // Fallback：使用已有 LiveData 中的数据
                val streak = dailyStatsRepo.getCurrentStreak()
                StudyStats(totalDays = 0, totalWords = 0, todayWords = 0, streakDays = streak)
            }
        }
        _studyStats.postValue(stats)
    }
}
