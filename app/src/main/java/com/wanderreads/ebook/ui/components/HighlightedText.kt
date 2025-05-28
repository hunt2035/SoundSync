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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * 带有高亮的文本组件
 * 
 * @param text 完整的文本内容
 * @param sentences 分割后的句子列表
 * @param highlightIndex 当前高亮的句子索引
 * @param isHighlighting 是否处于高亮状态
 * @param highlightColor 高亮颜色
 * @param textStyle 文本样式
 * @param modifier 修饰符
 */
@Composable
fun HighlightedText(
    text: String,
    sentences: List<String>,
    highlightIndex: Int,
    isHighlighting: Boolean,
    highlightColor: Color = Color(0xFF4CAF50), // 绿色
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 28.sp,
        fontFamily = FontFamily.Serif
    ),
    modifier: Modifier = Modifier
) {
    // 如果没有句子或不处于高亮状态，直接显示普通文本
    if (sentences.isEmpty() || !isHighlighting || highlightIndex < 0 || highlightIndex >= sentences.size) {
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier
        )
        return
    }
    
    // 构建带有高亮的文本
    val annotatedString = buildAnnotatedString {
        var currentPosition = 0
        
        // 遍历所有句子
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
                if (i == highlightIndex) {
                    withStyle(style = SpanStyle(color = highlightColor)) {
                        append(sentence)
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