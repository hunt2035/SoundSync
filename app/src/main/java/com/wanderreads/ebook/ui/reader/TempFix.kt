// 这是一个临时修复文件
package com.wanderreads.ebook.ui.reader

// 要修复的代码片段1 - 字体选项和列表项
// 问题: String和Int类型不匹配
// 解决方案: 确保fontFamily为字符串类型

// 原始代码:
/*
items(fontOptions) { (displayName, fontKey) ->
    FontOption(
        name = displayName,
        fontKey = fontKey,
        isSelected = fontFamily == fontKey,  // 这里可能是String与Int比较
        onSelected = { onFontFamilyChange(fontKey) }  // 这里可能是将String传给需要Int的函数
    )
}
*/

// 修复后的代码:
/*
items(fontOptions) { (displayName, fontKey) ->
    FontOption(
        name = displayName,
        fontKey = fontKey,
        isSelected = fontFamily == fontKey,  // 确保fontFamily和fontKey都是字符串
        onSelected = { onFontFamilyChange(fontKey) }  // 确保onFontFamilyChange接受字符串
    )
}
*/

// 要修复的代码片段2 - currentTab
// 问题: String和Int类型不匹配
// 解决方案: 确保currentTab是整数类型

// 原始代码:
/*
TabRow(
    selectedTabIndex = currentTab,  // 确保这是Int
    modifier = Modifier.fillMaxWidth()
) {
    Tab(
        selected = currentTab == 0,  // 确保比较是整数
        onClick = { onTabSelected(0) },
        text = { Text("文本") }
    )
    Tab(
        selected = currentTab == 1,
        onClick = { onTabSelected(1) },
        text = { Text("版式") }
    )
    Tab(
        selected = currentTab == 2,
        onClick = { onTabSelected(2) },
        text = { Text("主题") }
    )
}
*/

// 修复后的代码:
/*
TabRow(
    selectedTabIndex = currentTab,  // 已确认currentTab是Int
    modifier = Modifier.fillMaxWidth()
) {
    Tab(
        selected = currentTab == 0,  // 整数比较
        onClick = { onTabSelected(0) },  // 传递整数参数
        text = { Text("文本") }
    )
    Tab(
        selected = currentTab == 1,
        onClick = { onTabSelected(1) },
        text = { Text("版式") }
    )
    Tab(
        selected = currentTab == 2,
        onClick = { onTabSelected(2) },
        text = { Text("主题") }
    )
}
*/

/**
 * 修复后的FontOption组件定义
 * 确保所有参数类型匹配
 */
/*
@Composable
fun FontOption(
    name: String,
    fontKey: String,  // 确保是字符串
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    // 其他实现...
}
*/ 