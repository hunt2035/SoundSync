package org.soundsync.ebook.util

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLineProcessorTest {

    @Test
    fun `processOcrText should preserve newlines before lines starting with two spaces`() {
        val input = """
            这是第一行
            这是第二行
              这是缩进的第三行
            这是第四行
        """.trimIndent()
        
        val expected = """
            这是第一行 这是第二行
              这是缩进的第三行
            这是第四行
        """.trimIndent()
        
        val result = OcrLineProcessor.processOcrText(input)
        assertEquals(expected, result)
    }
    
    @Test
    fun `processOcrText should preserve newlines before lines starting with tab`() {
        val input = """
            这是第一行
            这是第二行
            	这是Tab缩进的第三行
            这是第四行
        """.trimIndent()
        
        val expected = """
            这是第一行 这是第二行
            	这是Tab缩进的第三行
            这是第四行
        """.trimIndent()
        
        val result = OcrLineProcessor.processOcrText(input)
        assertEquals(expected, result)
    }
    
    @Test
    fun `processOcrText should remove newlines between regular lines`() {
        val input = """
            这是第一段第一行
            这是第一段第二行
            这是第一段第三行
            
            这是第二段第一行
            这是第二段第二行
        """.trimIndent()
        
        val expected = """
            这是第一段第一行 这是第一段第二行 这是第一段第三行

            这是第二段第一行 这是第二段第二行
        """.trimIndent()
        
        val result = OcrLineProcessor.processOcrText(input)
        assertEquals(expected, result)
    }
    
    @Test
    fun `processOcrText should handle empty input`() {
        val input = ""
        val expected = ""
        val result = OcrLineProcessor.processOcrText(input)
        assertEquals(expected, result)
    }
    
    @Test
    fun `processOcrText should handle input with only blank lines`() {
        val input = "\n\n\n"
        val expected = "\n\n"
        val result = OcrLineProcessor.processOcrText(input)
        assertEquals(expected, result)
    }
    
    @Test
    fun `processOcrText should handle complex mixed content`() {
        val input = """
            标题行
            
            这是正文第一段，这是一个很长的句子，
            由于OCR识别，这里被分成了两行。
            
              这是一个缩进的段落，
              这个段落的第二行也是缩进的。
            
            这又是一个普通段落，
            同样被OCR分成了多行，
            需要合并处理。
            	这行以Tab开头，
            	应该保留换行符。
            
            最后一个段落。
        """.trimIndent()
        
        val expected = """
            标题行

            这是正文第一段，这是一个很长的句子， 由于OCR识别，这里被分成了两行。

              这是一个缩进的段落，
              这个段落的第二行也是缩进的。

            这又是一个普通段落， 同样被OCR分成了多行， 需要合并处理。
            	这行以Tab开头，
            	应该保留换行符。

            最后一个段落。
        """.trimIndent()
        
        val result = OcrLineProcessor.processOcrText(input)
        assertEquals(expected, result)
    }
} 