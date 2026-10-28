package com.felixbrucker.jules.ui.sessions

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.data.database.ScheduleType
import com.felixbrucker.jules.data.database.ScheduledTaskEntity
import com.felixbrucker.jules.data.database.ScheduledTaskRepo
import com.felixbrucker.jules.data.database.SourceEntity
import com.felixbrucker.jules.ui.components.LoadingStateButton
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduledTaskDialog(
    task: ScheduledTaskEntity,
    sources: List<SourceEntity>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSaveTask: (ScheduledTaskEntity) -> Unit,
    onSourceSelected: (SourceEntity) -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            CreateScheduledTaskDialogBody(
                task = task,
                sources = sources,
                isSaving = isSaving,
                errorMessage = errorMessage,
                onDismiss = onDismiss,
                onSaveTask = onSaveTask,
                onSourceSelected = onSourceSelected
            )
        }
    }
}

@Composable
private fun CreateScheduledTaskDialogBody(
    task: ScheduledTaskEntity,
    sources: List<SourceEntity>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSaveTask: (ScheduledTaskEntity) -> Unit,
    onSourceSelected: (SourceEntity) -> Unit
) {
    val initialRepos = remember(task.repos, sources) {
        if (task.repos.isNotEmpty()) task.repos
        else {
            val firstSrc = sources.firstOrNull()
            if (firstSrc != null) {
                val branch = firstSrc.githubRepo?.defaultBranchName
                    ?: firstSrc.githubRepo?.branches?.firstOrNull()?.displayName
                    ?: "main"
                listOf(ScheduledTaskRepo(firstSrc.id, branch))
            } else emptyList()
        }
    }

    var repos by remember { mutableStateOf(initialRepos) }
    var title by remember(task.title) { mutableStateOf(task.title ?: "") }
    var prompt by remember(task.prompt) { mutableStateOf(task.prompt) }
    var requirePlanApproval by remember(task.requirePlanApproval) { mutableStateOf(task.requirePlanApproval) }

    var scheduleType by remember(task.scheduleType) { mutableStateOf(task.scheduleType) }
    var timeHour by remember(task.timeHour) { mutableIntStateOf(task.timeHour) }
    var timeMinute by remember(task.timeMinute) { mutableIntStateOf(task.timeMinute) }
    var dateStr by remember(task.date) { mutableStateOf(task.date ?: LocalDate.now().toString()) }
    var dayOfWeek by remember(task.dayOfWeek) { mutableIntStateOf(task.dayOfWeek ?: 1) }
    var dayOfMonth by remember(task.dayOfMonth) { mutableIntStateOf(task.dayOfMonth ?: 1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (task.id.isNotBlank() && task.prompt.isNotBlank()) "Edit Scheduled Task" else "Create Scheduled Task",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Target Repositories",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        repos.forEachIndexed { index, repo ->
            val source = sources.firstOrNull { it.id == repo.sourceId }
            LaunchedEffect(source?.id) {
                source?.let { onSourceSelected(it) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    RepoAndBranchSelectionRow(
                        sources = sources,
                        selectedSource = source,
                        selectedBranch = repo.branch,
                        onSourceSelected = { newSource ->
                            val defaultBranch = newSource.githubRepo?.defaultBranchName
                                ?: newSource.githubRepo?.branches?.firstOrNull()?.displayName
                                ?: "main"
                            repos = repos.toMutableList().apply {
                                set(index, ScheduledTaskRepo(newSource.id, defaultBranch))
                            }
                            onSourceSelected(newSource)
                        },
                        onBranchSelected = { newBranch ->
                            repos = repos.toMutableList().apply {
                                set(index, ScheduledTaskRepo(repo.sourceId, newBranch))
                            }
                        }
                    )
                }
                if (repos.size > 1) {
                    IconButton(
                        onClick = {
                            repos = repos.toMutableList().apply { removeAt(index) }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Repo",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                val firstSrc = sources.firstOrNull()
                if (firstSrc != null) {
                    val branch = firstSrc.githubRepo?.defaultBranchName
                        ?: firstSrc.githubRepo?.branches?.firstOrNull()?.displayName
                        ?: "main"
                    repos = repos + ScheduledTaskRepo(firstSrc.id, branch)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Repository")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Schedule Configuration",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        ScheduleTypeDropdown(
            selectedType = scheduleType,
            onTypeSelected = { scheduleType = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = String.format(Locale.ROOT, "%02d", timeHour),
                onValueChange = { input ->
                    input.toIntOrNull()?.let { timeHour = it.coerceIn(0, 23) }
                },
                label = { Text("Hour (0-23)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Text(":", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = String.format(Locale.ROOT, "%02d", timeMinute),
                onValueChange = { input ->
                    input.toIntOrNull()?.let { timeMinute = it.coerceIn(0, 59) }
                },
                label = { Text("Minute (0-59)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        when (scheduleType) {
            ScheduleType.ONCE -> {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            ScheduleType.WEEKLY -> {
                Spacer(modifier = Modifier.height(8.dp))
                DayOfWeekDropdown(
                    selectedDay = dayOfWeek,
                    onDaySelected = { dayOfWeek = it }
                )
            }
            ScheduleType.MONTHLY -> {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dayOfMonth.toString(),
                    onValueChange = { input ->
                        input.toIntOrNull()?.let { dayOfMonth = it.coerceIn(1, 31) }
                    },
                    label = { Text("Day of Month (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            ScheduleType.DAILY -> {}
        }

        Spacer(modifier = Modifier.height(16.dp))
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            LoadingStateButton(
                label = "Save Scheduled Task",
                loadingLabel = "Saving...",
                isLoading = isSaving,
                enabled = prompt.isNotBlank() && repos.isNotEmpty(),
                onClick = {
                    val updatedTask = task.copy(
                        title = title.ifBlank { null },
                        prompt = prompt,
                        repos = repos,
                        requirePlanApproval = requirePlanApproval,
                        scheduleType = scheduleType,
                        timeHour = timeHour,
                        timeMinute = timeMinute,
                        date = if (scheduleType == ScheduleType.ONCE) dateStr else null,
                        dayOfWeek = if (scheduleType == ScheduleType.WEEKLY) dayOfWeek else null,
                        dayOfMonth = if (scheduleType == ScheduleType.MONTHLY) dayOfMonth else null,
                        updatedAt = Instant.now()
                    )
                    onSaveTask(updatedTask)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTypeDropdown(
    selectedType: ScheduleType,
    onTypeSelected: (ScheduleType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedType.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Schedule Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ScheduleType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOfWeekDropdown(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val days = (1..7).map { DayOfWeek.of(it) }
    val selectedDayName = DayOfWeek.of(selectedDay.coerceIn(1, 7))
        .getDisplayName(TextStyle.FULL, Locale.getDefault())

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedDayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Day of Week") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            days.forEach { day ->
                DropdownMenuItem(
                    text = { Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                    onClick = {
                        onDaySelected(day.value)
                        expanded = false
                    }
                )
            }
        }
    }
}
