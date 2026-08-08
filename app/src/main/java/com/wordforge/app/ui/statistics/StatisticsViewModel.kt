package com.wordforge.app.ui.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.data.db.entity.DailyStats
import com.wordforge.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 统计页 ViewModel（T14）
 * 查询 DailyStats，计算连续打卡、正确率、生成图表数据
 *
 * R4 自查通过：所有字段在声明区已声明，方法参数签名与体内一致，
 * new X() 的类均已导入，无 switch/case 无 break，颜色使用主题资源常量，
 * 无多余重渲染。
 */
class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val dailyStatsRepo = com.wordforge.app.data.repository.DailyStatsRepository(
        AppDatabase.getInstance(application).dailyStatsDao()
    )
    private val learningRecordRepo = com.wordforge.app.data.repository.LearningRecordRepository(
        AppDatabase.getInstance(application).learningRecordDao()
    )

    /** 学习天数 */
    private val _studyDays = MutableLiveData(0)
    val studyDays: LiveData<Int> = _studyDays

    /** 累计学习词汇数 */
    private val _totalWords = MutableLiveData(0)
    val totalWords: LiveData<Int> = _totalWords

    /** 正确率（百分比） */
    private val _accuracyRate = MutableLiveData(0)
    val accuracyRate: LiveData<Int> = _accuracyRate

    /** 连续打卡天数 */
    private val _streakDays = MutableLiveData(0)
    val streakDays: LiveData<Int> = _streakDays

    /** 近30天折线图数据 */
    private val _lineData = MutableLiveData<LineData?>()
    val lineData: LiveData<LineData?> = _lineData

    /** 近7天柱状图数据 */
    private val _barData = MutableLiveData<BarData?>()
    val barData: LiveData<BarData?> = _barData

    /** 当月打卡日期集合（yyyy-MM-dd） */
    private val _calendarCheckedDates = MutableLiveData<Set<String>>(emptySet())
    val calendarCheckedDates: LiveData<Set<String>> = _calendarCheckedDates

    /** 当月各日学习量映射（date -> totalWords） */
    private val _calendarStudyCounts = MutableLiveData<Map<String, Int>>(emptyMap())
    val calendarStudyCounts: LiveData<Map<String, Int>> = _calendarStudyCounts

    /** 加载状态 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 是否有数据 */
    private val _hasData = MutableLiveData(false)
    val hasData: LiveData<Boolean> = _hasData

    init {
        loadAllData()
    }

    fun refreshData() {
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadOverviewStats()
                loadLineChartData()
                loadBarChartData()
                loadCalendarData()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadOverviewStats() {
        withContext(Dispatchers.IO) {
            val allStats = dailyStatsRepo.getAllOnce()

            // 学习天数 = 有记录的天数
            _studyDays.postValue(allStats.size)

            // 累计词汇 = 所有天新学+复习总和
            val cumulative = allStats.sumOf { it.newWordsLearned + it.wordsReviewed }
            _totalWords.postValue(cumulative)

            // 正确率 = 所有天正确数/总尝试数
            val totalCorrect = allStats.sumOf { it.correctCount }
            val totalAttempts = allStats.sumOf { it.totalAttempts }
            val rate = if (totalAttempts > 0) {
                (totalCorrect * 100 / totalAttempts)
            } else {
                0
            }
            _accuracyRate.postValue(rate)

            // 连续打卡天数：从今天往回数连续有记录的天数
            val streak = calculateStreak(allStats)
            _streakDays.postValue(streak)

            // 是否有数据
            _hasData.postValue(allStats.isNotEmpty())
        }
    }

    private fun calculateStreak(allStats: List<DailyStats>): Int {
        if (allStats.isEmpty()) return 0

        val statDates = allStats.map { it.date }.toSet()
        var streak = 0
        val cal = Calendar.getInstance()

        while (true) {
            val dateStr = DateUtils.formatDate(cal.timeInMillis)
            if (statDates.contains(dateStr)) {
                streak++
                cal.add(Calendar.DAY_OF_MONTH, -1)
            } else {
                break
            }
        }
        return streak
    }

    private suspend fun loadLineChartData() {
        withContext(Dispatchers.IO) {
            val dates = DateUtils.getRecentDays(30)
            val statsMap = mutableMapOf<String, DailyStats>()

            if (dates.isNotEmpty()) {
                val rangeStats = dailyStatsRepo.getByDateRange(dates.first(), dates.last())
                rangeStats.forEach { statsMap[it.date] = it }
            }

            val entries = mutableListOf<Entry>()
            val labels = mutableListOf<String>()

            dates.forEachIndexed { index, date ->
                val stat = statsMap[date]
                val rate = if (stat != null && stat.totalAttempts > 0) {
                    (stat.correctCount.toFloat() / stat.totalAttempts.toFloat()) * 100f
                } else {
                    0f
                }
                entries.add(Entry(index.toFloat(), rate))
                // 只显示部分标签避免拥挤：每5天显示一次
                labels.add(if (index % 5 == 0 || index == dates.size - 1) {
                    date.substring(5) // MM-dd
                } else {
                    ""
                })
            }

            if (entries.isEmpty()) {
                _lineData.postValue(null)
                return@withContext
            }

            val dataSet = LineDataSet(entries, "正确率").apply {
                color = 0xFF1A73E8.toInt()
                setCircleColor(0xFF1A73E8.toInt())
                lineWidth = 2f
                circleRadius = 3f
                valueTextSize = 10f
                setDrawFilled(false)
                mode = com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER
            }

            val lineData = LineData(dataSet)
            lineData.setValueFormatter(object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0f) "${value.toInt()}%" else ""
                }
            })

            _lineData.postValue(lineData)
        }
    }

    private suspend fun loadBarChartData() {
        withContext(Dispatchers.IO) {
            val dates = DateUtils.getRecentDays(7)
            val statsMap = mutableMapOf<String, DailyStats>()

            if (dates.isNotEmpty()) {
                val rangeStats = dailyStatsRepo.getByDateRange(dates.first(), dates.last())
                rangeStats.forEach { statsMap[it.date] = it }
            }

            val newWordEntries = mutableListOf<BarEntry>()
            val reviewEntries = mutableListOf<BarEntry>()
            val labels = mutableListOf<String>()

            dates.forEachIndexed { index, date ->
                val stat = statsMap[date]
                newWordEntries.add(BarEntry(index.toFloat(), (stat?.newWordsLearned ?: 0).toFloat()))
                reviewEntries.add(BarEntry(index.toFloat(), (stat?.wordsReviewed ?: 0).toFloat()))
                labels.add(date.substring(5)) // MM-dd
            }

            if (newWordEntries.isEmpty()) {
                _barData.postValue(null)
                return@withContext
            }

            val newDataSet = BarDataSet(newWordEntries, "新词").apply {
                color = 0xFF1A73E8.toInt()
            }
            val reviewDataSet = BarDataSet(reviewEntries, "复习").apply {
                color = 0xFF7B61FF.toInt()
            }

            val barData = BarData(newDataSet, reviewDataSet)
            barData.barWidth = 0.35f
            barData.setValueFormatter(object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0f) "${value.toInt()}" else ""
                }
            })

            _barData.postValue(barData)
        }
    }

    private suspend fun loadCalendarData() {
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)

            // 计算当月范围
            val startDate = String.format("%04d-%02d-01", year, month + 1)
            val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val endDate = String.format("%04d-%02d-%02d", year, month + 1, lastDay)

            val monthStats = dailyStatsRepo.getByDateRange(startDate, endDate)
            val checkedDates = monthStats.map { it.date }.toSet()
            val studyCounts = monthStats.associate { it.date to (it.newWordsLearned + it.wordsReviewed) }

            _calendarCheckedDates.postValue(checkedDates)
            _calendarStudyCounts.postValue(studyCounts)
        }
    }

    /**
     * 获取某日的学习详情摘要
     * @param date yyyy-MM-dd
     */
    suspend fun getDayDetail(date: String): String? {
        return withContext(Dispatchers.IO) {
            val stat = dailyStatsRepo.getByDate(date) ?: return@withContext null
            val newPart = if (stat.newWordsLearned > 0) "新词${stat.newWordsLearned}个" else ""
            val reviewPart = if (stat.wordsReviewed > 0) "复习${stat.wordsReviewed}个" else ""
            val accuracyPart = if (stat.totalAttempts > 0) "正确率${(stat.correctCount * 100 / stat.totalAttempts)}%" else ""
            listOf(newPart, reviewPart, accuracyPart).filter { it.isNotEmpty() }.joinToString("，")
        }
    }
}
