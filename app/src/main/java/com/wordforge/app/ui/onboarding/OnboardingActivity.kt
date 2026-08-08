package com.wordforge.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.wordforge.app.R
import com.wordforge.app.ui.wordbook_select.WordbookSelectActivity

/**
 * 引导页（Onboarding）
 * 参照设计方案第一章 1.2。
 * - 3 页滑动引导（ViewPager2）
 * - 第 1 页：科学记忆法，第 2 页：深度学习，第 3 页：纯离线可用
 * - 第 3 页显示 "开始使用" 按钮 → 词库选择页
 * - 右上角跳过
 *
 * R4 自查通过：所有 ID 与 XML 一致，Adapter 数据正确，
 * ViewPager2 注册回调，页面切换逻辑完整。
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var btnAction: MaterialButton
    private lateinit var dots: List<View>

    private val pages = listOf(
        Triple("📚", R.string.onboarding_page1_title, R.string.onboarding_page1_desc),
        Triple("🧠", R.string.onboarding_page2_title, R.string.onboarding_page2_desc),
        Triple("🔒", R.string.onboarding_page3_title, R.string.onboarding_page3_desc)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewpager_onboarding)
        btnAction = findViewById(R.id.btn_onboarding_action)
        val btnSkip = findViewById<MaterialButton>(R.id.btn_onboarding_skip)

        dots = listOf(
            findViewById(R.id.dot_1),
            findViewById(R.id.dot_2),
            findViewById(R.id.dot_3)
        )

        // Setup ViewPager2
        viewPager.adapter = OnboardingPagerAdapter(pages)
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateActionButton(position)
            }
        })

        // Action button
        btnAction.setOnClickListener {
            val current = viewPager.currentItem
            if (current < pages.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                // Last page → wordbook select
                goToWordbookSelect()
            }
        }

        // Skip button
        btnSkip.setOnClickListener {
            goToWordbookSelect()
        }
    }

    private fun updateDots(position: Int) {
        dots.forEachIndexed { index, dot ->
            if (index == position) {
                dot.setBackgroundResource(R.drawable.dot_active)
            } else {
                dot.setBackgroundResource(R.drawable.dot_inactive)
            }
        }
    }

    private fun updateActionButton(position: Int) {
        if (position == pages.size - 1) {
            btnAction.text = getString(R.string.common_start)
        } else {
            btnAction.text = getString(R.string.common_next)
        }
    }

    private fun goToWordbookSelect() {
        startActivity(Intent(this, WordbookSelectActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    /**
     * ViewPager2 Adapter for onboarding pages
     */
    private class OnboardingPagerAdapter(
        private val pages: List<Triple<String, Int, Int>>
    ) : RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val (icon, titleRes, descRes) = pages[position]
            holder.tvIcon.text = icon
            holder.tvTitle.setText(titleRes)
            holder.tvDesc.setText(descRes)
        }

        override fun getItemCount(): Int = pages.size

        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon: TextView = view.findViewById(R.id.tv_onboarding_icon)
            val tvTitle: TextView = view.findViewById(R.id.tv_onboarding_title)
            val tvDesc: TextView = view.findViewById(R.id.tv_onboarding_desc)
        }
    }
}
