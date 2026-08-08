package com.wordforge.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 词库表
 * 管理内置词库和自定义词库
 */
@Entity(tableName = "wordbook")
data class Wordbook(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val totalWords: Int = 0,
    val type: String, // "builtin" or "custom"
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
