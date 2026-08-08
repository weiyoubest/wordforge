package com.wordforge.app.data.initializer

import android.content.Context
import android.util.Log
import com.wordforge.app.data.db.entity.Wordbook
import com.wordforge.app.data.db.entity.Word
import com.wordforge.app.data.importer.WordImporter
import com.wordforge.app.data.repository.SettingsRepository
import com.wordforge.app.data.repository.WordRepository
import com.wordforge.app.data.repository.WordbookRepository

/**
 * 预置词库初始化器
 * 遍历所有内置词库，将尚未加载的词库从assets导入到数据库。
 * 
 * 触发时机：
 * 1. WordForgeApp.onCreate（后台协程）
 * 2. MainActivity.onCreate（后台协程）
 * 3. 用户手动触发（设置页"重新加载词库"）
 * 
 * 幂等安全：已存在的词库通过name去重跳过。
 */
class BuiltinWordbookInitializer(
    private val context: Context,
    private val wordbookRepo: WordbookRepository,
    private val wordRepo: WordRepository,
    private val settingsRepo: SettingsRepository
) {
    companion object {
        private const val TAG = "WordbookInit"

        private val WORDBOOK_META = listOf(
            "cet4" to WordbookMeta("CET-4核心词汇", "大学英语四级核心509词", 509),
            "cet6" to WordbookMeta("CET-6核心词汇", "大学英语六级核心500词", 500),
            "kaoyan" to WordbookMeta("考研核心词汇", "考研英语核心504词", 504),
            "eu_digital_security" to WordbookMeta("欧盟数字安全术语", "欧盟数字安全领域核心术语319词", 319),
            "daily_life" to WordbookMeta("生活词汇", "日常生活和工作中最常用的英语高频词汇476词", 476),
            "legal_terms" to WordbookMeta("法律词汇", "法律领域的核心英语术语词汇341词", 341),
            "ai_terms" to WordbookMeta("AI词汇", "人工智能领域的核心英语术语词汇414词", 414)
        )
    }

    data class WordbookMeta(
        val name: String,
        val description: String,
        val wordCount: Int
    )

    private val importer = WordImporter()

    /**
     * 执行词库初始化。
     * 不再检查onboarding——只要有数据库就直接加载所有内置词库。
     * 已存在的通过getByName跳过。
     * 
     * @return 本次新加载的词库数量
     */
    suspend fun initializeIfNeeded(): Int {
        Log.d(TAG, "=== Wordbook initialization START ===")
        var totalInserted = 0
        var totalLoaded = 0

        for ((id, meta) in WORDBOOK_META) {
            try {
                // 检查是否已存在且有单词数据
                val existing = wordbookRepo.getByName(meta.name)
                if (existing != null) {
                    val wordCount = wordRepo.countByWordbook(existing.id)
                    if (wordCount > 0) {
                        Log.d(TAG, "SKIP '${meta.name}' (exists with $wordCount words, id=${existing.id})")
                        continue
                    } else {
                        // 空壳词库：删除后重新加载
                        Log.w(TAG, "EMPTY '${meta.name}' (id=${existing.id}, 0 words) — deleting and reloading")
                        wordRepo.deleteAllByWordbook(existing.id)
                        wordbookRepo.deleteById(existing.id)
                    }
                }

                // 从assets加载
                val assetPath = "wordbooks/$id.json"
                Log.d(TAG, "Loading '$id' from assets/$assetPath ...")

                val inputStream = context.assets.open(assetPath)
                val items = importer.parse(inputStream, WordImporter.Format.JSON)
                inputStream.close()

                if (items.isEmpty()) {
                    Log.w(TAG, "WARN '$id': parsed 0 words from $assetPath")
                    continue
                }

                // 创建词库记录
                val wordbook = Wordbook(
                    name = meta.name,
                    description = meta.description,
                    totalWords = meta.wordCount,
                    type = "builtin",
                    isActive = false,
                    createdAt = System.currentTimeMillis()
                )
                val wordbookDbId = wordbookRepo.insert(wordbook)
                Log.d(TAG, "Inserted wordbook '${meta.name}' (dbId=$wordbookDbId)")

                // 批量插入单词
                val entities = importer.toEntities(items, wordbookDbId)
                val batchSize = 200
                var count = 0
                for (i in entities.indices step batchSize) {
                    val batch = entities.subList(i, minOf(i + batchSize, entities.size))
                    wordRepo.insertAll(batch)
                    count += batch.size
                }
                Log.d(TAG, "Inserted $count words for '${meta.name}'")
                totalInserted += count
                totalLoaded++
            } catch (e: Exception) {
                Log.e(TAG, "FAIL '$id': ${e.message}", e)
            }
        }

        // 自动激活：如果没有激活的词库，激活第一个
        try {
            val active = wordbookRepo.getActiveWordbook()
            if (active == null) {
                val all = wordbookRepo.getAllOnce()
                if (all.isNotEmpty()) {
                    wordbookRepo.setActive(all.first().id)
                    Log.d(TAG, "Auto-activated: '${all.first().name}'")
                } else {
                    Log.w(TAG, "No wordbooks to activate!")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-activate failed: ${e.message}", e)
        }

        Log.d(TAG, "=== Wordbook initialization DONE: loaded $totalLoaded wordbooks, $totalInserted words total ===")
        return totalLoaded
    }
}
