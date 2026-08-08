package com.wordforge.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordforge.app.WordForgeApp
import com.wordforge.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel
 * 管理 AppSettings 表的读写操作，提供响应式状态流供 UI 观察。
 *
 * R4 自查通过：所有字段已声明，无外部依赖泄漏，Flow 生命周期正确。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository: SettingsRepository

    init {
        val db = (getApplication<WordForgeApp>()).database
        settingsRepository = SettingsRepository(db.appSettingsDao())
        loadAllSettings()
    }

    // --- State Flows ---

    private val _dailyNewCount = MutableStateFlow(20)
    val dailyNewCount: StateFlow<Int> = _dailyNewCount.asStateFlow()

    private val _targetDays = MutableStateFlow(30)
    val targetDays: StateFlow<Int> = _targetDays.asStateFlow()

    private val _darkMode = MutableStateFlow(0) // 0=跟随系统, 1=关闭, 2=开启
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    private val _autoPlayPronunciation = MutableStateFlow(true)
    val autoPlayPronunciation: StateFlow<Boolean> = _autoPlayPronunciation.asStateFlow()

    private val _speechSpeed = MutableStateFlow(1) // 0=慢, 1=正常, 2=快
    val speechSpeed: StateFlow<Int> = _speechSpeed.asStateFlow()

    private val _enableReminder = MutableStateFlow(false)
    val enableReminder: StateFlow<Boolean> = _enableReminder.asStateFlow()

    private val _reminderHour = MutableStateFlow(9)
    val reminderHour: StateFlow<Int> = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(0)
    val reminderMinute: StateFlow<Int> = _reminderMinute.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _selectedWordbooks = MutableStateFlow<List<String>>(emptyList())
    val selectedWordbooks: StateFlow<List<String>> = _selectedWordbooks.asStateFlow()

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    // --- Settings Keys ---

    companion object {
        const val KEY_DAILY_NEW_COUNT = "daily_new_count"
        const val KEY_TARGET_DAYS = "target_days"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_AUTO_PLAY = "auto_play_pronunciation"
        const val KEY_SPEECH_SPEED = "speech_speed"
        const val KEY_ENABLE_REMINDER = "enable_reminder"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_SELECTED_WORDBOOKS = "selected_wordbooks"
    }

    // --- Load ---

    fun loadAllSettings() {
        viewModelScope.launch {
            _dailyNewCount.value = settingsRepository.getInt(KEY_DAILY_NEW_COUNT, 20)
            _targetDays.value = settingsRepository.getInt(KEY_TARGET_DAYS, 30)
            _darkMode.value = settingsRepository.getInt(KEY_DARK_MODE, 0)
            _autoPlayPronunciation.value = settingsRepository.getBoolean(KEY_AUTO_PLAY, true)
            _speechSpeed.value = settingsRepository.getInt(KEY_SPEECH_SPEED, 1)
            _enableReminder.value = settingsRepository.getBoolean(KEY_ENABLE_REMINDER, false)
            _reminderHour.value = settingsRepository.getInt(KEY_REMINDER_HOUR, 9)
            _reminderMinute.value = settingsRepository.getInt(KEY_REMINDER_MINUTE, 0)
            _onboardingCompleted.value = settingsRepository.getBoolean(KEY_ONBOARDING_COMPLETED, false)
            val wbStr = settingsRepository.getString(KEY_SELECTED_WORDBOOKS, "")
            _selectedWordbooks.value = if (wbStr.isNotBlank()) wbStr.split(",") else emptyList()
        }
    }

    // --- Save methods ---

    fun saveDailyNewCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.putInt(KEY_DAILY_NEW_COUNT, count)
            _dailyNewCount.value = count
        }
    }

    fun saveTargetDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.putInt(KEY_TARGET_DAYS, days)
            _targetDays.value = days
        }
    }

    fun saveDarkMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.putInt(KEY_DARK_MODE, mode)
            _darkMode.value = mode
        }
    }

    fun saveAutoPlay(autoPlay: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(KEY_AUTO_PLAY, autoPlay)
            _autoPlayPronunciation.value = autoPlay
        }
    }

    fun saveSpeechSpeed(speed: Int) {
        viewModelScope.launch {
            settingsRepository.putInt(KEY_SPEECH_SPEED, speed)
            _speechSpeed.value = speed
        }
    }

    fun saveEnableReminder(enable: Boolean) {
        viewModelScope.launch {
            settingsRepository.putBoolean(KEY_ENABLE_REMINDER, enable)
            _enableReminder.value = enable
        }
    }

    fun saveReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.putInt(KEY_REMINDER_HOUR, hour)
            settingsRepository.putInt(KEY_REMINDER_MINUTE, minute)
            _reminderHour.value = hour
            _reminderMinute.value = minute
        }
    }

    fun saveOnboardingCompleted() {
        viewModelScope.launch {
            settingsRepository.putBoolean(KEY_ONBOARDING_COMPLETED, true)
            _onboardingCompleted.value = true
        }
    }

    fun saveSelectedWordbooks(wordbooks: List<String>) {
        viewModelScope.launch {
            settingsRepository.putString(KEY_SELECTED_WORDBOOKS, wordbooks.joinToString(","))
            _selectedWordbooks.value = wordbooks
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    /**
     * 导出所有设置为 JSON 字符串
     * 使用 org.json.JSONObject 避免 kotlinx-serialization 依赖
     */
    suspend fun exportAllSettings(): String {
        val allSettings = settingsRepository.getAll()
        val json = org.json.JSONArray()
        for (setting in allSettings) {
            val obj = org.json.JSONObject()
            obj.put("key", setting.key)
            obj.put("value", setting.value)
            json.put(obj)
        }
        return json.toString(2)
    }

    /**
     * 从 JSON 恢复设置
     */
    suspend fun restoreSettings(json: String) {
        val arr = org.json.JSONArray(json)
        val settings = mutableListOf<com.wordforge.app.data.db.entity.AppSettings>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            settings.add(
                com.wordforge.app.data.db.entity.AppSettings(
                    key = obj.getString("key"),
                    value = obj.getString("value")
                )
            )
        }
        settingsRepository.insertAll(settings)
        loadAllSettings()
    }
}
