package com.felixbrucker.jules.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.JulesApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val julesRepository: JulesRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    var apiKey by mutableStateOf("")
        private set

    var isValidatingApiKey by mutableStateOf(false)
        private set

    var apiKeyValidationErrorMessage by mutableStateOf<String?>(null)
        private set

    var isSuccess by mutableStateOf(false)
        private set

    fun onApiKeyChanged(newKey: String) {
        apiKey = newKey
        apiKeyValidationErrorMessage = null
    }

    fun validateAndSaveApiKey() {
        if (apiKey.isBlank()) {
            apiKeyValidationErrorMessage = "API key cannot be empty"
            return
        }

        viewModelScope.launch {
            isValidatingApiKey = true
            apiKeyValidationErrorMessage = null

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
        }
    }
}
