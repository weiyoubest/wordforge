package com.wordforge.app.service

/**
 * 艾宾浩斯遗忘曲线复习调度器
 * 管理复习间隔计算和复习队列排序
 *
 * 复习间隔表（9个阶段，Stage 8 标记为已掌握）：
 * Stage 0: 5分钟
 * Stage 1: 30分钟
 * Stage 2: 12小时
 * Stage 3: 1天
 * Stage 4: 2天
 * Stage 5: 4天
 * Stage 6: 7天
 * Stage 7: 15天
 * Stage 8: 已掌握
 */
object ReviewScheduler {

    // 各阶段的复习间隔（毫秒）
    private val INTERVALS_MS = longArrayOf(
        5 * 60 * 1000L,               // Stage 0: 5分钟
        30 * 60 * 1000L,              // Stage 1: 30分钟
        12 * 60 * 60 * 1000L,         // Stage 2: 12小时
        24 * 60 * 60 * 1000L,         // Stage 3: 1天
        2 * 24 * 60 * 60 * 1000L,     // Stage 4: 2天
        4 * 24 * 60 * 60 * 1000L,     // Stage 5: 4天
        7 * 24 * 60 * 60 * 1000L,     // Stage 6: 7天
        15 * 24 * 60 * 60 * 1000L,    // Stage 7: 15天
    )

    // 各阶段的中文描述
    val STAGE_LABELS = arrayOf(
        "5分钟", "30分钟", "12小时", "1天", "2天", "4天", "7天", "15天"
    )

    const val MAX_STAGE = 8 // 已掌握

    /**
     * 认知标记处理
     * @param currentStage 当前复习阶段
     * @param cognitionLevel 认知标记: "familiar", "vague", "unknown"
     * @param currentTime 当前时间戳
     * @return Pair<新阶段, 下次复习时间>，如果已掌握则 nextReviewAt 为 null
     */
    fun processCognition(
        currentStage: Int,
        cognitionLevel: String,
        currentTime: Long = System.currentTimeMillis()
    ): Pair<Int, Long?> {
        return when (cognitionLevel) {
            "familiar" -> {
                val newStage = currentStage + 1
                if (newStage >= MAX_STAGE) {
                    Pair(MAX_STAGE, null) // 已掌握
                } else {
                    val interval = INTERVALS_MS.getOrElse(newStage) { INTERVALS_MS.last() }
                    Pair(newStage, currentTime + interval)
                }
            }

            "vague" -> {
                val newStage = maxOf(currentStage - 1, 0)
                val interval = INTERVALS_MS[newStage]
                Pair(newStage, currentTime + interval)
            }

            "unknown" -> {
                val interval = INTERVALS_MS[0]
                Pair(0, currentTime + interval)
            }

            else -> {
                // 未知标记，按模糊处理
                val newStage = maxOf(currentStage - 1, 0)
                val interval = INTERVALS_MS[newStage]
                Pair(newStage, currentTime + interval)
            }
        }
    }

    /**
     * 获取指定阶段的间隔描述
     */
    fun getIntervalLabel(stage: Int): String {
        return STAGE_LABELS.getOrElse(stage) { "已掌握" }
    }

    /**
     * 获取指定阶段的间隔毫秒数
     */
    fun getIntervalMs(stage: Int): Long? {
        return INTERVALS_MS.getOrElse(stage) { 0L }
    }
}
