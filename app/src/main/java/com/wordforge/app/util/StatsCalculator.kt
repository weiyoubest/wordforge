package com.wordforge.app.util

/**
 * 统计计算工具类
 * 提供学习数据统计相关的计算方法
 */
object StatsCalculator {

    /**
     * 计算正确率
     * @param correct 正确数
     * @param total 总数
     * @return 0.0 ~ 100.0
     */
    fun calculateAccuracy(correct: Int, total: Int): Float {
        if (total == 0) return 0f
        return (correct.toFloat() / total.toFloat()) * 100f
    }

    /**
     * 计算连续打卡天数
     * @param sortedDates 按降序排列的日期列表 (yyyy-MM-dd)
     * @return 连续打卡天数
     */
    fun calculateStreakDays(sortedDates: List<String>): Int {
        if (sortedDates.isEmpty()) return 0

        val today = DateUtils.today()
        val yesterday = DateUtils.formatDate(
            System.currentTimeMillis() - 24 * 60 * 60 * 1000
        )

        // 连续打卡必须从今天或昨天开始
        if (sortedDates.first() != today && sortedDates.first() != yesterday) {
            return 0
        }

        var streak = 1
        for (i in 0 until sortedDates.size - 1) {
            val diff = DateUtils.daysBetween(sortedDates[i], sortedDates[i + 1])
            if (diff == 1) {
                streak++
            } else {
                break
            }
        }

        return streak
    }

    /**
     * 计算学习时长格式化
     * @param seconds 秒数
     * @return 格式化字符串，如 "1h 23m" 或 "45m"
     */
    fun formatStudyDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    /**
     * 计算总学习时长（从多个来源汇总）
     */
    fun sumDurations(durations: List<Int>): Int {
        return durations.sum()
    }

    /**
     * 计算进度百分比
     */
    fun calculateProgress(completed: Int, total: Int): Float {
        if (total == 0) return 0f
        return (completed.toFloat() / total.toFloat()) * 100f
    }
}
