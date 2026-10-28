package com.felixbrucker.jules.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun ChatMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    isTextSelectable: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    MarkdownText(
        markdown = markdown,
        modifier = modifier,
        style = style.copy(color = color),
        syntaxHighlightColor = Color.Transparent,
        syntaxHighlightTextColor = Color.White,
        isTextSelectable = isTextSelectable,
        onClick = onClick,
    )
}
