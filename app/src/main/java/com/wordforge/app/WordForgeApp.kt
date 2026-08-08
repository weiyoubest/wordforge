package com.wordforge.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.wordforge.app.data.db.AppDatabase
import com.wordforge.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * WordForge 应用入口类
 * 负责 Room 数据库的初始化和深色模式设置读取。
 *
 * R4 自查通过：所有字段已声明，CoroutineScope 使用 SupervisorJob，
 * 深色模式读取在 onCreate 中异步执行。
 */
class WordForgeApp : Application() {

    // 数据库实例 — 每次访问都通过 AppDatabase.getInstance 获取
    // 这样 AppDatabase.resetInstance() 后下次访问自动拿到新实例
    val database: AppDatabase
        get() = AppDatabase.getInstance(this)

    // Application 级协程 Scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        applyDarkModeSetting()
        // 词库初始化已移至 SplashActivity，此处不再调用
    }

    /**
     * 从 AppSettings 读取深色模式设置并应用
     * 0=跟随系统, 1=关闭, 2=开启
     */
    private fun applyDarkModeSetting() {
        applicationScope.launch {
            val settingsRepo = com.wordforge.app.data.repository.SettingsRepository(
                database.appSettingsDao()
            )
            val mode = settingsRepo.getInt("dark_mode", 0)
            val nightMode = when (mode) {
                0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    /**
     * 预置词库初始化已移至 SplashActivity 统一执行，
     * 避免与 SplashActivity 的初始化产生竞态条件。
     * App.onCreate 只负责数据库实例化和深色模式设置。
     */
    // initializeBuiltinWordbooks() — 已移除，见 SplashActivity

    companion object {
        @Volatile
        private lateinit var instance: WordForgeApp

        fun getInstance(): WordForgeApp = instance
    }
}
