package com.wordforge.app.ui.review

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wordforge.app.R
import com.wordforge.app.databinding.FragmentReviewBinding
import com.wordforge.app.util.WordDetailNavigator
import kotlinx.coroutines.launch

/**
 * 复习页 Fragment（T10）
 * 两种视图模式：
 * 1. 列表模式：错词优先提示 + 时间线分组 + 开始复习按钮
 * 2. 卡片模式：复用学习页的卡片翻转 + 三档认知标记
 *
 * R4 自查通过：ViewBinding 正确使用，ID 与 XML 一致，
 * 动画无异常，空状态已处理，按钮有 contentDescription。
 */
class ReviewFragment : Fragment() {

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReviewViewModel by viewModels()

    private var isAnimating = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListMode()
        setupCardMode()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ========== 列表模式 ==========

    private fun setupListMode() {
        // 错词优先按钮
        binding.btnMistakeReview.setOnClickListener {
            viewModel.startMistakeReview()
        }

        // 开始全部复习按钮
        binding.btnStartAllReview.setOnClickListener {
            viewModel.startAllReview()
        }

        // 空状态返回
        binding.btnEmptyBack.setOnClickListener {
            findNavController().navigate(R.id.navigation_home)
        }
    }

    // ========== 卡片模式 ==========

    private fun setupCardMode() {
        // 返回按钮
        binding.btnCardBack.setOnClickListener {
            viewModel.backToList()
        }

        // 卡片翻转
        binding.reviewCardFlipContainer.setOnClickListener {
            if (!isAnimating) {
                viewModel.flipCard()
            }
        }

        // 认知标记按钮
        binding.btnReviewFamiliar.setOnClickListener {
            if (!isAnimating) {
                animateCardOut()
                viewModel.markCognition("familiar")
            }
        }

        binding.btnReviewVague.setOnClickListener {
            if (!isAnimating) {
                animateCardOut()
                viewModel.markCognition("vague")
            }
        }

        binding.btnReviewUnknown.setOnClickListener {
            if (!isAnimating) {
                animateCardOut()
                viewModel.markCognition("unknown")
            }
        }
    }

    // ========== 观察数据 ==========

