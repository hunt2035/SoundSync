package com.wanderreads.ebook.domain.model

/**
 * 语音合成参数数据类
 */
data class SynthesisParams(
    val speechRate: Float = 1.0f,  // 语速
    val pitch: Float = 1.0f,       // 音调
    val volume: Float = 0.8f,      // 音量
    val synthesisRange: SynthesisRange = SynthesisRange.CURRENT_CHAPTER  // 合成范围
)

/**
 * 语音合成范围枚举
 */
enum class SynthesisRange {
    CURRENT_PAGE,      // 当前页
    CURRENT_CHAPTER    // 当前章节
} 