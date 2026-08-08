package com.wordforge.app.data.importer

import com.wordforge.app.data.db.entity.Word
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import org.json.JSONArray
import org.json.JSONObject

/**
 * 词库导入器
 * 支持解析 CSV、JSON、TXT 三种格式的词库文件
 *
 * 文件格式规范：
 * - CSV: 英文,中文释义,音标(可选)
 * - JSON: [{"word":"abandon","meaning":"放弃","phonetic":"əˈbændən"}]
 * - TXT: 每行一个 "英文 中文" 对
 */
class WordImporter {

    /**
     * 导入数据项
     */
    data class WordImportItem(
        val spelling: String,
        val meaning: String,
        val phonetic: String? = null,
        val partOfSpeech: String? = null,
        val exampleSentence: String? = null,
        val exampleTranslation: String? = null,
        val rootAffix: String? = null,
        val synonyms: String? = null,
        val antonyms: String? = null,
        val confusableWords: String? = null
    )

    /**
     * 导入结果
     */
    data class ImportResult(
        val successCount: Int,
        val failCount: Int,
        val skipCount: Int,
        val errors: List<String> = emptyList()
    )

    /**
     * 校验结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val totalCount: Int,
        val errors: List<String> = emptyList()
    )

    /**
     * 文件格式
     */
    enum class Format {
        CSV, JSON, TXT, UNKNOWN
    }

    /**
     * 检测文件格式
     */
    fun detectFormat(fileName: String): Format {
        return when {
            fileName.endsWith(".csv", ignoreCase = true) -> Format.CSV
            fileName.endsWith(".json", ignoreCase = true) -> Format.JSON
            fileName.endsWith(".txt", ignoreCase = true) -> Format.TXT
            else -> Format.UNKNOWN
        }
    }

    /**
     * 解析输入流为单词列表
     */
    fun parse(inputStream: InputStream, format: Format): List<WordImportItem> {
        return when (format) {
            Format.CSV -> parseCSV(inputStream)
            Format.JSON -> parseJSON(inputStream)
            Format.TXT -> parseTXT(inputStream)
            Format.UNKNOWN -> emptyList()
        }
    }

    /**
     * 解析 CSV 格式
     * 格式: 英文,中文释义,音标(可选)
     */
    private fun parseCSV(inputStream: InputStream): List<WordImportItem> {
        val items = mutableListOf<WordImportItem>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEachLine

            val parts = trimmed.split(",").map { it.trim() }
            if (parts.size >= 2) {
                items.add(
                    WordImportItem(
                        spelling = parts[0],
                        meaning = parts[1],
                        phonetic = parts.getOrNull(2)
                    )
                )
            }
        }

