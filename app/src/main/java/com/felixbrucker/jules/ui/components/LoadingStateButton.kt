package com.felixbrucker.jules.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun LoadingStateButton(
    label: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loadingLabel: String? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    animationDurationMillis: Int = 250
) {
    val slideTweenSpec = tween<IntOffset>(durationMillis = animationDurationMillis)
    val fadeTweenSpec = tween<Float>(durationMillis = animationDurationMillis)

    Button(
        modifier = modifier,
        enabled = enabled && !isLoading,
        onClick = onClick,
        colors = colors
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = isLoading,
                enter = slideInHorizontally(animationSpec = slideTweenSpec) { width -> width } + fadeIn(animationSpec = fadeTweenSpec),
                exit = slideOutHorizontally(animationSpec = slideTweenSpec) { width -> width } + fadeOut(animationSpec = fadeTweenSpec)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            val currentLabel = if (isLoading && loadingLabel != null) loadingLabel else label
            AnimatedContent(
                targetState = currentLabel,
                transitionSpec = {
                    fadeIn(animationSpec = fadeTweenSpec) togetherWith fadeOut(animationSpec = fadeTweenSpec)
                },
                label = "LoadingButtonLabelTransition"
            ) { targetText ->
                Text(text = targetText)
            }
        }
    }
}
