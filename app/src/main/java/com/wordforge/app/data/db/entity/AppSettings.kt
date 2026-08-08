package com.wordforge.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用设置表
 * 使用 key-value 存储全局设置项
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val key: String,
    val value: String
)
