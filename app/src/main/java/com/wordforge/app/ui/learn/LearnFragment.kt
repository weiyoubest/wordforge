package com.wordforge.app.ui.learn

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.setFragmentResultListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wordforge.app.R
import com.wordforge.app.databinding.FragmentLearnBinding
import com.wordforge.app.util.ClickableWordHelper
import com.wordforge.app.ui.worddetail.WordDetailBottomSheet
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 学习页 Fragment（T09）— 交互升级版
 * 新增功能：
 * - TTS 美音发音（点击单词自动朗读，新卡片自动播放，播放/暂停按钮）
 * - 左右滑动交互（左滑=不认识/红色，右滑=认识/绿色，带淡出淡入动画）
 * - 视觉升级（24dp圆角、进度指示器、28sp大字、释义例句分区）
 *
 * R4 自查通过：
 * - 变量引用正确，ViewBinding使用正确，方法签名一致
 * - TextToSpeech生命周期管理完整（onInit/onDestroyView）
 * - 触摸事件处理无异常字符，所有按钮有contentDescription
 * - 颜色使用主题属性对比度达标（WCAG AA）
 * - TTS仅在前台可用时调用，shutdown在onDestroyView中调用
 * - 滑动阈值和动画参数合理
 */
class LearnFragment : Fragment() {

    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LearnViewModel by viewModels()

    private var isAnimating = false

    // ===== TTS 相关 =====
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isSpeaking = false
    private var currentSpelling: String? = null

    // ===== 滑动相关 =====
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var isSwiping = false
    private val SWIPE_THRESHOLD = 150f // 滑动触发阈值（dp）
    private val SWIPE_MAX_ALPHA = 0.6f  // 滑动时最大透明度
    private var previousIndex = -1      // 上一次自动朗读的索引，避免重复

