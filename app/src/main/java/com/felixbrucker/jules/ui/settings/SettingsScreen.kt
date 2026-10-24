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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    onBackClick: () -> Unit
) {
    val periodicSyncEnabled by viewModel.periodicSyncEnabled.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()
    val archivedSyncEnabled by viewModel.archivedSyncEnabled.collectAsState()

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
            archivedSyncEnabled = archivedSyncEnabled
        )
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    periodicSyncEnabled: Boolean,
    syncIntervalMinutes: Int,
    archivedSyncEnabled: Boolean
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
        SyncSettingsCard(
            periodicSyncEnabled = periodicSyncEnabled,
            syncIntervalMinutes = syncIntervalMinutes,
            archivedSyncEnabled = archivedSyncEnabled,
            onTogglePeriodicSync = { viewModel.setPeriodicSyncEnabled(it) },
            onIntervalChange = { viewModel.setSyncIntervalMinutes(it) },
            onToggleArchivedSync = { viewModel.setArchivedSyncEnabled(it) }
        )
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
    archivedSyncEnabled: Boolean,
    onTogglePeriodicSync: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onToggleArchivedSync: (Boolean) -> Unit
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
            Spacer(modifier = Modifier.height(16.dp))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Sync Archived Sessions")
                    Text(
                        text = "Include archived sessions in periodic sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = archivedSyncEnabled,
                    enabled = periodicSyncEnabled,
                    onCheckedChange = onToggleArchivedSync
                )
            }
        }
    }
}
