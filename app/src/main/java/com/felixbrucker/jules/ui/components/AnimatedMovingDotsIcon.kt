package com.felixbrucker.jules.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedMovingDotsIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DotsTransition")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                1.0f at 200
                0.3f at 600
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot1Alpha"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                0.3f at 300
                1.0f at 500
                0.3f at 900
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot2Alpha"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                0.3f at 600
                1.0f at 800
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot3Alpha"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer { alpha = dot1Alpha }
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer { alpha = dot2Alpha }
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer { alpha = dot3Alpha }
                .background(color, CircleShape)
        )
    }
}
