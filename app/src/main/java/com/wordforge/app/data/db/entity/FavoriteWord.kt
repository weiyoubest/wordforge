package com.wordforge.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 收藏/生词本表
 * 用户主动收藏的单词
 */
@Entity(
    tableName = "favorite_word",
    indices = [
        Index(value = ["wordId"], unique = true)
    ]
)
data class FavoriteWord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wordId: Long,
    val favoritedAt: Long = System.currentTimeMillis()
)
