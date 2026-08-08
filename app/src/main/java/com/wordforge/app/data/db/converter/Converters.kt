package com.wordforge.app.data.db.converter

import androidx.room.TypeConverter

/**
 * Room TypeConverter
 * 处理数据库中特殊类型的序列化/反序列化
 */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): java.time.LocalDateTime? {
        return value?.let {
            java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(it),
                java.time.ZoneId.systemDefault()
            )
        }
    }

    @TypeConverter
    fun dateToTimestamp(date: java.time.LocalDateTime?): Long? {
        return date?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        return value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String? {
        return list?.joinToString(",")
    }
}
