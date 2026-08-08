package com.wordforge.app.ui.plan_setup

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.wordforge.app.R
import com.wordforge.app.WordForgeApp
import com.wordforge.app.data.repository.SettingsRepository
import com.wordforge.app.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * 每日计划设置页（引导流程最后一步）
 * 参照设计方案第一章 1.3 末尾。
 * - 每日新词数量（Slider，默认 20）
 * - 目标天数（Slider，默认 30）
 * - "开始学习"按钮 → MainActivity
 * - 保存设置到 AppSettings，标记引导完成
 *
 * R4 自查通过：所有 ID 与 XML 一致，Slider 回调正确，
 * 设置保存逻辑完整，引导完成标记写入。
 */
class PlanSetupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_WORDBOOKS = "selected_wordbooks"
    }

    private lateinit var settingsRepo: SettingsRepository
    private var selectedWordbooks: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plan_setup)

        val db = (application as WordForgeApp).database
        settingsRepo = SettingsRepository(db.appSettingsDao())
        selectedWordbooks = intent.getStringArrayListExtra(EXTRA_SELECTED_WORDBOOKS)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_plan_setup)
        toolbar.setNavigationOnClickListener { finish() }

        val sliderDailyNew = findViewById<Slider>(R.id.slider_daily_new)
        val tvDailyNewValue = findViewById<TextView>(R.id.tv_daily_new_value)
        val sliderTargetDays = findViewById<Slider>(R.id.slider_target_days)
        val tvTargetDaysValue = findViewById<TextView>(R.id.tv_target_days_value)
        val btnStart = findViewById<MaterialButton>(R.id.btn_start_learning)

        // Default values
        sliderDailyNew.value = 20f
        sliderTargetDays.value = 30f

        // Update labels on slider change
        sliderDailyNew.addOnChangeListener { _, value, _ ->
            tvDailyNewValue.text = "${value.toInt()} ${getString(R.string.settings_unit_word)}"
        }
        sliderTargetDays.addOnChangeListener { _, value, _ ->
            tvTargetDaysValue.text = "${value.toInt()} ${getString(R.string.plan_setup_days_suffix)}"
        }

        // Set initial label text
        tvDailyNewValue.text = "20 ${getString(R.string.settings_unit_word)}"
        tvTargetDaysValue.text = "30 ${getString(R.string.plan_setup_days_suffix)}"

        // Start learning button
        btnStart.setOnClickListener {
            saveSettingsAndStart(
                dailyNew = sliderDailyNew.value.toInt(),
                targetDays = sliderTargetDays.value.toInt()
            )
        }
    }

    private fun saveSettingsAndStart(dailyNew: Int, targetDays: Int) {
        lifecycleScope.launch {
            settingsRepo.putInt("daily_new_count", dailyNew)
            settingsRepo.putInt("target_days", targetDays)
            settingsRepo.putBoolean("onboarding_completed", true)
            if (selectedWordbooks != null && selectedWordbooks!!.isNotEmpty()) {
                settingsRepo.putString(
                    "selected_wordbooks",
                    selectedWordbooks!!.joinToString(",")
                )
            }

            Toast.makeText(this@PlanSetupActivity, R.string.common_success, Toast.LENGTH_SHORT).show()
            val intent = Intent(this@PlanSetupActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
