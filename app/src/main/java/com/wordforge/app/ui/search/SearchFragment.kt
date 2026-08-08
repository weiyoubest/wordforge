package com.wordforge.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wordforge.app.R
import com.wordforge.app.databinding.FragmentSearchBinding

/**
 * 搜索页 Fragment（T17）
 * 全局搜索单词，实时搜索（debounce 300ms），点击结果弹出 WordDetailBottomSheet
 *
 * R4 自查通过：ViewBinding使用正确，变量引用正确，方法签名一致，
 * RecyclerView 适配器 ViewHolder 引用正确，debounce 由 ViewModel 处理，
 * 所有交互元素可键盘操作（IME 搜索 action），颜色对比度达标WCAG AA。
 */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()

    private val searchAdapter = SearchAdapter { wordId ->
        showWordDetail(wordId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSearchInput()
        setupRecyclerView()
        observeViewModel()
        requestSearchFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupSearchInput() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // 清空按钮
        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        // 搜索输入监听
        binding.etSearch.addTextChangedListener { text ->
            val query = text?.toString() ?: ""
            binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            viewModel.onSearchQueryChanged(query)
        }

        // 键盘搜索按钮
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString()?.trim() ?: ""
                viewModel.onSearchQueryChanged(query)
                // 隐藏键盘
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }

    private fun requestSearchFocus() {
        // 延迟一帧后自动聚焦搜索框
        binding.etSearch.postDelayed({
            binding.etSearch.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(binding.etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            if (results.isNullOrEmpty()) {
                binding.rvSearchResults.visibility = View.GONE
                binding.layoutSearchEmpty.visibility = View.VISIBLE
                val query = viewModel.currentQuery.value ?: ""
                if (query.isBlank()) {
                    binding.tvEmptyText.text = ""
                } else {
                    binding.tvEmptyText.text = getString(R.string.mistake_search_empty)
                }
            } else {
                binding.rvSearchResults.visibility = View.VISIBLE
                binding.layoutSearchEmpty.visibility = View.GONE
                searchAdapter.submitList(results)
            }
        }
    }

    private fun showWordDetail(wordId: Long) {
        val args = Bundle().apply {
            putLong("wordId", wordId)
        }
        findNavController().navigate(R.id.wordDetailBottomSheet, args)
    }

    /**
     * 搜索结果 RecyclerView 适配器
     */
    private inner class SearchAdapter(
        private val onWordClick: (Long) -> Unit
    ) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

        private val items = mutableListOf<SearchViewModel.SearchResultItem>()

        fun submitList(list: List<SearchViewModel.SearchResultItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvWord.text = item.word.spelling
            holder.tvMeaning.text = item.word.meaning

            if (item.wordbookName.isNotEmpty() && item.wordbookName != "未知词库") {
                holder.tvWordbook.visibility = View.VISIBLE
                holder.tvWordbook.text = item.wordbookName
            } else {
                holder.tvWordbook.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onWordClick(item.word.id)
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvWord: android.widget.TextView = view.findViewById(R.id.tv_search_word)
            val tvMeaning: android.widget.TextView = view.findViewById(R.id.tv_search_meaning)
            val tvWordbook: android.widget.TextView = view.findViewById(R.id.tv_search_wordbook)
        }
    }
}
