package com.wordforge.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.Locale

/**
 * 音频播放器
 * 优先使用内置音频文件，其次使用 Android TTS
 *
 * 发音优先级：
 * 1. 内置音频文件 (assets/audio/{word}.mp3)
 * 2. Android TTS (en-US)
 * 3. 静默降级
 */
class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false
    private var ttsSpeed: Float = 0.9f

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                ttsInitialized = result == TextToSpeech.LANG_AVAILABLE ||
                        result == TextToSpeech.LANG_COUNTRY_AVAILABLE
            }
        }
    }

    /**
     * 播放单词发音
     * 优先尝试内置音频，失败后使用 TTS
     *
     * @param word 单词拼写
     * @param audioPath 可选的本地音频路径（来自数据库）
     */
    fun play(word: String, audioPath: String? = null) {
        stop()

        // 优先使用指定音频路径
        if (audioPath != null && File(audioPath).exists()) {
            playLocalFile(audioPath)
            return
        }

        // 尝试缓存音频
        val cachedFile = File(context.cacheDir, "audio/${word}.wav")
        if (cachedFile.exists()) {
            playLocalFile(cachedFile.absolutePath)
            return
        }

        // 使用 TTS
        if (ttsInitialized) {
            textToSpeech?.apply {
                setSpeechRate(ttsSpeed)
                setPitch(1.0f)
                speak(word, TextToSpeech.QUEUE_FLUSH, null, "wordforge_${word}_${
                    System.currentTimeMillis()
                }")
            }
        }
    }

    private fun playLocalFile(path: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(path)
                prepare()
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) {
            release()
            // 降级到 TTS
            if (ttsInitialized) {
                val word = File(path).nameWithoutExtension
                textToSpeech?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "wordforge_fallback")
            }
        }
    }

    /**
     * 停止当前播放
     */
    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    /**
     * 设置 TTS 语速
     * @param speed 0.5f ~ 2.0f，默认 0.9f
     */
    fun setSpeed(speed: Float) {
        ttsSpeed = speed.coerceIn(0.5f, 2.0f)
    }

    /**
     * 释放所有资源
     */
    fun release() {
        stop()
        textToSpeech?.apply {
            stop()
            shutdown()
        }
        textToSpeech = null
        ttsInitialized = false
    }
}
