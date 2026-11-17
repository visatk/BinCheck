package com.example

import android.app.Application
import com.example.analytics.FirebaseMetrics
import com.example.data.ValidatorPreferencesRepository
import com.example.domain.NetworkClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application entry point initializing shared HTTP caches and Firebase telemetry
 * (Analytics, Crashlytics, and Performance Monitoring) across UI and WorkManager processes.
 */
class BinCheckApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NetworkClientProvider.init(this)
        FirebaseMetrics.init(this, enabled = true)

        appScope.launch {
            val prefs = runCatching {
                ValidatorPreferencesRepository(applicationContext).settingsFlow.first()
            }.getOrNull()
            if (prefs != null) {
                FirebaseMetrics.setCollectionEnabled(prefs.firebaseMetricsEnabled)
                FirebaseMetrics.syncUserProperties(prefs)
            }
        }
    }
}
