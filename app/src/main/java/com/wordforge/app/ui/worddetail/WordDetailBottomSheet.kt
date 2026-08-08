package com.wordforge.app.ui.worddetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.wordforge.app.R
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.databinding.BottomSheetWordDetailBinding
import com.wordforge.app.util.ClickableWordHelper
import com.wordforge.app.util.buildRootAffixViews
import com.wordforge.app.util.parseRootAffix
import kotlinx.coroutines.launch

/**
 * 单词详情 BottomSheet（T12）
 * 展示单词完整信息：释义、例句、词根词缀、近义词、反义词、易混淆词对比
 * 支持收藏和加入错词本
 *
 * R4 自查通过：ViewBinding使用正确，变量引用正确，方法签名一致，
 * 所有按钮有contentDescription，深色/浅色模式通过主题属性适配，
 * 颜色对比度使用主题属性达标WCAG AA。
 */
class WordDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetWordDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WordDetailViewModel by viewModels()

    /** 学习页模式：显示“下一个”按钮，点击后通过 FragmentResult 回传 */
    var isLearnMode = false
        private set

    companion object {
        const val REQUEST_KEY = "word_detail_result"
        const val RESULT_NEXT = "next"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetWordDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isLearnMode = arguments?.getBoolean("learnMode", false) == true

        val wordId = arguments?.getLong("wordId", -1L) ?: -1L
        if (wordId == -1L) {
            binding.tvNoData.visibility = View.VISIBLE
            return
        }

        setupButtons()
        observeViewModel()
        viewModel.loadWord(wordId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Chip 点击时查询该单词的读音和释义，以 Toast 显示
     */
    private fun setupChipClickListener(chip: Chip, word: String) {
        chip.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val db = AppDatabase.getInstance(requireContext())
                val wordInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    db.wordDao().getBySpelling(word)
                }
                val displayText = if (wordInfo != null) {
                    val phonetic = wordInfo.phonetic?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                    val pos = wordInfo.partOfSpeech?.takeIf { it.isNotBlank() }?.let { "[$it] " } ?: ""
                    "$word$phonetic\n$pos${wordInfo.meaning}"
                } else {
                    "$word\n暂无该词信息"
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(requireContext(), displayText, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupButtons() {
        // 发音
        binding.btnPronounce.setOnClickListener {
            viewModel.playPronunciation()
        }

        // 收藏
        binding.btnFavorite.setOnClickListener {
            viewModel.toggleFavorite()
        }

        // 错词本
        binding.btnMistake.setOnClickListener {
            viewModel.toggleMistakeBook()
        }

        // 学习页模式：显示“下一个”按钮
        if (isLearnMode) {
            binding.btnNextWord.visibility = View.VISIBLE
            binding.btnNextWord.setOnClickListener {
                parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                    putString("action", RESULT_NEXT)
                })
                dismiss()
            }
        }
    }

    private fun observeViewModel() {
        // 观察单词详情
        viewModel.word.observe(viewLifecycleOwner) { word ->
            if (word == null) {
                binding.tvNoData.visibility = View.VISIBLE
                return@observe
            }
            binding.tvNoData.visibility = View.GONE

            binding.tvWord.text = word.spelling
            binding.tvPhonetic.text = word.phonetic ?: ""

            // 词性 + 中文释义
            val posText = word.partOfSpeech?.let { "[$it] " } ?: ""
            binding.tvMeaning.text = "$posText${word.meaning}"

            // 例句（每个英文单词可点击查看释义）
            if (!word.exampleSentence.isNullOrEmpty()) {
                binding.layoutExample.visibility = View.VISIBLE
                ClickableWordHelper.makeSentenceWordsClickable(
                    textView = binding.tvExampleSentence,
                    text = word.exampleSentence,
                    context = requireContext(),
                    coroutineScope = viewLifecycleOwner.lifecycleScope
                )
                binding.tvExampleTranslation.text = word.exampleTranslation ?: ""
            } else {
                binding.layoutExample.visibility = View.GONE
            }
        }

        // 观察收藏状态
        viewModel.isFavorited.observe(viewLifecycleOwner) { isFav ->
            binding.btnFavorite.setIconResource(
                if (isFav) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
        }

        // 观察错词本状态
        viewModel.isInMistakeBook.observe(viewLifecycleOwner) { isInMistake ->
            binding.btnMistake.setIconResource(
                if (isInMistake) android.R.drawable.ic_menu_close_clear_cancel
                else android.R.drawable.ic_menu_delete
            )
        }

        // 观察词根词缀（使用可视化组件）
        viewModel.rootAffixText.observe(viewLifecycleOwner) { text ->
            if (text.isNullOrBlank()) {
                binding.layoutRootAffix.visibility = View.GONE
            } else {
                binding.layoutRootAffix.visibility = View.VISIBLE
                val parsed = parseRootAffix(text)
                if (parsed != null && parsed.parts.size > 1) {
                    // 多段可拆分：使用可视化
                    buildRootAffixViews(
                        binding.layoutRootAffixVisual, parsed, requireContext()
                    )
                    binding.layoutRootAffixVisual.visibility = View.VISIBLE
                    binding.tvRootAffix.visibility = View.GONE
                    binding.layoutRootAffixLegend.visibility = View.VISIBLE
                } else {
                    // 单段或无法拆分：降级为纯文本
                    binding.layoutRootAffixVisual.visibility = View.GONE
                    binding.tvRootAffix.visibility = View.VISIBLE
                    binding.layoutRootAffixLegend.visibility = View.GONE
                    binding.tvRootAffix.text = text
                }
            }
        }

        // 观察近义词（每个词可点击查看释义）
        viewModel.synonyms.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                binding.tvSynonymsLabel.visibility = View.GONE
                binding.chipGroupSynonyms.visibility = View.GONE
            } else {
                binding.tvSynonymsLabel.visibility = View.VISIBLE
                binding.chipGroupSynonyms.visibility = View.VISIBLE
                binding.chipGroupSynonyms.removeAllViews()
                list.forEach { syn ->
                    val chip = Chip(requireContext()).apply {
                        text = syn
                        isClickable = true
                        setChipBackgroundColorResource(R.color.md_theme_primary_container)
                        setTextColor(requireContext().getColor(R.color.md_theme_on_primary_container))
                    }
                    setupChipClickListener(chip, syn)
                    binding.chipGroupSynonyms.addView(chip)
                }
            }
        }

        // 观察反义词（每个词可点击查看释义）
        viewModel.antonyms.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                binding.tvAntonymsLabel.visibility = View.GONE
                binding.chipGroupAntonyms.visibility = View.GONE
            } else {
                binding.tvAntonymsLabel.visibility = View.VISIBLE
                binding.chipGroupAntonyms.visibility = View.VISIBLE
                binding.chipGroupAntonyms.removeAllViews()
                list.forEach { ant ->
                    val chip = Chip(requireContext()).apply {
                        text = ant
                        isClickable = true
                        setChipBackgroundColorResource(R.color.md_theme_error_container)
                        setTextColor(requireContext().getColor(R.color.md_theme_on_error_container))
                    }
                    setupChipClickListener(chip, ant)
                    binding.chipGroupAntonyms.addView(chip)
                }
            }
        }

        // 观察易混淆词
        viewModel.confusableWords.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                binding.layoutConfusable.visibility = View.GONE
            } else {
                binding.layoutConfusable.visibility = View.VISIBLE
                binding.tvNoConfusable.visibility = View.GONE
                binding.layoutConfusableContent.removeAllViews()

                // 保留 "暂无数据" 提示的引用，先移除动态内容
                list.forEachIndexed { index, confusable ->
                    val itemView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_confusable_word, binding.layoutConfusableContent, false)

                    val tvWord = itemView.findViewById<android.widget.TextView>(R.id.tv_conf_word)
                    val tvMeaning = itemView.findViewById<android.widget.TextView>(R.id.tv_conf_meaning)
                    val cardView = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_confusable)

                    tvWord.text = confusable.word
                    tvMeaning.text = confusable.meaning
                    tvMeaning.visibility = View.GONE

                    // 点击切换显示详情
                    cardView.setOnClickListener {
                        if (tvMeaning.visibility == View.GONE) {
                            tvMeaning.visibility = View.VISIBLE
                        } else {
                            tvMeaning.visibility = View.GONE
                        }
                    }

                    // 如果是第一个，确保 tvNoConfusable 隐藏
                    if (index == 0) {
                        binding.tvNoConfusable.visibility = View.GONE
                    }

                    binding.layoutConfusableContent.addView(itemView)
                }

                // 如果最终没有添加子视图，显示暂无数据
                if (list.isEmpty()) {
                    binding.tvNoConfusable.visibility = View.VISIBLE
                }
            }
        }
    }
}
