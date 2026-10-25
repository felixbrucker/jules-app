package com.felixbrucker.jules

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.felixbrucker.jules.data.logging.AppLogTree
import com.felixbrucker.jules.data.repository.LogRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class JulesApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var logRepository: LogRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Timber.plant(AppLogTree(logRepository, applicationScope))
        Timber.tag(TAG).d("Initializing Jules application")
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        applicationScope.launch {
            try {
                logRepository.pruneOldLogs(daysToKeep = 7)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to prune old logs on app startup")
            }
        }
    }

    companion object {
        private const val TAG = "JulesApplication"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
