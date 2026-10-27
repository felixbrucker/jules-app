package com.felixbrucker.jules.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
    val currentOnClick by rememberUpdatedState(onClick)
    val tapModifier = if (onClick != null) {
        Modifier.pointerInput(onClick) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                    if (down.type == PointerEventType.Press) {
                        val downTime = System.currentTimeMillis()
                        val downPosition = down.changes.firstOrNull()?.position ?: Offset.Zero
                        var isTap = true
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val currentPos = event.changes.firstOrNull()?.position ?: downPosition
                            val distance = (currentPos - downPosition).getDistance()
                            if (distance > 15f || System.currentTimeMillis() - downTime > 350) {
                                isTap = false
                            }
                        } while (event.changes.any { it.pressed })
                        val totalDuration = System.currentTimeMillis() - downTime
                        if (isTap && totalDuration < 350) {
                            currentOnClick?.invoke()
                        }
                    }
                }
            }
        }
    } else Modifier

    MarkdownText(
        markdown = markdown,
        modifier = modifier.then(tapModifier),
        style = style.copy(color = color),
        syntaxHighlightColor = Color.Transparent,
        syntaxHighlightTextColor = Color.White,
        isTextSelectable = isTextSelectable,
        onClick = onClick,
        afterSetMarkdown = { textView ->
            textView.isFocusableInTouchMode = false
        }
    )
}
