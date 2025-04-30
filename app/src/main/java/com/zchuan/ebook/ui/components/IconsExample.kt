package com.zchuan.ebook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun IconsExample() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("材料图标示例")
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = "书籍图标"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Icons.Filled.MenuBook - 实心书籍图标")
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = "书籍轮廓图标"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Icons.Outlined.MenuBook - 轮廓书籍图标")
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = "书签图标"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Icons.Filled.Bookmark - 实心书签图标")
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Bookmark,
                contentDescription = "书签轮廓图标"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Icons.Outlined.Bookmark - 轮廓书签图标")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IconsExamplePreview() {
    IconsExample()
} 