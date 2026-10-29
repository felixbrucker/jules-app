package com.felixbrucker.jules.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.data.database.ScheduleType
import com.felixbrucker.jules.data.database.ScheduledTaskEntity
import com.felixbrucker.jules.data.database.ScheduledTaskRepo
import com.felixbrucker.jules.data.database.SourceEntity
import com.felixbrucker.jules.ui.components.LoadingStateButton
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

fun ScheduleType.displayName(): String = when (this) {
    ScheduleType.ONCE -> "Once"
    ScheduleType.DAILY -> "Daily"
    ScheduleType.WEEKLY -> "Weekly"
    ScheduleType.MONTHLY -> "Monthly"
}

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
                .imePadding()
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
        task.repos.ifEmpty {
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

        TimeSelectionField(
            hour = timeHour,
            minute = timeMinute,
            onTimeSelected = { h, m ->
                timeHour = h
                timeMinute = m
            }
        )

        when (scheduleType) {
            ScheduleType.ONCE -> {
                Spacer(modifier = Modifier.height(8.dp))
                DateSelectionField(
                    selectedDateStr = dateStr,
                    onDateSelected = { dateStr = it }
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
                DayOfMonthDropdown(
                    selectedDay = dayOfMonth,
                    onDaySelected = { dayOfMonth = it }
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
                    val isFutureOnceTask = scheduleType == ScheduleType.ONCE && try {
                        val localDate = LocalDate.parse(dateStr)
                        val localTime = LocalTime.of(timeHour, timeMinute)
                        LocalDateTime.of(localDate, localTime).atZone(ZoneId.systemDefault()).isAfter(ZonedDateTime.now())
                    } catch (_: Exception) { false }

                    val newScheduleType = scheduleType
                    val newDate = if (scheduleType == ScheduleType.ONCE) dateStr else null
                    val newDayOfWeek = if (scheduleType == ScheduleType.WEEKLY) dayOfWeek else null
                    val newDayOfMonth = if (scheduleType == ScheduleType.MONTHLY) dayOfMonth else null

                    val isScheduleChanged = task.scheduleType != newScheduleType ||
                        task.timeHour != timeHour ||
                        task.timeMinute != timeMinute ||
                        task.date != newDate ||
                        task.dayOfWeek != newDayOfWeek ||
                        task.dayOfMonth != newDayOfMonth

                    val updatedTask = task.copy(
                        title = title.ifBlank { null },
                        prompt = prompt,
                        repos = repos,
                        requirePlanApproval = requirePlanApproval,
                        scheduleType = newScheduleType,
                        timeHour = timeHour,
                        timeMinute = timeMinute,
                        date = newDate,
                        dayOfWeek = newDayOfWeek,
                        dayOfMonth = newDayOfMonth,
                        enabled = isFutureOnceTask || task.enabled,
                        lastRunAt = if (isScheduleChanged) null else task.lastRunAt,
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
            value = selectedType.displayName(),
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
                    text = { Text(type.displayName()) },
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
private fun TimeSelectionField(
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val formattedTime = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = formattedTime,
            onValueChange = {},
            readOnly = true,
            label = { Text("Time") },
            trailingIcon = {
                Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Select Time")
            },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showTimePicker = true }
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

@Composable
private fun DateSelectionField(
    selectedDateStr: String,
    onDateSelected: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedDateStr,
            onValueChange = {},
            readOnly = true,
            label = { Text("Date (YYYY-MM-DD)") },
            trailingIcon = {
                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Select Date")
            },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDatePicker = true }
        )
    }

    if (showDatePicker) {
        val initialUtcMillis = try {
            LocalDate.parse(selectedDateStr)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val localDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onDateSelected(localDate.toString())
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
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
        .getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)

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
                    text = { Text(day.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale)) },
                    onClick = {
                        onDaySelected(day.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOfMonthDropdown(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = "Day $selectedDay",
            onValueChange = {},
            readOnly = true,
            label = { Text("Day of Month") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            (1..31).forEach { day ->
                DropdownMenuItem(
                    text = { Text("Day $day") },
                    onClick = {
                        onDaySelected(day)
                        expanded = false
                    }
                )
            }
        }
    }
}
