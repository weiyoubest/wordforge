package com.wordforge.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 日期工具类
 * 统一日期格式处理
 */
object DateUtils {

    const val PATTERN_DATE = "yyyy-MM-dd"
    const val PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm:ss"
    const val PATTERN_TIME = "HH:mm"

    private val dateFormat = SimpleDateFormat(PATTERN_DATE, Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat(PATTERN_DATE_TIME, Locale.getDefault())
    private val timeFormat = SimpleDateFormat(PATTERN_TIME, Locale.getDefault())

    init {
        // 确保使用本地时区
        dateFormat.timeZone = TimeZone.getDefault()
        dateTimeFormat.timeZone = TimeZone.getDefault()
        timeFormat.timeZone = TimeZone.getDefault()
    }

    /**
     * 获取今天的日期字符串 (yyyy-MM-dd)
     */
    fun today(): String {
        return dateFormat.format(Date())
    }

    /**
     * 格式化时间戳为日期字符串
     */
    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    /**
     * 格式化时间戳为日期时间字符串
     */
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    /**
     * 格式化时间戳为时间字符串 (HH:mm)
     */
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    /**
     * 将日期字符串解析为时间戳
     */
    fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 获取相对时间描述（如"5分钟前"、"3天前"）
     */
    fun getRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> formatDate(timestamp)
        }
    }

    /**
     * 计算两个日期之间的天数差
     */
    fun daysBetween(dateStr1: String, dateStr2: String): Int {
        val time1 = parseDate(dateStr1)
        val time2 = parseDate(dateStr2)
        val diff = kotlin.math.abs(time1 - time2)
        return (diff / (24 * 60 * 60 * 1000)).toInt()
    }

    /**
     * 获取最近 N 天的日期列表
     */
    fun getRecentDays(days: Int): List<String> {
        val dates = mutableListOf<String>()
        val calendar = java.util.Calendar.getInstance()

        repeat(days) {
            dates.add(dateFormat.format(calendar.time))
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }

        return dates.reversed()
    }
}
