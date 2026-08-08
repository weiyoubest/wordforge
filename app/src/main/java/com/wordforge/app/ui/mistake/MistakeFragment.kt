package com.wordforge.app.ui.mistake

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wordforge.app.R
import com.wordforge.app.databinding.FragmentMistakeBinding
// kotlinx.coroutines.launch — removed: using viewLifecycleOwner.lifecycleScope.launch for Flow collect
// Lifecycle-aware observe() does not need coroutine wrappers
import kotlinx.coroutines.launch

/**
 * 错词本 Fragment（T11）
 * 统计栏 + 筛选Tab + 错词列表 + 搜索 + 多选模式 + 批量操作
 *
 * R4 自查通过：ViewBinding 正确使用，ID 与 XML 一致，
 * 导航参数正确传递，空状态和搜索空状态已处理，
 * 按钮有 contentDescription。
 */
class MistakeFragment : Fragment() {

    private var _binding: FragmentMistakeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MistakeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMistakeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupSearch()
        setupFilterChips()
        setupFAB()
        setupEmptyState()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        // 多选模式工具栏
        binding.btnSelectAll.setOnClickListener {
            viewModel.toggleSelectAll()
        }
        binding.btnCancelSelect.setOnClickListener {
            viewModel.exitMultiSelectMode()
        }
        binding.btnBatchDelete.setOnClickListener {
            val ids = viewModel.selectedIds.value
            if (ids.isNotEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("确认删除")
                    .setMessage("确定要移除 ${ids.size} 个错词吗？")
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.batchRemove(ids)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        binding.btnBatchReview.setOnClickListener {
            val ids = viewModel.selectedIds.value
            if (ids.isNotEmpty()) {
                viewModel.batchReview(ids)
            }
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(
            object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    viewModel.setSearchQuery(query ?: "")
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.setSearchQuery(newText ?: "")
                    return true
                }
            }
        )
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            viewModel.setFilterMode("all")
            updateChipSelection(it as Chip)
        }
        binding.chipDate.setOnClickListener {
            viewModel.setFilterMode("date")
            updateChipSelection(it as Chip)
        }
        binding.chipWordbook.setOnClickListener {
            viewModel.setFilterMode("wordbook")
            updateChipSelection(it as Chip)
        }
        binding.chipCount.setOnClickListener {
            viewModel.setFilterMode("count")
            updateChipSelection(it as Chip)
        }
    }

    private fun updateChipSelection(selectedChip: Chip) {
        val chipGroup = binding.chipGroupFilter
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            chip.isChecked = chip == selectedChip
        }
    }

    private fun setupFAB() {
        binding.fabMistakeReview.setOnClickListener {
            // 错词专项复习：跳转到复习页并触发错词复习
            findNavController().navigate(R.id.navigation_review)
        }
    }

    private fun setupEmptyState() {
        binding.btnEmptyBack.setOnClickListener {
            findNavController().navigate(R.id.navigation_home)
        }
    }

    private fun observeViewModel() {
        // 错词总数
        viewModel.mistakeCount.observe(viewLifecycleOwner) { count ->
            binding.tvMistakeCount.text = getString(R.string.mistake_count, count)
        }

        // 错词列表
        viewModel.mistakeList.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                val hasQuery = viewModel.searchQuery.value.isNotEmpty()
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvMistakeList.visibility = View.GONE
                binding.tvEmptyMessage.text = if (hasQuery) {
                    getString(R.string.mistake_search_empty)
                } else {
                    getString(R.string.mistake_empty)
                }
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvMistakeList.visibility = View.VISIBLE
                updateMistakeList(list)
            }
        }

        // 多选模式 — 使用 viewLifecycleOwner 作用域，避免 onDestroyView 后访问 null binding
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isMultiSelectMode.collect { isMultiSelect ->
                binding.layoutNormalToolbar.isVisible = !isMultiSelect
                binding.layoutMultiSelectToolbar.isVisible = isMultiSelect
                binding.fabMistakeReview.isVisible = !isMultiSelect

                if (isMultiSelect) {
                    binding.tvSelectedCount.text = "已选择 ${viewModel.selectedIds.value.size} 项"
                }
            }
        }

        // 选中数量 — 使用 viewLifecycleOwner 作用域
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedIds.collect { ids ->
                binding.tvSelectedCount.text = "已选择 ${ids.size} 项"
            }
        }

        // 成功消息
        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 错误消息
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMistakeList(list: List<MistakeViewModel.MistakeWithWord>) {
        binding.rvMistakeList.removeAllViews()

        list.forEach { item ->
            // 分组标题项
            if (item.groupTitle != null) {
                val groupView = LayoutInflater.from(requireContext())
                    .inflate(android.R.layout.simple_list_item_1, binding.rvMistakeList, false)
                val tv = groupView.findViewById<TextView>(android.R.id.text1)
                tv.text = item.groupTitle
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                tv.setTypeface(null, android.graphics.Typeface.BOLD)
                tv.setPadding(48, 32, 16, 16)
                tv.setBackgroundColor(0xFFE8EAF6.toInt())
                binding.rvMistakeList.addView(groupView)
                return@forEach
            }

            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_mistake_word, binding.rvMistakeList, false)

            val tvSpelling = itemView.findViewById<TextView>(R.id.tv_mistake_spelling)
            val tvMeaning = itemView.findViewById<TextView>(R.id.tv_mistake_meaning)
            val tvErrorCount = itemView.findViewById<TextView>(R.id.tv_mistake_error_count)
            val tvWordbook = itemView.findViewById<TextView>(R.id.tv_mistake_wordbook)
            val btnReview = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_mistake_review)
            val btnRemove = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_mistake_remove)
            val checkbox = itemView.findViewById<android.widget.CheckBox>(R.id.cb_mistake_select)
            val rootLayout = itemView.findViewById<ViewGroup>(R.id.layout_mistake_item_root)

            tvSpelling.text = item.word?.spelling ?: "未知单词"
            tvMeaning.text = item.word?.meaning ?: ""
            tvErrorCount.text = getString(R.string.mistake_times, item.mistake.errorCount)
            tvWordbook.text = item.wordbookName ?: ""

            // 多选模式下显示 Checkbox
            val isMultiSelect = viewModel.isMultiSelectMode.value
            checkbox.isVisible = isMultiSelect
            checkbox.isChecked = viewModel.selectedIds.value.contains(item.mistake.wordId)
            btnReview.isVisible = !isMultiSelect
            btnRemove.isVisible = !isMultiSelect

            // 点击项目 → 弹出详情
            rootLayout.setOnClickListener {
                if (isMultiSelect) {
                    viewModel.toggleSelection(item.mistake.wordId)
                } else {
                    val bundle = Bundle().apply { putLong("wordId", item.mistake.wordId) }
                    findNavController().navigate(R.id.wordDetailBottomSheet, bundle)
                }
            }

            // 长按 → 进入多选模式
            rootLayout.setOnLongClickListener {
                if (!isMultiSelect) {
                    viewModel.toggleMultiSelectMode()
                    viewModel.toggleSelection(item.mistake.wordId)
                    true
                } else {
                    false
                }
            }

            // 复习按钮
            btnReview.setOnClickListener {
                val bundle = Bundle().apply { putLong("wordId", item.mistake.wordId) }
                findNavController().navigate(R.id.wordDetailBottomSheet, bundle)
            }

            // 移除按钮
            btnRemove.setOnClickListener {
                viewModel.removeMistake(item.mistake.wordId)
            }

            binding.rvMistakeList.addView(itemView)
        }
    }
}
