package com.felixbrucker.jules.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.R
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.database.EmbeddedPullRequest
import com.felixbrucker.jules.network.api.model.PullRequestState

@Composable
fun PullRequestChip(
    pr: EmbeddedPullRequest,
    pullRequestState: PullRequestState?,
    workflows: List<DispatchableWorkflowEntity>,
    onPrClick: (String) -> Unit,
    onWorkflowSelected: (DispatchableWorkflowEntity) -> Unit
) {
    if (workflows.isEmpty()) {
        SimplePullRequestChip(
            pr = pr,
            pullRequestState = pullRequestState,
            onPrClick = onPrClick
        )
    } else {
        CompoundPullRequestWorkflowButton(
            pr = pr,
            pullRequestState = pullRequestState,
            workflows = workflows,
            onPrClick = onPrClick,
            onWorkflowSelected = onWorkflowSelected
        )
    }
}

@Composable
private fun SimplePullRequestChip(
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

@Composable
private fun CompoundPullRequestWorkflowButton(
    pr: EmbeddedPullRequest,
    pullRequestState: PullRequestState?,
    workflows: List<DispatchableWorkflowEntity>,
    onPrClick: (String) -> Unit,
    onWorkflowSelected: (DispatchableWorkflowEntity) -> Unit
) {
    val (iconRes, iconColor) = getPullRequestIconInfo(pullRequestState)
    val prNumberText = pr.number?.let { " (#$it)" } ?: ""
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onPrClick(pr.url) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "GitHub PR",
                    modifier = Modifier.size(14.dp),
                    tint = iconColor
                )
                Text(
                    text = "View PR$prNumberText",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Workflow Dispatches",
                    modifier = Modifier.size(18.dp)
                )
                WorkflowDropdownMenu(
                    expanded = menuExpanded,
                    workflows = workflows,
                    onDismiss = { menuExpanded = false },
                    onWorkflowSelected = onWorkflowSelected
                )
            }
        }
    }
}

@Composable
private fun WorkflowDropdownMenu(
    expanded: Boolean,
    workflows: List<DispatchableWorkflowEntity>,
    onDismiss: () -> Unit,
    onWorkflowSelected: (DispatchableWorkflowEntity) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        workflows.forEach { workflow ->
            DropdownMenuItem(
                text = { Text(workflow.name) },
                onClick = {
                    onDismiss()
                    onWorkflowSelected(workflow)
                }
            )
        }
    }
}

private fun getPullRequestIconInfo(pullRequestState: PullRequestState?): Pair<Int, Color> {
    return when (pullRequestState) {
        PullRequestState.MERGED -> R.drawable.ic_github_pr_merged to Color(0xFFA371F7)
        PullRequestState.CLOSED -> R.drawable.ic_github_pr_closed to Color(0xFFF85149)
        PullRequestState.OPEN, null -> R.drawable.ic_github_pr_open to Color(0xFF3FB950)
    }
}
