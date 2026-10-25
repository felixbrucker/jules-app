package com.felixbrucker.jules

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.worker.JulesSyncWorker
import com.felixbrucker.jules.data.worker.WorkerScheduler
import com.felixbrucker.jules.ui.navigation.JulesNavGraph
import com.felixbrucker.jules.ui.navigation.Screen
import com.felixbrucker.jules.ui.theme.JulesTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Inject
    lateinit var sessionDao: SessionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            combine(
                appSettingsRepository.appSettingsFlow,
                sessionDao.hasActiveInProgressSessionsFlow()
            ) { settings, hasInProgress ->
                WorkerScheduler.evaluate(settings, hasInProgress)
            }
                .distinctUntilChanged()
                .collect { decision ->
                    if (decision.shouldEnqueue) {
                        JulesSyncWorker.enqueueSyncWork(applicationContext, decision.intervalMinutes)
                    } else {
                        JulesSyncWorker.cancelSyncWork(applicationContext)
                    }
                }
        }

        setContent {
            JulesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by viewModel.authState.collectAsState(
                        initial = AuthState(isReady = false, apiKey = null)
                    )
                    if (authState.isReady) {
                        val startDest = if (authState.apiKey.isNullOrBlank()) Screen.Login.route else Screen.SessionList.route
                        JulesNavGraph(startDestination = startDest)
                    }
                }
            }
        }
    }
}

data class AuthState(
    val isReady: Boolean,
    val apiKey: String?
)

@HiltViewModel
class MainViewModel @Inject constructor(
    appSettingsRepository: AppSettingsRepository
) : ViewModel() {
    val authState = appSettingsRepository.apiKeyFlow
        .distinctUntilChanged()
        .map { AuthState(isReady = true, apiKey = it) }
}
