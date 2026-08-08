package com.wordforge.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.data.db.entity.LearningRecord
import com.wordforge.app.data.db.entity.MistakeWord
import com.wordforge.app.data.db.entity.Word
import com.wordforge.app.data.db.entity.Wordbook
import com.wordforge.app.data.repository.LearningRecordRepository
import com.wordforge.app.data.repository.MistakeWordRepository
import com.wordforge.app.data.repository.SettingsRepository
import com.wordforge.app.data.repository.WordRepository
import com.wordforge.app.data.repository.WordbookRepository
import com.wordforge.app.service.AudioPlayer
import com.wordforge.app.service.ReviewScheduler
import com.wordforge.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 学习页 ViewModel（T09）
 * 管理今日新词列表、认知标记、复习调度
 */
class LearnViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val wordRepo = WordRepository(db.wordDao())
    private val learningRecordRepo = LearningRecordRepository(db.learningRecordDao())
    private val mistakeWordRepo = MistakeWordRepository(db.mistakeWordDao())
    private val wordbookRepo = WordbookRepository(db.wordbookDao())
    private val settingsRepo = SettingsRepository(db.appSettingsDao())

    private val audioPlayer = AudioPlayer(application)
    private val today = DateUtils.today()

    /** 今日待学新词列表 */
    private val _wordList = MutableLiveData<List<Word>>(emptyList())
    val wordList: LiveData<List<Word>> = _wordList

    /** 每个单词的剩余重复次数（key=word.id, value=剩余次数） */
    private val repeatMap = mutableMapOf<Long, Int>()

    /** 当前单词索引 */
    private val _currentIndex = MutableLiveData(0)
    val currentIndex: LiveData<Int> = _currentIndex

    /** 卡片是否翻转到背面 */
    private val _isFlipped = MutableLiveData(false)
    val isFlipped: LiveData<Boolean> = _isFlipped

    /** 认知标记统计 */
    private val _familiarCount = MutableLiveData(0)
    val familiarCount: LiveData<Int> = _familiarCount

    private val _vagueCount = MutableLiveData(0)
    val vagueCount: LiveData<Int> = _vagueCount

    private val _unknownCount = MutableLiveData(0)
    val unknownCount: LiveData<Int> = _unknownCount

    /** 学习完成弹窗数据 */
    private val _showCompleteDialog = MutableLiveData(false)
    val showCompleteDialog: LiveData<Boolean> = _showCompleteDialog

    /** 加载状态 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 是否为空（无新词可学） */
    private val _isEmpty = MutableLiveData(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    /** 错误信息 */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /** 当前活跃词库 */
    private var currentWordbookId: Long = 0

    /** 活跃词库引用，用于外部访问 */
    private val _activeWordbook = MutableLiveData<Wordbook?>()
    val activeWordbook: LiveData<Wordbook?> = _activeWordbook

    init {
        loadWords()
    }

    /**
     * 加载今日待学新词
     */
    fun loadWords() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val wordbook = withContext(Dispatchers.IO) {
                    wordbookRepo.getActiveWordbook()
                }
                _activeWordbook.postValue(wordbook)

                if (wordbook == null) {
                    _isEmpty.postValue(true)
                    _isLoading.postValue(false)
                    return@launch
                }

                currentWordbookId = wordbook.id
                val dailyTarget = withContext(Dispatchers.IO) {
                    settingsRepo.getInt("daily_new_words", 20)
                }

                val alreadyLearnedToday = withContext(Dispatchers.IO) {
                    learningRecordRepo.countNewWordsOnDate(today)
                }

                val remaining = dailyTarget - alreadyLearnedToday
                if (remaining <= 0) {
                    _isEmpty.postValue(true)
                    _isLoading.postValue(false)
                    return@launch
                }

                val words = withContext(Dispatchers.IO) {
                    wordRepo.getRandomUnlearned(wordbook.id, remaining.coerceAtMost(50))
                }

                if (words.isEmpty()) {
                    _isEmpty.postValue(true)
                } else {
                    _wordList.postValue(words)
                    _currentIndex.postValue(0)
                    _isFlipped.postValue(false)
                    _isEmpty.postValue(false)
                }
            } catch (e: Exception) {
                _errorMessage.postValue(e.message ?: "加载失败")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * 继续上次学习（从第几个词开始）
     */
    fun continueFrom(position: Int) {
        val list = _wordList.value ?: return
        if (position in list.indices) {
            _currentIndex.value = position
            _isFlipped.value = false
        }
    }

    /**
     * 翻转卡片
     */
    fun flipCard() {
        _isFlipped.value = !(_isFlipped.value ?: false)
    }

    /**
     * 播放发音
     */
    fun playPronunciation(word: Word?) {
        word?.let { audioPlayer.play(it.spelling, it.audioPath) }
    }

    /**
     * 处理认知标记
     * @param cognitionLevel "familiar" / "vague" / "unknown"
     */
    fun markCognition(cognitionLevel: String) {
        viewModelScope.launch {
            val list = _wordList.value ?: return@launch
            val idx = _currentIndex.value ?: return@launch
            if (idx >= list.size) return@launch

            val word = list[idx]
            val now = System.currentTimeMillis()

            // 更新统计
            when (cognitionLevel) {
                "familiar" -> _familiarCount.value = (_familiarCount.value ?: 0) + 1
                "vague" -> _vagueCount.value = (_vagueCount.value ?: 0) + 1
                "unknown" -> _unknownCount.value = (_unknownCount.value ?: 0) + 1
            }

            // 计算复习调度
            val currentRecord = withContext(Dispatchers.IO) {
                learningRecordRepo.getLatestByWordId(word.id)
            }

            val currentStage = currentRecord?.reviewStage ?: 0
            val (newStage, nextReviewAt) = ReviewScheduler.processCognition(
                currentStage, cognitionLevel, now
            )

            // 保存学习记录
            withContext(Dispatchers.IO) {
                val record = LearningRecord(
                    wordId = word.id,
                    recordType = "new",
                    cognitionLevel = cognitionLevel,
                    reviewedAt = now,
                    nextReviewAt = nextReviewAt,
                    reviewStage = newStage,
                    wordbookId = currentWordbookId,
                    sessionDate = today
                )
                learningRecordRepo.insert(record)

                // 如果标记为"不认识"，加入错词本
                if (cognitionLevel == "unknown") {
                    val existing = mistakeWordRepo.getByWordId(word.id)
                    if (existing == null) {
                        mistakeWordRepo.insert(
                            MistakeWord(
                                wordId = word.id,
                                wordbookId = currentWordbookId
                            )
                        )
                    } else {
                        mistakeWordRepo.incrementErrorCount(word.id, now)
                    }
                }
            }

            // 移动到下一个单词（扇贝模式：不认识/模糊的词重复出现）
            _isFlipped.postValue(false)

            when (cognitionLevel) {
                "familiar" -> {
                    // 认识：正常跳下一个
                    repeatMap.remove(word.id)
                    if (idx + 1 < list.size) {
                        _currentIndex.postValue(idx + 1)
                    } else {
                        checkSessionComplete(list)
                    }
                }
                "vague", "unknown" -> {
                    // 不认识/模糊：放回队列尾部重复
                    val remaining = repeatMap.getOrDefault(word.id, if (cognitionLevel == "unknown") 3 else 2)
                    if (remaining > 0) {
                        repeatMap[word.id] = remaining - 1
                        // 把当前词移到队列尾部
                        val mutableList = list.toMutableList()
                        mutableList.removeAt(idx)
                        mutableList.add(word)
                        _wordList.postValue(mutableList)
                        _currentIndex.postValue(mutableList.size - 1)
                    } else {
                        // 重复次数用完，正常跳下一个
                        repeatMap.remove(word.id)
                        if (idx + 1 < list.size) {
                            _currentIndex.postValue(idx + 1)
                        } else {
                            checkSessionComplete(list)
                        }
                    }
                }
            }
        }
    }

    /** 检查本轮学习是否完成（所有新词+重复词都已处理） */
    private fun checkSessionComplete(list: List<Word>) {
        if (repeatMap.isEmpty()) {
            _showCompleteDialog.postValue(true)
        } else {
            // 还有重复词没处理完，提示继续
            val remaining = repeatMap.size
            // repeatMap中都是还没到上限的词，但他们已经不在list尾部了
            // 实际上上面的逻辑已经把词放回尾部了，所以到这里说明全部处理完
            _showCompleteDialog.postValue(true)
        }
    }

    /**
     * 获取总进度信息
     */
    fun getProgressInfo(): Triple<Int, Int, Float> {
        val list = _wordList.value ?: emptyList()
        val idx = _currentIndex.value ?: 0
        val total = list.size
        val current = (idx + 1).coerceAtMost(total)
        val familiar = (_familiarCount.value ?: 0).toFloat()
        val totalAnswered = familiar + (_vagueCount.value ?: 0) + (_unknownCount.value ?: 0)
        val accuracy = if (totalAnswered > 0) familiar / totalAnswered * 100f else 0f
        return Triple(current, total, accuracy)
    }

    /** 获取不重复的新词总数（排除重复加入的词） */
    fun getUniqueWordCount(): Int {
        return (_wordList.value?.distinctBy { it.id }?.size) ?: 0
    }

    /**
     * 释放音频资源
     */
    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
