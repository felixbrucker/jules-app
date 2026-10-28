package com.felixbrucker.jules.ui.settings

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.data.worker.ScheduledTaskScheduler
import com.felixbrucker.jules.network.api.model.GitHubApiException
import com.felixbrucker.jules.network.api.model.JulesApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class JulesPlan(val displayName: String, val dailyLimit: Int) {
    FREE("Free", 15),
    PRO("Google AI Pro", 100),
    ULTRA("Google AI Ultra", 300);

    companion object {
        fun fromLimit(limit: Int): JulesPlan {
            return entries.find { it.dailyLimit == limit } ?: FREE
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val julesRepository: JulesRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val scheduledTaskScheduler: ScheduledTaskScheduler
) : ViewModel() {

    var apiKey by mutableStateOf("")
        private set

    var isValidatingApiKey by mutableStateOf(false)
        private set

    var apiKeyValidationErrorMessage by mutableStateOf<String?>(null)
        private set

    val applyApiKeyLabel by derivedStateOf {
        if (isApiKeySavedSuccessfully) "Saved" else "Apply"
    }

    private var isApiKeySavedSuccessfully by mutableStateOf(false)

    var githubAuthToken by mutableStateOf("")
        private set

    var isValidatingGithubAuthToken by mutableStateOf(false)
        private set

    var githubAuthTokenValidationErrorMessage by mutableStateOf<String?>(null)
        private set

    val applyGithubAuthTokenLabel by derivedStateOf {
        if (isGithubAuthTokenSavedSuccessfully) "Saved" else "Apply"
    }

    private var isGithubAuthTokenSavedSuccessfully by mutableStateOf(false)

    val periodicSyncEnabled = appSettingsRepository.periodicSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncIntervalMinutes = appSettingsRepository.syncIntervalMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    val smartSyncEnabled = appSettingsRepository.smartSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val feedbackNotificationsEnabled = appSettingsRepository.feedbackNotificationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val completionNotificationsEnabled = appSettingsRepository.completionNotificationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val useExactAlarms = appSettingsRepository.useExactAlarmsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedPlan = appSettingsRepository.dailySessionLimitFlow
        .map { JulesPlan.fromLimit(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JulesPlan.FREE)

    var isForceSyncing by mutableStateOf(false)
        private set

    var persistedApiKey by mutableStateOf<String?>(null)
        private set

    var persistedGithubAuthToken by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            appSettingsRepository.apiKeyFlow.collect { key ->
                persistedApiKey = key
                if (key != null && apiKey.isBlank()) {
                    apiKey = key
                }
            }
        }
        viewModelScope.launch {
            appSettingsRepository.githubAuthTokenFlow.collect { token ->
                persistedGithubAuthToken = token
                if (token != null && githubAuthToken.isBlank()) {
                    githubAuthToken = token
                }
            }
        }
    }

    fun onApiKeyChanged(newKey: String) {
        apiKey = newKey
        apiKeyValidationErrorMessage = null
        isApiKeySavedSuccessfully = false
    }

    fun onGithubAuthTokenChanged(newToken: String) {
        githubAuthToken = newToken
        githubAuthTokenValidationErrorMessage = null
        isGithubAuthTokenSavedSuccessfully = false
    }

    fun validateAndApplyApiKey() {
        if (apiKey.isBlank()) {
            apiKeyValidationErrorMessage = "API key cannot be empty"
            return
        }

        viewModelScope.launch {
            isValidatingApiKey = true
            apiKeyValidationErrorMessage = null
            isApiKeySavedSuccessfully = false

            try {
                julesRepository.validateApiKey(apiKey.trim())
            } catch (e: JulesApiException) {
                apiKeyValidationErrorMessage = if (e.httpStatusCode == 401) {
                    "Invalid API key"
                } else {
                    e.message ?: "Invalid API key"
                }
                return@launch
            } catch (e: Exception) {
                apiKeyValidationErrorMessage = e.message ?: "Unknown error"
                return@launch
            } finally {
                isValidatingApiKey = false
            }

            appSettingsRepository.saveApiKey(apiKey.trim())
            isApiKeySavedSuccessfully = true
        }
    }

    fun validateAndApplyGithubAuthToken() {
        if (githubAuthToken.isBlank()) {
            viewModelScope.launch {
                appSettingsRepository.saveGithubAuthToken("")
                isGithubAuthTokenSavedSuccessfully = true
                githubAuthTokenValidationErrorMessage = null
            }
            return
        }

        viewModelScope.launch {
            isValidatingGithubAuthToken = true
            githubAuthTokenValidationErrorMessage = null
            isGithubAuthTokenSavedSuccessfully = false

            try {
                julesRepository.validateGithubAuthToken(githubAuthToken.trim())
            } catch (e: GitHubApiException) {
                githubAuthTokenValidationErrorMessage = if (e.httpStatusCode == 401) {
                    "Invalid GitHub Auth Token"
                } else {
                    e.message ?: "Invalid GitHub Auth Token"
                }
                return@launch
            } catch (e: Exception) {
                githubAuthTokenValidationErrorMessage = e.message ?: "Unknown error"
                return@launch
            } finally {
                isValidatingGithubAuthToken = false
            }

            appSettingsRepository.saveGithubAuthToken(githubAuthToken.trim())
            isGithubAuthTokenSavedSuccessfully = true
        }
    }

    fun setPeriodicSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setPeriodicSyncEnabled(enabled)
        }
    }

    fun setSyncIntervalMinutes(interval: Int) {
        viewModelScope.launch {
            appSettingsRepository.setSyncIntervalMinutes(interval)
        }
    }

    fun setSmartSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setSmartSyncEnabled(enabled)
        }
    }

    fun setFeedbackNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setFeedbackNotificationsEnabled(enabled)
        }
    }

    fun setCompletionNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setCompletionNotificationsEnabled(enabled)
        }
    }

    fun setUseExactAlarms(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setUseExactAlarms(enabled)
            scheduledTaskScheduler.rescheduleAllTasks()
        }
    }

    fun selectPlan(plan: JulesPlan) {
        viewModelScope.launch {
            appSettingsRepository.setDailySessionLimit(plan.dailyLimit)
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            isForceSyncing = true
            try {
                julesRepository.sync(force = true)
            } catch (_: Exception) {
            } finally {
                isForceSyncing = false
            }
        }
    }
}
