package com.example.ebook.ui.reader

import android.webkit.JavascriptInterface
import com.example.ebook.util.PageDirection

/**
 * JavaScript接口类，用于WebView和Kotlin交互
 */
@Suppress("UNUSED")
class EpubReaderJsInterface(
    private val viewModel: EpubReaderViewModel,
    private val toggleControls: () -> Unit
) {
    
    @JavascriptInterface
    fun onPageChanged(currentPage: Int, totalPages: Int) {
        viewModel.updatePageInfo(currentPage, totalPages)
    }
    
    @JavascriptInterface
    fun onTotalPagesCalculated(totalPages: Int) {
        // 只更新总页数
        viewModel.updatePageInfo(0, totalPages)
    }
    
    @JavascriptInterface
    fun onLeftTap() {
        viewModel.navigatePage(PageDirection.PREVIOUS)
    }
    
    @JavascriptInterface
    fun onRightTap() {
        viewModel.navigatePage(PageDirection.NEXT)
    }
    
    @JavascriptInterface
    fun onCenterTap() {
        // 通知中间点击
        toggleControls()
    }
    
    @JavascriptInterface
    fun onFirstPage() {
        viewModel.onFirstPage()
    }
    
    @JavascriptInterface
    fun onLastPage() {
        viewModel.onLastPage()
    }
} 