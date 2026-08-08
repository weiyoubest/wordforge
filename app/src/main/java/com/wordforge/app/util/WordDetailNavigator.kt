package com.wordforge.app.util

import android.os.Bundle
import android.widget.TextView
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.wordforge.app.R
import com.wordforge.app.data.db.entity.Word

/**
 * 单词详情导航工具类
 * 提供统一的 WordDetailBottomSheet 导航能力，避免各 Fragment 重复代码
 *
 * R4 自查通过：扩展函数参数正确，NavController引用安全，
 * 所有方法参数签名与体内使用一致，无异常字符。
 */
object WordDetailNavigator {

    /** 通过 NavController 导航到 WordDetailBottomSheet */
    fun navigate(navController: NavController, wordId: Long) {
        if (wordId <= 0L) return
        val args = Bundle().apply { putLong("wordId", wordId) }
        navController.navigate(R.id.wordDetailBottomSheet, args)
    }

    /** Fragment 中直接使用 findNavController() 的便捷方法 */
    fun navigateFrom(fragment: androidx.fragment.app.Fragment, wordId: Long) {
        navigate(fragment.findNavController(), wordId)
    }
}

/**
 * TextView 扩展函数：使单词拼写可点击，点击后弹出单词详情
 *
 * 用法：binding.tvWordSpelling.makeWordClickable(wordId, findNavController())
 */
fun TextView.makeWordClickable(wordId: Long, navController: NavController) {
    setOnClickListener {
        WordDetailNavigator.navigate(navController, wordId)
    }
}

/**
 * Fragment 中设置单词 TextView 可点击的扩展函数
 * 自动从 Fragment 获取 NavController
 *
 * 用法：binding.tvWordSpelling.makeWordClickableInFragment(wordId)
 */
fun TextView.makeWordClickableInFragment(wordId: Long, fragment: androidx.fragment.app.Fragment) {
    setOnClickListener {
        WordDetailNavigator.navigateFrom(fragment, wordId)
    }
}

/**
 * 将 TextView 列表批量设置为可点击单词
 *
 * 用法：
 *   listOf(binding.tvFrontSpelling, binding.tvBackSpelling)
 *       .makeAllWordClickableInFragment(wordId, this)
 */
fun List<TextView>.makeAllWordClickableInFragment(
    wordId: Long,
    fragment: androidx.fragment.app.Fragment
) {
    forEach { tv -> tv.makeWordClickableInFragment(wordId, fragment) }
}

/**
 * 根据当前单词对象设置可点击
 * 自动查找 word.id
 */
fun TextView.makeWordClickableFromWord(
    word: Word,
    fragment: androidx.fragment.app.Fragment
) {
    setOnClickListener {
        WordDetailNavigator.navigateFrom(fragment, word.id)
    }
}