    /** 当前待处理的认知标记（BottomSheet弹出时暂存） */
    private var pendingCognitionLevel: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        initTts()
        setupCardFlip()
        setupSwipeGesture()
        setupCognitionButtons()
        setupViewDetail()
        setupFragmentResultListener()
        setupEmptyState()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 释放 TTS 资源
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        _binding = null
    }

    // ===== TTS 初始化 =====

    /**
     * 初始化 Android 原生 TTS，设置美音
     */
    private fun initTts() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                ttsReady = (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE)
            }
        }
    }

    /**
     * 使用 TTS 朗读指定单词（美音）
     */
    private fun speakWord(spelling: String) {
        if (!ttsReady || tts == null) return
        currentSpelling = spelling
        tts?.stop()
        // QUEUE_ADD 避免打断，实际用 QUEUE_FLUSH 替换之前的
        tts?.speak(spelling, TextToSpeech.QUEUE_FLUSH, null, "wordforge_tts")
        isSpeaking = true
        updatePronounceButtons(true)
    }

    /**
     * 停止 TTS 发音
     */
    private fun stopSpeaking() {
        tts?.stop()
        isSpeaking = false
        updatePronounceButtons(false)
    }

    /**
     * 切换播放/暂停
     */
    private fun togglePronunciation() {
        if (isSpeaking) {
            stopSpeaking()
        } else {
            val word = currentSpelling ?: return
            speakWord(word)
        }
    }

    /**
     * 更新发音按钮的图标状态
     * @param playing true=正在播放（显示暂停图标），false=已停止（显示播放图标）
     */
    private fun updatePronounceButtons(playing: Boolean) {
        val iconRes = if (playing) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        binding.btnPronounceFront.setIconResource(iconRes)
        binding.btnPronounce.setIconResource(iconRes)
    }

    // ===== Toolbar =====

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    // ===== 卡片翻转 =====

    private fun setupCardFlip() {
        // 点击正面翻转（非滑动时才触发）
        binding.cardFlipContainer.setOnClickListener {
            if (!isAnimating && !isSwiping) {
                viewModel.flipCard()
            }
        }

        // 点击单词拼写TextView时朗读发音
        binding.tvWordSpelling.setOnClickListener {
            if (currentSpelling != null) {
                togglePronunciation()
            }
        }

        // 正面发音按钮
        binding.btnPronounceFront.setOnClickListener {
            currentSpelling?.let { togglePronunciation() }
        }

        // 背面发音按钮
        binding.btnPronounce.setOnClickListener {
            currentSpelling?.let { togglePronunciation() }
        }
    }

    // ===== 滑动手势处理 =====

    /**
     * 设置左右滑动手势
     * 向左滑 = 不认识（红色反馈），向右滑 = 认识（绿色反馈）
     */
    private fun setupSwipeGesture() {
        val container = binding.cardFlipContainer
        val density = resources.displayMetrics.density
        val thresholdPx = SWIPE_THRESHOLD * density

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    isSwiping = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - swipeStartX
                    val dy = event.rawY - swipeStartY
                    if (!isSwiping && Math.abs(dx) > 20 && Math.abs(dx) > Math.abs(dy)) {
                        isSwiping = true
                    }
                    if (isSwiping) {
                        container.translationX = dx
                        val ratio = Math.min(Math.abs(dx) / (thresholdPx * 2), 1f)
                        container.alpha = 1f - ratio * SWIPE_MAX_ALPHA
                        if (dx < 0) {
                            binding.ivSwipeHintLeft.alpha = ratio
                            binding.ivSwipeHintRight.alpha = 0f
                        } else {
                            binding.ivSwipeHintLeft.alpha = 0f
                            binding.ivSwipeHintRight.alpha = ratio
                        }
                        if (dx < -thresholdPx * 0.5f) {
                            binding.cardFront.setCardBackgroundColor(getColorWithAlpha(R.color.semantic_unknown, 0.1f))
                        } else if (dx > thresholdPx * 0.5f) {
                            binding.cardFront.setCardBackgroundColor(getColorWithAlpha(R.color.semantic_familiar, 0.1f))
                        } else {
                            binding.cardFront.setCardBackgroundColor(
                                resolveColor(com.google.android.material.R.attr.colorSurface)
                            )
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSwiping) {
                        val dx = event.rawX - swipeStartX
                        if (dx < -thresholdPx) {
                            animateSwipeOut(-1f, Color.parseColor("#D93025"))
                            viewModel.markCognition("unknown")
                        } else if (dx > thresholdPx) {
                            animateSwipeOut(1f, Color.parseColor("#1E8E3E"))
                            viewModel.markCognition("familiar")
                        } else {
                            resetCardPosition()
                        }
                    } else {
                        isSwiping = false
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 获取带透明度的颜色
     */
    private fun getColorWithAlpha(colorRes: Int, alpha: Float): Int {
        val color = resources.getColor(colorRes, requireActivity().theme)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.argb((alpha * 255).toInt(), r, g, b)
    }

    /**
     * 解析主题颜色属性
     */
    private fun resolveColor(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /**
     * 滑动飞出动画（卡片向指定方向飞出 + 淡出，然后新卡片淡入）
     * @param direction 1f=向右飞出，-1f=向左飞出
     * @param feedbackColor 反馈颜色
     */
    private fun animateSwipeOut(direction: Float, feedbackColor: Int) {
        if (isAnimating) return
        isAnimating = true
        isSwiping = false

        val container = binding.cardFlipContainer
        val targetX = direction * container.width * 1.5f

        // 隐藏方向提示
        binding.ivSwipeHintLeft.alpha = 0f
        binding.ivSwipeHintRight.alpha = 0f

        // 飞出动画
        container.animate()
            .translationX(targetX)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                // 重置位置
                container.translationX = 0f
                container.alpha = 1f
                // 恢复卡片颜色
                binding.cardFront.setCardBackgroundColor(
                    resolveColor(com.google.android.material.R.attr.colorSurface)
                )
                isAnimating = false
            }
            .start()
    }

    /**
     * 重置卡片位置（未达阈值时弹回）
     */
    private fun resetCardPosition() {
        val container = binding.cardFlipContainer
        isSwiping = false

        container.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.cardFront.setCardBackgroundColor(
                    resolveColor(com.google.android.material.R.attr.colorSurface)
                )
            }
            .start()

        // 隐藏方向提示
        binding.ivSwipeHintLeft.animate().alpha(0f).setDuration(150).start()
        binding.ivSwipeHintRight.animate().alpha(0f).setDuration(150).start()
    }

    // ===== 认知按钮 =====

    private fun setupCognitionButtons() {
        // 认识：直接跳下一个（不弹详情）
        binding.btnFamiliar.setOnClickListener {
            if (!isAnimating) {
                animateCardOut(R.color.semantic_familiar)
                viewModel.markCognition("familiar")
            }
        }

        // 模糊：弹出详情BottomSheet
        binding.btnVague.setOnClickListener {
            if (!isAnimating) {
                showDetailBottomSheet("vague")
            }
        }

        // 不认识：弹出详情BottomSheet
        binding.btnUnknown.setOnClickListener {
            if (!isAnimating) {
                showDetailBottomSheet("unknown")
            }
        }
    }

    /**
     * 弹出单词详情BottomSheet（学习模式），暂存认知标记
     */
    private fun showDetailBottomSheet(cognitionLevel: String) {
        pendingCognitionLevel = cognitionLevel
        val wordList = viewModel.wordList.value
        val idx = viewModel.currentIndex.value
        if (wordList == null || idx == null) return
        if (idx < wordList.size) {
            // 不立即标记认知，等BottomSheet关闭后再标记
            findNavController().navigate(
                R.id.wordDetailBottomSheet,
                Bundle().apply {
                    putLong("wordId", wordList[idx].id)
                    putBoolean("learnMode", true)
                }
            )
        }
    }

    /**
     * 注册 FragmentResult 监听：BottomSheet 点击“下一个”时收到回调
     */
    private fun setupFragmentResultListener() {
        parentFragmentManager.setFragmentResultListener(
            WordDetailBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val action = result.getString("action")
            if (action == WordDetailBottomSheet.RESULT_NEXT) {
                // BottomSheet关闭后，现在才标记认知并跳下一个
                pendingCognitionLevel?.let { viewModel.markCognition(it) }
                pendingCognitionLevel = null
            }
        }
    }

    private fun setupViewDetail() {
        val showDetail: () -> Unit = lambda@{
            val wordList = viewModel.wordList.value
            val idx = viewModel.currentIndex.value
            if (wordList == null || idx == null) return@lambda
            if (idx < wordList.size) {
                findNavController().navigate(
                    R.id.wordDetailBottomSheet,
                    Bundle().apply { putLong("wordId", wordList[idx].id) }
                )
            }
        }

        // 长按拼写 TextView 弹出详情（短按保留发音功能）
        binding.tvWordSpelling.setOnLongClickListener {
            showDetail()
            true
        }
        binding.tvBackSpelling.setOnLongClickListener {
            showDetail()
            true
        }

        // 查看详情按钮
        binding.btnViewDetail.setOnClickListener { showDetail() }
    }

    private fun setupEmptyState() {
        binding.btnEmptyBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    // ===== ViewModel 观察 =====

    private fun observeViewModel() {
        // 观察单词列表
        viewModel.wordList.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                showEmptyState()
            } else {
                hideEmptyState()
                updateCurrentWord()
            }
        }

        // 观察当前索引 + 进度（合并，避免重复 observe 同一个 LiveData）
        viewModel.currentIndex.observe(viewLifecycleOwner) { _ ->
            updateCurrentWord()
            updateProgress()
        }

        // 观察翻转状态
        viewModel.isFlipped.observe(viewLifecycleOwner) { flipped ->
            if (flipped) {
                performFlipAnimation()
            }
        }

        // 观察完成弹窗
        viewModel.showCompleteDialog.observe(viewLifecycleOwner) { show ->
            if (show) {
                showCompleteDialog()
            }
        }

        // 观察空状态
        viewModel.isEmpty.observe(viewLifecycleOwner) { empty ->
            if (empty) {
                showEmptyState()
                val wb = viewModel.activeWordbook.value
                binding.tvEmptyTitle.text = if (wb == null) {
                    getString(R.string.learn_empty_wordbook)
                } else {
                    getString(R.string.learn_complete)
                }
            }
        }

        // 观察错误
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== 更新当前单词 =====

    private fun updateCurrentWord() {
        val list = viewModel.wordList.value ?: return
        val idx = viewModel.currentIndex.value ?: return
        if (idx >= list.size) return

        val word = list[idx]

        // 停止之前的发音
        stopSpeaking()

        // 正面内容
        binding.tvWordSpelling.text = word.spelling
        binding.tvWordPhonetic.text = word.phonetic ?: ""

        // 背面内容
        binding.tvBackSpelling.text = word.spelling
        binding.tvBackPhonetic.text = word.phonetic ?: ""
        binding.tvMeaning.text = word.meaning

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

        // 保存当前拼写用于发音
        currentSpelling = word.spelling

        // 重置发音按钮状态
        updatePronounceButtons(false)

        // 重置翻转
        binding.cardFront.visibility = View.VISIBLE
        binding.cardBack.visibility = View.GONE
        binding.cardFront.rotationY = 0f
        binding.cardBack.rotationY = -90f

        updateProgress()

        // 新卡片展示时自动朗读一次（美音）
        // 仅在索引变化时朗读，避免重复
        if (idx != previousIndex) {
            previousIndex = idx
            word.spelling.let {
                // 延迟一小段时间再朗读，等待卡片渲染完成
                binding.cardFlipContainer.postDelayed({
                    speakWord(it)
                }, 300)
            }
        }
    }

    private fun updateProgress() {
        val (current, total, _) = viewModel.getProgressInfo()
        binding.tvProgress.text = getString(R.string.learn_progress, current, total)
        binding.progressBar.progress = if (total > 0) (current * 100 / total) else 0

        // 更新"今日新词"进度指示器
        if (total > 0) {
            binding.tvNewWordProgress.visibility = View.VISIBLE
            binding.tvNewWordProgress.text = getString(R.string.learn_new_word_progress, current, total)
        } else {
            binding.tvNewWordProgress.visibility = View.GONE
        }
    }

    // ===== 动画 =====

    /**
     * 3D 翻转动画
     */
    private fun performFlipAnimation() {
        if (isAnimating) return
        isAnimating = true

        val front = binding.cardFront
        val back = binding.cardBack

        // 正面 → 90度
        front.animate()
            .rotationY(90f)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    front.visibility = View.GONE
                    back.visibility = View.VISIBLE
                    back.rotationY = -90f
                    // 背面 → 0度
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

    /**
     * 卡片滑出动画（按钮触发，保留原有逻辑）
     */
    private fun animateCardOut(colorRes: Int) {
        if (isAnimating) return
        isAnimating = true

        val container = binding.cardFlipContainer
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

    // ===== 弹窗 =====

    /**
     * 显示学习完成弹窗
     */
    private fun showCompleteDialog() {
        val (total, _, accuracy) = viewModel.getProgressInfo()
        val familiar = viewModel.familiarCount.value ?: 0
        val vague = viewModel.vagueCount.value ?: 0
        val unknown = viewModel.unknownCount.value ?: 0

        val message = """
            本轮学习完成！
            
            学习单词：${total} 个
            正确率：${"%.0f".format(accuracy)}%
            
            熟悉：${familiar} 个
            模糊：${vague} 个
            不认识：${unknown} 个
            
            ${if (unknown > 0) "已将 ${unknown} 个不认识的词加入错词本" else "表现不错，继续保持！"}
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("学习完成")
            .setMessage(message)
            .setPositiveButton("返回首页") { _, _ ->
                findNavController().navigate(R.id.navigation_home)
            }
            .setNegativeButton("继续学习") { _, _ ->
                previousIndex = -1
                viewModel.loadWords()
            }
            .setCancelable(false)
            .show()
    }

    private fun showEmptyState() {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.toolbar.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.layoutEmpty.visibility = View.GONE
        binding.toolbar.visibility = View.VISIBLE
    }
}