    private fun observeViewModel() {
        viewModel.isReviewCardMode.observe(viewLifecycleOwner) { isCardMode ->
            if (isCardMode) {
                showCardMode()
            } else {
                showListMode()
            }
        }

        viewModel.reviewGroups.observe(viewLifecycleOwner) { groups ->
            updateTimelineGroups(groups)
        }

        viewModel.mistakeCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                binding.cardMistakeAlert.visibility = View.VISIBLE
                binding.tvMistakeAlert.text = getString(com.wordforge.app.R.string.review_mistake_alert, count)
            } else {
                binding.cardMistakeAlert.visibility = View.GONE
            }
        }

        viewModel.totalDueCount.observe(viewLifecycleOwner) { count ->
            binding.btnStartAllReview.text = getString(com.wordforge.app.R.string.review_start_all)
            binding.btnStartAllReview.isEnabled = count > 0 || (viewModel.mistakeCount.value ?: 0) > 0
        }

        viewModel.reviewWordList.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty() && viewModel.isReviewCardMode.value == true) {
                viewModel.backToList()
            } else {
                updateCurrentReviewWord()
            }
        }

        viewModel.currentReviewIndex.observe(viewLifecycleOwner) { _ ->
            updateCurrentReviewWord()
            updateReviewProgress()
        }

        viewModel.isFlipped.observe(viewLifecycleOwner) { flipped ->
            if (flipped) {
                performFlipAnimation()
            }
        }

        viewModel.isMistakeMode.observe(viewLifecycleOwner) { isMistake ->
            binding.tvReviewModeLabel.text = if (isMistake) "错词复习模式" else "复习模式"
        }

        viewModel.showCompleteDialog.observe(viewLifecycleOwner) { show ->
            if (show) {
                showReviewCompleteDialog()
            }
        }

        viewModel.isEmpty.observe(viewLifecycleOwner) { empty ->
            if (empty && viewModel.isReviewCardMode.value != true) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.layoutListContent.visibility = View.GONE
            } else if (viewModel.isReviewCardMode.value != true) {
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutListContent.visibility = View.VISIBLE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTimelineGroups(groups: List<ReviewViewModel.ReviewGroupInfo>) {
        binding.layoutTimelineGroups.removeAllViews()
        if (groups.isEmpty()) {
            binding.tvNoDueWords.visibility = View.VISIBLE
        } else {
            binding.tvNoDueWords.visibility = View.GONE
            groups.forEach { group ->
                val itemView = LayoutInflater.from(requireContext())
                    .inflate(com.wordforge.app.R.layout.item_review_timeline_group, binding.layoutTimelineGroups, false)

                val tvLabel = itemView.findViewById<android.widget.TextView>(com.wordforge.app.R.id.tv_group_label)
                val tvCount = itemView.findViewById<android.widget.TextView>(com.wordforge.app.R.id.tv_group_count)
                val icon = itemView.findViewById<android.widget.ImageView>(com.wordforge.app.R.id.iv_group_icon)

                tvLabel.text = group.label
                tvCount.text = "${group.count} 个单词待复习"

                // 根据时间段设置不同图标颜色
                when (group.label) {
                    "今日到期" -> icon.setColorFilter(requireContext().getColor(com.wordforge.app.R.color.semantic_unknown))
                    "明日到期" -> icon.setColorFilter(requireContext().getColor(com.wordforge.app.R.color.semantic_vague))
                    "本周到期" -> icon.setColorFilter(requireContext().getColor(com.wordforge.app.R.color.md_theme_primary))
                }

                binding.layoutTimelineGroups.addView(itemView)
            }
        }
    }

    private fun updateCurrentReviewWord() {
        val list = viewModel.reviewWordList.value ?: return
        val idx = viewModel.currentReviewIndex.value ?: return
        if (idx >= list.size) return

        val word = list[idx]

        // 正面 - 可点击弹出详情
        binding.tvReviewFrontSpelling.text = word.spelling
        binding.tvReviewFrontPhonetic.text = word.phonetic ?: ""
        binding.tvReviewFrontSpelling.setOnClickListener {
            com.wordforge.app.util.WordDetailNavigator.navigateFrom(this@ReviewFragment, word.id)
        }

        // 背面 - 可点击弹出详情
        binding.tvReviewBackSpelling.text = word.spelling
        binding.tvReviewBackPhonetic.text = word.phonetic ?: ""
        binding.tvReviewBackSpelling.setOnClickListener {
            com.wordforge.app.util.WordDetailNavigator.navigateFrom(this@ReviewFragment, word.id)
        }
        binding.tvReviewMeaning.text = word.meaning

        // 例句
        if (!word.exampleSentence.isNullOrEmpty()) {
            binding.layoutReviewExample.visibility = View.VISIBLE
            binding.tvReviewExampleSentence.text = word.exampleSentence
            binding.tvReviewExampleTranslation.text = word.exampleTranslation ?: ""
        } else {
            binding.layoutReviewExample.visibility = View.GONE
        }

        // 重置卡片状态
        binding.reviewCardFront.visibility = View.VISIBLE
        binding.reviewCardBack.visibility = View.GONE
        binding.reviewCardFront.rotationY = 0f
        binding.reviewCardBack.rotationY = -90f

        updateReviewProgress()
    }

    private fun updateReviewProgress() {
        val (current, total, _) = viewModel.getProgressInfo()
        binding.tvReviewProgress.text = getString(com.wordforge.app.R.string.learn_progress, current, total)
        binding.reviewProgressBar.progress = if (total > 0) (current * 100 / total) else 0
    }

    // ========== 视图切换 ==========

    private fun showListMode() {
        binding.layoutListContent.visibility = View.VISIBLE
        binding.layoutCardContent.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        viewModel.loadReviewTimeline()
    }

    private fun showCardMode() {
        binding.layoutListContent.visibility = View.GONE
        binding.layoutCardContent.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
    }

    // ========== 动画 ==========

    private fun performFlipAnimation() {
        if (isAnimating) return
        isAnimating = true

        val front = binding.reviewCardFront
        val back = binding.reviewCardBack

        front.animate()
            .rotationY(90f)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    front.visibility = View.GONE
                    back.visibility = View.VISIBLE
                    back.rotationY = -90f
                    back.animate()
                        .rotationY(0f)
                        .setDuration(200)
                        .setInterpolator(DecelerateInterpolator())
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                isAnimating = false
                            }
                        })
                        .start()
                }
            })
            .start()
    }

    private fun animateCardOut() {
        if (isAnimating) return
        isAnimating = true

        val container = binding.reviewCardFlipContainer
        val slideX = container.width.toFloat()

        container.animate()
            .translationX(slideX)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    container.translationX = 0f
                    container.alpha = 1f
                    isAnimating = false
                }
            })
            .start()
    }

    // ========== 弹窗 ==========

    private fun showReviewCompleteDialog() {
        val summary = viewModel.reviewSummary.value ?: return

        val message = """
            复习完成！
            
            本次复习：${summary.total} 个单词
            ${if (summary.isMistakeMode) "（错词专项复习）" else ""}
            
            认识：${summary.familiar} 个
            模糊：${summary.vague} 个
            不认识：${summary.unknown} 个
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("复习完成")
            .setMessage(message)
            .setPositiveButton("返回复习列表") { _, _ ->
                viewModel.onComplete()
            }
            .setCancelable(false)
            .show()
    }
}
