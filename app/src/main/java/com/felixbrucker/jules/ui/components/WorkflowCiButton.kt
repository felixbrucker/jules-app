package com.felixbrucker.jules.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.database.DispatchedWorkflowRunEntity
import com.felixbrucker.jules.network.api.model.WorkflowRunState

@Composable
fun WorkflowCiButton(
    sessionId: String,
    dispatchedWorkflowRun: DispatchedWorkflowRunEntity?,
    workflows: List<DispatchableWorkflowEntity>,
    onWorkflowSelected: (DispatchableWorkflowEntity) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    if (workflows.isEmpty() && dispatchedWorkflowRun == null) {
        return
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val labelText = dispatchedWorkflowRun?.workflowName ?: "CI"

    Box(
        modifier = with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "ci_badge_${sessionId}"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    ) {
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
                        .clickable(enabled = workflows.isNotEmpty()) { menuExpanded = true }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dispatchedWorkflowRun?.let { run ->
                        WorkflowStateIndicator(state = run.state)
                    }

                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (workflows.isNotEmpty()) {
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
    }
}

@Composable
private fun WorkflowStateIndicator(state: WorkflowRunState) {
    when (state) {
        WorkflowRunState.QUEUED -> {
            Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = "Queued",
                modifier = Modifier.size(12.dp),
                tint = Color(0xFFD29922)
            )
        }
        WorkflowRunState.IN_PROGRESS -> {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = "In Progress",
                modifier = Modifier.size(10.dp),
                tint = Color(0xFFD29922)
            )
        }
        WorkflowRunState.SUCCESS -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Succeeded",
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF3FB950)
            )
        }
        WorkflowRunState.FAILURE -> {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Failed",
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFF85149)
            )
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
