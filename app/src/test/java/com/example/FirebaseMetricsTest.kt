package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.analytics.FirebaseMetrics
import com.example.analytics.MetricsSink
import com.example.analytics.PerformanceTraceHandle
import com.example.analytics.TelemetrySanitizer
import com.example.data.AppThemeMode
import com.example.data.ValidatorPreferencesRepository
import com.example.data.ValidatorSettings
import com.example.domain.DeviceNetworkStatus
import com.example.domain.NetworkClientProvider
import com.example.domain.NetworkTelemetrySnapshot
import com.example.domain.ValidationMode
import com.example.ui.SettingsBottomSheet
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingMetricsSink : MetricsSink {
    data class LoggedEvent(val name: String, val params: Map<String, Any?>)
    data class LoggedScreen(val screenName: String, val screenClass: String)
    data class LoggedHttpMetric(
        val sanitizedUrl: String,
        val method: String,
        val statusCode: Int,
        val latencyMs: Long,
        val responseBytes: Long
    )

    var isEnabledState: Boolean = true
    val events = mutableListOf<LoggedEvent>()
    val screens = mutableListOf<LoggedScreen>()
    val userProperties = mutableMapOf<String, String>()
    val customKeys = mutableMapOf<String, String>()
    val breadcrumbs = mutableListOf<String>()
    val nonFatals = mutableListOf<Pair<String, Throwable>>()
    val tracesStarted = mutableListOf<String>()
    val tracesStopped = mutableListOf<String>()
    val httpMetrics = mutableListOf<LoggedHttpMetric>()

    override fun setCollectionEnabled(enabled: Boolean) {
        isEnabledState = enabled
    }

    override fun logScreenView(screenName: String, screenClass: String) {
        screens.add(LoggedScreen(screenName, screenClass))
    }

    override fun logEvent(name: String, params: Map<String, Any?>) {
        events.add(LoggedEvent(name, params))
    }

    override fun setUserProperty(name: String, value: String) {
        userProperties[name] = value
    }

    override fun setCustomKey(key: String, value: String) {
        customKeys[key] = value
    }

    override fun logBreadcrumb(message: String) {
        breadcrumbs.add(message)
    }

    override fun recordNonFatal(tag: String, throwable: Throwable, context: Map<String, String>) {
        nonFatals.add(tag to throwable)
    }

    override fun startTrace(name: String): PerformanceTraceHandle {
        tracesStarted.add(name)
        return object : PerformanceTraceHandle {
            override fun putAttribute(name: String, value: String) = Unit
            override fun incrementMetric(name: String, by: Long) = Unit
            override fun stop() {
                tracesStopped.add(name)
            }
        }
    }

    override fun recordHttpMetric(
        sanitizedUrl: String,
        method: String,
        statusCode: Int,
        latencyMs: Long,
        responseBytes: Long
    ) {
        httpMetrics.add(LoggedHttpMetric(sanitizedUrl, method, statusCode, latencyMs, responseBytes))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseMetricsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var sink: RecordingMetricsSink

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sink = RecordingMetricsSink()
        FirebaseMetrics.installSinkForTesting(sink, enabled = true)
        FirebaseMetrics.attachNetworkHttpMetricHook()
    }

    @After
    fun tearDown() {
        FirebaseMetrics.installSinkForTesting(null, enabled = true)
        NetworkClientProvider.httpMetricListener = null
    }

    @Test
    fun `telemetry sanitizer scrubs card numbers tokens and query parameters`() {
        val scrubbedCard = TelemetrySanitizer.sanitizeValue(
            "Failed for 4532250055360003|08|2028|113 with Bearer sk_live_abc123"
        )
        assertFalse(scrubbedCard.contains("4532250055360003"))
        assertFalse(scrubbedCard.contains("sk_live_abc123"))
        assertTrue(scrubbedCard.contains("[REDACTED_RECORD]"))

        val scrubbedUrl = TelemetrySanitizer.sanitizeUrl(
            "https://api.telegram.org/bot9139193386:AAHSB1wGBBh6/sendMessage?chat_id=12345&text=4532250055360003"
        )
        assertFalse(scrubbedUrl.contains("9139193386"))
        assertFalse(scrubbedUrl.contains("12345"))
        assertFalse(scrubbedUrl.contains("4532250055360003"))
        assertEquals("https://api.telegram.org/bot[REDACTED]/sendMessage", scrubbedUrl)

        val safeEventName = TelemetrySanitizer.sanitizeName("123_My Custom-Event Name That Is Much Too Long For GA4")
        assertTrue(safeEventName.length <= 40)
        assertTrue(safeEventName.first() in 'a'..'z')
    }

    @Test
    fun `firebase metrics logs batch lifecycle bin lookup and respects opt out toggle`() {
        FirebaseMetrics.logScreenView("Workspace_Split", "MainActivity")
        FirebaseMetrics.logBatchStarted(
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            sourceType = "console",
            lineCount = 25,
            concurrency = 4,
            delayMs = 55L,
            wifiOnly = false,
            batterySaver = false,
            deduplicate = true
        )
        val trace = FirebaseMetrics.startTrace("batch_validation_run")
        trace.putAttribute("mode", "local_deterministic")
        trace.incrementMetric("processed_items", 25L)
        trace.stop()
        FirebaseMetrics.logBatchCompleted(
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            processed = 25,
            total = 25,
            live = 20,
            die = 5,
            unknown = 0,
            durationMs = 420L,
            dedupSaved = 2,
            outcome = "success"
        )
        FirebaseMetrics.syncUserProperties(
            ValidatorSettings(
                validationMode = ValidationMode.SANDBOX_SUITE,
                themeMode = AppThemeMode.DARK,
                concurrency = 6,
                batterySaverMode = true
            )
        )

        assertEquals(1, sink.screens.size)
        assertEquals("Workspace_Split", sink.screens.first().screenName)
        assertEquals(listOf("batch_started", "batch_completed"), sink.events.map { it.name })
        assertEquals(80.0, sink.events[1].params["live_rate_pct"] as Double, 0.01)
        assertEquals(listOf("batch_validation_run"), sink.tracesStarted)
        assertEquals(listOf("batch_validation_run"), sink.tracesStopped)
        assertEquals("sandbox_suite", sink.userProperties["validation_mode"])
        assertEquals("dark", sink.userProperties["theme_mode"])
        assertEquals("6", sink.userProperties["worker_concurrency"])

        // Disable collection and confirm no further telemetry is dispatched
        FirebaseMetrics.setCollectionEnabled(false)
        assertFalse(sink.isEnabledState)
        FirebaseMetrics.logBinLookup(
            source = "binlist.net",
            cacheHit = false,
            scheme = "VISA",
            countryCode = "US",
            isPrepaid = false,
            isVerified = true,
            success = true,
            latencyMs = 180L
        )
        assertEquals(2, sink.events.size)
    }

    @Test
    fun `network http metric listener forwards sanitized url without query parameters`() {
        NetworkClientProvider.httpMetricListener?.onHttpCallCompleted(
            rawUrl = "https://mock.payate.com/api.php?lista=4532250055360003|08|2028|113",
            method = "GET",
            statusCode = 200,
            latencyMs = 310L,
            responseBytes = 128L,
            fromCache = false
        )

        assertEquals(1, sink.httpMetrics.size)
        val recorded = sink.httpMetrics.first()
        assertEquals("https://mock.payate.com/api.php", recorded.sanitizedUrl)
        assertEquals(200, recorded.statusCode)
        assertEquals(128L, recorded.responseBytes)
    }

    @Test
    fun `preferences repository persists firebase metrics toggle`() = runTest {
        val repo = ValidatorPreferencesRepository(context)
        assertTrue(repo.settingsFlow.first().firebaseMetricsEnabled)

        repo.setFirebaseMetricsEnabled(false)
        assertFalse(repo.settingsFlow.first().firebaseMetricsEnabled)

        repo.setFirebaseMetricsEnabled(true)
        assertTrue(repo.settingsFlow.first().firebaseMetricsEnabled)
    }

    @Test
    fun `settings system tab displays firebase metrics card and toggles state`() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                SettingsBottomSheet(
                    settings = ValidatorSettings(firebaseMetricsEnabled = true),
                    networkStats = NetworkTelemetrySnapshot(),
                    deviceNetworkStatus = DeviceNetworkStatus(),
                    isTestingEndpoint = false,
                    httpProbeResult = null,
                    isTestingTelegram = false,
                    telegramProbeResult = null,
                    onDismiss = {},
                    onTestEndpoint = {},
                    onDetectTelegramChat = {},
                    onTestTelegramWebhook = {},
                    onClearCaches = {},
                    onSaveSettings = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onGenerateBatchToConsole = { _, _ -> },
                    onClearAllBuckets = {},
                    onToggleFirebaseMetrics = { toggledValue = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_tab_system").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("firebase_metrics_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("firebase_metrics_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(false, toggledValue)
    }
}
