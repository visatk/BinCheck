package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.data.AppThemeMode
import com.example.data.ValidatorPreferencesRepository
import com.example.data.local.AppDatabase
import com.example.data.local.HistoryFilter
import com.example.data.local.ValidationHistoryRepository
import com.example.domain.CardValidatorEngine
import com.example.domain.ResultBucket
import com.example.domain.ValidationMode
import com.example.ui.ConsoleInputCard
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
            validationMode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
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
}
