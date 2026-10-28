package com.felixbrucker.jules.ui.sessions

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.felixbrucker.jules.R
import com.felixbrucker.jules.data.database.SessionDraftWithSource

@Composable
fun SessionDraftCard(
    modifier: Modifier = Modifier,
    draftWithSource: SessionDraftWithSource,
    onCardClick: () -> Unit,
    onDeleteDraft: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteDraftDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeleteDraft()
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
            DraftCardHeader(draftWithSource = draftWithSource)
            Spacer(modifier = Modifier.height(8.dp))
            DraftCardPromptRow(
                prompt = draftWithSource.draft.prompt,
                onRequestDelete = { showDeleteDialog = true }
            )
            Spacer(modifier = Modifier.height(12.dp))
            DraftCardFooter(draftWithSource = draftWithSource)
        }
    }
}

@Composable
private fun DraftCardHeader(draftWithSource: SessionDraftWithSource) {
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
            DraftBadge()
            Text(
                text = draftWithSource.draft.title.takeIf { !it.isNullOrBlank() } ?: "Untitled Draft",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DraftBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF2563EB)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.White
            )
            Text(
                text = "Draft",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DraftCardPromptRow(
    prompt: String,
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
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Draft Actions")
            }
            DraftActionDropdownMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onRequestDelete = onRequestDelete
            )
        }
    }
}

@Composable
private fun DraftActionDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRequestDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Delete Draft") },
            leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
            onClick = {
                onDismiss()
                onRequestDelete()
            }
        )
    }
}

@Composable
private fun DraftCardFooter(draftWithSource: SessionDraftWithSource) {
    val context = LocalContext.current
    val draft = draftWithSource.draft
    val source = draftWithSource.source
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        draft.updatedAt.toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    val repoOwnerAndName = source?.githubRepo?.let { "${it.owner}/${it.repo}" }
        ?: source?.id?.removePrefix("sources/")?.removePrefix("github/")
    val repoLabel = repoOwnerAndName ?: source?.name

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = Int.MAX_VALUE
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repoLabel?.let { label ->
                val fullLabel = draft.branch?.let { "$label:$it" } ?: label
                val repoUrl = repoOwnerAndName?.let { "https://github.com/$it" }
                AssistChip(
                    modifier = Modifier.height(28.dp),
                    onClick = {
                        repoUrl?.let { url ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    },
                    label = { Text(fullLabel, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub Repo",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }
        Text(
            text = "Updated $relativeTime",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeleteDraftDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Draft") },
        text = { Text("Are you sure you want to delete this session draft?") },
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
