package com.felixbrucker.jules.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.jules.data.database.SessionDraftEntity
import com.felixbrucker.jules.data.database.SourceEntity
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionDialog(
    draft: SessionDraftEntity,
    sources: List<SourceEntity>,
    isCreating: Boolean,
    isPersistedDraft: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSaveDraft: (SessionDraftEntity) -> Unit,
    onCreateSession: (SessionDraftEntity) -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            CreateSessionDialogBody(
                draft = draft,
                sources = sources,
                isCreating = isCreating,
                isPersistedDraft = isPersistedDraft,
                errorMessage = errorMessage,
                onDismiss = onDismiss,
                onSaveDraft = onSaveDraft,
                onCreateSession = onCreateSession
            )
        }
    }
}

@Composable
private fun CreateSessionDialogBody(
    draft: SessionDraftEntity,
    sources: List<SourceEntity>,
    isCreating: Boolean,
    isPersistedDraft: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSaveDraft: (SessionDraftEntity) -> Unit,
    onCreateSession: (SessionDraftEntity) -> Unit
) {
    val initialSource = remember(draft.sourceId, sources) {
        sources.firstOrNull { it.id == draft.sourceId || "sources/" + it.id == draft.sourceId }
            ?: sources.firstOrNull()
    }

    var selectedSource by remember(initialSource) { mutableStateOf(initialSource) }
    var selectedBranch by remember(selectedSource, draft.branch) {
        val branch = draft.branch
            ?: selectedSource?.githubRepo?.defaultBranchName
            ?: selectedSource?.githubRepo?.branches?.firstOrNull()?.displayName
            ?: "main"
        mutableStateOf(branch)
    }

    var title by remember(draft.title) { mutableStateOf(draft.title ?: "") }
    var prompt by remember(draft.prompt) { mutableStateOf(draft.prompt) }
    var requirePlanApproval by remember(draft.requirePlanApproval) { mutableStateOf(draft.requirePlanApproval) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            text = if (isPersistedDraft) "Edit Session Draft" else "Create Session",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        RepoAndBranchSelectionRow(
            sources = sources,
            selectedSource = selectedSource,
            selectedBranch = selectedBranch,
            onSourceSelected = { newSource ->
                selectedSource = newSource
                selectedBranch = newSource.githubRepo?.defaultBranchName
                    ?: newSource.githubRepo?.branches?.firstOrNull()?.displayName
                    ?: "main"
            },
            onBranchSelected = { selectedBranch = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        TitleInputField(
            title = title,
            onTitleChange = { title = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        PromptInputField(
            prompt = prompt,
            onPromptChange = { prompt = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        RequirePlanApprovalToggleRow(
            requirePlanApproval = requirePlanApproval,
            onToggle = { requirePlanApproval = it }
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        CreateSessionDialogButtons(
            isCreating = isCreating,
            isPersistedDraft = isPersistedDraft,
            canCreate = prompt.isNotBlank() && selectedSource != null,
            onDismiss = onDismiss,
            onSaveDraft = {
                val updated = draft.copy(
                    title = title.ifBlank { null },
                    prompt = prompt,
                    sourceId = selectedSource?.id,
                    branch = selectedBranch,
                    requirePlanApproval = requirePlanApproval,
                    updatedAt = Instant.now()
                )
                onSaveDraft(updated)
            },
            onCreate = {
                val updated = draft.copy(
                    title = title.ifBlank { null },
                    prompt = prompt,
                    sourceId = selectedSource?.id,
                    branch = selectedBranch,
                    requirePlanApproval = requirePlanApproval,
                    updatedAt = Instant.now()
                )
                onCreateSession(updated)
            }
        )
    }
}

@Composable
private fun RepoAndBranchSelectionRow(
    sources: List<SourceEntity>,
    selectedSource: SourceEntity?,
    selectedBranch: String,
    onSourceSelected: (SourceEntity) -> Unit,
    onBranchSelected: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RepoDropdown(
                sources = sources,
                selectedSource = selectedSource,
                onSourceSelected = onSourceSelected,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            BranchDropdown(
                selectedSource = selectedSource,
                selectedBranch = selectedBranch,
                onBranchSelected = onBranchSelected
            )
        }
    }
}

@Composable
private fun RepoDropdown(
    sources: List<SourceEntity>,
    selectedSource: SourceEntity?,
    onSourceSelected: (SourceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val displayText = selectedSource?.let { src ->
        src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name
    } ?: "Choose a repo"

    Box(modifier = modifier) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selectedSource != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
            modifier = Modifier.clickable { expanded = true }
        )
        RepoDropdownMenu(
            expanded = expanded,
            searchQuery = searchQuery,
            sources = sources,
            onSearchQueryChange = { searchQuery = it },
            onDismiss = {
                expanded = false
                searchQuery = ""
            },
            onSourceSelected = { src ->
                onSourceSelected(src)
                expanded = false
                searchQuery = ""
            }
        )
    }
}

@Composable
private fun RepoDropdownMenu(
    expanded: Boolean,
    searchQuery: String,
    sources: List<SourceEntity>,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSourceSelected: (SourceEntity) -> Unit
) {
    val filteredSources = remember(sources, searchQuery) {
        if (searchQuery.isBlank()) sources
        else sources.filter { src ->
            val label = src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name
            label.contains(searchQuery, ignoreCase = true)
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            placeholder = { Text("Choose a repo", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            singleLine = true
        )
        Column(
            modifier = Modifier
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
        ) {
            filteredSources.forEach { source ->
                val label = source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { onSourceSelected(source) }
                )
            }
        }
    }
}

@Composable
private fun BranchDropdown(
    selectedSource: SourceEntity?,
    selectedBranch: String,
    onBranchSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val branches = selectedSource?.githubRepo?.branches?.map { it.displayName } ?: listOf("main")

    Box {
        Text(
            text = selectedBranch,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            branches.forEach { branchName ->
                DropdownMenuItem(
                    text = { Text(branchName, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onBranchSelected(branchName)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TitleInputField(
    title: String,
    onTitleChange: (String) -> Unit
) {
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Title (optional)") },
        supportingText = {
            Text(
                text = "Will be auto-generated by Jules if blank.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun PromptInputField(
    prompt: String,
    onPromptChange: (String) -> Unit
) {
    OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChange,
        label = { Text("Prompt") },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 240.dp),
        minLines = 4
    )
}

@Composable
private fun RequirePlanApprovalToggleRow(
    requirePlanApproval: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Require plan approval",
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = requirePlanApproval,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun CreateSessionDialogButtons(
    isCreating: Boolean,
    isPersistedDraft: Boolean,
    canCreate: Boolean,
    onDismiss: () -> Unit,
    onSaveDraft: () -> Unit,
    onCreate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onDismiss,
            enabled = !isCreating
        ) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(
            onClick = onSaveDraft,
            enabled = !isCreating
        ) {
            Text(if (isPersistedDraft) "Save" else "Save as Draft")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(
            onClick = onCreate,
            enabled = canCreate && !isCreating
        ) {
            if (isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Create")
        }
    }
}
