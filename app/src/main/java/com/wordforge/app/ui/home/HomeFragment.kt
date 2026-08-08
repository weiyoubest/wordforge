package com.wordforge.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.wordforge.app.R
import com.wordforge.app.databinding.FragmentHomeBinding

/**
 * 首页 Fragment（T08）
 * 展示今日任务卡片（渐变背景）、快速操作圆形按钮、本周打卡（勾号/空心圆）、
 * 学习统计2x2网格、每日一句（半透明卡片）
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    // 每日一句数据集
    private val quotes = arrayOf(
        Pair("The only way to do great work is to love what you do.", "Steve Jobs"),
        Pair("Success is not final, failure is not fatal.", "Winston Churchill"),
        Pair("Believe you can and you're halfway there.", "Theodore Roosevelt"),
        Pair("The future belongs to those who believe in the beauty of their dreams.", "Eleanor Roosevelt"),
        Pair("It does not matter how slowly you go as long as you do not stop.", "Confucius"),
        Pair("The best time to plant a tree was 20 years ago. The second best time is now.", "Chinese Proverb"),
        Pair("Education is the most powerful weapon which you can use to change the world.", "Nelson Mandela")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSwipeRefresh()
        setupSearchBar()
        setupQuickActions()
        setupDailyQuote()
        setupWeekDays()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupSearchBar() {
        binding.searchBar.setOnClickListener {
            Toast.makeText(requireContext(), "搜索功能开发中", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuickActions() {
        // 开始学习
        binding.btnStartLearn.setOnClickListener {
            findNavController().navigate(R.id.navigation_learn)
        }

        // 开始复习
        binding.btnStartReview.setOnClickListener {
            findNavController().navigate(R.id.navigation_review)
        }

        // 错词复习
        binding.btnMistakeReview.setOnClickListener {
            findNavController().navigate(R.id.navigation_mistake)
        }
    }

    private fun setupDailyQuote() {
        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        val quote = quotes[dayOfYear % quotes.size]
        binding.tvDailyQuote.text = quote.first
        binding.tvQuoteAuthor.text = "— ${quote.second}"
    }

    private fun setupWeekDays() {
        val dayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
        binding.weeklyDays.removeAllViews()
        dayNames.forEachIndexed { index, name ->
            val dayView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_week_day, binding.weeklyDays, false)
            val tvDay = dayView.findViewById<TextView>(R.id.tv_day_name)
            val ivCheckin = dayView.findViewById<ImageView>(R.id.iv_checkin_dot)
            tvDay.text = name
            // 默认设置为未打卡空心圆
            ivCheckin.setImageResource(R.drawable.ic_checkin_empty)
            dayView.tag = index
            binding.weeklyDays.addView(dayView)
        }
    }

    private fun observeViewModel() {
        viewModel.activeWordbook.observe(viewLifecycleOwner) { wordbook ->
            if (wordbook == null) {
                showEmptyNoWordbook()
            } else {
                hideEmptyStates()
            }
        }

        viewModel.newWordsProgress.observe(viewLifecycleOwner) { (learned, target) ->
            binding.tvNewProgress.text = "${learned}/${target}"
            val progress = if (target > 0) (learned.toFloat() / target.toFloat()) else 0f
            binding.progressRingNew.progress = (progress * 100).toInt()
        }

        viewModel.reviewProgress.observe(viewLifecycleOwner) { (reviewed, due) ->
            binding.tvReviewProgress.text = "${reviewed}/${due}"
        }

        viewModel.streakDays.observe(viewLifecycleOwner) { streak ->
            binding.tvStreakDays.text = "${streak}天"
        }

        viewModel.weeklyCheckins.observe(viewLifecycleOwner) { checkins ->
            for (i in 0 until binding.weeklyDays.childCount) {
                val dayView = binding.weeklyDays.getChildAt(i)
                val ivCheckin = dayView.findViewById<ImageView>(R.id.iv_checkin_dot)
                val isChecked = if (i < checkins.size) checkins[i] else false
                ivCheckin.setImageResource(
                    if (isChecked) R.drawable.ic_checkin_done else R.drawable.ic_checkin_empty
                )
            }
        }

        viewModel.allComplete.observe(viewLifecycleOwner) { complete ->
            if (complete) {
                showAllDone()
            } else {
                binding.layoutEmptyAllDone.visibility = View.GONE
            }
        }

        // 学习统计
        viewModel.studyStats.observe(viewLifecycleOwner) { stats ->
            binding.tvStatDays.text = "${stats.totalDays}"
            binding.tvStatTotalWords.text = "${stats.totalWords}"
            binding.tvStatToday.text = "${stats.todayWords}"
            binding.tvStatStreak.text = "${stats.streakDays}天"
        }
    }

    // runDiagnostics() removed — wordbook initialization is handled in MainActivity.
    // Running heavy DB initialization + Toast on every Fragment view creation caused UI freeze.


    private fun showEmptyNoWordbook() {
        binding.cardTodayTask.visibility = View.GONE
        binding.btnStartLearn.visibility = View.GONE
        binding.btnStartReview.visibility = View.GONE
        binding.btnMistakeReview.visibility = View.GONE
        binding.cardWeekly.visibility = View.GONE
        binding.cardStudyStats.visibility = View.GONE
        binding.cardStudyStatsRow2.visibility = View.GONE
        binding.layoutEmptyNoWordbook.visibility = View.VISIBLE
        binding.layoutEmptyAllDone.visibility = View.GONE
        binding.btnGoWordbook.setOnClickListener {
            findNavController().navigate(R.id.navigation_profile)
        }
    }

    private fun showAllDone() {
        binding.layoutEmptyAllDone.visibility = View.VISIBLE
    }

    private fun hideEmptyStates() {
        binding.cardTodayTask.visibility = View.VISIBLE
        binding.btnStartLearn.visibility = View.VISIBLE
        binding.btnStartReview.visibility = View.VISIBLE
        binding.btnMistakeReview.visibility = View.VISIBLE
        binding.cardWeekly.visibility = View.VISIBLE
        binding.cardStudyStats.visibility = View.VISIBLE
        binding.cardStudyStatsRow2.visibility = View.VISIBLE
        binding.layoutEmptyNoWordbook.visibility = View.GONE
        binding.layoutEmptyAllDone.visibility = View.GONE
    }
}
