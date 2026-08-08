package com.wordforge.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wordforge.app.data.db.converter.Converters
import com.wordforge.app.data.db.dao.AppSettingsDao
import com.wordforge.app.data.db.dao.DailyStatsDao
import com.wordforge.app.data.db.dao.FavoriteWordDao
import com.wordforge.app.data.db.dao.LearningRecordDao
import com.wordforge.app.data.db.dao.MistakeWordDao
import com.wordforge.app.data.db.dao.WordDao
import com.wordforge.app.data.db.dao.WordbookDao
import com.wordforge.app.data.db.entity.AppSettings
import com.wordforge.app.data.db.entity.DailyStats
import com.wordforge.app.data.db.entity.FavoriteWord
import com.wordforge.app.data.db.entity.LearningRecord
import com.wordforge.app.data.db.entity.MistakeWord
import com.wordforge.app.data.db.entity.Word
import com.wordforge.app.data.db.entity.Wordbook

/**
 * WordForge Room 数据库
 * 版本1，包含7张表
 */
@Database(
    entities = [
        Word::class,
        Wordbook::class,
        LearningRecord::class,
        MistakeWord::class,
        DailyStats::class,
        FavoriteWord::class,
        AppSettings::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wordDao(): WordDao
    abstract fun wordbookDao(): WordbookDao
    abstract fun learningRecordDao(): LearningRecordDao
    abstract fun mistakeWordDao(): MistakeWordDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun favoriteWordDao(): FavoriteWordDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "wordforge.db"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * 重置数据库实例（用于删除旧数据库后强制重建）
         */
        fun resetInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
