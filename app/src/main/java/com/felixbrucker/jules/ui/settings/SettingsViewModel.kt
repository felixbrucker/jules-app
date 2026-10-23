package com.felixbrucker.jules.ui.settings

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val julesRepository: JulesRepository,
    private val appSettingsRepository: AppSettingsRepository
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

    val periodicSyncEnabled = appSettingsRepository.periodicSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncIntervalMinutes = appSettingsRepository.syncIntervalMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    val archivedSyncEnabled = appSettingsRepository.archivedSyncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var persistedApiKey by mutableStateOf<String?>(null)
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
    }

    fun onApiKeyChanged(newKey: String) {
        apiKey = newKey
        apiKeyValidationErrorMessage = null
        isApiKeySavedSuccessfully = false
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
            } catch (e: ApiException) {
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

    fun setArchivedSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setArchivedSyncEnabled(enabled)
        }
    }
}
