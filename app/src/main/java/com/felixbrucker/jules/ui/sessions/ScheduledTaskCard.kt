package com.felixbrucker.jules.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.data.database.ScheduleType
import com.felixbrucker.jules.data.database.ScheduledTaskEntity
import com.felixbrucker.jules.data.database.SourceEntity
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduledTaskCard(
    modifier: Modifier = Modifier,
    task: ScheduledTaskEntity,
    sources: List<SourceEntity>,
    onCardClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onExecuteNow: () -> Unit,
    onDeleteTask: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteScheduledTaskDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeleteTask()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            ScheduledTaskCardHeader(task = task, onToggleEnabled = onToggleEnabled)
            Spacer(modifier = Modifier.height(8.dp))
            ScheduledTaskCardPromptRow(
                prompt = task.prompt,
                onRequestExecute = onExecuteNow,
                onRequestDelete = { showDeleteDialog = true }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ScheduledTaskCardFooter(task = task, sources = sources)
        }
    }
}

@Composable
private fun ScheduledTaskCardHeader(
    task: ScheduledTaskEntity,
    onToggleEnabled: (Boolean) -> Unit
) {
    val displayTitle = task.title.takeIf { !it.isNullOrBlank() }
        ?: task.prompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        ?: "Untitled Scheduled Task"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScheduledBadge()
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = task.enabled,
            onCheckedChange = onToggleEnabled,
            modifier = Modifier.scale(0.75f)
        )
    }
}

@Composable
private fun ScheduledTaskCardPromptRow(
    prompt: String,
    onRequestExecute: () -> Unit,
    onRequestDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prompt.ifBlank { "No prompt entered" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (prompt.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Box(modifier = Modifier.offset(x = 12.dp)) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Task Actions")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Execute Now") },
                    leadingIcon = { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRequestExecute()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRequestDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun ScheduledBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF6B21A8)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color(0xFFF3E8FF)
            )
            Text(
                text = "Scheduled",
                color = Color(0xFFF3E8FF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScheduledTaskCardFooter(
    task: ScheduledTaskEntity,
    sources: List<SourceEntity>
) {
    val scheduleSummary = formatScheduleSummary(task)
    val reposCount = task.repos.size

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = scheduleSummary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (reposCount == 1) "1 repo" else "$reposCount repos",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatScheduleSummary(task: ScheduledTaskEntity): String {
    val timeStr = String.format(Locale.ROOT, "%02d:%02d", task.timeHour, task.timeMinute)
    return when (task.scheduleType) {
        ScheduleType.ONCE -> "Once on ${task.date ?: "unknown date"} at $timeStr"
        ScheduleType.DAILY -> "Daily at $timeStr"
        ScheduleType.WEEKLY -> {
            val dayName = task.dayOfWeek?.let {
                DayOfWeek.of(it.coerceIn(1, 7)).getDisplayName(TextStyle.FULL, Locale.getDefault())
            } ?: "Monday"
            "Weekly on $dayName at $timeStr"
        }
        ScheduleType.MONTHLY -> "Monthly on day ${task.dayOfMonth ?: 1} at $timeStr"
    }
}

@Composable
private fun DeleteScheduledTaskDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Scheduled Task") },
        text = { Text("Are you sure you want to delete this scheduled task?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
