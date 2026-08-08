package com.wordforge.app.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wordforge.app.R
import com.wordforge.app.WordForgeApp
import com.wordforge.app.data.repository.SettingsRepository
import com.wordforge.app.data.repository.WordbookRepository
import com.wordforge.app.ui.MainActivity
import com.wordforge.app.ui.wordbook_select.WordbookSelectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashInit"
        private const val PREFS_NAME = "wordforge_init"
        private const val KEY_INIT_CHECK = "init_check"

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

    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ivLogo = findViewById<ImageView>(R.id.iv_splash_logo)
        val tvBrand = findViewById<TextView>(R.id.tv_splash_brand)
        val tvSlogan = findViewById<TextView>(R.id.tv_splash_slogan)
        val progressBar = findViewById<ProgressBar>(R.id.progress_splash)
        val tvStatus = findViewById<TextView>(R.id.tv_splash_status)

        val scaleX = ObjectAnimator.ofFloat(ivLogo, View.SCALE_X, 0.8f, 1.0f).apply { duration = 600 }
        val scaleY = ObjectAnimator.ofFloat(ivLogo, View.SCALE_Y, 0.8f, 1.0f).apply { duration = 600 }
        AnimatorSet().apply { playTogether(scaleX, scaleY) }.start()

        Handler(Looper.getMainLooper()).postDelayed({
            tvBrand.alpha = 0f; tvBrand.translationY = 16f
            tvBrand.animate().alpha(1f).translationY(0f).setDuration(400).start()
            tvSlogan.alpha = 0f; tvSlogan.translationY = 16f
            tvSlogan.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(200).start()
        }, 300)

        lifecycleScope.launch {
            runOnUiThread {
                tvStatus.text = "正在初始化词库..."
                progressBar.visibility = View.VISIBLE
            }
            val result = initializeWordbooks(tvStatus)
            writeInitCheckResult(result)
            runOnUiThread {
                tvStatus.text = if (result.wordbookCount > 0)
                    "词库完成: ${result.wordbookCount}词库 ${result.wordCount}词"
                else
                    "初始化异常: ${result.error ?: "unknown"}"
            }
            Log.d(TAG, "Final: wb=${result.wordbookCount}, words=${result.wordCount}")
            Handler(Looper.getMainLooper()).postDelayed({ navigateToNext() }, 1500)
        }
    }

    private data class InitResult(
        val wordbookCount: Int, val wordCount: Int,
        val success: Boolean, val error: String? = null
    )

    private suspend fun initializeWordbooks(tvStatus: TextView): InitResult {
        return withContext(Dispatchers.IO) {
            try {
                val app = application as WordForgeApp
                val roomDb = app.database
                val db: SupportSQLiteDatabase = roomDb.openHelper.writableDatabase

                val c1 = db.query("SELECT COUNT(*) FROM wordbook")
                var existingCount = 0
                if (c1.moveToFirst()) existingCount = c1.getInt(0)
                c1.close()
                Log.d(TAG, "Existing wordbooks: $existingCount")

                if (existingCount > 0) {
                    val c2 = db.query("SELECT COUNT(*) FROM word")
                    var existingWords = 0
                    if (c2.moveToFirst()) existingWords = c2.getInt(0)
                    c2.close()
                    if (existingWords > 0) {
                        Log.d(TAG, "Data exists, skip")
                        return@withContext InitResult(existingCount, existingWords, true)
                    }
                    Log.d(TAG, "0 words, cleaning")
                    db.delete("word", null, null)
                    db.delete("wordbook", null, null)
                }

                var totalWb = 0
                var totalWords = 0

                for ((id, meta) in WORDBOOK_META) {
                    val name = meta[0] as String
                    val desc = meta[1] as String
                    val expectedCount = meta[2] as Int

                    try {
                        runOnUiThread { tvStatus.text = "加载 $name..." }

                        val content = try {
                            assets.open("wordbooks/$id.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                        } catch (e: Exception) {
                            Log.e(TAG, "Cannot open: ${e.message}")
                            continue
                        }
                        Log.d(TAG, "READ $name: ${content.length} chars")

                        val arr = parseJsonArray(content)
                        if (arr == null || arr.length() == 0) {
                            Log.w(TAG, "PARSE FAIL $name")
                            continue
                        }
                        Log.d(TAG, "PARSED $name: ${arr.length()} words")

                        val wbCv = android.content.ContentValues().apply {
                            put("name", name)
                            put("description", desc)
                            put("totalWords", expectedCount)
                            put("type", "builtin")
                            put("isActive", 0)
                            put("createdAt", System.currentTimeMillis())
                        }
                        val wbId = db.insert("wordbook", 0, wbCv)
                        Log.d(TAG, "INSERT wordbook $name -> id=$wbId")
                        if (wbId < 0) {
                            Log.e(TAG, "Failed insert wordbook $name")
                            continue
                        }

                        var inserted = 0
                        db.beginTransaction()
                        try {
                            for (i in 0 until arr.length()) {
                                try {
                                    val obj = arr.getJSONObject(i)
                                    val spelling = obj.getString("word").trim()
                                    if (spelling.isBlank()) continue
                                    val meaning = if (obj.has("meaningCn"))
                                        obj.optString("meaningCn", "").trim()
                                    else
                                        obj.optString("meaning", "").trim()
                                    val wordCv = android.content.ContentValues().apply {
                                        put("spelling", spelling)
                                        put("meaning", meaning)
                                        putOpt("phonetic", obj.optString("phonetic", ""))
                                        putOpt("partOfSpeech", obj.optString("partOfSpeech", ""))
                                        putOpt("exampleSentence",
                                            if (obj.has("exampleEn")) obj.optString("exampleEn", "") else obj.optString("exampleSentence", ""))
                                        putOpt("exampleTranslation",
                                            if (obj.has("exampleCn")) obj.optString("exampleCn", "") else obj.optString("exampleTranslation", ""))
                                        putOpt("rootAffix", obj.optString("rootAffix", ""))
                                        putOpt("synonyms", jsonArrayToCsv(obj.optJSONArray("synonyms")))
                                        putOpt("antonyms", jsonArrayToCsv(obj.optJSONArray("antonyms")))
                                        putOpt("confusableWords", jsonArrayToCsv(obj.optJSONArray("confusableWords")))
                                        put("wordbookId", wbId)
                                    }
                                    db.insert("word", 0, wordCv)
                                    inserted++
                                } catch (e: Exception) {
                                    Log.w(TAG, "Skip word[$i]: ${e.message}")
                                }
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        Log.d(TAG, "INSERTED $inserted words for $name")
                        totalWb++
                        totalWords += inserted
                    } catch (e: Exception) {
                        Log.e(TAG, "FAIL $name: ${e.message}")
                    }
                }

                if (totalWb > 0) {
                    try {
                        db.execSQL("UPDATE wordbook SET isActive = 0")
                        db.execSQL("UPDATE wordbook SET isActive = 1 WHERE id = (SELECT MIN(id) FROM wordbook)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-activate fail: ${e.message}")
                    }
                }

                Log.d(TAG, "DONE: $totalWb wordbooks, $totalWords words")
                InitResult(totalWb, totalWords, true)
            } catch (e: Exception) {
                Log.e(TAG, "INIT FAILED", e)
                runOnUiThread { tvStatus.text = "失败: ${e.message}" }
                InitResult(0, 0, false, error = e.message)
            }
        }
    }

    private fun android.content.ContentValues.putOpt(key: String, value: String?) {
        if (value.isNullOrBlank()) putNull(key) else put(key, value)
    }

    private fun parseJsonArray(content: String): JSONArray? {
        val t = content.trim()
        return try {
            when {
                t.startsWith("[") -> JSONArray(t)
                t.startsWith("{") -> {
                    val r = JSONObject(t)
                    if (r.has("words")) r.getJSONArray("words") else null
                }
                else -> null
            }
        } catch (e: Exception) { Log.e(TAG, "JSON: ${e.message}"); null }
    }

    private fun jsonArrayToCsv(arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        val p = mutableListOf<String>()
        for (i in 0 until arr.length()) { val s = arr.optString(i, "").trim(); if (s.isNotBlank()) p.add(s) }
        return if (p.isEmpty()) null else p.joinToString(",")
    }

    private suspend fun writeInitCheckResult(result: InitResult) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_INIT_CHECK,
                    "wbCount=${result.wordbookCount},wordCount=${result.wordCount},success=${result.success}" +
                    (if (result.error != null) ",error=${result.error}" else "")).apply()
            } catch (_: Exception) {}
        }
    }

    private fun navigateToNext() {
        if (navigated) return
        navigated = true
        lifecycleScope.launch {
            val db = (application as WordForgeApp).database
            val settingsRepo = SettingsRepository(db.appSettingsDao())
            val wbRepo = WordbookRepository(db.wordbookDao())
            val onboardingCompleted = settingsRepo.getBoolean("onboarding_completed", false)
            val activeWordbook = wbRepo.getActiveWordbook()
            val intent = if (onboardingCompleted && activeWordbook != null)
                Intent(this@SplashActivity, MainActivity::class.java)
            else
                Intent(this@SplashActivity, WordbookSelectActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
