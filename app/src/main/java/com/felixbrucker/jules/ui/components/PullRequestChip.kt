package com.felixbrucker.jules.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.R
import com.felixbrucker.jules.data.database.EmbeddedPullRequest
import com.felixbrucker.jules.network.api.model.PullRequestState

@Composable
fun PullRequestChip(
    pr: EmbeddedPullRequest,
    pullRequestState: PullRequestState?,
    onPrClick: (String) -> Unit
) {
    val (iconRes, iconColor) = getPullRequestIconInfo(pullRequestState)
    val prNumberText = pr.number?.let { " (#$it)" } ?: ""

    AssistChip(
        modifier = Modifier.height(28.dp),
        onClick = { onPrClick(pr.url) },
        label = { Text("View PR$prNumberText", style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = "GitHub PR",
                modifier = Modifier.size(14.dp),
                tint = iconColor
            )
        }
    )
}

private fun getPullRequestIconInfo(pullRequestState: PullRequestState?): Pair<Int, Color> {
    return when (pullRequestState) {
        PullRequestState.MERGED -> R.drawable.ic_github_pr_merged to Color(0xFFA371F7)
        PullRequestState.CLOSED -> R.drawable.ic_github_pr_closed to Color(0xFFF85149)
        PullRequestState.OPEN, null -> R.drawable.ic_github_pr_open to Color(0xFF3FB950)
    }
}
