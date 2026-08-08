package com.wordforge.app.data.initializer

import android.content.Context
import android.util.Log
import com.wordforge.app.data.db.entity.Word
import com.wordforge.app.data.db.entity.Wordbook
import org.json.JSONArray
import org.json.JSONObject

/**
 * 预置词库初始化器 v2 — 极简版
 * 
 * 不依赖 WordImporter，直接内联解析 JSON。
 * 每一步都 Log，确保问题可追踪。
 * 
 * 触发时机：App.onCreate / MainActivity.onCreate / 首页诊断 / 词库管理页
 */
class BuiltinWordbookInitializerV2(
    private val context: Context,
    private val insertWordbook: suspend (Wordbook) -> Long,
    private val insertWords: suspend (List<Word>) -> List<Long>,
    private val getWordbookByName: suspend (String) -> com.wordforge.app.data.db.entity.Wordbook?,
    private val deleteWordbookById: suspend (Long) -> Unit,
    private val deleteWordsByWordbook: suspend (Long) -> Unit,
    private val countWordsByWordbook: suspend (Long) -> Int,
    private val getAllWordbooks: suspend () -> List<Wordbook>,
    private val setActiveWordbook: suspend (Long) -> Unit,
    private val getActiveWordbook: suspend () -> Wordbook?
) {
    companion object {
        private const val TAG = "WordbookInitV2"

        private val WORDBOOK_META = listOf(
            "cet4" to arrayOf("CET-4核心词汇", "大学英语四级核心509词", 509),
            "cet6" to arrayOf("CET-6核心词汇", "大学英语六级核心500词", 500),
            "kaoyan" to arrayOf("考研核心词汇", "考研英语核心504词", 504),
            "eu_digital_security" to arrayOf("欧盟数字安全术语", "欧盟数字安全领域核心术语319词", 319),
            "daily_life" to arrayOf("生活词汇", "日常生活和工作中最常用的英语高频词汇476词", 476),
            "legal_terms" to arrayOf("法律词汇", "法律领域的核心英语术语词汇341词", 341),
            "ai_terms" to arrayOf("AI词汇", "人工智能领域的核心英语术语词汇414词", 414)
        )
    }

    /**
     * 返回诊断日志列表 + 加载数量
     */
    suspend fun initialize(): Pair<List<String>, Int> {
        val logs = mutableListOf<String>()
        logs.add("START initialization")
        var totalLoaded = 0

        for ((id, meta) in WORDBOOK_META) {
            val name = meta[0] as String
            val desc = meta[1] as String
            val expectedCount = meta[2] as Int

            try {
                // 检查已存在
                val existing = getWordbookByName(name)
                if (existing != null) {
                    val wc = countWordsByWordbook(existing.id)
                    if (wc > 0) {
                        logs.add("SKIP $name (id=${existing.id}, $wc words)")
                        continue
                    } else {
                        logs.add("EMPTY $name (id=${existing.id}) — deleting")
                        deleteWordsByWordbook(existing.id)
                        deleteWordbookById(existing.id)
                    }
                }

                // 读取 assets
                val assetPath = "wordbooks/$id.json"
                val content: String
                try {
                    val stream = context.assets.open(assetPath)
                    content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    stream.close()
                } catch (e: Exception) {
                    logs.add("FAIL $name: cannot open asset '$assetPath': ${e.message}")
                    Log.e(TAG, "Cannot open $assetPath", e)
                    continue
                }
                logs.add("READ $name: ${content.length} chars")

                // 解析 JSON
                val words = parseWordJson(content)
                logs.add("PARSED $name: ${words.size} words")
                Log.d(TAG, "Parsed $id: ${words.size} words")

                if (words.isEmpty()) {
                    logs.add("WARN $name: 0 words parsed!")
                    continue
                }

                // 插入词库记录
                val wb = Wordbook(
                    name = name,
                    description = desc,
                    totalWords = expectedCount,
                    type = "builtin",
                    isActive = false,
                    createdAt = System.currentTimeMillis()
                )
                val wbId = insertWordbook(wb)
                logs.add("INSERT wordbook $name -> id=$wbId")
                Log.d(TAG, "Inserted wordbook $name (id=$wbId)")

                // 批量插入单词（wordbookId已由外部设置）
                var inserted = 0
                val batchSize = 200
                for (i in words.indices step batchSize) {
                    val batch = words.subList(i, minOf(i + batchSize, words.size)).map { it.copy(wordbookId = wbId) }
                    insertWords(batch)
                    inserted += batch.size
                }
                logs.add("INSERT $inserted words for $name")
                Log.d(TAG, "Inserted $inserted words for $name")
                totalLoaded++

            } catch (e: Exception) {
                val msg = "FAIL $name: ${e.javaClass.simpleName}: ${e.message}"
                logs.add(msg)
                Log.e(TAG, msg, e)
            }
        }

        // 自动激活第一个
        try {
            val active = getActiveWordbook()
            if (active == null) {
                val all = getAllWordbooks()
                if (all.isNotEmpty()) {
                    setActiveWordbook(all.first().id)
                    logs.add("ACTIVATED: ${all.first().name}")
                } else {
                    logs.add("WARN: no wordbooks to activate!")
                }
            }
        } catch (e: Exception) {
            logs.add("ACTIVATE FAIL: ${e.message}")
        }

        logs.add("DONE: $totalLoaded wordbooks loaded")
        Log.d(TAG, logs.joinToString("\n"))
        return Pair(logs, totalLoaded)
    }

    /**
     * 直接解析JSON — 不依赖 WordImporter
     * 支持扁平数组 [{"word":"...", "meaningCn":"..."}] 和包装对象 {"words":[...]}
     */
    private fun parseWordJson(content: String): List<Word> {
        val trimmed = content.trim()
        val arr: JSONArray

        try {
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                arr = if (root.has("words")) root.getJSONArray("words") else return emptyList()
            } else if (trimmed.startsWith("[")) {
                arr = JSONArray(trimmed)
            } else {
                Log.w(TAG, "JSON starts with unexpected char: ${trimmed.firstOrNull()}")
                return emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error: ${e.message}")
            return emptyList()
        }

        val words = mutableListOf<Word>()
        // wordbookId 设为0，外部插入时会设置正确的ID
        // 不行，Entity需要wordbookId。用一个临时值-1
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val spelling = obj.getString("word").trim()
                if (spelling.isBlank()) continue

                // meaning: 优先 meaningCn → 回退 meaning
                val meaning = if (obj.has("meaningCn") && !obj.optString("meaningCn").isNullOrBlank()) {
                    obj.getString("meaningCn")
                } else {
                    obj.getString("meaning")
                }.trim()

                // phonetic
                val phonetic = obj.optString("phonetic", null).ifBlank { null }?.trim()

                // partOfSpeech
                val partOfSpeech = obj.optString("partOfSpeech", null).ifBlank { null }?.trim()

                // exampleSentence: 优先 exampleEn → 回退 exampleSentence
                val exampleSentence = if (obj.has("exampleEn") && !obj.optString("exampleEn").isNullOrBlank()) {
                    obj.getString("exampleEn")
                } else {
                    obj.optString("exampleSentence", null).ifBlank { null }
                }?.trim()

                // exampleTranslation: 优先 exampleCn → 回退 exampleTranslation
                val exampleTranslation = if (obj.has("exampleCn") && !obj.optString("exampleCn").isNullOrBlank()) {
                    obj.getString("exampleCn")
                } else {
                    obj.optString("exampleTranslation", null).ifBlank { null }
                }?.trim()

                // rootAffix
                val rootAffix = obj.optString("rootAffix", null).ifBlank { null }?.trim()

                // synonyms: array → comma string
                val synonyms = arrToStr(obj.optJSONArray("synonyms"))

                // antonyms: array → comma string
                val antonyms = arrToStr(obj.optJSONArray("antonyms"))

                // confusableWords: array → comma string
                val confusableWords = arrToStr(obj.optJSONArray("confusableWords"))

                words.add(Word(
                    spelling = spelling,
                    meaning = meaning,
                    phonetic = phonetic,
                    partOfSpeech = partOfSpeech,
                    exampleSentence = exampleSentence,
                    exampleTranslation = exampleTranslation,
                    rootAffix = rootAffix,
                    synonyms = synonyms,
                    antonyms = antonyms,
                    confusableWords = confusableWords,
                    wordbookId = -1L  // 临时值，在插入前替换
                ))
            } catch (e: Exception) {
                Log.w(TAG, "Skip word[$i]: ${e.message}")
            }
        }
        return words
    }

    private fun arrToStr(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        val parts = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            parts.add(arr.getString(i))
        }
        return parts.joinToString(",")
    }
}
