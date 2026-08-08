package com.wordforge.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单词表
 * 存储所有单词信息，包括释义、音标、例句、词根词缀等
 */
@Entity(
    tableName = "word",
    indices = [
        Index(value = ["spelling"]),
        Index(value = ["wordbookId"])
    ]
)
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val spelling: String,
    val phonetic: String? = null,
    val meaning: String,
    val partOfSpeech: String? = null,
    val exampleSentence: String? = null,
    val exampleTranslation: String? = null,
    val rootAffix: String? = null,
    val synonyms: String? = null,
    val antonyms: String? = null,
    val confusableWords: String? = null,
    val audioPath: String? = null,
    val wordbookId: Long,
    val difficulty: Int? = null
)
