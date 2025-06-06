package com.wanderreads.ebook.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.wanderreads.ebook.util.TtsManager

/**
 * 带有高亮的文本组件
 * 
 * @param text 完整的文本内容
 * @param sentences 分割后的句子列表
 * @param highlightIndex 当前高亮的句子索引
 * @param isHighlighting 是否处于高亮状态
 * @param currentWordIndex 当前朗读的单词索引（-1表示不高亮任何单词）
 * @param ttsStatus TTS当前状态，决定高亮颜色
 * @param textStyle 文本样式
 * @param modifier 修饰符
 */
@Composable
fun HighlightedText(
    text: String,
    sentences: List<String>,
    highlightIndex: Int,
    isHighlighting: Boolean,
    currentWordIndex: Int = -1, // 新增参数：当前朗读的单词索引
    ttsStatus: Int = TtsManager.STATUS_PLAYING,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 28.sp,
        fontFamily = FontFamily.Serif
    ),
    modifier: Modifier = Modifier
) {
    // 根据TTS状态决定高亮颜色
    val highlightColor = when (ttsStatus) {
        TtsManager.STATUS_PAUSED -> Color(0xFFFFA500) // 橙色
        else -> Color(0xFF4CAF50) // 绿色
    }
    
    // 浅绿色用于未朗读的单词
    val lightGreenColor = Color(0xFFB5E6B5) // 浅绿色
    
    // 如果没有句子或不处于高亮状态，直接显示普通文本
    if (sentences.isEmpty() || !isHighlighting || highlightIndex < 0) {
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier
        )
        return
    }
    
    // 获取当前高亮的句子内容
    val highlightSentence = if (highlightIndex >= 0 && highlightIndex < sentences.size) {
        sentences[highlightIndex]
    } else {
        ""
    }
    
    // 构建带有高亮的文本
    val annotatedString = buildAnnotatedString {
        var currentPosition = 0
        
        // 遍历段落中的所有句子
        for (i in sentences.indices) {
            val sentence = sentences[i]
            
            // 在原文中查找当前句子
            val sentenceStart = text.indexOf(sentence, currentPosition)
            
            if (sentenceStart >= 0) {
                // 添加句子前的文本
                if (sentenceStart > currentPosition) {
                    append(text.substring(currentPosition, sentenceStart))
                }
                
                // 添加句子，如果是当前朗读的句子则高亮
                if (sentence == highlightSentence && highlightSentence.isNotEmpty()) {
                    // 如果启用了单词级高亮
                    if (currentWordIndex >= 0 && ttsStatus == TtsManager.STATUS_PLAYING) {
                        // 将句子分割成单词
                        val words = sentence.split(Regex("\\s+"))
                        var wordPosition = 0
                        
                        // 遍历单词
                        for (wordIndex in words.indices) {
                            val word = words[wordIndex]
                            val wordStart = sentence.indexOf(word, wordPosition)
                            
                            if (wordStart >= 0) {
                                // 添加单词前的空格或其他字符
                                if (wordStart > wordPosition) {
                                    append(sentence.substring(wordPosition, wordStart))
                                }
                                
                                // 根据单词位置设置不同颜色
                                if (wordIndex < currentWordIndex) {
                                    // 已朗读的单词，使用标准绿色
                                    withStyle(style = SpanStyle(color = highlightColor)) {
                                        append(word)
                                    }
                                } else if (wordIndex == currentWordIndex) {
                                    // 当前朗读的单词，使用标准绿色
                                    withStyle(style = SpanStyle(color = highlightColor)) {
                                        append(word)
                                    }
                                } else {
                                    // 未朗读的单词，使用浅绿色
                                    withStyle(style = SpanStyle(color = lightGreenColor)) {
                                        append(word)
                                    }
                                }
                                
                                // 更新单词位置
                                wordPosition = wordStart + word.length
                            }
                        }
                        
                        // 添加句子中剩余部分
                        if (wordPosition < sentence.length) {
                            append(sentence.substring(wordPosition))
                        }
                    } else {
                        // 普通句子高亮模式
                        withStyle(style = SpanStyle(color = highlightColor)) {
                            append(sentence)
                        }
                    }
                } else {
                    append(sentence)
                }
                
                // 更新当前位置
                currentPosition = sentenceStart + sentence.length
            }
        }
        
        // 添加剩余文本
        if (currentPosition < text.length) {
            append(text.substring(currentPosition))
        }
    }
    
    // 显示带有高亮的文本
    Text(
        text = annotatedString,
        style = textStyle,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
} 