package com.wordforge.app.ui.worddetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.data.db.entity.FavoriteWord
import com.wordforge.app.data.db.entity.MistakeWord
import com.wordforge.app.data.db.entity.Word
import com.wordforge.app.data.repository.FavoriteWordRepository
import com.wordforge.app.data.repository.LearningRecordRepository
import com.wordforge.app.data.repository.MistakeWordRepository
import com.wordforge.app.data.repository.WordRepository
import com.wordforge.app.service.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 单词详情 ViewModel（T12）
 * 加载单词详细信息，管理收藏和错词本状态
 */
class WordDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val wordRepo = WordRepository(db.wordDao())
    private val favoriteRepo = FavoriteWordRepository(db.favoriteWordDao())
    private val mistakeRepo = MistakeWordRepository(db.mistakeWordDao())
    private val learningRecordRepo = LearningRecordRepository(db.learningRecordDao())

    private val audioPlayer = AudioPlayer(application)

    /** 单词详情 */
    private val _word = MutableLiveData<Word?>()
    val word: LiveData<Word?> = _word

    /** 是否已收藏 */
    private val _isFavorited = MutableLiveData(false)
    val isFavorited: LiveData<Boolean> = _isFavorited

    /** 是否在错词本中 */
    private val _isInMistakeBook = MutableLiveData(false)
    val isInMistakeBook: LiveData<Boolean> = _isInMistakeBook

    /** 词根词缀原始文本 */
    private val _rootAffixText = MutableLiveData<String?>()
    val rootAffixText: LiveData<String?> = _rootAffixText

    /** 词根词缀解析后是否包含可分段数据（用于决定显示可视化还是纯文本） */
    private val _rootAffixParsed = MutableLiveData(false)
    val rootAffixParsed: LiveData<Boolean> = _rootAffixParsed

    /** 近义词列表 */
    private val _synonyms = MutableLiveData<List<String>>(emptyList())
    val synonyms: LiveData<List<String>> = _synonyms

    /** 反义词列表 */
    private val _antonyms = MutableLiveData<List<String>>(emptyList())
    val antonyms: LiveData<List<String>> = _antonyms

    /** 易混淆词列表（JSON 解析后） */
    private val _confusableWords = MutableLiveData<List<ConfusableWord>>(emptyList())
    val confusableWords: LiveData<List<ConfusableWord>> = _confusableWords

    /**
     * 加载单词详情
     */
    fun loadWord(wordId: Long) {
        viewModelScope.launch {
            val word = withContext(Dispatchers.IO) {
                wordRepo.getById(wordId)
            }
            _word.postValue(word)

            if (word != null) {
                loadFavoriteState(wordId)
                loadMistakeState(wordId)
                parseWordDetails(word)
            }
        }
    }

    private suspend fun loadFavoriteState(wordId: Long) {
        val isFav = withContext(Dispatchers.IO) {
            favoriteRepo.getByWordId(wordId) != null
        }
        _isFavorited.postValue(isFav)
    }

    private suspend fun loadMistakeState(wordId: Long) {
        val isInMistake = withContext(Dispatchers.IO) {
            mistakeRepo.getByWordId(wordId) != null
        }
        _isInMistakeBook.postValue(isInMistake)
    }

    private fun parseWordDetails(word: Word) {
        _rootAffixText.value = word.rootAffix
        // 判断词根词缀是否包含可拆分的分隔符
        _rootAffixParsed.value = word.rootAffix?.let {
            it.contains("/") || (it.contains("-") && it.count { c -> c == '-' } >= 2) || it.contains(" + ")
        } ?: false
        _synonyms.value = word.synonyms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        _antonyms.value = word.antonyms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        // 解析易混淆词 JSON
        _confusableWords.value = parseConfusableWords(word.confusableWords)
    }

    /**
     * 解析易混淆词 JSON
     * 格式：[{"word":"desert","meaning":"v.抛弃(军事含义)"},...]
     */
    private fun parseConfusableWords(json: String?): List<ConfusableWord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            org.json.JSONArray(json).let { array ->
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    ConfusableWord(
                        word = obj.optString("word", ""),
                        meaning = obj.optString("meaning", "")
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 播放发音
     */
    fun playPronunciation() {
        _word.value?.let { audioPlayer.play(it.spelling, it.audioPath) }
    }

    /**
     * 切换收藏状态
     */
    fun toggleFavorite() {
        val word = _word.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (_isFavorited.value == true) {
                    favoriteRepo.deleteByWordId(word.id)
                } else {
                    favoriteRepo.insert(FavoriteWord(wordId = word.id))
                }
            }
            _isFavorited.value = !(_isFavorited.value ?: false)
        }
    }

    /**
     * 切换错词本状态
     */
    fun toggleMistakeBook() {
        val word = _word.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (_isInMistakeBook.value == true) {
                    mistakeRepo.deleteByWordId(word.id)
                } else {
                    mistakeRepo.insert(MistakeWord(wordId = word.id, wordbookId = word.wordbookId))
                }
            }
            _isInMistakeBook.value = !(_isInMistakeBook.value ?: false)
        }
    }

    /**
     * 易混淆词数据类
     */
    data class ConfusableWord(
        val word: String,
        val meaning: String
    )

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
