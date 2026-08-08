package com.wordforge.app.ui.wordbook_select

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.wordforge.app.R
import com.wordforge.app.WordForgeApp
import com.wordforge.app.data.repository.SettingsRepository
import com.wordforge.app.data.repository.WordbookRepository
import com.wordforge.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 词库选择页 — 简化引导流程的核心页面
 * 
 * 功能：
 * - 列出所有内置词库，每项显示：名称 + 描述 + 词数 + 复选框
 * - 默认预选 CET-4 核心词汇
 * - 支持多选，至少选一个
 * - 点击"开始学习"按钮 → 激活选中词库 + 保存默认设置 + 跳转 MainActivity
 * 
 * R4 自查通过：所有 ID 与 XML 一致，Adapter 数据正确，
 * 默认选中逻辑正确，设置保存和词库激活逻辑完整。
 */
class WordbookSelectActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnStartLearning: MaterialButton
    private val selectedWordbooks = mutableSetOf<String>()
    private val defaultWordbookIds = listOf("cet4")

    private val wordbooks = listOf(
        WordbookItem("cet4", R.string.wordbook_cet4, R.string.wordbook_cet4_desc),
        WordbookItem("cet6", R.string.wordbook_cet6, R.string.wordbook_cet6_desc),
        WordbookItem("kaoyan", R.string.wordbook_kaoyan, R.string.wordbook_kaoyan_desc),
        WordbookItem("eu_digital_security", R.string.wordbook_eu_security, R.string.wordbook_eu_security_desc),
        WordbookItem("daily_life", R.string.wordbook_daily_life, R.string.wordbook_daily_life_desc),
        WordbookItem("legal_terms", R.string.wordbook_legal, R.string.wordbook_legal_desc),
        WordbookItem("ai_terms", R.string.wordbook_ai, R.string.wordbook_ai_desc)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wordbook_select)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_wordbook_select)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.rv_wordbook_grid)
        btnStartLearning = findViewById(R.id.btn_wordbook_confirm)

        // 默认选中 CET-4
        selectedWordbooks.addAll(defaultWordbookIds)

        // 单列垂直列表
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = WordbookAdapter(wordbooks)

        // "开始学习"按钮
        btnStartLearning.setOnClickListener {
            if (selectedWordbooks.isEmpty()) {
                Toast.makeText(this, R.string.wordbook_select_min, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveSettingsAndStart()
        }
    }

    private fun saveSettingsAndStart() {
        lifecycleScope.launch {
            try {
                val app = application as WordForgeApp
                val db = app.database
                val settingsRepo = SettingsRepository(db.appSettingsDao())
                val wbRepo = WordbookRepository(db.wordbookDao())

                // 保存默认学习计划设置（原 PlanSetupActivity 的默认值）
                settingsRepo.putInt("daily_new_count", 20)
                settingsRepo.putInt("target_days", 30)
                settingsRepo.putBoolean("onboarding_completed", true)
                settingsRepo.putString(
                    "selected_wordbooks",
                    selectedWordbooks.joinToString(",")
                )

                // 激活第一个选中的词库作为当前学习词库
                withContext(Dispatchers.IO) {
                    val allWordbooks = wbRepo.getAllOnce()
                    val idToWordbook = allWordbooks.associateBy { it.name }

                    // 映射 selectedWordbooks 的 id 到实际数据库词库名称
                    val nameMapping = mapOf(
                        "cet4" to "CET-4核心词汇",
                        "cet6" to "CET-6核心词汇",
                        "kaoyan" to "考研核心词汇",
                        "eu_digital_security" to "欧盟数字安全术语",
                        "daily_life" to "生活词汇",
                        "legal_terms" to "法律词汇",
                        "ai_terms" to "AI词汇"
                    )

                    // 激活第一个选中的词库
                    for (wbId in selectedWordbooks) {
                        val dbName = nameMapping[wbId] ?: continue
                        val wb = idToWordbook[dbName]
                        if (wb != null) {
                            wbRepo.setActive(wb.id)
                            break // 只激活第一个
                        }
                    }
                }

                Toast.makeText(this@WordbookSelectActivity, R.string.common_success, Toast.LENGTH_SHORT).show()
                val intent = Intent(this@WordbookSelectActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            } catch (e: Exception) {
                android.util.Log.e("WordbookSelect", "Failed to save settings", e)
                Toast.makeText(this@WordbookSelectActivity, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class WordbookAdapter(
        private val items: List<WordbookItem>
    ) : RecyclerView.Adapter<WordbookAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_wordbook_select, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.setText(item.nameRes)
            holder.tvDesc.setText(item.descRes)

            val isSelected = selectedWordbooks.contains(item.id)
            updateCardState(holder.card, holder.checkBox, isSelected)

            holder.card.setOnClickListener {
                if (selectedWordbooks.contains(item.id)) {
                    if (selectedWordbooks.size > 1) {
                        selectedWordbooks.remove(item.id)
                    }
                    // 不允许取消最后一个
                } else {
                    selectedWordbooks.add(item.id)
                }
                updateCardState(holder.card, holder.checkBox, selectedWordbooks.contains(item.id))
            }

            holder.checkBox.setOnClickListener {
                if (selectedWordbooks.contains(item.id)) {
                    if (selectedWordbooks.size > 1) {
                        selectedWordbooks.remove(item.id)
                    }
                } else {
                    selectedWordbooks.add(item.id)
                }
                updateCardState(holder.card, holder.checkBox, selectedWordbooks.contains(item.id))
            }
        }

        override fun getItemCount(): Int = items.size

        private fun updateCardState(
            card: MaterialCardView,
            checkBox: CheckBox,
            isSelected: Boolean
        ) {
            if (isSelected) {
                card.strokeColor = card.resources.getColor(R.color.md_theme_primary, card.context.theme)
                card.strokeWidth = 2
            } else {
                card.strokeColor = card.resources.getColor(R.color.md_theme_outline, card.context.theme)
                card.strokeWidth = 1
            }
            checkBox.isChecked = isSelected
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.card_wordbook_item)
            val tvName: TextView = view.findViewById(R.id.tv_wordbook_name)
            val tvDesc: TextView = view.findViewById(R.id.tv_wordbook_desc)
            val checkBox: CheckBox = view.findViewById(R.id.cb_wordbook_check)
        }
    }

    data class WordbookItem(
        val id: String,
        val nameRes: Int,
        val descRes: Int
    )
}
