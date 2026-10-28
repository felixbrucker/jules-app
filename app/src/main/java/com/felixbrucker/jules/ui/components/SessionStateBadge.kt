package com.felixbrucker.jules.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.network.api.model.SessionState

data class StateBadgeStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector? = null,
    val isAnimatedDots: Boolean = false
)

fun getSessionStateStyle(state: SessionState): StateBadgeStyle {
    return when (state) {
        SessionState.IN_PROGRESS -> StateBadgeStyle(
            containerColor = Color(0xFF1E3A8A),
            contentColor = Color(0xFF93C5FD),
            isAnimatedDots = true
        )
        SessionState.AWAITING_PLAN_APPROVAL, SessionState.AWAITING_USER_FEEDBACK -> StateBadgeStyle(
            containerColor = Color(0xFF78350F),
            contentColor = Color(0xFFFDE68A),
            icon = Icons.Default.RateReview
        )
        SessionState.COMPLETED -> StateBadgeStyle(
            containerColor = Color(0xFF14532D),
            contentColor = Color(0xFF86EFAC),
            icon = Icons.Default.CheckCircle
        )
        SessionState.FAILED -> StateBadgeStyle(
            containerColor = Color(0xFF7F1D1D),
            contentColor = Color(0xFFFCA5A5),
            icon = Icons.Default.Error
        )
        SessionState.QUEUED, SessionState.PLANNING -> StateBadgeStyle(
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF94A3B8),
            icon = Icons.Default.HourglassEmpty
        )
        SessionState.PAUSED -> StateBadgeStyle(
            containerColor = Color(0xFF374151),
            contentColor = Color(0xFFD1D5DB),
            icon = Icons.Default.PauseCircle
        )
        else -> StateBadgeStyle(
            containerColor = Color(0xFF374151),
            contentColor = Color(0xFFD1D5DB),
            icon = Icons.AutoMirrored.Filled.Help
        )
    }
}

@Composable
fun SessionStateBadge(
    state: SessionState,
    sessionId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val style = getSessionStateStyle(state)
    val badgeContent = @Composable {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = style.containerColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (style.isAnimatedDots) {
                    AnimatedMovingDotsIcon(color = style.contentColor)
                } else style.icon?.let { iconVec ->
                    Icon(
                        imageVector = iconVec,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = style.contentColor
                    )
                }
                Text(
                    text = state.label,
                    color = style.contentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (sessionId != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
        Box(
            modifier = with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = "state_$sessionId"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        ) {
            badgeContent()
        }
    } else {
        badgeContent()
    }
}
