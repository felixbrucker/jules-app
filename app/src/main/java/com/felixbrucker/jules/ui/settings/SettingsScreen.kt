package com.felixbrucker.jules.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.OutlinedButton
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.felixbrucker.jules.R
import com.felixbrucker.jules.ui.components.ApiKeyTextField
import com.felixbrucker.jules.ui.components.LoadingStateButton
import com.felixbrucker.jules.ui.components.ObfuscatedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onViewLogsClick: () -> Unit
) {
    val context = LocalContext.current
    val periodicSyncEnabled by viewModel.periodicSyncEnabled.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()
    val smartSyncEnabled by viewModel.smartSyncEnabled.collectAsState()
    val feedbackNotificationsEnabled by viewModel.feedbackNotificationsEnabled.collectAsState()
    val completionNotificationsEnabled by viewModel.completionNotificationsEnabled.collectAsState()
    val selectedPlan by viewModel.selectedPlan.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        SettingsContent(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel,
            periodicSyncEnabled = periodicSyncEnabled,
            syncIntervalMinutes = syncIntervalMinutes,
            smartSyncEnabled = smartSyncEnabled,
            feedbackNotificationsEnabled = feedbackNotificationsEnabled,
            completionNotificationsEnabled = completionNotificationsEnabled,
            selectedPlan = selectedPlan,
            onViewLogsClick = onViewLogsClick
        )
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked && enabled,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    periodicSyncEnabled: Boolean,
    syncIntervalMinutes: Int,
    smartSyncEnabled: Boolean,
    feedbackNotificationsEnabled: Boolean,
    completionNotificationsEnabled: Boolean,
    selectedPlan: JulesPlan,
    onViewLogsClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ApiKeyCard(viewModel = viewModel)
        Spacer(modifier = Modifier.height(16.dp))
        GitHubAuthTokenCard(viewModel = viewModel)
        Spacer(modifier = Modifier.height(16.dp))
        PlanSelectionCard(
            currentPlan = selectedPlan,
            onPlanSelected = { viewModel.selectPlan(it) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SyncSettingsCard(
            periodicSyncEnabled = periodicSyncEnabled,
            syncIntervalMinutes = syncIntervalMinutes,
            smartSyncEnabled = smartSyncEnabled,
            feedbackNotificationsEnabled = feedbackNotificationsEnabled,
            completionNotificationsEnabled = completionNotificationsEnabled,
            isForceSyncing = viewModel.isForceSyncing,
            onTogglePeriodicSync = { viewModel.setPeriodicSyncEnabled(it) },
            onIntervalChange = { viewModel.setSyncIntervalMinutes(it) },
            onToggleSmartSync = { viewModel.setSmartSyncEnabled(it) },
            onToggleFeedbackNotifications = { viewModel.setFeedbackNotificationsEnabled(it) },
            onToggleCompletionNotifications = { viewModel.setCompletionNotificationsEnabled(it) },
            onForceSync = { viewModel.forceSync() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        DiagnosticsCard(onViewLogsClick = onViewLogsClick)
    }
}

@Composable
private fun DiagnosticsCard(
    onViewLogsClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(
                    text = "Debug & Diagnostics",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "App Logs", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "View and filter real-time and historical application logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onViewLogsClick) {
                    Text("View Logs")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanSelectionCard(
    currentPlan: JulesPlan,
    onPlanSelected: (JulesPlan) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Jules Plan",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = "${currentPlan.displayName} (${currentPlan.dailyLimit} sessions/day)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Selected Plan") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    JulesPlan.entries.forEach { plan ->
                        DropdownMenuItem(
                            text = { Text("${plan.displayName} (${plan.dailyLimit} sessions/day)") },
                            onClick = {
                                expanded = false
                                onPlanSelected(plan)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    viewModel: SettingsViewModel
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(
                    text = "API Key Configuration",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApiKeyTextField(
                    modifier = Modifier.weight(1f),
                    apiKey = viewModel.apiKey,
                    onApiKeyChange = { viewModel.onApiKeyChanged(it) },
                    errorMessage = viewModel.apiKeyValidationErrorMessage,
                    onImeAction = { viewModel.validateAndApplyApiKey() }
                )
                LoadingStateButton(
                    modifier = Modifier.padding(top = 11.dp),
                    label = viewModel.applyApiKeyLabel,
                    loadingLabel = "Validating...",
                    isLoading = viewModel.isValidatingApiKey,
                    enabled = viewModel.apiKey.isNotBlank() && viewModel.persistedApiKey != viewModel.apiKey,
                    onClick = { viewModel.validateAndApplyApiKey() }
                )
            }
        }
    }
}

@Composable
private fun GitHubAuthTokenCard(
    viewModel: SettingsViewModel
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(
                    text = "GitHub Auth Token",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ObfuscatedTextField(
                    modifier = Modifier.weight(1f),
                    value = viewModel.githubAuthToken,
                    onValueChange = { viewModel.onGithubAuthTokenChanged(it) },
                    label = "GitHub Token",
                    errorMessage = viewModel.githubAuthTokenValidationErrorMessage,
                    supportingText = "Optional. Increases API rate limits and enables PR state access for private repositories.",
                    onImeAction = { viewModel.validateAndApplyGithubAuthToken() }
                )
                LoadingStateButton(
                    modifier = Modifier.padding(top = 11.dp),
                    label = viewModel.applyGithubAuthTokenLabel,
                    loadingLabel = "Validating...",
                    isLoading = viewModel.isValidatingGithubAuthToken,
                    enabled = viewModel.persistedGithubAuthToken != viewModel.githubAuthToken,
                    onClick = { viewModel.validateAndApplyGithubAuthToken() }
                )
            }
        }
    }
}

@Composable
private fun SyncSettingsCard(
    periodicSyncEnabled: Boolean,
    syncIntervalMinutes: Int,
    smartSyncEnabled: Boolean,
    feedbackNotificationsEnabled: Boolean,
    completionNotificationsEnabled: Boolean,
    isForceSyncing: Boolean,
    onTogglePeriodicSync: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onToggleSmartSync: (Boolean) -> Unit,
    onToggleFeedbackNotifications: (Boolean) -> Unit,
    onToggleCompletionNotifications: (Boolean) -> Unit,
    onForceSync: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SyncSettingsHeader()
            Spacer(modifier = Modifier.height(16.dp))
            PeriodicSyncRow(
                periodicSyncEnabled = periodicSyncEnabled,
                onTogglePeriodicSync = onTogglePeriodicSync
            )
            Spacer(modifier = Modifier.height(12.dp))
            SmartSyncRow(
                smartSyncEnabled = smartSyncEnabled,
                periodicSyncEnabled = periodicSyncEnabled,
                onToggleSmartSync = onToggleSmartSync
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Sync Interval: $syncIntervalMinutes minutes",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                modifier = Modifier.fillMaxWidth(),
                value = syncIntervalMinutes.toFloat(),
                onValueChange = { onIntervalChange(it.toInt()) },
                valueRange = 5f..60f,
                steps = 10,
                enabled = periodicSyncEnabled
            )
            Spacer(modifier = Modifier.height(12.dp))
            TaskNotificationsSection(
                feedbackNotificationsEnabled = feedbackNotificationsEnabled,
                completionNotificationsEnabled = completionNotificationsEnabled,
                periodicSyncEnabled = periodicSyncEnabled,
                onToggleFeedbackNotifications = onToggleFeedbackNotifications,
                onToggleCompletionNotifications = onToggleCompletionNotifications
            )
            Spacer(modifier = Modifier.height(16.dp))
            ForceSyncRow(
                isForceSyncing = isForceSyncing,
                onForceSync = onForceSync
            )
        }
    }
}

@Composable
private fun SmartSyncRow(
    smartSyncEnabled: Boolean,
    periodicSyncEnabled: Boolean,
    onToggleSmartSync: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Smart Background Sync")
            Text(
                text = "Only run background sync when tasks are in progress",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = smartSyncEnabled && periodicSyncEnabled,
            enabled = periodicSyncEnabled,
            onCheckedChange = onToggleSmartSync
        )
    }
}