        reader.close()
        return items
    }

    /**
     * 解析 JSON 格式
     * 格式: [{"word":"abandon","meaning":"放弃","phonetic":"əˈbændən"}]
     */
    private fun parseJSON(inputStream: InputStream): List<WordImportItem> {
        val items = mutableListOf<WordImportItem>()
        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        // 支持两种格式：
        // 1. 扁平数组: [{"word":"..."}]
        // 2. 包装对象: {"name":"...","words":[{"word":"..."}]}
        val jsonArray: JSONArray
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            if (root.has("words")) {
                jsonArray = root.getJSONArray("words")
            } else {
                // 单个对象，跳过
                return items
            }
        } else {
            jsonArray = JSONArray(trimmed)
        }

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            // meaning: 优先使用 meaningCn，回退到 meaning
            val meaning = if (obj.has("meaningCn") && !obj.optString("meaningCn").isBlank()) {
                obj.getString("meaningCn")
            } else {
                obj.getString("meaning")
            }
            // exampleSentence: 优先使用 exampleEn，回退到 exampleSentence
            val exampleSentence = if (obj.has("exampleEn") && !obj.optString("exampleEn").isBlank()) {
                obj.getString("exampleEn")
            } else {
                obj.optString("exampleSentence", null).ifBlank { null }
            }
            // exampleTranslation: 优先使用 exampleCn，回退到 exampleTranslation
            val exampleTranslation = if (obj.has("exampleCn") && !obj.optString("exampleCn").isBlank()) {
                obj.getString("exampleCn")
            } else {
                obj.optString("exampleTranslation", null).ifBlank { null }
            }
            // synonyms/antonyms/confusableWords: JSON数组转逗号分隔字符串
            val synonyms = jsonArrayToString(obj.optJSONArray("synonyms"))
            val antonyms = jsonArrayToString(obj.optJSONArray("antonyms"))
            val confusableWords = jsonArrayToString(obj.optJSONArray("confusableWords"))

            items.add(
                WordImportItem(
                    spelling = obj.getString("word"),
                    meaning = meaning,
                    phonetic = obj.optString("phonetic", null).ifBlank { null },
                    partOfSpeech = obj.optString("partOfSpeech", null).ifBlank { null },
                    exampleSentence = exampleSentence,
                    exampleTranslation = exampleTranslation,
                    rootAffix = obj.optString("rootAffix", null).ifBlank { null },
                    synonyms = synonyms,
                    antonyms = antonyms,
                    confusableWords = confusableWords
                )
            )
        }

        return items
    }

    /**
     * 解析 TXT 格式
     * 格式: 每行一个 "英文 中文"
     */
    private fun parseTXT(inputStream: InputStream): List<WordImportItem> {
        val items = mutableListOf<WordImportItem>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEachLine

            // 尝试用多种分隔符分割
            val separators = arrayOf("\t", "  ", " ", "，")
            var spelling = ""
            var meaning = ""

            for (sep in separators) {
                val idx = trimmed.indexOf(sep)
                if (idx > 0) {
                    spelling = trimmed.substring(0, idx).trim()
                    meaning = trimmed.substring(idx + sep.length).trim()
                    break
                }
            }

            if (spelling.isNotBlank() && meaning.isNotBlank()) {
                items.add(WordImportItem(spelling = spelling, meaning = meaning))
            }
        }

        reader.close()
        return items
    }

    /**
     * 校验导入数据
     */
    fun validate(items: List<WordImportItem>): ValidationResult {
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var invalidCount = 0

        items.forEachIndexed { index, item ->
            if (item.spelling.isBlank() || item.meaning.isBlank()) {
                errors.add("第${index + 1}行: 单词或释义为空")
                invalidCount++
            } else if (!item.spelling.matches(Regex("^[a-zA-Z\\-'.]+$"))) {
                errors.add("第${index + 1}行: 单词格式不正确 (${item.spelling})")
                invalidCount++
            } else if (seen.contains(item.spelling.lowercase())) {
                errors.add("第${index + 1}行: 重复单词 (${item.spelling})")
                invalidCount++
            } else {
                seen.add(item.spelling.lowercase())
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            totalCount = items.size - invalidCount,
            errors = errors
        )
    }

    /**
     * 预览前 N 个导入项
     */
    fun preview(items: List<WordImportItem>, limit: Int = 10): List<WordImportItem> {
        return items.take(limit)
    }

    /**
     * 将导入项转换为数据库 Entity
     */
    /**
     * JSONArray转逗号分隔字符串，null安全
     */
    private fun jsonArrayToString(array: JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        val parts = mutableListOf<String>()
        for (i in 0 until array.length()) {
            parts.add(array.getString(i))
        }
        return parts.joinToString(",")
    }

    fun toEntities(items: List<WordImportItem>, wordbookId: Long): List<Word> {
        return items.map { item ->
            Word(
                spelling = item.spelling.trim(),
                meaning = item.meaning.trim(),
                phonetic = item.phonetic?.trim(),
                partOfSpeech = item.partOfSpeech?.trim(),
                exampleSentence = item.exampleSentence?.trim(),
                exampleTranslation = item.exampleTranslation?.trim(),
                rootAffix = item.rootAffix?.trim(),
                synonyms = item.synonyms?.trim(),
                antonyms = item.antonyms?.trim(),
                confusableWords = item.confusableWords?.trim(),
                wordbookId = wordbookId
            )
        }
    }
}
