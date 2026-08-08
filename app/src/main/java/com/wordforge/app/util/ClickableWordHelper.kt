package com.wordforge.app.util

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import com.wordforge.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

object ClickableWordHelper {

    fun makeSentenceWordsClickable(
        textView: TextView,
        text: String,
        context: Context,
        coroutineScope: CoroutineScope,
        @ColorInt linkColor: Int? = null
    ) {
        if (text.isBlank()) {
            textView.text = text
            return
        }
        val spannableString = SpannableString(text)
        val wordRegex = Regex("[a-zA-Z]+(?:'[a-zA-Z]+)?")
        val resolvedColor = linkColor
            ?: resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary)
        wordRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val word = matchResult.value
            spannableString.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    queryAndShowWordInfo(context, coroutineScope, word)
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = resolvedColor
                    ds.isUnderlineText = false
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = android.graphics.Color.TRANSPARENT
    }

    fun makeWordListClickable(
        textView: TextView,
        words: List<String>,
        context: Context,
        coroutineScope: CoroutineScope,
        separator: String = ", ",
        @ColorInt linkColor: Int? = null
    ) {
        if (words.isEmpty()) {
            textView.text = ""
            return
        }
        val fullText = words.joinToString(separator)
        val spannableString = SpannableString(fullText)
        val resolvedColor = linkColor
            ?: resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary)
        var currentPos = 0
        words.forEachIndexed { _, word ->
            val start = currentPos
            val end = start + word.length
            spannableString.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    queryAndShowWordInfo(context, coroutineScope, word)
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = resolvedColor
                    ds.isUnderlineText = true
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            currentPos = end + separator.length
        }
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = android.graphics.Color.TRANSPARENT
    }

    fun makeSingleWordClickable(
        textView: TextView,
        word: String,
        context: Context,
        coroutineScope: CoroutineScope,
        @ColorInt linkColor: Int? = null
    ) {
        if (word.isBlank()) return
        val spannableString = SpannableString(word)
        val resolvedColor = linkColor
            ?: resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary)
        spannableString.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                queryAndShowWordInfo(context, coroutineScope, word)
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = resolvedColor
                ds.isUnderlineText = true
            }
        }, 0, word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = android.graphics.Color.TRANSPARENT
    }

    private fun queryAndShowWordInfo(
        context: Context,
        coroutineScope: CoroutineScope,
        word: String
    ) {
        coroutineScope.launch {
            val db = AppDatabase.getInstance(context)
            val lowerWord = word.lowercase(Locale.getDefault())

            // 1. 精确匹配（小写）
            var wordInfo = withContext(Dispatchers.IO) {
                db.wordDao().getBySpelling(lowerWord)
            }
            // 2. 原始大小写
            if (wordInfo == null && word != lowerWord) {
                wordInfo = withContext(Dispatchers.IO) {
                    db.wordDao().getBySpelling(word)
                }
            }
            // 3. 词干匹配（去后缀）
            if (wordInfo == null) {
                val stems = generateStems(lowerWord)
                for (stem in stems) {
                    if (stem.length >= 3) {
                        wordInfo = withContext(Dispatchers.IO) {
                            db.wordDao().getBySpelling(stem)
                        }
                        if (wordInfo != null) break
                    }
                }
            }

            val displayText = if (wordInfo != null) {
                val phonetic = wordInfo.phonetic?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                val pos = wordInfo.partOfSpeech?.takeIf { it.isNotBlank() }?.let { "[$it] " } ?: ""
                "$word$phonetic\n$pos${wordInfo.meaning}"
            } else {
                "$word\n暂无该词信息"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, displayText, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun generateStems(word: String): List<String> {
        val stems = mutableListOf(word)
        val w = word
        // -s, -es
        if (w.endsWith("es") && w.length > 3) stems.add(w.dropLast(2))
        else if (w.endsWith("s") && w.length > 2) stems.add(w.dropLast(1))
        // -ed
        if (w.endsWith("ied") && w.length > 4) { stems.add(w.dropLast(3) + "y"); stems.add(w.dropLast(1)) }
        else if (w.endsWith("ed") && w.length > 3) { stems.add(w.dropLast(2)); stems.add(w.dropLast(1)) }
        // -ing
        if (w.endsWith("ying") && w.length > 5) stems.add(w.dropLast(4) + "y")
        else if (w.endsWith("ing") && w.length > 4) { stems.add(w.dropLast(3)); stems.add(w.dropLast(3) + "e") }
        // -ly
        if (w.endsWith("ly") && w.length > 3) {
            stems.add(w.dropLast(2))
            if (w.endsWith("ily") && w.length > 4) stems.add(w.dropLast(3) + "y")
        }
        // -tion → -te
        if (w.endsWith("tion") && w.length > 5) stems.add(w.dropLast(4) + "te")
        // -ness
        if (w.endsWith("ness") && w.length > 5) stems.add(w.dropLast(4))
        // -ment
        if (w.endsWith("ment") && w.length > 5) stems.add(w.dropLast(4))
        // -er, -est
        if (w.endsWith("er") && w.length > 3) stems.add(w.dropLast(2))
        if (w.endsWith("est") && w.length > 4) stems.add(w.dropLast(3))
        return stems.distinct()
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}
