package com.felixbrucker.jules.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormattedMarkdownText(
    markdown: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val lines = markdown.lines()
        lines.forEach { line ->
            val trimmed = line.trimStart()
            val indentSpaces = line.length - trimmed.length
            val indentLevel = indentSpaces / 2

            when {
                trimmed == "---" || trimmed == "***" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                trimmed.startsWith("#") && trimmed.contains(" ") -> {
                    val headerLevel = trimmed.takeWhile { it == '#' }.length
                    val headerText = trimmed.drop(headerLevel).trimStart()
                    val headerFontSize = when (headerLevel) {
                        1 -> 20.sp
                        2 -> 18.sp
                        3 -> 16.sp
                        else -> 14.sp
                    }
                    Text(
                        text = parseInlineMarkdown(headerText),
                        style = style.copy(
                            fontSize = headerFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    val bulletContent = trimmed.drop(2)
                    Row(
                        modifier = Modifier.padding(start = (indentLevel * 12).dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = style.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = parseInlineMarkdown(bulletContent),
                            style = style
                        )
                    }
                }
                else -> {
                    if (line.isNotBlank()) {
                        Text(
                            text = parseInlineMarkdown(line),
                            style = style
                        )
                    }
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            } else if (text.startsWith("*", i)) {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            } else if (text.startsWith("`", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2D3748))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