@Composable
private fun TaskNotificationsSection(
    feedbackNotificationsEnabled: Boolean,
    completionNotificationsEnabled: Boolean,
    periodicSyncEnabled: Boolean,
    onToggleFeedbackNotifications: (Boolean) -> Unit,
    onToggleCompletionNotifications: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Task Notifications",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        NotificationToggleRow(
            title = "Task Feedback Required",
            description = "Notify when a task requires plan approval, feedback, or fails",
            checked = feedbackNotificationsEnabled && periodicSyncEnabled,
            enabled = periodicSyncEnabled,
            onCheckedChange = onToggleFeedbackNotifications
        )
        Spacer(modifier = Modifier.height(8.dp))
        NotificationToggleRow(
            title = "Task Completed",
            description = "Notify when a task state changes to completed",
            checked = completionNotificationsEnabled && periodicSyncEnabled,
            enabled = periodicSyncEnabled,
            onCheckedChange = onToggleCompletionNotifications
        )
    }
}

@Composable
private fun SubNotificationToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked && enabled,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}


@Composable
private fun SyncSettingsHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Text(
            text = "Sync & Background Work",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PeriodicSyncRow(
    periodicSyncEnabled: Boolean,
    onTogglePeriodicSync: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Enable Periodic Sync"
        )
        Switch(
            checked = periodicSyncEnabled,
            onCheckedChange = onTogglePeriodicSync
        )
    }
}


@Composable
private fun ForceSyncRow(
    isForceSyncing: Boolean,
    onForceSync: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Force Sync")
            Text(
                text = "Force sync sessions and sources immediately",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LoadingStateButton(
            label = "Force Sync",
            loadingLabel = "Syncing...",
            isLoading = isForceSyncing,
            enabled = !isForceSyncing,
            onClick = onForceSync
        )
    }
}
