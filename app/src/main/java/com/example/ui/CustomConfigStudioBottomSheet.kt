package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.analytics.FirebaseMetrics
import com.example.domain.ConfigBlock
import com.example.domain.CustomCheckpointRule
import com.example.domain.CustomConfigProfile
import com.example.domain.CustomConfigStudioEngine
import com.example.domain.CustomHttpConfig
import com.example.domain.CustomParseRule
import com.example.domain.HttpProbeResult
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BUNDLE_STRING_CHARS = 24_000
private const val BUNDLE_OVERSIZE_SENTINEL = "__BINCHECK_BUNDLE_OVERSIZE__"

private fun boundedStringSaver(fallback: () -> String): Saver<String, String> = Saver(
    save = { value ->
        if (value.length <= MAX_BUNDLE_STRING_CHARS) value else BUNDLE_OVERSIZE_SENTINEL
    },
    restore = { saved ->
        if (saved == BUNDLE_OVERSIZE_SENTINEL) fallback() else saved
    }
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun CustomConfigStudioBottomSheet(
    initialConfig: CustomHttpConfig,
    savedProfiles: List<CustomConfigProfile>,
    isTestingEndpoint: Boolean,
    probeResult: HttpProbeResult?,
    onTestConfig: (CustomHttpConfig, String) -> Unit,
    onClearProbeResult: () -> Unit,
    onSaveAndUseConfig: (CustomHttpConfig) -> Unit,
    onSaveProfile: (CustomConfigProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val ioScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    fun copyPlainText(label: String, text: String) {
        val mgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        mgr?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun readPlainTextClipboard(): String {
        val mgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = mgr?.primaryClip
        val item = if (clip != null && clip.itemCount > 0) clip.getItemAt(0) else null
        return item?.coerceToText(context)?.toString().orEmpty()
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var statusBanner by rememberSaveable { mutableStateOf<String?>(null) }
    var showCurlImportDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        val tabName = ConfigStudioTab.entries.getOrNull(selectedTab)?.title ?: "Request"
        FirebaseMetrics.logScreenView("ConfigStudio_$tabName", "MainActivity")
    }

    // Editable state mirrors initialConfig (rotation-safe with rememberSaveable)
    var profileName by rememberSaveable { mutableStateOf(initialConfig.profileName.ifBlank { "Custom Config" }) }
    var url by rememberSaveable { mutableStateOf(initialConfig.url) }
    var method by rememberSaveable { mutableStateOf(initialConfig.method) }
    var headersText by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.headersText }) {
        mutableStateOf(initialConfig.headersText)
    }
    var bodyTemplate by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.bodyTemplate }) {
        mutableStateOf(initialConfig.bodyTemplate)
    }
    var userAgent by rememberSaveable { mutableStateOf(initialConfig.userAgent) }
    var followRedirects by rememberSaveable { mutableStateOf(initialConfig.followRedirects) }
    var compressBodyGzip by rememberSaveable { mutableStateOf(initialConfig.compressBodyGzip) }
    var skipLocalLuhnPrecheck by rememberSaveable { mutableStateOf(initialConfig.skipLocalLuhnPrecheck) }
    var timeoutSeconds by rememberSaveable { mutableIntStateOf(initialConfig.timeoutSeconds) }
    var maxRetries by rememberSaveable { mutableIntStateOf(initialConfig.maxRetries) }
    var liveKeywords by rememberSaveable { mutableStateOf(initialConfig.liveKeywords) }
    var dieKeywords by rememberSaveable { mutableStateOf(initialConfig.dieKeywords) }
    var proxyEnabled by rememberSaveable { mutableStateOf(initialConfig.proxyEnabled) }
    var proxyProtocol by rememberSaveable { mutableStateOf(initialConfig.proxyProtocol) }
    var proxyRotation by rememberSaveable { mutableStateOf(initialConfig.proxyRotation) }
    var proxyListText by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.proxyListText }) {
        mutableStateOf(initialConfig.proxyListText)
    }

    // Step 1 Pre-Request (Handshake) state
    var preRequestEnabled by rememberSaveable { mutableStateOf(initialConfig.preRequestEnabled) }
    var preRequestUrl by rememberSaveable { mutableStateOf(initialConfig.preRequestUrl) }
    var preRequestMethod by rememberSaveable { mutableStateOf(initialConfig.preRequestMethod) }
    var preRequestHeaders by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.preRequestHeaders }) {
        mutableStateOf(initialConfig.preRequestHeaders)
    }
    var preRequestBody by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.preRequestBody }) {
        mutableStateOf(initialConfig.preRequestBody)
    }

    var sampleTestLine by rememberSaveable { mutableStateOf("453225005536xxx3|08|2028|113") }
    var dryRunStatusCode by rememberSaveable { mutableIntStateOf(200) }
    var dryRunBodyText by rememberSaveable(
        stateSaver = boundedStringSaver {
            """{"status":"succeeded","code":"approved","id":"tok_123","error":{"code":"none"}}"""
        }
    ) {
        mutableStateOf("""{"status":"succeeded","code":"approved","id":"tok_123","error":{"code":"none"}}""")
    }

    LaunchedEffect(probeResult) {
        if (probeResult != null && probeResult.rawResponseBody.isNotBlank()) {
            dryRunBodyText = probeResult.rawResponseBody
            probeResult.statusCode?.let { dryRunStatusCode = it }
        }
    }

    // Persist dynamic rule/checkpoint/block specs across configuration changes (rotation)
    var savedPreRulesSpec by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.preRequestParseRulesSpec }) {
        mutableStateOf(initialConfig.preRequestParseRulesSpec)
    }
    var savedParseRulesSpec by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.parseRulesSpec }) {
        mutableStateOf(initialConfig.parseRulesSpec)
    }
    var savedCheckpointRulesSpec by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.checkpointRulesSpec }) {
        mutableStateOf(initialConfig.checkpointRulesSpec)
    }
    var savedBlocksSpec by rememberSaveable(stateSaver = boundedStringSaver { initialConfig.blocksSpec }) {
        mutableStateOf(initialConfig.blocksSpec)
    }

    val preRequestParseRules = remember {
        mutableStateListOf<CustomParseRule>().apply {
            addAll(CustomConfigStudioEngine.parseParseRules(savedPreRulesSpec))
        }
    }
    val parseRules = remember {
        mutableStateListOf<CustomParseRule>().apply {
            addAll(CustomConfigStudioEngine.parseParseRules(savedParseRulesSpec))
        }
    }
    val checkpointRules = remember {
        mutableStateListOf<CustomCheckpointRule>().apply {
            addAll(CustomConfigStudioEngine.parseCheckpointRules(savedCheckpointRulesSpec))
        }
    }
    val configBlocks = remember {
        mutableStateListOf<ConfigBlock>().apply {
            addAll(CustomConfigStudioEngine.parseBlocks(savedBlocksSpec))
        }
    }

    fun syncPreRulesSpec() {
        savedPreRulesSpec = CustomConfigStudioEngine.serializeParseRules(preRequestParseRules.toList())
    }

    fun syncParseRulesSpec() {
        savedParseRulesSpec = CustomConfigStudioEngine.serializeParseRules(parseRules.toList())
    }

    fun syncCheckpointRulesSpec() {
        savedCheckpointRulesSpec = CustomConfigStudioEngine.serializeCheckpointRules(checkpointRules.toList())
    }

    fun syncBlocksSpec() {
        savedBlocksSpec = CustomConfigStudioEngine.serializeBlocks(configBlocks.toList())
    }

    fun buildCurrentConfig(): CustomHttpConfig = CustomHttpConfig(
        profileName = profileName.trim().ifEmpty { "Custom Config" },
        url = url.trim(),
        method = method,
        headersText = headersText.trim(),
        bodyTemplate = bodyTemplate.trim(),
        timeoutSeconds = timeoutSeconds.coerceIn(5, 60),
        maxRetries = maxRetries.coerceIn(0, 3),
        liveKeywords = liveKeywords.trim().ifEmpty { CustomHttpConfig.DEFAULT_LIVE_KEYWORDS },
        dieKeywords = dieKeywords.trim().ifEmpty { CustomHttpConfig.DEFAULT_DIE_KEYWORDS },
        compressBodyGzip = compressBodyGzip,
        userAgent = userAgent.trim(),
        followRedirects = followRedirects,
        parseRulesSpec = CustomConfigStudioEngine.serializeParseRules(parseRules.toList()),
        checkpointRulesSpec = CustomConfigStudioEngine.serializeCheckpointRules(checkpointRules.toList()),
        proxyEnabled = proxyEnabled,
        proxyProtocol = proxyProtocol,
        proxyRotation = proxyRotation,
        proxyListText = proxyListText.trim(),
        preRequestEnabled = preRequestEnabled,
        preRequestUrl = preRequestUrl.trim(),
        preRequestMethod = preRequestMethod,
        preRequestHeaders = preRequestHeaders.trim(),
        preRequestBody = preRequestBody.trim(),
        preRequestParseRulesSpec = CustomConfigStudioEngine.serializeParseRules(preRequestParseRules.toList()),
        skipLocalLuhnPrecheck = skipLocalLuhnPrecheck,
        blocksSpec = CustomConfigStudioEngine.serializeBlocks(configBlocks.toList())
    )

    fun loadConfigIntoEditor(cfg: CustomHttpConfig, message: String, preserveRulesIfEmpty: Boolean = false) {
        profileName = cfg.profileName.ifBlank { "Custom Config" }
        url = cfg.url
        method = cfg.method
        headersText = cfg.headersText
        bodyTemplate = cfg.bodyTemplate
        userAgent = cfg.userAgent
        followRedirects = cfg.followRedirects
        compressBodyGzip = cfg.compressBodyGzip
        skipLocalLuhnPrecheck = cfg.skipLocalLuhnPrecheck
        timeoutSeconds = cfg.timeoutSeconds
        maxRetries = cfg.maxRetries
        if (!preserveRulesIfEmpty || cfg.liveKeywords != CustomHttpConfig.DEFAULT_LIVE_KEYWORDS) {
            liveKeywords = cfg.liveKeywords
        }
        if (!preserveRulesIfEmpty || cfg.dieKeywords != CustomHttpConfig.DEFAULT_DIE_KEYWORDS) {
            dieKeywords = cfg.dieKeywords
        }
        val importedParse = cfg.parsedParseRules()
        val importedCheckpoints = cfg.parsedCheckpointRules()
        val importedBlocks = cfg.parsedBlocks()
        if (!preserveRulesIfEmpty || importedParse.isNotEmpty() || importedCheckpoints.isNotEmpty() || importedBlocks.isNotEmpty()) {
            parseRules.clear()
            parseRules.addAll(importedParse)
            syncParseRulesSpec()
            checkpointRules.clear()
            checkpointRules.addAll(importedCheckpoints)
            syncCheckpointRulesSpec()
            configBlocks.clear()
            configBlocks.addAll(importedBlocks)
            syncBlocksSpec()
        }
        if (!preserveRulesIfEmpty) {
            proxyEnabled = cfg.proxyEnabled
            proxyProtocol = cfg.proxyProtocol
            proxyRotation = cfg.proxyRotation
            proxyListText = cfg.proxyListText
            preRequestEnabled = cfg.preRequestEnabled
            preRequestUrl = cfg.preRequestUrl
            preRequestMethod = cfg.preRequestMethod
            preRequestHeaders = cfg.preRequestHeaders
            preRequestBody = cfg.preRequestBody
            preRequestParseRules.clear()
            preRequestParseRules.addAll(cfg.parsedPreRequestParseRules())
            syncPreRulesSpec()
        }
        onClearProbeResult()
        statusBanner = message
    }

    // SAF File Import (.json config)
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ioScope.launch {
            val rawText = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }.getOrNull()
            }
            if (rawText.isNullOrBlank()) {
                statusBanner = "Could not read selected config file."
                return@launch
            }
            CustomConfigStudioEngine.importConfigFromJson(rawText)
                .onSuccess { imported ->
                    FirebaseMetrics.logCustomConfigImported(format = "json_file", success = true)
                    loadConfigIntoEditor(imported, "Imported '${imported.profileName}' from .json file.")
                }
                .onFailure { err ->
                    FirebaseMetrics.logCustomConfigImported(format = "json_file", success = false)
                    statusBanner = "Import failed: ${err.localizedMessage ?: "Invalid JSON"}"
                }
        }
    }

    // SAF File Export (.json config)
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val jsonBytes = CustomConfigStudioEngine.exportConfigToJson(buildCurrentConfig()).toByteArray(Charsets.UTF_8)
        ioScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonBytes)
                        out.flush()
                    } != null
                }.getOrDefault(false)
            }
            statusBanner = if (ok) {
                FirebaseMetrics.logCustomConfigExported(format = "json_file")
                "Exported '${profileName.ifBlank { "config" }}.json' successfully."
            } else {
                "Failed to write config JSON file."
            }
        }
    }

    // SAF Proxy List Import (.txt)
    val importProxiesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ioScope.launch {
            val rawText = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }.getOrNull()
            }
            if (rawText.isNullOrBlank()) {
                statusBanner = "Selected proxy file is empty or unreadable."
                return@launch
            }
            val parsed = CustomConfigStudioEngine.parseProxyPool(rawText, proxyProtocol)
            if (parsed.isEmpty()) {
                statusBanner = "No valid host:port proxies found in selected file."
            } else {
                val merged = listOf(proxyListText.trim(), rawText.trim())
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                proxyListText = merged
                proxyEnabled = true
                statusBanner = "Loaded ${parsed.size} proxy endpoint(s) into pool."
            }
        }
    }

    if (showCurlImportDialog) {
        CurlOrFetchImportDialog(
            onPasteClipboard = { readPlainTextClipboard() },
            onDismiss = { showCurlImportDialog = false },
            onImported = { importedConfig ->
                showCurlImportDialog = false
                loadConfigIntoEditor(
                    cfg = importedConfig,
                    message = "Imported request into '${importedConfig.profileName}'.",
                    preserveRulesIfEmpty = true
                )
                selectedTab = ConfigStudioTab.REQUEST.ordinal
            }
        )
    }

    val validProxyCount = remember(proxyListText, proxyProtocol) {
        CustomConfigStudioEngine.parseProxyPool(proxyListText, proxyProtocol).size
    }

    val dryRunCaptures = remember(dryRunBodyText, parseRules.toList(), probeResult?.responseHeaders) {
        CustomConfigStudioEngine.extractCaptures(
            bodyStr = dryRunBodyText,
            responseHeaders = probeResult?.responseHeaders ?: emptyMap(),
            rules = parseRules.toList()
        )
    }

    val dryRunEvaluation = remember(
        dryRunStatusCode,
        dryRunBodyText,
        dryRunCaptures,
        checkpointRules.toList(),
        probeResult?.responseHeaders,
        probeResult?.latencyMs
    ) {
        CustomConfigStudioEngine.evaluateCheckpointsDetailed(
            statusCode = dryRunStatusCode,
            bodyStr = dryRunBodyText,
            captures = dryRunCaptures,
            rules = checkpointRules.toList(),
            responseHeaders = probeResult?.responseHeaders ?: emptyMap(),
            latencyMs = probeResult?.latencyMs ?: 0L
        )
    }

    val liveRequestPreview = remember(
        url,
        method,
        headersText,
        bodyTemplate,
        userAgent,
        compressBodyGzip,
        followRedirects,
        skipLocalLuhnPrecheck,
        preRequestEnabled,
        preRequestMethod,
        preRequestParseRules.toList(),
        sampleTestLine
    ) {
        CustomConfigStudioEngine.renderRequestPreview(
            config = buildCurrentConfig(),
            sampleLine = sampleTestLine
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("config_studio_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = colorScheme.primary.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.32f))
                    ) {
                        Icon(
                            imageVector = AppIcons.Tune,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier
                                .padding(9.dp)
                                .size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Custom Config Studio",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            fontFamily = SpaceGroteskFamily,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "2-Step HTTP • 9-Category Blocks • 6-Mode Parser • Keycheck",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_config_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Custom Config Studio"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Actions & File I/O Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCurlImportDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                        .testTag("config_studio_open_curl_import_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Api,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import cURL / fetch / Loli", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { importJsonLauncher.launch("*/*") },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                        .testTag("config_studio_import_json_button")
                ) {
                    Icon(
                        imageVector = AppIcons.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import .json", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        val safeName = profileName.trim().lowercase()
                            .replace(Regex("[^a-z0-9_-]+"), "_")
                            .trim('_')
                            .ifEmpty { "bincheck_config" }
                        exportJsonLauncher.launch("$safeName.json")
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                        .testTag("config_studio_export_json_button")
                ) {
                    Icon(
                        imageVector = AppIcons.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export .json", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        val curlCmd = CustomConfigStudioEngine.exportToCurlCommand(
                            config = buildCurrentConfig(),
                            sampleLine = sampleTestLine
                        )
                        copyPlainText("BinCheck cURL", curlCmd)
                        FirebaseMetrics.logCustomConfigExported(format = "curl")
                        statusBanner = "Copied ready-to-run cURL command to clipboard."
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                        .testTag("config_studio_copy_curl_button")
                ) {
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy cURL", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        val loli = CustomConfigStudioEngine.exportToLoliScript(buildCurrentConfig())
                        copyPlainText("BinCheck LoliScript", loli)
                        FirebaseMetrics.logCustomConfigExported(format = "loliscript")
                        statusBanner = "Copied OpenBullet LoliScript block to clipboard."
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                        .testTag("config_studio_copy_loli_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy LoliScript", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Pipeline Indicator Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preRequestEnabled) {
                    PipelineStagePill(
                        step = "S1",
                        label = "Handshake (${preRequestParseRules.size})",
                        active = selectedTab == ConfigStudioTab.REQUEST.ordinal,
                        accent = consoleColors.unknownAccent,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = ConfigStudioTab.REQUEST.ordinal
                        }
                    )
                    Text("→", style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
                }
                PipelineStagePill(
                    step = "1",
                    label = method.label,
                    active = selectedTab == ConfigStudioTab.REQUEST.ordinal,
                    accent = colorScheme.primary,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTab = ConfigStudioTab.REQUEST.ordinal
                    }
                )
                Text("→", style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
                PipelineStagePill(
                    step = "2",
                    label = "Blocks (${configBlocks.size})",
                    active = selectedTab == ConfigStudioTab.BLOCKS.ordinal,
                    accent = colorScheme.tertiary,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTab = ConfigStudioTab.BLOCKS.ordinal
                    }
                )
                Text("→", style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
                PipelineStagePill(
                    step = "3",
                    label = "Parse (${parseRules.size})",
                    active = selectedTab == ConfigStudioTab.PARSE.ordinal,
                    accent = consoleColors.telegramAccent,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTab = ConfigStudioTab.PARSE.ordinal
                    }
                )
                Text("→", style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
                PipelineStagePill(
                    step = "4",
                    label = "Keycheck (${checkpointRules.size})",
                    active = selectedTab == ConfigStudioTab.CHECKPOINTS.ordinal,
                    accent = consoleColors.liveAccent,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTab = ConfigStudioTab.CHECKPOINTS.ordinal
                    }
                )
                Text("→", style = MaterialTheme.typography.labelSmall, color = colorScheme.outline)
                PipelineStagePill(
                    step = "5",
                    label = if (proxyEnabled) "Proxy ($validProxyCount)" else "Direct",
                    active = selectedTab == ConfigStudioTab.PROXY.ordinal,
                    accent = if (proxyEnabled) consoleColors.unknownAccent else colorScheme.onSurfaceVariant,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTab = ConfigStudioTab.PROXY.ordinal
                    }
                )
            }

            AnimatedVisibility(
                visible = !statusBanner.isNullOrBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                statusBanner?.let { msg ->
                    Surface(
                        color = colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { statusBanner = null },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 6-Tab Navigation Row
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = { HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f)) }
            ) {
                ConfigStudioTab.entries.forEachIndexed { idx, tab ->
                    val badgeCount = when (tab) {
                        ConfigStudioTab.REQUEST -> if (preRequestEnabled) 2 else 0
                        ConfigStudioTab.BLOCKS -> configBlocks.size
                        ConfigStudioTab.PARSE -> parseRules.size
                        ConfigStudioTab.CHECKPOINTS -> checkpointRules.size
                        ConfigStudioTab.PROXY -> if (proxyEnabled) validProxyCount.coerceAtLeast(1) else 0
                        ConfigStudioTab.PROFILES -> savedProfiles.size + CustomConfigStudioEngine.BUILT_IN_PROFILES.size
                    }
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        modifier = Modifier.testTag("config_studio_tab_${tab.name.lowercase()}"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium
                                )
                                if (badgeCount > 0) {
                                    Surface(
                                        color = if (selectedTab == idx) {
                                            colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            colorScheme.surfaceContainerHighest
                                        },
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = badgeCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = if (selectedTab == idx) {
                                                colorScheme.primary
                                            } else {
                                                colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Tab Body Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 260.dp, max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedContent(
                    targetState = ConfigStudioTab.entries[selectedTab],
                    transitionSpec = {
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                    },
                    label = "config_studio_tab_body"
                ) { tab ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (tab) {
                            ConfigStudioTab.REQUEST -> RequestStudioTabContent(
                                profileName = profileName,
                                onProfileNameChange = { profileName = it },
                                url = url,
                                onUrlChange = { url = it; onClearProbeResult() },
                                method = method,
                                onMethodChange = { method = it; onClearProbeResult() },
                                headersText = headersText,
                                onHeadersChange = { headersText = it },
                                bodyTemplate = bodyTemplate,
                                onBodyTemplateChange = { bodyTemplate = it },
                                userAgent = userAgent,
                                onUserAgentChange = { userAgent = it },
                                followRedirects = followRedirects,
                                onFollowRedirectsChange = { followRedirects = it },
                                compressBodyGzip = compressBodyGzip,
                                onCompressBodyGzipChange = { compressBodyGzip = it },
                                skipLocalLuhnPrecheck = skipLocalLuhnPrecheck,
                                onSkipLocalLuhnPrecheckChange = { skipLocalLuhnPrecheck = it },
                                timeoutSeconds = timeoutSeconds,
                                onTimeoutChange = { timeoutSeconds = it },
                                maxRetries = maxRetries,
                                onMaxRetriesChange = { maxRetries = it },
                                preRequestEnabled = preRequestEnabled,
                                onPreRequestEnabledChange = { preRequestEnabled = it },
                                preRequestUrl = preRequestUrl,
                                onPreRequestUrlChange = { preRequestUrl = it },
                                preRequestMethod = preRequestMethod,
                                onPreRequestMethodChange = { preRequestMethod = it },
                                preRequestHeaders = preRequestHeaders,
                                onPreRequestHeadersChange = { preRequestHeaders = it },
                                preRequestBody = preRequestBody,
                                onPreRequestBodyChange = { preRequestBody = it },
                                preRequestParseRules = preRequestParseRules,
                                onAddPreRule = {
                                    preRequestParseRules.add(it)
                                    syncPreRulesSpec()
                                },
                                onRemovePreRule = { idx ->
                                    if (idx in preRequestParseRules.indices) {
                                        preRequestParseRules.removeAt(idx)
                                        syncPreRulesSpec()
                                    }
                                },
                                liveRequestPreview = liveRequestPreview
                            )

                            ConfigStudioTab.BLOCKS -> BlocksStudioTabContent(
                                blocks = configBlocks,
                                sampleTestLine = sampleTestLine,
                                dryRunBodyText = dryRunBodyText,
                                dryRunStatusCode = dryRunStatusCode,
                                currentConfig = buildCurrentConfig(),
                                onAddBlock = {
                                    configBlocks.add(it)
                                    syncBlocksSpec()
                                },
                                onUpdateBlock = { idx, updated ->
                                    if (idx in configBlocks.indices) {
                                        configBlocks[idx] = updated
                                        syncBlocksSpec()
                                    }
                                },
                                onMoveBlock = { from, to ->
                                    if (from in configBlocks.indices && to in configBlocks.indices) {
                                        val item = configBlocks.removeAt(from)
                                        configBlocks.add(to, item)
                                        syncBlocksSpec()
                                    }
                                },
                                onRemoveBlock = { idx ->
                                    if (idx in configBlocks.indices) {
                                        configBlocks.removeAt(idx)
                                        syncBlocksSpec()
                                    }
                                },
                                onReplaceAllBlocks = { replacement ->
                                    configBlocks.clear()
                                    configBlocks.addAll(replacement)
                                    syncBlocksSpec()
                                }
                            )

                            ConfigStudioTab.PARSE -> ParseStudioTabContent(
                                parseRules = parseRules,
                                dryRunBodyText = dryRunBodyText,
                                onDryRunBodyChange = { dryRunBodyText = it },
                                dryRunCaptures = dryRunCaptures,
                                onAddRule = {
                                    parseRules.add(it)
                                    syncParseRulesSpec()
                                },
                                onUpdateRule = { idx, updated ->
                                    if (idx in parseRules.indices) {
                                        parseRules[idx] = updated
                                        syncParseRulesSpec()
                                    }
                                },
                                onMoveRule = { from, to ->
                                    if (from in parseRules.indices && to in parseRules.indices) {
                                        val item = parseRules.removeAt(from)
                                        parseRules.add(to, item)
                                        syncParseRulesSpec()
                                    }
                                },
                                onRemoveRule = { idx ->
                                    if (idx in parseRules.indices) {
                                        parseRules.removeAt(idx)
                                        syncParseRulesSpec()
                                    }
                                },
                                onToggleShow = { idx ->
                                    if (idx in parseRules.indices) {
                                        val old = parseRules[idx]
                                        parseRules[idx] = old.copy(showInOutput = !old.showInOutput)
                                        syncParseRulesSpec()
                                    }
                                }
                            )

                            ConfigStudioTab.CHECKPOINTS -> CheckpointsStudioTabContent(
                                checkpointRules = checkpointRules,
                                dryRunStatusCode = dryRunStatusCode,
                                onDryRunStatusCodeChange = { dryRunStatusCode = it },
                                dryRunBodyText = dryRunBodyText,
                                onDryRunBodyChange = { dryRunBodyText = it },
                                dryRunCaptures = dryRunCaptures,
                                dryRunEvaluation = dryRunEvaluation,
                                onAddCheckpoint = {
                                    checkpointRules.add(it)
                                    syncCheckpointRulesSpec()
                                },
                                onUpdateCheckpoint = { idx, updated ->
                                    if (idx in checkpointRules.indices) {
                                        checkpointRules[idx] = updated
                                        syncCheckpointRulesSpec()
                                    }
                                },
                                onMoveCheckpoint = { from, to ->
                                    if (from in checkpointRules.indices && to in checkpointRules.indices) {
                                        val item = checkpointRules.removeAt(from)
                                        checkpointRules.add(to, item)
                                        syncCheckpointRulesSpec()
                                    }
                                },
                                onRemoveCheckpoint = { idx ->
                                    if (idx in checkpointRules.indices) {
                                        checkpointRules.removeAt(idx)
                                        syncCheckpointRulesSpec()
                                    }
                                },
                                liveKeywords = liveKeywords,
                                onLiveKeywordsChange = { liveKeywords = it },
                                dieKeywords = dieKeywords,
                                onDieKeywordsChange = { dieKeywords = it }
                            )

                            ConfigStudioTab.PROXY -> ProxyStudioTabContent(
                                proxyEnabled = proxyEnabled,
                                onProxyEnabledChange = { proxyEnabled = it },
                                proxyProtocol = proxyProtocol,
                                onProxyProtocolChange = { proxyProtocol = it },
                                proxyRotation = proxyRotation,
                                onProxyRotationChange = { proxyRotation = it },
                                proxyListText = proxyListText,
                                onProxyListTextChange = { proxyListText = it },
                                onImportProxyFile = { importProxiesLauncher.launch("*/*") }
                            )

                            ConfigStudioTab.PROFILES -> ProfilesStudioTabContent(
                                currentProfileName = profileName,
                                onProfileNameChange = { profileName = it },
                                savedProfiles = savedProfiles,
                                onSaveCurrentProfile = { desc ->
                                    val cfg = buildCurrentConfig()
                                    val profile = CustomConfigProfile(
                                        id = "user_${cfg.profileName.lowercase().hashCode()}_${System.currentTimeMillis() % 10000}",
                                        name = cfg.profileName,
                                        description = desc.ifBlank {
                                            val stepTag = if (cfg.preRequestEnabled) "2-Step ${cfg.method.label}" else cfg.method.label
                                            val blkTag = if (configBlocks.isNotEmpty()) " • ${configBlocks.size} blocks" else ""
                                            "$stepTag$blkTag • ${parseRules.size} captures • ${checkpointRules.size} checkpoints"
                                        },
                                        config = cfg,
                                        isBuiltIn = false
                                    )
                                    onSaveProfile(profile)
                                    statusBanner = "Saved '${cfg.profileName}' to your library."
                                },
                                onDuplicateProfile = { profile ->
                                    val copyName = "${profile.name} (Copy)"
                                    val dupCfg = profile.config.copy(profileName = copyName)
                                    val dupProfile = CustomConfigProfile(
                                        id = "user_${copyName.lowercase().hashCode()}_${System.currentTimeMillis() % 10000}",
                                        name = copyName,
                                        description = profile.description,
                                        config = dupCfg,
                                        isBuiltIn = false
                                    )
                                    onSaveProfile(dupProfile)
                                    loadConfigIntoEditor(dupCfg, "Duplicated and loaded '$copyName'.")
                                },
                                onLoadProfile = { profile ->
                                    loadConfigIntoEditor(profile.config, "Loaded profile '${profile.name}'.")
                                    selectedTab = ConfigStudioTab.REQUEST.ordinal
                                },
                                onDeleteProfile = { id ->
                                    onDeleteProfile(id)
                                    statusBanner = "Deleted saved profile."
                                },
                                onCopyJson = {
                                    val json = CustomConfigStudioEngine.exportConfigToJson(buildCurrentConfig())
                                    copyPlainText("BinCheck Config JSON", json)
                                    FirebaseMetrics.logCustomConfigExported(format = "json_clipboard")
                                    statusBanner = "Copied Config JSON to clipboard."
                                },
                                onPasteJson = {
                                    val clip = readPlainTextClipboard()
                                    if (clip.isBlank()) {
                                        statusBanner = "Clipboard is empty."
                                    } else {
                                        CustomConfigStudioEngine.importConfigFromJson(clip)
                                            .onSuccess { imported ->
                                                FirebaseMetrics.logCustomConfigImported(format = "json_clipboard", success = true)
                                                loadConfigIntoEditor(
                                                    imported,
                                                    "Imported '${imported.profileName}' from clipboard JSON."
                                                )
                                            }
                                            .onFailure { err ->
                                                FirebaseMetrics.logCustomConfigImported(format = "json_clipboard", success = false)
                                                statusBanner = "Clipboard JSON invalid: ${err.localizedMessage ?: "Parse error"}"
                                            }
                                    }
                                },
                                onCopyCurl = {
                                    val curlCmd = CustomConfigStudioEngine.exportToCurlCommand(
                                        config = buildCurrentConfig(),
                                        sampleLine = sampleTestLine
                                    )
                                    copyPlainText("BinCheck cURL", curlCmd)
                                    FirebaseMetrics.logCustomConfigExported(format = "curl")
                                    statusBanner = "Copied cURL command to clipboard."
                                },
                                onCopyLoliScript = {
                                    val loli = CustomConfigStudioEngine.exportToLoliScript(buildCurrentConfig())
                                    copyPlainText("BinCheck LoliScript", loli)
                                    FirebaseMetrics.logCustomConfigExported(format = "loliscript")
                                    statusBanner = "Copied LoliScript block to clipboard."
                                },
                                onOpenCurlImporter = { showCurlImportDialog = true }
                            )
                        }
                    }
                }

                // Live Probe / Debug Trace Section
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sampleTestLine,
                        onValueChange = { sampleTestLine = it },
                        label = { Text("Probe Input Line (Card or email:pass)") },
                        placeholder = { Text("453225005536xxx3|08|2028|113") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("config_studio_sample_line_input")
                    )
                    PresetMiniPill(
                        label = if (sampleTestLine.contains('@')) "Card Input" else "Combo Input"
                    ) {
                        sampleTestLine = if (sampleTestLine.contains('@')) {
                            "453225005536xxx3|08|2028|113"
                        } else {
                            "admin@drkingbd.org:Secr3tPass99"
                        }
                    }
                }

                AnimatedVisibility(visible = probeResult != null) {
                    probeResult?.let { probe ->
                        ConfigProbeTraceCard(
                            probe = probe,
                            onCopyRawBody = { raw ->
                                copyPlainText("Probe Response Body", raw)
                                statusBanner = "Copied probe response body."
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Sticky Bottom Action Bar (48.dp touch target compliant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTestConfig(buildCurrentConfig(), sampleTestLine)
                    },
                    enabled = (url.isNotBlank() || configBlocks.any { it.enabled }) && !isTestingEndpoint,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("config_studio_test_button")
                ) {
                    if (isTestingEndpoint) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Probing...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test / Probe")
                    }
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSaveAndUseConfig(buildCurrentConfig())
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp)
                        .testTag("config_studio_apply_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Use Config", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
