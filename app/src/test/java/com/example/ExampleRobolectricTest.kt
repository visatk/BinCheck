package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.data.AppThemeMode
import com.example.data.ValidatorPreferencesRepository
import com.example.data.ValidatorSettings
import com.example.data.local.AppDatabase
import com.example.data.local.BinCacheEntity
import com.example.data.local.HistoryFilter
import com.example.data.local.ValidationHistoryRepository
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.BinInfo
import com.example.domain.BinLookupService
import com.example.domain.CardScheme
import com.example.domain.CardValidatorEngine
import com.example.domain.CustomHttpConfig
import com.example.domain.DeviceNetworkStatus
import com.example.domain.HttpRequestMethod
import com.example.domain.ListSortOrder
import com.example.domain.ListToolkitEngine
import com.example.domain.ListToolkitOptions
import com.example.domain.NetworkTelemetrySnapshot
import com.example.domain.OutputFormat
import com.example.domain.ResultBucket
import com.example.domain.TelegramWebhookClient
import com.example.domain.TelegramWebhookConfig
import com.example.domain.ToolkitTransformResult
import com.example.domain.ValidationApiProviders
import com.example.domain.ValidationMode
import com.example.domain.ValidationRecord
import okio.Buffer
import com.example.ui.BinCheckTopBar
import com.example.ui.ConsoleInputCard
import com.example.ui.CustomHttpSectionCard
import com.example.ui.HistoryLogBottomSheet
import com.example.ui.HistoryUiSummary
import com.example.ui.ListToolkitBottomSheet
import com.example.ui.NetworkOptimizationSectionCard
import com.example.ui.ResultsCard
import com.example.ui.SettingsBottomSheet
import com.example.ui.TelegramWebhookSectionCard
import com.example.ui.TopTelemetryStrip
import com.example.ui.ValidatorUiState
import com.example.ui.theme.MyApplicationTheme
import com.example.worker.BatchValidationWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var inMemoryDb: AppDatabase
    private lateinit var repository: ValidationHistoryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ValidationHistoryRepository(inMemoryDb.validationHistoryDao())
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
    }

    @Test
    fun `read string resources from context`() {
        assertEquals("BinCheck", context.getString(R.string.app_name))
        assertEquals("453225005536xxx3|08|2028|113", context.getString(R.string.input_placeholder))
        assertEquals("https://t.me/drkingbd", context.getString(R.string.developer_channel_url))
    }

    @Test
    fun `history repository filters success and failure entries`() = runTest {
        val validRecord = CardValidatorEngine.validateLine(
            rawLine = "453225005536xxx3|08|2028|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = true
        )
        val invalidRecord = CardValidatorEngine.validateLine(
            rawLine = "123456|08|2028|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = false
        )

        repository.recordResult("batch-1", "sample.txt", validRecord, "Local")
        repository.recordResult("batch-1", "sample.txt", invalidRecord, "Local")

        val all = repository.observeHistory(HistoryFilter.ALL).first()
        val success = repository.observeHistory(HistoryFilter.SUCCESS).first()
        val failure = repository.observeHistory(HistoryFilter.FAILURE).first()

        assertEquals(2, all.size)
        assertEquals(1, success.size)
        assertEquals(ResultBucket.LIVE.name, success.first().status)
        assertEquals(1, failure.size)
        assertEquals(ResultBucket.DIE.name, failure.first().status)
    }

    @Test
    fun `batch validation worker processes queued entries concurrently and saves history`() = runTest {
        val appDb = AppDatabase.getInstance(context)
        val appRepo = ValidationHistoryRepository(appDb.validationHistoryDao())
        appRepo.clearAllHistory()

        val batchId = "worker-test-batch"
        appRepo.enqueueBatch(
            batchId = batchId,
            lines = listOf(
                "453225005536xxx3|08|2028|113",
                "453225005536xxx3|09|2029|224",
                "bad_line_format"
            ),
            sourceName = "upload.html"
        )

        val worker = TestListenableWorkerBuilder<BatchValidationWorker>(context)
            .setInputData(
                workDataOf(
                    BatchValidationWorker.KEY_BATCH_ID to batchId,
                    BatchValidationWorker.KEY_VALIDATION_MODE to ValidationMode.LOCAL_DETERMINISTIC.name,
                    BatchValidationWorker.KEY_AUTO_RESOLVE_WILDCARDS to true,
                    BatchValidationWorker.KEY_CONCURRENCY to 3,
                    BatchValidationWorker.KEY_BATCH_DELAY_MS to 0L,
                    BatchValidationWorker.KEY_SOURCE_NAME to "upload.html"
                )
            )
            .build()

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(3, output.getInt(BatchValidationWorker.KEY_PROCESSED, -1))
        assertEquals(2, output.getInt(BatchValidationWorker.KEY_LIVE, -1))
        assertEquals(1, output.getInt(BatchValidationWorker.KEY_UNKNOWN, -1))

        val savedHistory = appRepo.observeHistory(HistoryFilter.ALL).first()
        assertEquals(3, savedHistory.size)
        assertTrue(appRepo.getQueuedBatch(batchId).isEmpty())
        appRepo.clearAllHistory()
    }

    @Test
    fun `preferences repository persists theme mode and dynamic color`() = runTest {
        val prefsRepo = ValidatorPreferencesRepository(context)
        prefsRepo.updateSettings(
            validationMode = ValidationMode.REMOTE_WEBHOOK,
            webhookUrl = "https://api.example.com/check?lista={lista}",
            httpMethod = HttpRequestMethod.POST_JSON,
            httpHeaders = "Authorization: Bearer abc",
            httpBodyTemplate = """{"lista":"{lista}"}""",
            httpTimeoutSeconds = 20,
            httpMaxRetries = 2,
            httpLiveKeywords = "live,charged",
            httpDieKeywords = "die,declined",
            autoResolveWildcards = true,
            batchDelayMs = 40L,
            concurrency = 6,
            patternTemplate = "453225005536xxx3|08|2028|113",
            generateQuantity = 25,
            themeMode = AppThemeMode.LIGHT,
            useDynamicColor = false
        )

        val stored = prefsRepo.settingsFlow.first()
        assertEquals(AppThemeMode.LIGHT, stored.themeMode)
        assertEquals(false, stored.useDynamicColor)
        assertEquals(6, stored.concurrency)
        assertEquals(HttpRequestMethod.POST_JSON, stored.httpMethod)
        assertEquals(20, stored.httpTimeoutSeconds)
        assertEquals(2, stored.httpMaxRetries)
        assertEquals("live,charged", stored.httpLiveKeywords)
    }

    @Test
    fun `consoleInputCard renders multi line batch cleanly`() {
        val sampleLines = (1..30).joinToString("\n") { idx ->
            "453225005536${idx.toString().padStart(3, '0')}3|08|2028|113"
        }
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                ConsoleInputCard(
                    uiState = ValidatorUiState(rawInputText = sampleLines),
                    consoleHeight = 250.dp,
                    cardPadding = 16.dp,
                    onInputChanged = {},
                    onToggleRun = {},
                    onOpenSettings = {},
                    onOpenFileImport = {},
                    onQuickGen10x = {},
                    onExpandWildcards = {},
                    onPasteOrFill = {},
                    onClearInput = {},
                    onClearBanner = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("card_batch_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("play_validation_button").assertIsDisplayed()
    }

    @Test
    fun `customHttpSectionCard renders HTTP controls and test button`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CustomHttpSectionCard(
                        config = CustomHttpConfig(
                            url = "https://api.example.com/verify",
                            method = HttpRequestMethod.POST_JSON,
                            headersText = "Authorization: Bearer test",
                            bodyTemplate = """{"lista":"{lista}"}"""
                        ),
                        isTestingEndpoint = false,
                        probeResult = null,
                        onConfigChange = {},
                        onTestEndpoint = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("webhook_url_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("http_headers_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_http_endpoint_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `binLookupService parses binlist and handyapi payloads accurately`() {
        val binlistSample = """
            {
              "scheme": "visa",
              "type": "credit",
              "brand": "Platinum",
              "prepaid": false,
              "country": {
                "alpha2": "US",
                "name": "United States of America",
                "emoji": "🇺🇸",
                "currency": "USD"
              },
              "bank": {
                "name": "JPMORGAN CHASE BANK N.A.",
                "url": "www.chase.com",
                "phone": "1-800-432-3117"
              }
            }
        """.trimIndent()
        val parsedBinlist = BinLookupService.parseBinlistJson("453225", binlistSample)!!
        assertEquals("453225", parsedBinlist.bin)
        assertEquals("VISA", parsedBinlist.scheme)
        assertEquals("CREDIT", parsedBinlist.type)
        assertEquals("PLATINUM", parsedBinlist.brand)
        assertEquals("JPMORGAN CHASE BANK N.A.", parsedBinlist.bankName)
        assertEquals("US", parsedBinlist.countryCode)
        assertEquals("USD", parsedBinlist.currency)
        assertTrue(parsedBinlist.compactSummary().contains("JPMORGAN CHASE BANK N.A."))

        val handySample = """
            {
              "Status": "SUCCESS",
              "Scheme": "MASTERCARD",
              "Type": "DEBIT",
              "Issuer": "BARCLAYS BANK UK PLC",
              "CardTier": "WORLD",
              "Country": { "A2": "GB", "Name": "UNITED KINGDOM" }
            }
        """.trimIndent()
        val parsedHandy = BinLookupService.parseHandyApiJson("541275", handySample)!!
        assertEquals("MASTERCARD", parsedHandy.scheme)
        assertEquals("DEBIT", parsedHandy.type)
        assertEquals("BARCLAYS BANK UK PLC", parsedHandy.bankName)
        assertEquals("GB", parsedHandy.countryCode)
        assertEquals("🇬🇧", parsedHandy.countryEmoji)
    }

    @Test
    fun `resultsCard displays BIN lookup badge for Live records`() {
        val liveRecord = ValidationRecord(
            id = "live_1",
            rawLine = "4532250055360003|08|2028|113",
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            month = "08",
            year = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            bucket = ResultBucket.LIVE,
            message = "VISA • Luhn Valid • Exp 08/2028",
            latencyMs = 4
        )
        val binInfo = BinInfo(
            bin = "453225",
            scheme = "VISA",
            type = "CREDIT",
            brand = "CLASSIC",
            bankName = "CHASE BANK",
            countryCode = "US",
            countryEmoji = "🇺🇸"
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                ResultsCard(
                    uiState = ValidatorUiState(
                        selectedTab = ResultBucket.LIVE,
                        liveRecords = listOf(liveRecord),
                        binLookupMap = mapOf("453225" to binInfo)
                    ),
                    fillRemainingHeight = false,
                    onSelectTab = {},
                    onCopyAll = { _, _ -> },
                    onClearTab = {},
                    onCopyRecord = {},
                    onRemoveRecord = {},
                    onInspectBin = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_bin_lookup_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_bin_badge_4532250055360003").assertIsDisplayed()
        composeTestRule.onNodeWithText("🇺🇸 US | CREDIT • CLASSIC | CHASE BANK").assertIsDisplayed()
    }

    @Test
    fun `validationApiProviders builds and parses Payate and Chkr responses`() {
        val payateReq = ValidationApiProviders.buildPayateRequest("5154620021111602|10|2031|564")
        assertEquals("POST", payateReq.method)
        assertEquals(ValidationApiProviders.PAYATE_URL, payateReq.url.toString())
        val payateBuf = Buffer()
        payateReq.body!!.writeTo(payateBuf)
        assertEquals("data=5154620021111602%7C10%7C2031%7C564", payateBuf.readUtf8())

        val payateLive = ValidationApiProviders.parsePayateResponse(
            200,
            """{"error":1,"msg":"<div><b style='color:#008000'>Live<\/b> | 5154620021111602 | 10\/2031 | [BIN: 515462] $2.06 Checked - Shinji<\/div>"}"""
        )
        assertEquals(ResultBucket.LIVE, payateLive.bucket)
        assertTrue(payateLive.detailMessage.contains("515462"))

        val payateDie = ValidationApiProviders.parsePayateResponse(
            200,
            """{"error":2,"msg":"<div><b style='color:#FF0000'>Die<\/b> | 5154620021124407 | 02\/2028 | [BIN: 515462] $0.30 Checked - Shinji<\/div>"}"""
        )
        assertEquals(ResultBucket.DIE, payateDie.bucket)

        val payateUnknown = ValidationApiProviders.parsePayateResponse(
            200,
            """{"error":3,"msg":"<div><b style='color:#FF8C00'>Unknown<\/b> | 5154620021142854 | 11\/2029 | [BIN: 515462] $2.07 Checked - Shinji<\/div>"}"""
        )
        assertEquals(ResultBucket.UNKNOWN, payateUnknown.bucket)

        val chkrReq = ValidationApiProviders.buildChkrRequest("379363037471377|07|2034|3204")
        assertEquals("POST", chkrReq.method)
        assertEquals(ValidationApiProviders.CHKR_URL, chkrReq.url.toString())
        val chkrBuf = Buffer()
        chkrReq.body!!.writeTo(chkrBuf)
        assertEquals("""{"data":"379363037471377|07|2034|3204","charge":false}""", chkrBuf.readUtf8())

        val chkrLive = ValidationApiProviders.parseChkrResponse(
            pan = "379363037471377",
            statusCode = 200,
            bodyStr = """{"code":1,"status":"Live","message":"Charge OK. [GATE_01@chkr.cc]","card":{"card":"379363037471377|07|2034|3204","bank":"AMERICAN EXPRESS US","type":"american express","category":"credit","brand":"AMERICAN EXPRESS","country":{"name":"US","code":"XX","emoji":"🇺🇸","currency":"USD"}}}"""
        )
        assertEquals(ResultBucket.LIVE, chkrLive.bucket)
        assertEquals("Charge OK. [GATE_01@chkr.cc]", chkrLive.detailMessage)
        assertEquals("AMERICAN EXPRESS US", chkrLive.embeddedBinInfo?.bankName)
        assertEquals("US", chkrLive.embeddedBinInfo?.countryCode)
        assertEquals("🇺🇸", chkrLive.embeddedBinInfo?.countryEmoji)

        val chkrDie = ValidationApiProviders.parseChkrResponse(
            pan = "379363037459380",
            statusCode = 200,
            bodyStr = """{"code":0,"status":"Die","message":"Unable to authenticate"}"""
        )
        assertEquals(ResultBucket.DIE, chkrDie.bucket)
        assertEquals("Unable to authenticate", chkrDie.detailMessage)
    }

    @Test
    fun `consoleInputCard renders SplitButton and LinearWavyProgressIndicator when running`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                ConsoleInputCard(
                    uiState = ValidatorUiState(
                        rawInputText = "4532250055360003|08|2028|113",
                        isRunning = true,
                        processedCount = 4,
                        totalBatchCount = 10
                    ),
                    consoleHeight = 200.dp,
                    cardPadding = 14.dp,
                    onInputChanged = {},
                    onToggleRun = {},
                    onOpenSettings = {},
                    onOpenFileImport = {},
                    onQuickGen10x = {},
                    onExpandWildcards = {},
                    onPasteOrFill = {},
                    onClearInput = {},
                    onClearBanner = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("play_validation_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("engine_split_dropdown_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("batch_wavy_progress_indicator").assertIsDisplayed()
    }

    @Test
    fun `navigation drawer shell renders permanent drawer on expanded width`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                val drawerState = androidx.compose.material3.rememberDrawerState(
                    androidx.compose.material3.DrawerValue.Closed
                )
                com.example.ui.BinCheckNavigationShell(
                    isExpandedWidth = true,
                    drawerState = drawerState,
                    uiState = ValidatorUiState(rawInputText = "4532250055360003|08|2028|113"),
                    activePane = com.example.ui.CompactWorkspacePane.SPLIT,
                    channelUrl = "https://t.me/drkingbd",
                    onSelectPane = {},
                    onOpenBinLookup = {},
                    onOpenFileImport = {},
                    onOpenHistory = {},
                    onOpenSettings = {},
                    onSelectThemeMode = {},
                    onToggleDynamicColor = {},
                    onOpenDeveloperChannel = {},
                    onCloseDrawer = {}
                ) {
                    Box {}
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("permanent_navigation_drawer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_item_workspace").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_item_bin_lookup").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_item_toolkit").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_item_history").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `topBar and telemetryStrip display stream metrics and quick actions`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                androidx.compose.foundation.layout.Column {
                    BinCheckTopBar(
                        showMenuButton = true,
                        isRunning = true,
                        isLookingUpBin = false,
                        processedCount = 3,
                        totalBatchCount = 10,
                        validationMode = ValidationMode.MULTI_API_AUTO,
                        httpMethod = HttpRequestMethod.POST_JSON,
                        concurrency = 4,
                        historyCount = 12,
                        onOpenDrawer = {},
                        onOpenBinLookup = {},
                        onOpenHistory = {},
                        onOpenSettings = {}
                    )
                    TopTelemetryStrip(
                        binDigits = "453225",
                        detectedScheme = CardScheme.VISA,
                        loadedFileName = "cards.txt",
                        onOpenBinLookup = {},
                        onOpenDeveloperChannel = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_navigation_drawer_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("topbar_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("topbar_bin_lookup_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("telemetry_bin_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("header_developer_channel_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Validating 3 / 10").assertIsDisplayed()
        composeTestRule.onNodeWithText("BIN 453225 • VISA").assertIsDisplayed()
    }

    @Test
    fun `resultsCard renders expressive empty state and BIN CTA when empty`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                ResultsCard(
                    uiState = ValidatorUiState(selectedTab = ResultBucket.LIVE),
                    fillRemainingHeight = true,
                    onSelectTab = {},
                    onCopyAll = { _, _ -> },
                    onClearTab = {},
                    onCopyRecord = {},
                    onRemoveRecord = {},
                    onInspectBin = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("No Live records yet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_bin_lookup_button").assertIsDisplayed()
    }

    @Test
    fun `topBar displays fixed 10s delay summary when idle in Chkr mode`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                BinCheckTopBar(
                    showMenuButton = true,
                    isRunning = false,
                    isLookingUpBin = false,
                    processedCount = 0,
                    totalBatchCount = 0,
                    validationMode = ValidationMode.CHKR_CC,
                    httpMethod = HttpRequestMethod.AUTO,
                    concurrency = 4,
                    historyCount = 0,
                    onOpenDrawer = {},
                    onOpenBinLookup = {},
                    onOpenHistory = {},
                    onOpenSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CHKR.CC • 10s delay").assertIsDisplayed()
    }

    @Test
    fun `preferences repository persists custom batch delay above 2 seconds`() = runTest {
        val prefsRepo = ValidatorPreferencesRepository(context)
        prefsRepo.updateSettings(
            validationMode = ValidationMode.REMOTE_WEBHOOK,
            webhookUrl = "https://api.example.com/check",
            autoResolveWildcards = true,
            batchDelayMs = 4_500L,
            concurrency = 2,
            patternTemplate = "453225005536xxx3|08|2028|113",
            generateQuantity = 10,
            themeMode = AppThemeMode.DARK,
            useDynamicColor = false
        )

        val stored = prefsRepo.settingsFlow.first()
        assertEquals(4_500L, stored.batchDelayMs)
        assertEquals(4_500L, stored.effectiveDelayMs)
    }

    @Test
    fun `preferences repository persists telegram webhook configuration`() = runTest {
        val prefsRepo = ValidatorPreferencesRepository(context)
        prefsRepo.updateSettings(
            validationMode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = true,
            batchDelayMs = 55L,
            concurrency = 4,
            patternTemplate = "453225005536xxx3|08|2028|113",
            generateQuantity = 10,
            themeMode = AppThemeMode.SYSTEM,
            useDynamicColor = true,
            telegramWebhookEnabled = true,
            telegramChatId = "-1001234567890",
            telegramNotifyLiveOnly = false
        )

        val stored = prefsRepo.settingsFlow.first()
        assertTrue(stored.telegramWebhookEnabled)
        assertEquals("-1001234567890", stored.telegramChatId)
        assertEquals(false, stored.telegramNotifyLiveOnly)
    }

    @Test
    fun `customHttpSectionCard renders payload editor and presets in AUTO mode`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CustomHttpSectionCard(
                        config = CustomHttpConfig(
                            url = "https://api.example.com/verify",
                            method = HttpRequestMethod.AUTO
                        ),
                        isTestingEndpoint = false,
                        probeResult = null,
                        onConfigChange = {},
                        onTestEndpoint = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("payload_preset_json_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("http_body_template_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("payload_token_bin").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `telegramWebhookSectionCard renders chat id and detect controls`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TelegramWebhookSectionCard(
                        config = TelegramWebhookConfig(
                            enabled = true,
                            chatId = "123456789",
                            notifyLiveOnly = true
                        ),
                        isTestingTelegram = false,
                        probeResult = null,
                        onConfigChange = {},
                        onDetectChat = {},
                        onTestWebhook = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("telegram_webhook_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("telegram_chat_id_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("telegram_detect_chat_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("telegram_test_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `telegramWebhookClient builds sendMessage request and parses bot updates`() {
        val req = TelegramWebhookClient.buildSendMessageRequest(
            botToken = "12345:TEST_TOKEN",
            chatId = "-10099887766",
            htmlText = "<b>BinCheck Live</b>"
        )
        assertEquals("POST", req.method)
        assertEquals("https://api.telegram.org/bot12345:TEST_TOKEN/sendMessage", req.url.toString())
        val buf = Buffer()
        req.body!!.writeTo(buf)
        val jsonBody = buf.readUtf8()
        assertTrue(jsonBody.contains("-10099887766"))
        assertTrue(jsonBody.contains("HTML"))

        assertEquals(
            "SandBoxAutoBot",
            TelegramWebhookClient.parseBotUsername("""{"ok":true,"result":{"username":"SandBoxAutoBot"}}""")
        )
        assertEquals(
            "987654321",
            TelegramWebhookClient.parseLatestChatId(
                """{"ok":true,"result":[{"update_id":1,"message":{"chat":{"id":111}}},{"update_id":2,"message":{"chat":{"id":987654321}}}]}"""
            )
        )
    }

    @Test
    fun `binCacheDao stores respects TTL and prunes expired bins`() = runTest {
        val dao = inMemoryDb.binCacheDao()
        val now = 1_000_000L
        val validInfo = BinInfo(
            bin = "453225",
            scheme = "VISA",
            type = "CREDIT",
            brand = "CLASSIC",
            bankName = "CHASE BANK",
            countryCode = "US",
            countryEmoji = "🇺🇸",
            isNetworkVerified = true
        )
        val expiredInfo = validInfo.copy(bin = "541275", scheme = "MASTERCARD")

        dao.upsertBin(BinCacheEntity.fromBinInfo(validInfo, ttlMillis = 60_000L, nowMillis = now))
        dao.upsertBin(BinCacheEntity.fromBinInfo(expiredInfo, ttlMillis = 100L, nowMillis = now - 500L))

        val loadedValid = dao.getValidBin("453225", now)
        assertEquals("CHASE BANK", loadedValid?.bankName)
        assertEquals(null, dao.getValidBin("541275", now))
        assertEquals(1, dao.pruneExpired(now))
        assertEquals(1, dao.countValidBins(now))
    }

    @Test
    fun `history repository supports SQLite pagination search filtering and trimming`() = runTest {
        val entries = (1..5).map { idx ->
            val pan = "453225005536000$idx"
            val record = ValidationRecord(
                id = "r_$idx",
                rawLine = "$pan|08|2028|113",
                normalizedLine = "$pan|08|2028|113",
                pan = pan,
                month = "08",
                year = "2028",
                cvv = "113",
                scheme = if (idx % 2 == 0) CardScheme.MASTERCARD else CardScheme.VISA,
                bucket = if (idx % 2 == 0) ResultBucket.DIE else ResultBucket.LIVE,
                message = "Test $idx",
                latencyMs = 5L
            )
            repository.toEntity("batch-pg", "test.txt", record, "Luhn")
        }
        repository.recordResultBatch(entries)

        val page1 = repository.getHistoryPage(HistoryFilter.ALL, query = "", limit = 2, offset = 0)
        val page2 = repository.getHistoryPage(HistoryFilter.ALL, query = "", limit = 2, offset = 2)
        assertEquals(2, page1.size)
        assertEquals(2, page2.size)
        assertTrue(page1.map { it.id }.intersect(page2.map { it.id }.toSet()).isEmpty())

        val pagedVisa = repository.observePagedHistory(HistoryFilter.SUCCESS, query = "VISA", limit = 10).first()
        assertEquals(3, pagedVisa.size)
        assertEquals(3, repository.observeFilteredCount(HistoryFilter.SUCCESS, query = "VISA").first())
    }

    @Test
    fun `preferences repository persists high-traffic network optimizer settings`() = runTest {
        val prefsRepo = ValidatorPreferencesRepository(context)
        prefsRepo.updateSettings(
            validationMode = ValidationMode.REMOTE_WEBHOOK,
            webhookUrl = "https://api.example.com/check",
            autoResolveWildcards = true,
            batchDelayMs = 100L,
            concurrency = 4,
            patternTemplate = "453225005536xxx3|08|2028|113",
            generateQuantity = 10,
            themeMode = AppThemeMode.DARK,
            useDynamicColor = true,
            deduplicateBatchInputs = false,
            gzipCustomRequests = true,
            wifiOnlyBatch = true,
            batterySaverMode = true
        )

        val stored = prefsRepo.settingsFlow.first()
        assertEquals(false, stored.deduplicateBatchInputs)
        assertEquals(true, stored.gzipCustomRequests)
        assertEquals(true, stored.wifiOnlyBatch)
        assertEquals(true, stored.batterySaverMode)
        assertEquals(true, stored.toCustomHttpConfig().compressBodyGzip)
    }

    @Test
    fun `networkOptimizationSectionCard renders telemetry badges and optimizer switches`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    NetworkOptimizationSectionCard(
                        networkStats = NetworkTelemetrySnapshot(
                            httpRequestsSent = 18,
                            cacheHits = 6,
                            deduplicatedSaved = 4,
                            retriesPerformed = 2,
                            circuitBreakerTrips = 1
                        ),
                        deviceNetworkStatus = DeviceNetworkStatus(
                            isConnected = true,
                            isMetered = true,
                            isPowerSaveMode = true
                        ),
                        deduplicateBatchInputs = true,
                        wifiOnlyBatch = true,
                        batterySaverMode = true,
                        onToggleDedupCache = {},
                        onToggleWifiOnly = {},
                        onToggleBatterySaver = {},
                        onClearCaches = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("network_telemetry_strip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dedup_cache_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_only_batch_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("battery_saver_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_network_cache_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("HTTP: 18").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saved: 10").assertIsDisplayed()
    }

    @Test
    fun `historyLogBottomSheet displays pagination status and Load More button when hasMore is true`() {
        val sampleEntries = (1..3).map { idx ->
            val pan = "453225005536000$idx"
            repository.toEntity(
                batchId = "b1",
                sourceName = "console",
                record = ValidationRecord(
                    id = "id_$idx",
                    rawLine = "$pan|08|2028|113",
                    normalizedLine = "$pan|08|2028|113",
                    pan = pan,
                    month = "08",
                    year = "2028",
                    cvv = "113",
                    scheme = CardScheme.VISA,
                    bucket = ResultBucket.LIVE,
                    message = "Valid",
                    latencyMs = 2L
                ),
                endpointUsed = "LUHN"
            ).copy(id = idx.toLong())
        }

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                HistoryLogBottomSheet(
                    entries = sampleEntries,
                    summary = HistoryUiSummary(totalCount = 12, successCount = 12, failureCount = 0),
                    activeFilter = HistoryFilter.ALL,
                    searchQuery = "",
                    filteredTotalCount = 12,
                    hasMore = true,
                    onLoadMore = {},
                    onSelectFilter = {},
                    onSearchQueryChanged = {},
                    onCopyEntry = {},
                    onDeleteEntry = {},
                    onClearAllHistory = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_lazy_list")
            .performScrollToNode(hasTestTag("history_load_more_button"))
        composeTestRule.onNodeWithTag("history_pagination_status").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_load_more_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Showing 3 of 12").assertIsDisplayed()
    }

    @Test
    fun `settingsBottomSheet switches cleanly across Engine Gateway and System tabs`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                com.example.ui.SettingsBottomSheet(
                    settings = com.example.data.ValidatorSettings(),
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
                    onClearAllBuckets = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_tab_engine").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_tab_gateway").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_tab_system").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_all_buckets_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pattern_template_input").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_tab_gateway").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("webhook_url_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("telegram_webhook_switch").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_tab_system").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("network_telemetry_strip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_mode_dark").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_developer_channel_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `listToolkitEngine cleans messy dumps deduplicates prunes expired and bad Luhn`() {
        val validVisa = CardValidatorEngine.resolveWildcardPan("45322500553600x3")
        val validVisaDashed = validVisa.chunked(4).joinToString("-")
        val validMc = CardValidatorEngine.resolveWildcardPan("51546200211116x2")
        val badLuhnPan = if (validVisa.endsWith("0")) validVisa.dropLast(1) + "1" else validVisa.dropLast(1) + "0"

        val messyInput = """
            # Header noise line
            <div><b style='color:#008000'>Live</b> | $validVisa | 10/2031 | 113</div>
            $validVisaDashed / 10/31 / 113
            $validMc:05:2024:564
            $badLuhnPan|08|2029|321
            $validMc|09|2029|999
        """.trimIndent()

        val result = ListToolkitEngine.transform(
            rawInput = messyInput,
            options = ListToolkitOptions(
                outputFormat = OutputFormat.PIPE_YYYY
            ),
            referenceYear = 2026,
            referenceMonth = 11
        )

        assertEquals(6, result.totalInputLines)
        assertEquals(2, result.outputCount)
        assertEquals(1, result.removedUnparseable)
        assertEquals(1, result.removedDuplicates)
        assertEquals(1, result.removedExpired)
        assertEquals(1, result.removedInvalidLuhn)
        assertEquals(
            "$validVisa|10|2031|113\n$validMc|09|2029|999",
            result.outputText
        )
    }

    @Test
    fun `listToolkitEngine filters by scheme BIN prefix minExpiryYear and extracts BIN6`() {
        val visa = CardValidatorEngine.resolveWildcardPan("45322500553600x3")
        val amex = CardValidatorEngine.resolveWildcardPan("3793630374713x7")
        val mc = CardValidatorEngine.resolveWildcardPan("51546200211116x2")

        val input = """
            $visa|08|2027|113
            $amex|07|2034|3204
            $mc|12|2030|564
        """.trimIndent()

        val filtered = ListToolkitEngine.transform(
            rawInput = input,
            options = ListToolkitOptions(
                selectedSchemes = setOf(CardScheme.AMEX, CardScheme.MASTERCARD),
                excludeBinPrefixes = "5154",
                minExpiryYear = 2030,
                outputFormat = OutputFormat.PIPE_YY
            ),
            referenceYear = 2026,
            referenceMonth = 11
        )
        assertEquals("$amex|07|34|3204", filtered.outputText)

        val binOnly = ListToolkitEngine.transform(
            rawInput = input,
            options = ListToolkitOptions(
                removeExpired = false,
                removeInvalidLuhn = false,
                dropIncomplete = false,
                outputFormat = OutputFormat.BIN_ONLY,
                sortOrder = ListSortOrder.BY_EXPIRY_DESC
            ),
            referenceYear = 2026,
            referenceMonth = 11
        )
        assertEquals("379363\n515462\n453225", binOnly.outputText)
    }

    @Test
    fun `listToolkitBottomSheet switches tabs formats output and applies to console`() {
        val validVisa = CardValidatorEngine.resolveWildcardPan("45322500553600x3")
        var appliedOutput = ""
        var appliedResult: ToolkitTransformResult? = null

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                ListToolkitBottomSheet(
                    initialInputText = "$validVisa|08|2029|113\n$validVisa|08|2029|113\nnoise_line",
                    liveResultsText = "",
                    allResultsText = "",
                    liveResultsCount = 0,
                    allResultsCount = 0,
                    onPasteFromClipboard = { null },
                    onCopyOutput = {},
                    onApplyToConsole = { out, res ->
                        appliedOutput = out
                        appliedResult = res
                    },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolkit_stat_io").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 in → 1 out").assertIsDisplayed()

        composeTestRule.onNodeWithTag("toolkit_tab_format").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolkit_format_colon").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("toolkit_tab_filter").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolkit_scheme_visa").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("toolkit_apply_console_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("$validVisa:08:2029:113", appliedOutput)
        assertEquals(1, appliedResult?.outputCount)
        assertEquals(1, appliedResult?.removedDuplicates)
        assertEquals(1, appliedResult?.removedUnparseable)
    }

    @Test
    fun `historyRepository exposes combined summary and incremental batch delta`() = runTest {
        val validRecord = CardValidatorEngine.validateLine(
            rawLine = "453225005536xxx3|08|2029|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = true
        )
        val invalidRecord = CardValidatorEngine.validateLine(
            rawLine = "123456|08|2029|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = false
        )

        repository.recordResult("batch-delta", "sample.txt", validRecord, "Local")
        repository.recordResult("batch-delta", "sample.txt", invalidRecord, "Local")
        repository.recordResult("batch-other", "other.txt", invalidRecord, "Local")

        val summary = repository.historySummary.first()
        assertEquals(3, summary.totalCount)
        assertEquals(1, summary.successCount)
        assertEquals(2, summary.failureCount)

        val initialDelta = repository.getBatchResultsAfterId("batch-delta", 0L)
        assertEquals(2, initialDelta.size)
        assertTrue(initialDelta[0].id < initialDelta[1].id)

        repository.recordResult("batch-delta", "sample.txt", validRecord, "Local")
        val incrementalDelta = repository.getBatchResultsAfterId("batch-delta", initialDelta.last().id)
        assertEquals(1, incrementalDelta.size)
        assertEquals(
            incrementalDelta.last().id,
            repository.observeLatestBatchRowId("batch-delta").first()
        )
    }

    @Test
    fun `listToolkitEngine extracts email pass combos from messy logs json and urls`() {
        val messyDump = """
            https://portal.example.com/login:alice@gmail.com:AlicePass#1
            {"email":"bob@outlook.com","password":"BobSecret99"}
            <div>Email: alice@gmail.com | Password: AlicePass#1</div>
            user=charlie@yahoo.com pass=Charlie!23
            just_some_random_noise_line
        """.trimIndent()

        val result = ListToolkitEngine.transform(
            rawInput = messyDump,
            options = ListToolkitOptions.fromPreset(com.example.domain.ToolkitPreset.EXTRACT_EMAIL_PASS)
                .copy(excludeBinPrefixes = "yahoo.com")
        )

        assertEquals(5, result.totalInputLines)
        assertEquals(2, result.outputCount)
        assertEquals(1, result.removedDuplicates)
        assertEquals(1, result.removedUnparseable)
        assertEquals(1, result.removedByFilter)
        assertEquals(
            "alice@gmail.com:AlicePass#1\nbob@outlook.com:BobSecret99",
            result.outputText
        )
    }

    @Test
    fun `listToolkitBottomSheet exposes file select export save and email pass recipe`() {
        var appliedOutput = ""
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                ListToolkitBottomSheet(
                    initialInputText = "https://site.org:dev@company.io:Sup3rSecr3t\ndev@company.io|Sup3rSecr3t",
                    liveResultsText = "",
                    allResultsText = "",
                    liveResultsCount = 0,
                    allResultsCount = 0,
                    onPasteFromClipboard = { null },
                    onCopyOutput = {},
                    onApplyToConsole = { out, _ -> appliedOutput = out },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toolkit_select_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolkit_export_save_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolkit_preset_extract_email_pass").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("2 in → 1 out").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toolkit_apply_console_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("dev@company.io:Sup3rSecr3t", appliedOutput)
    }

    @Test
    fun `settingsBottomSheet exposes sandbox suite loaders and http sandbox presets`() {
        var loadedProvider: com.example.domain.SandboxProvider? = null
        var savedMode: ValidationMode? = null
        var savedHttpConfig: CustomHttpConfig? = null

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                SettingsBottomSheet(
                    settings = ValidatorSettings(validationMode = ValidationMode.LOCAL_DETERMINISTIC),
                    networkStats = com.example.domain.NetworkTelemetrySnapshot(),
                    deviceNetworkStatus = com.example.domain.DeviceNetworkStatus(),
                    isTestingEndpoint = false,
                    httpProbeResult = null,
                    isTestingTelegram = false,
                    telegramProbeResult = null,
                    onDismiss = {},
                    onTestEndpoint = {},
                    onDetectTelegramChat = {},
                    onTestTelegramWebhook = {},
                    onClearCaches = {},
                    onSaveSettings = { mode, httpCfg, _, _, _, _, _, _, _, _, _, _, _, _ ->
                        savedMode = mode
                        savedHttpConfig = httpCfg
                    },
                    onGenerateBatchToConsole = { _, _ -> },
                    onLoadSandboxSuite = { provider -> loadedProvider = provider },
                    onClearAllBuckets = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sandbox_suite_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("sandbox_env_status_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("sandbox_load_stripe").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.domain.SandboxProvider.STRIPE, loadedProvider)

        composeTestRule.onNodeWithTag("mode_sandbox_option").performScrollTo().assertIsDisplayed()

        // Switch to Gateway tab and verify HTTP Sandbox Presets
        composeTestRule.onNodeWithTag("settings_tab_gateway").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("http_sandbox_preset_httpbin_echo").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_settings_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(ValidationMode.SANDBOX_SUITE, savedMode)
        assertEquals("https://httpbin.org/post", savedHttpConfig?.url)
        assertEquals(CustomHttpConfig.ZERO_DOLLAR_AUTH_JSON_PAYLOAD, savedHttpConfig?.bodyTemplate)
    }
}

