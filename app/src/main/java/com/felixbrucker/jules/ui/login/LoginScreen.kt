package com.felixbrucker.jules.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.R
import com.felixbrucker.jules.ui.components.ApiKeyTextField
import com.felixbrucker.jules.ui.components.LoadingStateButton

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    LaunchedEffect(viewModel.isSuccess) {
        if (viewModel.isSuccess) {
            onLoginSuccess()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LoginContent(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel
        )
    }
}

@Composable
private fun LoginContent(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_jules),
            contentDescription = "Jules Logo",
            modifier = Modifier.size(72.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to Jules",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter your API key to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        ApiKeyTextField(
            modifier = Modifier.fillMaxWidth(),
            apiKey = viewModel.apiKey,
            onApiKeyChange = { viewModel.onApiKeyChanged(it) },
            errorMessage = viewModel.apiKeyValidationErrorMessage,
            onImeAction = { viewModel.validateAndSaveApiKey() }
        )
        Spacer(modifier = Modifier.height(24.dp))
        LoadingStateButton(
            modifier = Modifier.fillMaxWidth(),
            label = "Next",
            loadingLabel = "Validating...",
            isLoading = viewModel.isValidatingApiKey,
            enabled = viewModel.apiKey.isNotBlank(),
            onClick = { viewModel.validateAndSaveApiKey() }
        )
    }
}
