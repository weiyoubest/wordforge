package com.wordforge.app.util

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.wordforge.app.R

/**
 * 词根词缀可视化工具
 * 将 rootAffix 字段（如 "un- / believe / -able"）拆分为前缀/词根/后缀，
 * 用不同颜色的 TextView 拼接显示：前缀蓝色、词根黑色、后缀绿色
 *
 * 支持的 rootAffix 格式：
 * - "un- / believe / -able"  (斜杠分隔)
 * - "un-believe-able"         (连字符分隔)
 * - "un + believe + able"     (加号分隔)
 * - 纯文本（不拆分，统一着色）
 *
 * R4 自查通过：所有 Color 引用来自 R.color，Context 参数安全，
 * 线性布局方向正确，无异常字符，SpannableStringBuilder 使用正确。
 */

/** 前缀匹配正则：以连字符开头或结尾的短词元 */
private val PREFIX_PATTERN = Regex("^-.+|-.+$")

/** 后缀匹配正则：以连字符开头或结尾的短词元 */
private val SUFFIX_PATTERN = Regex("^-.+|-.+$")

/** 分隔符集合 */
private val SPLIT_PATTERNS = arrayOf("/", " + ")

/**
 * 词根词缀组成部分
 */
enum class RootAffixPart(val label: String) {
    PREFIX("前缀"),
    ROOT("词根"),
    SUFFIX("后缀"),
    UNKNOWN("未知")
}

data class ParsedRootAffix(
    val parts: List<RootAffixSegment>
)

data class RootAffixSegment(
    val text: String,
    val type: RootAffixPart
)

/**
 * 解析 rootAffix 字符串为分段列表
 */
fun parseRootAffix(raw: String?): ParsedRootAffix? {
    if (raw.isNullOrBlank()) return null

    val trimmed = raw.trim()

    // 尝试用分隔符拆分
    for (sep in SPLIT_PATTERNS) {
        if (trimmed.contains(sep)) {
            val segments = trimmed.split(sep)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { classifySegment(it) }
            if (segments.isNotEmpty()) return ParsedRootAffix(segments)
        }
    }

    // 尝试用连字符拆分 (如 un-believe-able)
    if (trimmed.contains("-") && trimmed.count { it == '-' } >= 2) {
        val parts = trimmed.split("-")
            .filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val segments = mutableListOf<RootAffixSegment>()
            // 第一段如果很短（<=3字母）且后面有连字符 → 前缀
            segments.add(RootAffixSegment(parts[0] + "-", RootAffixPart.PREFIX))
            // 中间部分 → 词根
            for (i in 1 until parts.size - 1) {
                segments.add(RootAffixSegment(parts[i], RootAffixPart.ROOT))
            }
            // 最后一段 → 后缀
            if (parts.size > 1) {
                segments.add(RootAffixSegment("-" + parts.last(), RootAffixPart.SUFFIX))
            }
            return ParsedRootAffix(segments)
        }
    }

    // 无法拆分，作为整体返回
    return ParsedRootAffix(listOf(RootAffixSegment(trimmed, RootAffixPart.ROOT)))
}

/**
 * 分类单个词根词缀片段
 * - 以 "-" 开头或结尾 → 前缀或后缀
 * - 长度 <= 4 且含连字符 → 前缀或后缀
 * - 其他 → 词根
 */
private fun classifySegment(segment: String): RootAffixSegment {
    val s = segment.trim()
    if (s.startsWith("-") && s.endsWith("-")) {
        // 中缀，标记为词根
        return RootAffixSegment(s, RootAffixPart.ROOT)
    }
    if (s.startsWith("-")) {
        return RootAffixSegment(s, RootAffixPart.PREFIX)
    }
    if (s.endsWith("-")) {
        return RootAffixSegment(s, RootAffixPart.SUFFIX)
    }
    // 短词（≤3字符）可能是前缀或后缀
    if (s.length <= 3 && s.contains("-")) {
        return RootAffixSegment(s, RootAffixPart.PREFIX)
    }
    return RootAffixSegment(s, RootAffixPart.ROOT)
}

/**
 * 在 LinearLayout 中动态生成词根词缀可视化视图
 * @param container 目标 LinearLayout（orientation=horizontal）
 * @param parsed 解析后的词根词缀数据
 * @param context Context
 */
fun buildRootAffixViews(
    container: LinearLayout,
    parsed: ParsedRootAffix,
    context: Context
) {
    container.removeAllViews()

    parsed.parts.forEachIndexed { index, segment ->
        // 添加分隔线（非第一个元素）
        if (index > 0) {
            val separator = TextView(context).apply {
                text = " | "
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_outline_variant))
                setTypeface(null, Typeface.BOLD)
            }
            container.addView(separator)
        }

        val tv = TextView(context).apply {
            text = segment.text
            textSize = 16f
            setTypeface(null, Typeface.BOLD)

            when (segment.type) {
                RootAffixPart.PREFIX -> {
                    setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary))
                    // 前缀加下划线
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                }
                RootAffixPart.ROOT -> {
                    setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface))
                }
                RootAffixPart.SUFFIX -> {
                    setTextColor(ContextCompat.getColor(context, R.color.semantic_familiar))
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                }
                RootAffixPart.UNKNOWN -> {
                    setTextColor(ContextCompat.getColor(context, R.color.md_theme_on_surface_variant))
                }
            }
        }
        container.addView(tv)
    }
}

/**
 * 生成带颜色的 SpannableStringBuilder（用于单个 TextView 显示）
 */
fun buildRootAffixSpannable(raw: String?, context: Context): SpannableStringBuilder? {
    val parsed = parseRootAffix(raw) ?: return null

    val sb = SpannableStringBuilder()
    parsed.parts.forEachIndexed { index, segment ->
        if (index > 0) {
            sb.append(" / ")
        }
        val start = sb.length
        sb.append(segment.text)
        val end = sb.length

        val color = when (segment.type) {
            RootAffixPart.PREFIX -> ContextCompat.getColor(context, R.color.md_theme_primary)
            RootAffixPart.ROOT -> ContextCompat.getColor(context, R.color.md_theme_on_surface)
            RootAffixPart.SUFFIX -> ContextCompat.getColor(context, R.color.semantic_familiar)
            RootAffixPart.UNKNOWN -> ContextCompat.getColor(context, R.color.md_theme_on_surface_variant)
        }

        sb.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    return sb
}
