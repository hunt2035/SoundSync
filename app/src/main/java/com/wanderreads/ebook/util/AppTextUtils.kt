package com.wanderreads.ebook.util

import android.util.Log

/**
 * 文本处理工具类
 * 提供通用的文本处理方法
 */
object AppTextUtils {
    
    private const val TAG = "AppTextUtils"
    
    /**
     * 句子分隔正则表达式
     * 用于将文本分割成句子
     */
    private val SENTENCE_DELIMITER_REGEX = Regex(
        "([.][\\s\\n])|" +  // 英文标点后跟空白或换行
       // "([.!?;:]$)|" +     // 英文标点在行尾
        "[!?;:]|" +         // 英文标点
        "[。！？；：]|" +    // 中文标点
        "\\.{3,}|…{1,}|" +  // 英文省略号和中文省略号
        "\\n|\\r\\n"        // 换行或回车换行也作为分隔符
    )
    
    /**
     * 段落分隔正则表达式
     * 用于识别段落分隔符
     */
    private val PARAGRAPH_DELIMITER_REGEX = Regex("\\n\\n|\\r\\n\\r\\n")
    
    /**
     * 单行分隔正则表达式
     * 用于识别单行分隔符
     */
    private val LINE_DELIMITER_REGEX = Regex("\\n|\\r\\n")
    
    /**
     * 将文本分割为句子
     * 
     * @param text 要分割的文本
     * @return 句子列表
     */
    fun splitTextIntoSentences(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        
        // 使用句子分隔正则表达式分割文本
        return text.split(SENTENCE_DELIMITER_REGEX)
            .filter { it.isNotBlank() }  // 过滤空白句子
    }
    
    /**
     * 将文本分割为段落
     * 
     * @param text 要分割的文本
     * @return 段落列表
     */
    fun splitTextIntoParagraphs(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        
        // 首先按段落分割（空行分隔）
        val paragraphs = text.split(PARAGRAPH_DELIMITER_REGEX)
        
        // 如果只有一个段落，可能没有空行分隔，尝试按单个换行符分割
        if (paragraphs.size <= 1 && text.contains("\n")) {
            return text.split(LINE_DELIMITER_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        
        return paragraphs.map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    
    /**
     * 将文本分割为带有原始位置信息的句子
     * 
     * @param text 要分割的文本
     * @return 句子列表，每个句子包含原始文本中的起始位置
     */
    fun splitTextIntoSentencesWithPositions(text: String): List<SentenceInfo> {
        if (text.isEmpty()) return emptyList()
        
        val result = mutableListOf<SentenceInfo>()
        val matcher = SENTENCE_DELIMITER_REGEX.toPattern().matcher(text)
        
        var lastEnd = 0
        while (matcher.find()) {
            val start = lastEnd
            val end = matcher.end()
            
            // 提取句子内容（不包括分隔符）
            val sentenceText = text.substring(start, matcher.start())
            if (sentenceText.isNotBlank()) {
                result.add(SentenceInfo(sentenceText, start, matcher.start()))
            }
            
            lastEnd = end
        }
        
        // 添加最后一个句子
        if (lastEnd < text.length) {
            val lastSentence = text.substring(lastEnd)
            if (lastSentence.isNotBlank()) {
                result.add(SentenceInfo(lastSentence, lastEnd, text.length))
            }
        }
        
        return result
    }
    
    /**
     * 将大文本分割成适合TTS处理的块
     * 
     * @param text 要分割的文本
     * @param maxChunkSize 每个块的最大大小
     * @return 文本块列表
     */
    fun splitLargeTextIntoChunks(text: String, maxChunkSize: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        
        try {
            // 将文本分成自然段落
            val paragraphs = text.split(LINE_DELIMITER_REGEX)
            val chunks = mutableListOf<String>()
            val currentChunk = StringBuilder()
            
            // 按段落组织块，确保每个块不超过最大大小
            for (paragraph in paragraphs) {
                // 如果当前段落加上当前块会超过大小限制，则开始新块
                if (currentChunk.length + paragraph.length > maxChunkSize) {
                    // 如果当前块不为空，添加到块列表
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString())
                        currentChunk.clear()
                    }
                    
                    // 如果单个段落就超过了块大小限制，需要进一步分割
                    if (paragraph.length > maxChunkSize) {
                        // 按句子分割大段落
                        val sentences = splitTextIntoSentences(paragraph)
                        
                        for (sentence in sentences) {
                            if (currentChunk.length + sentence.length > maxChunkSize) {
                                if (currentChunk.isNotEmpty()) {
                                    chunks.add(currentChunk.toString())
                                    currentChunk.clear()
                                }
                                
                                // 如果单个句子还是太长，按字符数直接分割
                                if (sentence.length > maxChunkSize) {
                                    var start = 0
                                    while (start < sentence.length) {
                                        val end = minOf(start + maxChunkSize, sentence.length)
                                        chunks.add(sentence.substring(start, end))
                                        start = end
                                    }
                                } else {
                                    currentChunk.append(sentence)
                                }
                            } else {
                                currentChunk.append(sentence)
                            }
                        }
                    } else {
                        // 段落可以作为一个新块
                        currentChunk.append(paragraph)
                    }
                } else {
                    // 添加段落到当前块
                    if (currentChunk.isNotEmpty()) {
                        currentChunk.append("\n")
                    }
                    currentChunk.append(paragraph)
                }
            }
            
            // 添加最后一个块
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
            }
            
            Log.d(TAG, "文本已分为 ${chunks.size} 个块进行处理")
            return chunks
            
        } catch (e: Exception) {
            Log.e(TAG, "分割大文本出错", e)
            // 如果分割失败，返回整个文本作为一个块
            return listOf(text)
        }
    }
    
    /**
     * 句子信息类，包含句子文本和原始位置
     */
    data class SentenceInfo(
        val text: String,       // 句子文本
        val startPosition: Int, // 在原文中的起始位置
        val endPosition: Int    // 在原文中的结束位置
    )
} 