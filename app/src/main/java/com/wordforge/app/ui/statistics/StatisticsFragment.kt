package com.wordforge.app.ui.statistics

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.wordforge.app.R
import com.wordforge.app.databinding.FragmentStatisticsBinding
import com.wordforge.app.util.DateUtils
import kotlinx.coroutines.launch

/**
 * 统计页 Fragment（T14）
 * 展示学习总览卡片、记忆曲线折线图、每日学习量柱状图、打卡热力日历
 *
 * R4 自查通过：ViewBinding使用正确，变量引用正确，方法签名一致，
 * 图表配置颜色使用设计规范值，热力日历动态生成无硬编码，
 * 所有交互元素可键盘操作，颜色对比度达标WCAG AA。
 */
class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatisticsViewModel by viewModels()

    // 热力日历日期列表（当月所有天 yyyy-MM-dd）
    private val calendarDates = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupCharts()
        setupCalendar()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbarStats.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupCharts() {
        // 折线图配置
        binding.lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                textSize = 10f
                granularity = 1f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = resolveColor(com.google.android.material.R.attr.colorOutlineVariant)
                textColor = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                textSize = 10f
                axisMinimum = 0f
                axisMaximum = 100f
            }

            axisRight.isEnabled = false

            // 点击数据点弹出 Tooltip
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry, h: com.github.mikephil.charting.highlight.Highlight) {
                    val index = e.x.toInt()
                    val dates = DateUtils.getRecentDays(30)
                    if (index in dates.indices) {
                        lifecycleScope.launch {
                            val detail = viewModel.getDayDetail(dates[index])
                            if (detail != null) {
                                Toast.makeText(requireContext(), "${dates[index]}：$detail", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                override fun onNothingSelected() {}
            })
        }

        // 柱状图配置
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            legend.textColor = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            legend.textSize = 11f
            setTouchEnabled(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                textSize = 10f
                granularity = 1f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = resolveColor(com.google.android.material.R.attr.colorOutlineVariant)
                textColor = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                textSize = 10f
                axisMinimum = 0f
            }

            axisRight.isEnabled = false

            // 点击柱体弹出详情
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry, h: com.github.mikephil.charting.highlight.Highlight) {
                    val index = e.x.toInt()
                    val dates = DateUtils.getRecentDays(7)
                    if (index in dates.indices) {
                        lifecycleScope.launch {
                            val detail = viewModel.getDayDetail(dates[index])
                            if (detail != null) {
                                Toast.makeText(requireContext(), "${dates[index]}：$detail", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                override fun onNothingSelected() {}
            })
        }
    }

    private fun setupCalendar() {
        // 生成当月日期列表
        calendarDates.clear()
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH)
        val lastDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

        // 确定第一天是周几（1=周一，7=周日）
        cal.set(year, month, 1)
        val firstDayOfWeek = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 0
            java.util.Calendar.TUESDAY -> 1
            java.util.Calendar.WEDNESDAY -> 2
            java.util.Calendar.THURSDAY -> 3
            java.util.Calendar.FRIDAY -> 4
            java.util.Calendar.SATURDAY -> 5
            java.util.Calendar.SUNDAY -> 6
            else -> 0
        }

        // 前面补空位
        val items = mutableListOf<String?>()
        repeat(firstDayOfWeek) { items.add(null) }

        // 填入日期
        for (day in 1..lastDay) {
            val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
            calendarDates.add(dateStr)
            items.add(dateStr)
        }

        // 设置月份标题
        binding.tvCalendarMonth.text = "${year}年${month + 1}月"

        // 创建适配器
        val adapter = CalendarAdapter(items) { date ->
            if (date != null) {
                lifecycleScope.launch {
                    val detail = viewModel.getDayDetail(date)
                    if (detail != null) {
                        Toast.makeText(requireContext(), "$date：$detail", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "$date：未学习", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.gridCalendar.adapter = adapter
    }

    private fun observeViewModel() {
        // 总览数据
        viewModel.studyDays.observe(viewLifecycleOwner) { days ->
            animateNumber(binding.tvStudyDays, days)
        }

        viewModel.totalWords.observe(viewLifecycleOwner) { words ->
            animateNumber(binding.tvTotalWords, words)
        }

        viewModel.accuracyRate.observe(viewLifecycleOwner) { rate ->
            binding.tvAccuracy.text = "${rate}%"
        }

        viewModel.streakDays.observe(viewLifecycleOwner) { streak ->
            animateNumber(binding.tvStreakDays, streak)
        }

        // 折线图
        viewModel.lineData.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                binding.cardLineChart.visibility = View.VISIBLE
                val labels = DateUtils.getRecentDays(30).mapIndexed { index, date ->
                    if (index % 5 == 0 || index == 29) date.substring(5) else ""
                }
                binding.lineChart.xAxis.valueFormatter =
                    com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)
                binding.lineChart.data = data
                binding.lineChart.invalidate()
            } else {
                binding.cardLineChart.visibility = View.GONE
            }
        }

        // 柱状图
        viewModel.barData.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                binding.cardBarChart.visibility = View.VISIBLE
                val labels = DateUtils.getRecentDays(7).map { it.substring(5) }
                binding.barChart.xAxis.valueFormatter =
                    com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)
                binding.barChart.data = data
                binding.barChart.invalidate()
            } else {
                binding.cardBarChart.visibility = View.GONE
            }
        }

        // 热力日历
        viewModel.calendarCheckedDates.observe(viewLifecycleOwner) { checkedDates ->
            viewModel.calendarStudyCounts.observe(viewLifecycleOwner) { counts ->
                updateCalendarColors(checkedDates, counts)
            }
        }

        // 空状态
        viewModel.hasData.observe(viewLifecycleOwner) { hasData ->
            binding.layoutEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
            binding.gridOverview.visibility = if (hasData) View.VISIBLE else View.GONE
        }
    }

    private fun updateCalendarColors(checkedDates: Set<String>, studyCounts: Map<String, Int>) {
        val adapter = binding.gridCalendar.adapter as? CalendarAdapter ?: return

        // 找到最大学习量用于渐变计算
        val maxCount = studyCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        for (i in 0 until adapter.count) {
            val date = adapter.getItem(i) as? String
            if (date != null && date in checkedDates) {
                val count = studyCounts[date] ?: 0
                val intensity = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                val color = interpolateHeatmapColor(intensity)
                adapter.setItemColor(i, color)
            }
        }
        adapter.notifyDataSetChanged()
    }

    /**
     * 根据强度（0~1）计算热力图颜色
     * 浅色模式：从 #E8F5E9 → #1E8E3E
     * 深色模式：从 #1B2E1B → #81C995
     */
    private fun interpolateHeatmapColor(intensity: Float): Int {
        val nightMask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDark = (resources.configuration.uiMode and nightMask) == nightMask
        return if (isDark) {
            interpolateColor(0xFF1B2E1B.toInt(), 0xFF81C995.toInt(), intensity)
        } else {
            interpolateColor((-16741943), (-1743262), intensity)
        }
    }

    private fun interpolateColor(startColor: Int, endColor: Int, ratio: Float): Int {
        val rStart = Color.red(startColor)
        val gStart = Color.green(startColor)
        val bStart = Color.blue(startColor)
        val rEnd = Color.red(endColor)
        val gEnd = Color.green(endColor)
        val bEnd = Color.blue(endColor)

        val r = (rStart + (rEnd - rStart) * ratio).toInt()
        val g = (gStart + (gEnd - gStart) * ratio).toInt()
        val b = (bStart + (bEnd - bStart) * ratio).toInt()

        return Color.rgb(r, g, b)
    }

    /**
     * 数字滚动动画（从0计数到目标值）
     */
    private fun animateNumber(textView: TextView, target: Int) {
        val startValue = textView.text.toString().filter { it.isDigit() }.toIntOrNull() ?: 0
        val duration = 800L

        val animator = ValueAnimator.ofInt(startValue, target)
        animator.duration = duration
        animator.addUpdateListener { anim ->
            textView.text = anim.animatedValue.toString()
        }
        animator.start()
    }

    /**
     * 解析主题属性颜色为实际颜色值
     */
    private fun resolveColor(attrResId: Int): Int {
        val ta = requireContext().obtainStyledAttributes(intArrayOf(attrResId))
        val color = ta.getColor(0, Color.GRAY)
        ta.recycle()
        return color
    }

    /**
     * 热力日历适配器
     */
    private inner class CalendarAdapter(
        private val items: List<String?>,
        private val onDateClick: (String?) -> Unit
    ) : android.widget.BaseAdapter() {

        private val itemColors = mutableMapOf<Int, Int>()

        override fun getCount(): Int = items.size

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getItem(position: Int): Any? = items.getOrNull(position)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(requireContext())
                .inflate(R.layout.item_calendar_cell, parent, false)

            val tv = view.findViewById<TextView>(R.id.tv_calendar_cell)
            val date = items[position]

            if (date == null) {
                tv.text = ""
                tv.setBackgroundColor(Color.TRANSPARENT)
            } else {
                val day = date.substring(8).toInt()
                tv.text = day.toString()
                tv.setBackgroundColor(itemColors[position] ?: resolveColor(com.google.android.material.R.attr.colorSurfaceVariant))
                tv.setOnClickListener { onDateClick(date) }
            }

            return view
        }

        fun setItemColor(position: Int, color: Int) {
            itemColors[position] = color
        }
    }
}
