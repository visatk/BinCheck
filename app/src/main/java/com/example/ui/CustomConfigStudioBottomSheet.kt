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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.analytics.FirebaseMetrics
import com.example.domain.CheckpointCondition
import com.example.domain.CheckpointEvaluationResult
import com.example.domain.ConfigBlock
import com.example.domain.CustomCheckpointRule
import com.example.domain.CustomConfigProfile
import com.example.domain.CustomConfigStudioEngine
import com.example.domain.CustomHttpConfig
import com.example.domain.CustomParseRule
import com.example.domain.HttpProbeResult
import com.example.domain.HttpRequestMethod
import com.example.domain.ParseRuleMode
import com.example.domain.ProxyProtocol
import com.example.domain.ProxyRotationMode
import com.example.domain.RenderedRequestPreview
import com.example.domain.ResultBucket
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ConfigStudioTab(val title: String) {
    REQUEST("Request"),
    BLOCKS("Blocks"),
    PARSE("Parse"),
    CHECKPOINTS("Checkpoints"),
    PROXY("Proxy"),
    PROFILES("Profiles")
}

private enum class TokenCategory(val label: String) {
    ALL("All"),
    CARD("Card & BIN"),
    COMBO("Combo"),
    DYNAMIC("Dynamic & Hash"),
    STEP1("Step 1 Captures")
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
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

    // Editable state mirrors initialConfig
    var profileName by rememberSaveable { mutableStateOf(initialConfig.profileName.ifBlank { "Custom Config" }) }
    var url by rememberSaveable { mutableStateOf(initialConfig.url) }
    var method by rememberSaveable { mutableStateOf(initialConfig.method) }
    var headersText by rememberSaveable { mutableStateOf(initialConfig.headersText) }
    var bodyTemplate by rememberSaveable { mutableStateOf(initialConfig.bodyTemplate) }
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
    var proxyListText by rememberSaveable { mutableStateOf(initialConfig.proxyListText) }

    // Step 1 Pre-Request (Handshake) state
    var preRequestEnabled by rememberSaveable { mutableStateOf(initialConfig.preRequestEnabled) }
    var preRequestUrl by rememberSaveable { mutableStateOf(initialConfig.preRequestUrl) }
    var preRequestMethod by rememberSaveable { mutableStateOf(initialConfig.preRequestMethod) }
    var preRequestHeaders by rememberSaveable { mutableStateOf(initialConfig.preRequestHeaders) }
    var preRequestBody by rememberSaveable { mutableStateOf(initialConfig.preRequestBody) }

    var sampleTestLine by rememberSaveable { mutableStateOf("453225005536xxx3|08|2028|113") }
    var dryRunStatusCode by rememberSaveable { mutableIntStateOf(200) }
    var dryRunBodyText by rememberSaveable {
        mutableStateOf("""{"status":"succeeded","code":"approved","id":"tok_123","error":{"code":"none"}}""")
    }

    LaunchedEffect(probeResult) {
        if (probeResult != null && probeResult.rawResponseBody.isNotBlank()) {
            dryRunBodyText = probeResult.rawResponseBody
            probeResult.statusCode?.let { dryRunStatusCode = it }
        }
    }

    val preRequestParseRules = remember {
        mutableStateListOf<CustomParseRule>().apply {
            addAll(initialConfig.parsedPreRequestParseRules())
        }
    }
    val parseRules = remember {
        mutableStateListOf<CustomParseRule>().apply {
            addAll(initialConfig.parsedParseRules())
        }
    }
    val checkpointRules = remember {
        mutableStateListOf<CustomCheckpointRule>().apply {
            addAll(initialConfig.parsedCheckpointRules())
        }
    }
    val configBlocks = remember {
        mutableStateListOf<ConfigBlock>().apply {
            addAll(initialConfig.parsedBlocks())
        }
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
            checkpointRules.clear()
            checkpointRules.addAll(importedCheckpoints)
            configBlocks.clear()
            configBlocks.addAll(importedBlocks)
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
        preRequestEnabled,
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

            // Quick Actions & File I/O Bar (cURL/fetch/Raw HTTP/LoliScript + Import/Export JSON + Copy cURL/Loli)
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
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
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 5-Tab Navigation Row
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
                                onAddPreRule = { preRequestParseRules.add(it) },
                                onRemovePreRule = { idx ->
                                    if (idx in preRequestParseRules.indices) preRequestParseRules.removeAt(idx)
                                },
                                liveRequestPreview = liveRequestPreview
                            )

                            ConfigStudioTab.BLOCKS -> BlocksStudioTabContent(
                                blocks = configBlocks,
                                sampleTestLine = sampleTestLine,
                                dryRunBodyText = dryRunBodyText,
                                dryRunStatusCode = dryRunStatusCode,
                                currentConfig = buildCurrentConfig(),
                                onAddBlock = { configBlocks.add(it) },
                                onUpdateBlock = { idx, updated ->
                                    if (idx in configBlocks.indices) configBlocks[idx] = updated
                                },
                                onMoveBlock = { from, to ->
                                    if (from in configBlocks.indices && to in configBlocks.indices) {
                                        val item = configBlocks.removeAt(from)
                                        configBlocks.add(to, item)
                                    }
                                },
                                onRemoveBlock = { idx ->
                                    if (idx in configBlocks.indices) configBlocks.removeAt(idx)
                                },
                                onReplaceAllBlocks = { replacement ->
                                    configBlocks.clear()
                                    configBlocks.addAll(replacement)
                                }
                            )

                            ConfigStudioTab.PARSE -> ParseStudioTabContent(
                                parseRules = parseRules,
                                dryRunBodyText = dryRunBodyText,
                                onDryRunBodyChange = { dryRunBodyText = it },
                                dryRunCaptures = dryRunCaptures,
                                onAddRule = { parseRules.add(it) },
                                onUpdateRule = { idx, updated ->
                                    if (idx in parseRules.indices) parseRules[idx] = updated
                                },
                                onMoveRule = { from, to ->
                                    if (from in parseRules.indices && to in parseRules.indices) {
                                        val item = parseRules.removeAt(from)
                                        parseRules.add(to, item)
                                    }
                                },
                                onRemoveRule = { idx -> if (idx in parseRules.indices) parseRules.removeAt(idx) },
                                onToggleShow = { idx ->
                                    if (idx in parseRules.indices) {
                                        val old = parseRules[idx]
                                        parseRules[idx] = old.copy(showInOutput = !old.showInOutput)
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
                                onAddCheckpoint = { checkpointRules.add(it) },
                                onUpdateCheckpoint = { idx, updated ->
                                    if (idx in checkpointRules.indices) checkpointRules[idx] = updated
                                },
                                onMoveCheckpoint = { from, to ->
                                    if (from in checkpointRules.indices && to in checkpointRules.indices) {
                                        val item = checkpointRules.removeAt(from)
                                        checkpointRules.add(to, item)
                                    }
                                },
                                onRemoveCheckpoint = { idx ->
                                    if (idx in checkpointRules.indices) checkpointRules.removeAt(idx)
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

@Composable
private fun PipelineStagePill(
    step: String,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        border = BorderStroke(
            width = if (active) 1.dp else 0.75.dp,
            color = if (active) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (active) accent else accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudioSummaryPill(
    label: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CurlOrFetchImportDialog(
    onPasteClipboard: () -> String,
    onDismiss: () -> Unit,
    onImported: (CustomHttpConfig) -> Unit
) {
    var snippet by rememberSaveable { mutableStateOf("") }
    var autoTokenize by rememberSaveable { mutableStateOf(true) }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Universal Request Importer",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste Chrome DevTools cURL / fetch(), Burp / HTTP Toolkit Raw HTTP, or an OpenBullet LoliScript block. URL, headers, payload, and rules are auto-extracted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick gateway request templates for 1-tap loading
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetMiniPill(label = "Stripe cURL") {
                        val pubKey = CustomConfigStudioEngine.secretOrPlaceholder(
                            com.example.BuildConfig.STRIPE_SANDBOX_PUBLISHABLE_KEY,
                            "<STRIPE_PUBLISHABLE_KEY>"
                        )
                        snippet = """
                            curl -X POST 'https://api.stripe.com/v1/tokens' \
                              -H 'Authorization: Bearer $pubKey' \
                              -H 'Content-Type: application/x-www-form-urlencoded' \
                              --data-raw 'card[number]=4532250055360003&card[exp_month]=08&card[exp_year]=2028&card[cvc]=113'
                        """.trimIndent()
                        errorMsg = null
                    }
                    PresetMiniPill(label = "Checkout Raw HTTP") {
                        val ckoKey = CustomConfigStudioEngine.secretOrPlaceholder(
                            com.example.BuildConfig.CHECKOUT_SANDBOX_SECRET_KEY,
                            "<CHECKOUT_SECRET_KEY>"
                        )
                        snippet = """
                            POST /payments HTTP/1.1
                            Host: api.sandbox.checkout.com
                            Authorization: Bearer $ckoKey
                            Content-Type: application/json
                            X-Idempotency-Key: {uuid}

                            {"amount":0,"currency":"USD","source":{"type":"card","number":"4532250055360003","expiry_month":8,"expiry_year":2028,"cvv":"113"}}
                        """.trimIndent()
                        errorMsg = null
                    }
                    PresetMiniPill(label = "Payate LoliScript") {
                        snippet = """
                            REQUEST POST "https://mock.payate.com/api.php"
                              CONTENT "data=<CC>|<MM>|<YYYY>|<CVV>"
                              CONTENTTYPE "application/x-www-form-urlencoded"
                              HEADER "Accept: application/json"
                            PARSE "<SOURCE>" JSON "msg" -> CAP "msg"
                            KEYCHECK
                              KEYCHAIN Success OR
                                KEY "Live"
                              KEYCHAIN Failure OR
                                KEY "Die"
                        """.trimIndent()
                        errorMsg = null
                    }
                }

                OutlinedTextField(
                    value = snippet,
                    onValueChange = { snippet = it; errorMsg = null },
                    label = { Text("cURL / fetch() / Raw HTTP / LoliScript") },
                    placeholder = { Text("curl ... OR fetch(\"https://...\", {...}) OR POST /v1/... HTTP/1.1") },
                    minLines = 4,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("curl_import_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-tokenize card/CVV/exp/email",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoTokenize,
                        onCheckedChange = { autoTokenize = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = onPasteClipboard()
                            if (clip.isNotBlank()) {
                                snippet = clip
                                errorMsg = null
                            } else {
                                errorMsg = "Clipboard is empty."
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(AppIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Clipboard", style = MaterialTheme.typography.labelSmall)
                    }
                }
                errorMsg?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    CustomConfigStudioEngine.importFromCurlOrFetch(snippet, autoTokenize)
                        .onSuccess {
                            FirebaseMetrics.logCustomConfigImported(format = "curl_fetch_raw_loli", success = true)
                            onImported(it)
                        }
                        .onFailure { err ->
                            FirebaseMetrics.logCustomConfigImported(format = "curl_fetch_raw_loli", success = false)
                            errorMsg = err.localizedMessage ?: "Failed to parse snippet."
                        }
                },
                enabled = snippet.isNotBlank(),
                modifier = Modifier.testTag("curl_import_confirm_button")
            ) {
                Text("Parse & Load")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// =============================================================================
// TAB 1: HTTP REQUEST PIPELINE (+ 2-STEP HANDSHAKE & LIVE REQUEST PREVIEW)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RequestStudioTabContent(
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    method: HttpRequestMethod,
    onMethodChange: (HttpRequestMethod) -> Unit,
    headersText: String,
    onHeadersChange: (String) -> Unit,
    bodyTemplate: String,
    onBodyTemplateChange: (String) -> Unit,
    userAgent: String,
    onUserAgentChange: (String) -> Unit,
    followRedirects: Boolean,
    onFollowRedirectsChange: (Boolean) -> Unit,
    compressBodyGzip: Boolean,
    onCompressBodyGzipChange: (Boolean) -> Unit,
    skipLocalLuhnPrecheck: Boolean,
    onSkipLocalLuhnPrecheckChange: (Boolean) -> Unit,
    timeoutSeconds: Int,
    onTimeoutChange: (Int) -> Unit,
    maxRetries: Int,
    onMaxRetriesChange: (Int) -> Unit,
    preRequestEnabled: Boolean,
    onPreRequestEnabledChange: (Boolean) -> Unit,
    preRequestUrl: String,
    onPreRequestUrlChange: (String) -> Unit,
    preRequestMethod: HttpRequestMethod,
    onPreRequestMethodChange: (HttpRequestMethod) -> Unit,
    preRequestHeaders: String,
    onPreRequestHeadersChange: (String) -> Unit,
    preRequestBody: String,
    onPreRequestBodyChange: (String) -> Unit,
    preRequestParseRules: List<CustomParseRule>,
    onAddPreRule: (CustomParseRule) -> Unit,
    onRemovePreRule: (Int) -> Unit,
    liveRequestPreview: RenderedRequestPreview
) {
    val consoleColors = LocalConsoleColors.current
    var selectedTokenCategory by rememberSaveable { mutableStateOf(TokenCategory.ALL) }
    var showLivePreview by rememberSaveable { mutableStateOf(false) }
    var step1VarName by rememberSaveable { mutableStateOf("") }
    var step1VarExpr by rememberSaveable { mutableStateOf("") }
    var step1VarMode by rememberSaveable { mutableStateOf(ParseRuleMode.JSON) }

    OutlinedTextField(
        value = profileName,
        onValueChange = onProfileNameChange,
        label = { Text("Config Profile Name") },
        placeholder = { Text("e.g. Custom Gateway Verification") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("config_studio_profile_name_input")
    )

    // 2-Step Pre-Request (Token / Session Handshake) Builder Card
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = if (preRequestEnabled) 1.dp else 0.75.dp,
            color = if (preRequestEnabled) {
                consoleColors.unknownAccent.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Step 1: Token / Session Handshake (2-Step Pipeline)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (preRequestEnabled) consoleColors.unknownAccent else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Run a pre-request to capture payment tokens, nonces, or cookies -> inject as {cap:name} in Step 2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preRequestEnabled,
                    onCheckedChange = onPreRequestEnabledChange,
                    modifier = Modifier.testTag("config_studio_prerequest_switch")
                )
            }

            AnimatedVisibility(
                visible = preRequestEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetMiniPill(label = "Preset: Tokenize Card Form") {
                            val pubKey = CustomConfigStudioEngine.secretOrPlaceholder(
                                com.example.BuildConfig.STRIPE_SANDBOX_PUBLISHABLE_KEY,
                                "<STRIPE_PUBLISHABLE_KEY>"
                            )
                            onPreRequestUrlChange("https://api.stripe.com/v1/tokens")
                            onPreRequestMethodChange(HttpRequestMethod.POST_FORM)
                            onPreRequestHeadersChange("Authorization: Bearer $pubKey\nAccept: application/json")
                            onPreRequestBodyChange("card[number]={card}&card[exp_month]={mm}&card[exp_year]={yyyy}&card[cvc]={cvv}")
                            if (preRequestParseRules.none { it.name == "payment_token" }) {
                                onAddPreRule(
                                    CustomParseRule(
                                        name = "payment_token",
                                        mode = ParseRuleMode.JSON,
                                        expression = "id",
                                        defaultValue = "tok_visa"
                                    )
                                )
                            }
                        }
                        PresetMiniPill(label = "Preset: Fetch CSRF / Cookie") {
                            onPreRequestUrlChange("https://mock.payate.com/api.php")
                            onPreRequestMethodChange(HttpRequestMethod.GET)
                            onPreRequestBodyChange("")
                            if (preRequestParseRules.none { it.name == "session_cookie" }) {
                                onAddPreRule(
                                    CustomParseRule(
                                        name = "session_cookie",
                                        mode = ParseRuleMode.COOKIE,
                                        expression = "*"
                                    )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = preRequestUrl,
                        onValueChange = onPreRequestUrlChange,
                        label = { Text("Step 1 Handshake URL") },
                        placeholder = { Text("https://api.stripe.com/v1/tokens") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_studio_prerequest_url_input")
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        HttpRequestMethod.entries.forEachIndexed { idx, m ->
                            SegmentedButton(
                                selected = preRequestMethod == m,
                                onClick = { onPreRequestMethodChange(m) },
                                shape = SegmentedButtonDefaults.itemShape(idx, HttpRequestMethod.entries.size)
                            ) {
                                Text("S1 ${m.label}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (preRequestMethod != HttpRequestMethod.GET) {
                        OutlinedTextField(
                            value = preRequestBody,
                            onValueChange = onPreRequestBodyChange,
                            label = { Text("Step 1 Body Template") },
                            placeholder = { Text("card[number]={card}&card[cvc]={cvv}") },
                            minLines = 2,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = preRequestHeaders,
                        onValueChange = onPreRequestHeadersChange,
                        label = { Text("Step 1 Custom Headers (Optional)") },
                        placeholder = { Text("Authorization: Bearer pk_live_...") },
                        minLines = 1,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Step 1 Captures (Use as {cap:name} in Step 2)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = step1VarName,
                            onValueChange = { step1VarName = it },
                            label = { Text("Var Name") },
                            placeholder = { Text("token") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.weight(0.8f)
                        )
                        OutlinedTextField(
                            value = step1VarExpr,
                            onValueChange = { step1VarExpr = it },
                            label = { Text(if (step1VarMode == ParseRuleMode.COOKIE) "Cookie Name" else "JSON Path / Regex") },
                            placeholder = { Text("id") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.weight(1.2f)
                        )
                        FilledTonalButton(
                            onClick = {
                                if (step1VarName.isNotBlank() && step1VarExpr.isNotBlank()) {
                                    onAddPreRule(
                                        CustomParseRule(
                                            name = step1VarName.trim(),
                                            mode = step1VarMode,
                                            expression = step1VarExpr.trim(),
                                            showInOutput = true
                                        )
                                    )
                                    step1VarName = ""
                                    step1VarExpr = ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (preRequestParseRules.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            preRequestParseRules.forEachIndexed { idx, r ->
                                Surface(
                                    onClick = { onRemovePreRule(idx) },
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(1.dp, consoleColors.unknownAccent.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "{cap:${r.name}} ← ${r.expression}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = consoleColors.unknownAccent
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Step 1 capture",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(if (preRequestEnabled) "Step 2 Main Endpoint URL" else "Endpoint URL (http:// or https://)") },
        placeholder = { Text("https://api.stripe.com/v1/setup_intents") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("config_studio_url_input")
    )

    // HTTP Method Selector
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "HTTP Request Method",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            HttpRequestMethod.entries.forEachIndexed { idx, item ->
                SegmentedButton(
                    selected = method == item,
                    onClick = { onMethodChange(item) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = HttpRequestMethod.entries.size)
                ) {
                    Text(item.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // Categorized Dynamic Token Palette (28+ tokens)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dynamic Tokens (Tap to insert into Body or GET URL)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TokenCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedTokenCategory == cat,
                    onClick = { selectedTokenCategory = cat },
                    label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        val step1Tokens = preRequestParseRules.map { "{cap:${it.name}}" } + listOf("{cookie}", "{cap:payment_token}")
        val displayedTokens = when (selectedTokenCategory) {
            TokenCategory.ALL -> listOf(
                "{lista}", "{card}", "{card_spaced}", "{card_dashed}", "{pan4}",
                "{mm}", "{mm_no_pad}", "{yy}", "{yyyy}", "{cvv}",
                "{bin}", "{bin8}", "{scheme}", "{scheme_lower}",
                "{email}", "{user}", "{pass}",
                "{uuid}", "{timestamp}", "{timestamp_ms}", "{iso_date}",
                "{random6}", "{random_hex16}", "{random_ua}",
                "{base64_lista}", "{base64_combo}", "{sha256_card}", "{md5_lista}"
            ) + step1Tokens.distinct()
            TokenCategory.CARD -> listOf(
                "{lista}", "{card}", "{card_spaced}", "{card_dashed}", "{pan4}",
                "{mm}", "{mm_no_pad}", "{yy}", "{yyyy}", "{cvv}",
                "{bin}", "{bin8}", "{scheme}", "{scheme_lower}"
            )
            TokenCategory.COMBO -> listOf(
                "{email}", "{user}", "{pass}", "{base64_combo}", "{base64_lista}"
            )
            TokenCategory.DYNAMIC -> listOf(
                "{uuid}", "{guid}", "{timestamp}", "{timestamp_ms}", "{iso_date}",
                "{random6}", "{random_hex16}", "{random_ua}",
                "{sha256_card}", "{md5_lista}"
            )
            TokenCategory.STEP1 -> step1Tokens.distinct()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            displayedTokens.forEach { token ->
                Surface(
                    onClick = {
                        if (method == HttpRequestMethod.GET) {
                            onUrlChange(url + token)
                        } else {
                            onBodyTemplateChange(bodyTemplate + token)
                        }
                    },
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = token,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }

    // Body Template + Presets
    if (method != HttpRequestMethod.GET) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Request Body Template & Presets",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                liveRequestPreview.isJsonValid?.let { isValid ->
                    StudioSummaryPill(
                        label = if (isValid) "Valid JSON" else liveRequestPreview.jsonDiagnostic,
                        color = if (isValid) consoleColors.liveAccent else consoleColors.dieAccent
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetMiniPill(label = "$0 Auth JSON") {
                    onMethodChange(HttpRequestMethod.POST_JSON)
                    onBodyTemplateChange(CustomHttpConfig.ZERO_DOLLAR_AUTH_JSON_PAYLOAD)
                }
                PresetMiniPill(label = "JSON Card") {
                    onMethodChange(HttpRequestMethod.POST_JSON)
                    onBodyTemplateChange(CustomHttpConfig.DEFAULT_JSON_PAYLOAD)
                }
                PresetMiniPill(label = "Form Body") {
                    onMethodChange(HttpRequestMethod.POST_FORM)
                    onBodyTemplateChange(CustomHttpConfig.DEFAULT_FORM_PAYLOAD)
                }
                PresetMiniPill(label = "Email:Pass JSON") {
                    onMethodChange(HttpRequestMethod.POST_JSON)
                    onBodyTemplateChange(CustomHttpConfig.EMAIL_PASS_JSON_PAYLOAD)
                    onSkipLocalLuhnPrecheckChange(true)
                }
                PresetMiniPill(label = "GraphQL Card") {
                    onMethodChange(HttpRequestMethod.POST_JSON)
                    onBodyTemplateChange(
                        """{"operationName":"VerifyCard","variables":{"input":{"number":"{card}","month":"{mm}","year":"{yyyy}","cvv":"{cvv}"}},"query":"mutation VerifyCard(${'$'}input: CardInput!) { verifyCard(input: ${'$'}input) { status code } }"}"""
                    )
                }
                PresetMiniPill(label = "Clear") {
                    onBodyTemplateChange("")
                }
            }

            OutlinedTextField(
                value = bodyTemplate,
                onValueChange = onBodyTemplateChange,
                label = { Text("Payload Body (JSON or Form URL-Encoded)") },
                placeholder = { Text(CustomHttpConfig.DEFAULT_JSON_PAYLOAD) },
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_studio_body_input")
            )
        }
    }

    // Custom Headers + 1-Tap Header Snippets
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetMiniPill(label = "+ Bearer Auth") {
                val line = "Authorization: Bearer {cap:token}"
                onHeadersChange(if (headersText.isBlank()) line else "$headersText\n$line")
            }
            PresetMiniPill(label = "+ Basic Combo") {
                val line = "Authorization: Basic {base64_combo}"
                onHeadersChange(if (headersText.isBlank()) line else "$headersText\n$line")
            }
            PresetMiniPill(label = "+ Idempotency-Key") {
                val line = "X-Idempotency-Key: {uuid}"
                onHeadersChange(if (headersText.isBlank()) line else "$headersText\n$line")
            }
            PresetMiniPill(label = "+ X-Requested-With") {
                val line = "X-Requested-With: XMLHttpRequest"
                onHeadersChange(if (headersText.isBlank()) line else "$headersText\n$line")
            }
        }

        OutlinedTextField(
            value = headersText,
            onValueChange = onHeadersChange,
            label = { Text("Custom HTTP Headers (Key: Value per line)") },
            placeholder = { Text("Authorization: Bearer ...\nX-Idempotency-Key: {uuid}") },
            minLines = 2,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("config_studio_headers_input")
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetMiniPill(label = "UA: BinCheck") {
                onUserAgentChange(CustomHttpConfig.DEFAULT_USER_AGENT)
            }
            PresetMiniPill(label = "UA: Chrome Android") {
                onUserAgentChange("Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
            }
            PresetMiniPill(label = "UA: Safari iOS") {
                onUserAgentChange("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1")
            }
            PresetMiniPill(label = "UA: Rotate Random") {
                onUserAgentChange("{random_ua}")
            }
        }
        OutlinedTextField(
            value = userAgent,
            onValueChange = onUserAgentChange,
            label = { Text("User-Agent Header (supports {random_ua})") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("config_studio_user_agent_input")
        )
    }

    // Redirects, GZIP & Bypass Luhn Pre-check switches
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Follow Redirects", style = MaterialTheme.typography.labelSmall)
                Switch(
                    checked = followRedirects,
                    onCheckedChange = onFollowRedirectsChange
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GZIP Body", style = MaterialTheme.typography.labelSmall)
                Switch(
                    checked = compressBodyGzip,
                    onCheckedChange = onCompressBodyGzipChange
                )
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bypass Local Card Luhn Pre-Check",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Send raw combos (email:pass) or negative test cards straight to the remote gateway",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = skipLocalLuhnPrecheck,
                onCheckedChange = onSkipLocalLuhnPrecheckChange,
                modifier = Modifier.testTag("config_studio_skip_luhn_switch")
            )
        }
    }

    // Timeout & Retries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Timeout: ${timeoutSeconds}s", style = MaterialTheme.typography.labelSmall)
            val timeouts = listOf(8, 12, 20, 30)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                timeouts.forEachIndexed { i, sec ->
                    SegmentedButton(
                        selected = timeoutSeconds == sec,
                        onClick = { onTimeoutChange(sec) },
                        shape = SegmentedButtonDefaults.itemShape(i, timeouts.size)
                    ) {
                        Text("${sec}s", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Retries: ${maxRetries}x", style = MaterialTheme.typography.labelSmall)
            val retries = listOf(0, 1, 2, 3)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                retries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = maxRetries == r,
                        onClick = { onMaxRetriesChange(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, retries.size)
                    ) {
                        Text("${r}x", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    // Live Request Preview Inspector Card
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLivePreview = !showLivePreview },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Live Resolved Request Preview",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StudioSummaryPill(
                        label = liveRequestPreview.detectedInputType,
                        color = consoleColors.telegramAccent
                    )
                }
                Text(
                    text = if (showLivePreview) "Hide" else "Show",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (liveRequestPreview.unresolvedTokens.isNotEmpty()) {
                Text(
                    text = "Note: Unresolved custom token(s): ${liveRequestPreview.unresolvedTokens.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = consoleColors.unknownAccent
                )
            }

            AnimatedVisibility(visible = showLivePreview) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${liveRequestPreview.resolvedMethod} ${liveRequestPreview.resolvedUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (liveRequestPreview.resolvedHeaders.isNotEmpty()) {
                        Text(
                            text = liveRequestPreview.resolvedHeaders.joinToString("\n") { "${it.first}: ${it.second}" },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (liveRequestPreview.resolvedBody.isNotBlank()) {
                        Text(
                            text = "BODY: ${liveRequestPreview.resolvedBody}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = consoleColors.liveAccent
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 2: RESPONSE PARSER, SMART DISCOVERY & LIVE DRY-RUN CAPTURE TESTER
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ParseStudioTabContent(
    parseRules: List<CustomParseRule>,
    dryRunBodyText: String,
    onDryRunBodyChange: (String) -> Unit,
    dryRunCaptures: Map<String, String>,
    onAddRule: (CustomParseRule) -> Unit,
    onUpdateRule: (Int, CustomParseRule) -> Unit,
    onMoveRule: (Int, Int) -> Unit,
    onRemoveRule: (Int) -> Unit,
    onToggleShow: (Int) -> Unit
) {
    val consoleColors = LocalConsoleColors.current
    var editingIndex by rememberSaveable { mutableIntStateOf(-1) }
    var varName by rememberSaveable { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableStateOf(ParseRuleMode.JSON) }
    var expression by rememberSaveable { mutableStateOf("") }
    var leftDelim by rememberSaveable { mutableStateOf("") }
    var rightDelim by rememberSaveable { mutableStateOf("") }
    var showInOutput by rememberSaveable { mutableStateOf(true) }
    var recursive by rememberSaveable { mutableStateOf(false) }
    var defaultValue by rememberSaveable { mutableStateOf("") }

    val discoveredKeys = remember(dryRunBodyText) {
        CustomConfigStudioEngine.discoverJsonKeySuggestions(dryRunBodyText)
    }

    Text(
        text = "Extract values from JSON dot-paths (including arrays like errors[0].code), Left/Right delimiters, Regex groups, HTTP Headers, Set-Cookie, or HTML attributes into variables.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 1-Tap Quick Capture Presets
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PresetMiniPill(label = "+ JSON status") {
            onAddRule(CustomParseRule(name = "status", mode = ParseRuleMode.JSON, expression = "status", showInOutput = true))
        }
        PresetMiniPill(label = "+ JSON error.code") {
            onAddRule(CustomParseRule(name = "err", mode = ParseRuleMode.JSON, expression = "error.code", showInOutput = true))
        }
        PresetMiniPill(label = "+ LR Token") {
            onAddRule(CustomParseRule(name = "tok", mode = ParseRuleMode.LR, leftDelimiter = "\"id\":\"", rightDelimiter = "\"", showInOutput = true))
        }
        PresetMiniPill(label = "+ Regex BIN6") {
            onAddRule(CustomParseRule(name = "bin", mode = ParseRuleMode.REGEX, expression = "\"bin\"\\s*:\\s*\"(\\d{6})\"", showInOutput = true))
        }
        PresetMiniPill(label = "+ Cookie session") {
            onAddRule(CustomParseRule(name = "sid", mode = ParseRuleMode.COOKIE, expression = "session_id", showInOutput = false))
        }
        PresetMiniPill(label = "+ HTML CSRF") {
            onAddRule(CustomParseRule(name = "csrf", mode = ParseRuleMode.CSS, expression = "input[name=csrf_token]@value", showInOutput = false))
        }
    }

    // Smart Auto-Discovered JSON Keys from Sample/Probe Body
    if (discoveredKeys.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Smart Auto-Discovered JSON Keys (Tap to capture):",
                style = MaterialTheme.typography.labelSmall,
                color = consoleColors.telegramAccent,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                discoveredKeys.forEach { (path, sampleVal) ->
                    val shortName = path.substringAfterLast('.').replace("[0]", "")
                    PresetMiniPill(label = "+ $path (${sampleVal.take(14)})") {
                        onAddRule(
                            CustomParseRule(
                                name = shortName.ifEmpty { "val" },
                                mode = ParseRuleMode.JSON,
                                expression = path,
                                showInOutput = true
                            )
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Capture Rule Builder Card
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = if (editingIndex >= 0) 1.25.dp else 1.dp,
            color = if (editingIndex >= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingIndex >= 0) "Editing Parse Rule #${editingIndex + 1}" else "Add Capture / Parse Rule",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (editingIndex >= 0) {
                    TextButton(
                        onClick = {
                            editingIndex = -1
                            varName = ""
                            expression = ""
                            leftDelim = ""
                            rightDelim = ""
                            recursive = false
                            defaultValue = ""
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Cancel Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 6 Parse Modes in scrollable strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ParseRuleMode.entries.forEach { mode ->
                    val selected = selectedMode == mode
                    Surface(
                        onClick = { selectedMode = mode },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = varName,
                    onValueChange = { varName = it },
                    label = { Text("Variable Name (e.g. code, avs)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("config_studio_parse_name_input")
                )
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text("Fallback (Opt)") },
                    placeholder = { Text("N/A") },
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
            }

            if (selectedMode == ParseRuleMode.LR) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = leftDelim,
                        onValueChange = { leftDelim = it },
                        label = { Text("Left String (LR)") },
                        placeholder = { Text("\"code\":\"") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rightDelim,
                        onValueChange = { rightDelim = it },
                        label = { Text("Right String (LR)") },
                        placeholder = { Text("\"") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = { Text(selectedMode.hint) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("config_studio_parse_expr_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = showInOutput,
                        onClick = { showInOutput = !showInOutput },
                        label = { Text("Show [var=val]", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = recursive,
                        onClick = { recursive = !recursive },
                        label = { Text("Recursive", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val cleanName = varName.trim()
                        val valid = cleanName.isNotEmpty() && (
                            (selectedMode == ParseRuleMode.LR && leftDelim.isNotEmpty()) ||
                                (selectedMode != ParseRuleMode.LR && expression.isNotBlank())
                            )
                        if (valid) {
                            val builtRule = CustomParseRule(
                                name = cleanName,
                                mode = selectedMode,
                                expression = expression.trim(),
                                leftDelimiter = leftDelim,
                                rightDelimiter = rightDelim,
                                showInOutput = showInOutput,
                                recursive = recursive,
                                defaultValue = defaultValue.trim()
                            )
                            if (editingIndex in parseRules.indices) {
                                onUpdateRule(editingIndex, builtRule)
                                editingIndex = -1
                            } else {
                                onAddRule(builtRule)
                            }
                            varName = ""
                            expression = ""
                            leftDelim = ""
                            rightDelim = ""
                            recursive = false
                            defaultValue = ""
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .testTag("config_studio_add_parse_rule_button")
                ) {
                    Icon(
                        imageVector = if (editingIndex >= 0) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (editingIndex >= 0) "Update Rule" else "Add Rule",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // Active Rules List with In-Place Edit & Reordering
    if (parseRules.isEmpty()) {
        Text(
            text = "No custom capture rules configured yet. Add one above or tap a quick preset.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parseRules.forEachIndexed { idx, rule ->
                val isBeingEdited = editingIndex == idx
                Surface(
                    color = if (isBeingEdited) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = MaterialTheme.shapes.small,
                    border = if (isBeingEdited) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingIndex = idx
                            varName = rule.name
                            selectedMode = rule.mode
                            expression = rule.expression
                            leftDelim = rule.leftDelimiter
                            rightDelim = rule.rightDelimiter
                            showInOutput = rule.showInOutput
                            recursive = rule.recursive
                            defaultValue = rule.defaultValue
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StudioSummaryPill(label = rule.mode.label, color = consoleColors.telegramAccent)
                                Text(
                                    text = rule.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                if (rule.recursive) {
                                    StudioSummaryPill(label = "ALL", color = consoleColors.unknownAccent)
                                }
                            }
                            val specDesc = if (rule.mode == ParseRuleMode.LR) {
                                "L: '${rule.leftDelimiter}' .. R: '${rule.rightDelimiter}'"
                            } else {
                                rule.expression
                            }
                            Text(
                                text = specDesc + if (rule.defaultValue.isNotBlank()) " (default: ${rule.defaultValue})" else "",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    editingIndex = idx
                                    varName = rule.name
                                    selectedMode = rule.mode
                                    expression = rule.expression
                                    leftDelim = rule.leftDelimiter
                                    rightDelim = rule.rightDelimiter
                                    showInOutput = rule.showInOutput
                                    recursive = rule.recursive
                                    defaultValue = rule.defaultValue
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit parse rule",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveRule(idx, idx - 1) },
                                enabled = idx > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Move rule up",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = 180f }
                                )
                            }
                            IconButton(
                                onClick = { onMoveRule(idx, idx + 1) },
                                enabled = idx < parseRules.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Move rule down",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Switch(
                                checked = rule.showInOutput,
                                onCheckedChange = { onToggleShow(idx) }
                            )
                            IconButton(
                                onClick = {
                                    if (editingIndex == idx) editingIndex = -1
                                    onRemoveRule(idx)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.DeleteOutline,
                                    contentDescription = "Delete parse rule",
                                    tint = consoleColors.dieAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Live Dry-Run Capture Evaluator
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Live Dry-Run Capture Tester",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = consoleColors.telegramAccent
            )
            OutlinedTextField(
                value = dryRunBodyText,
                onValueChange = onDryRunBodyChange,
                label = { Text("HTTP Response Body (Auto-filled on Probe)") },
                minLines = 2,
                maxLines = 4,
                textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parse_dry_run_input")
            )
            if (dryRunCaptures.isEmpty()) {
                Text(
                    text = "Extracted variables: (none matched current response body)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dryRunCaptures.forEach { (k, v) ->
                        StudioSummaryPill(label = "$k = $v", color = consoleColors.liveAccent)
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 3: CHECKPOINTS / KEYCHECK ENGINE & STEP-BY-STEP DEBUGGER
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CheckpointsStudioTabContent(
    checkpointRules: List<CustomCheckpointRule>,
    dryRunStatusCode: Int,
    onDryRunStatusCodeChange: (Int) -> Unit,
    dryRunBodyText: String,
    onDryRunBodyChange: (String) -> Unit,
    dryRunCaptures: Map<String, String>,
    dryRunEvaluation: CheckpointEvaluationResult,
    onAddCheckpoint: (CustomCheckpointRule) -> Unit,
    onUpdateCheckpoint: (Int, CustomCheckpointRule) -> Unit,
    onMoveCheckpoint: (Int, Int) -> Unit,
    onRemoveCheckpoint: (Int) -> Unit,
    liveKeywords: String,
    onLiveKeywordsChange: (String) -> Unit,
    dieKeywords: String,
    onDieKeywordsChange: (String) -> Unit
) {
    val consoleColors = LocalConsoleColors.current
    var editingIndex by rememberSaveable { mutableIntStateOf(-1) }
    var selectedVerdict by rememberSaveable { mutableStateOf(ResultBucket.LIVE) }
    var selectedCondition by rememberSaveable { mutableStateOf(CheckpointCondition.BODY_CONTAINS) }
    var targetKey by rememberSaveable { mutableStateOf("") }
    var expectedValue by rememberSaveable { mutableStateOf("") }
    var customLabel by rememberSaveable { mutableStateOf("") }

    Text(
        text = "Checkpoints evaluate top-to-bottom (#1 → #N). The first match decides LIVE, DIE, or UNKNOWN before falling back to keyword lists.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Quick Checkpoint Presets + Captured Variable Suggestions
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PresetMiniPill(label = "+ 'succeeded' -> LIVE") {
            onAddCheckpoint(
                CustomCheckpointRule(
                    verdict = ResultBucket.LIVE,
                    condition = CheckpointCondition.BODY_CONTAINS,
                    expectedValue = "\"succeeded\"",
                    label = "Succeeded"
                )
            )
        }
        PresetMiniPill(label = "+ Declines (,) -> DIE") {
            onAddCheckpoint(
                CustomCheckpointRule(
                    verdict = ResultBucket.DIE,
                    condition = CheckpointCondition.BODY_CONTAINS_ANY,
                    expectedValue = "card_declined,incorrect_cvc,expired_card",
                    label = "Declined"
                )
            )
        }
        PresetMiniPill(label = "+ HTTP 200,201 -> LIVE") {
            onAddCheckpoint(
                CustomCheckpointRule(
                    verdict = ResultBucket.LIVE,
                    condition = CheckpointCondition.STATUS_CODE_IN,
                    expectedValue = "200,201",
                    label = "HTTP 2xx OK"
                )
            )
        }
        PresetMiniPill(label = "+ HTTP 402 -> DIE") {
            onAddCheckpoint(
                CustomCheckpointRule(
                    verdict = ResultBucket.DIE,
                    condition = CheckpointCondition.STATUS_CODE_EQUALS,
                    expectedValue = "402",
                    label = "402 Declined"
                )
            )
        }
        PresetMiniPill(label = "+ HTTP 429 -> UNKNOWN") {
            onAddCheckpoint(
                CustomCheckpointRule(
                    verdict = ResultBucket.UNKNOWN,
                    condition = CheckpointCondition.STATUS_CODE_IN,
                    expectedValue = "429,502,503",
                    label = "Rate Limited"
                )
            )
        }
        dryRunCaptures.forEach { (varKey, varVal) ->
            PresetMiniPill(label = "+ Var '$varKey'=='${varVal.take(10)}' -> LIVE") {
                onAddCheckpoint(
                    CustomCheckpointRule(
                        verdict = ResultBucket.LIVE,
                        condition = CheckpointCondition.CAPTURED_VAR_EQUALS,
                        targetKey = varKey,
                        expectedValue = varVal,
                        label = "$varKey OK"
                    )
                )
            }
        }
    }

    // Add / Edit Checkpoint Builder Card
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = if (editingIndex >= 0) 1.25.dp else 1.dp,
            color = if (editingIndex >= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingIndex >= 0) {
                        "Editing Checkpoint #${editingIndex + 1}"
                    } else {
                        "Add Keycheck / Checkpoint Assertion"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (editingIndex >= 0) {
                    TextButton(
                        onClick = {
                            editingIndex = -1
                            targetKey = ""
                            expectedValue = ""
                            customLabel = ""
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Cancel Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ResultBucket.entries.forEachIndexed { idx, bucket ->
                    SegmentedButton(
                        selected = selectedVerdict == bucket,
                        onClick = { selectedVerdict = bucket },
                        shape = SegmentedButtonDefaults.itemShape(idx, ResultBucket.entries.size)
                    ) {
                        Text(bucket.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // All 14 Checkpoint Conditions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CheckpointCondition.entries.forEach { cond ->
                    val selected = selectedCondition == cond
                    Surface(
                        onClick = { selectedCondition = cond },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    ) {
                        Text(
                            text = cond.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (selectedCondition.requiresKey) {
                OutlinedTextField(
                    value = targetKey,
                    onValueChange = { targetKey = it },
                    label = {
                        Text(
                            when (selectedCondition) {
                                CheckpointCondition.JSON_KEY_EQUALS,
                                CheckpointCondition.JSON_KEY_CONTAINS -> "JSON Key Path (e.g. status or errors[0].code)"
                                CheckpointCondition.HEADER_CONTAINS -> "HTTP Header Name (e.g. content-type or x-error)"
                                else -> "Captured Variable Name (from Parse tab)"
                            }
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = expectedValue,
                    onValueChange = { expectedValue = it },
                    label = { Text("Expected Value / List") },
                    placeholder = { Text("approved / 200,201 / card_declined") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("config_studio_checkpoint_value_input")
                )
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text("Reason Tag (Opt)") },
                    placeholder = { Text("CVV Match") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = {
                        if (expectedValue.isNotBlank() ||
                            selectedCondition == CheckpointCondition.CAPTURED_VAR_CONTAINS ||
                            selectedCondition == CheckpointCondition.CAPTURED_VAR_NOT_CONTAINS
                        ) {
                            val builtRule = CustomCheckpointRule(
                                verdict = selectedVerdict,
                                condition = selectedCondition,
                                targetKey = targetKey.trim(),
                                expectedValue = expectedValue.trim(),
                                label = customLabel.trim()
                            )
                            if (editingIndex in checkpointRules.indices) {
                                onUpdateCheckpoint(editingIndex, builtRule)
                                editingIndex = -1
                            } else {
                                onAddCheckpoint(builtRule)
                            }
                            expectedValue = ""
                            customLabel = ""
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .testTag("config_studio_add_checkpoint_button")
                ) {
                    Icon(
                        imageVector = if (editingIndex >= 0) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (editingIndex >= 0) "Update Checkpoint" else "Add Checkpoint",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }

    // Active Checkpoints List with Priority Controls & In-Place Edit
    if (checkpointRules.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            checkpointRules.forEachIndexed { idx, rule ->
                val isBeingEdited = editingIndex == idx
                val badgeColor = when (rule.verdict) {
                    ResultBucket.LIVE -> consoleColors.liveAccent
                    ResultBucket.DIE -> consoleColors.dieAccent
                    ResultBucket.UNKNOWN -> consoleColors.unknownAccent
                }
                val stepEval = dryRunEvaluation.steps.getOrNull(idx)
                Surface(
                    color = if (isBeingEdited) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        if (stepEval?.isWinner == true || isBeingEdited) 1.25.dp else 0.75.dp,
                        if (isBeingEdited) MaterialTheme.colorScheme.primary else badgeColor.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingIndex = idx
                            selectedVerdict = rule.verdict
                            selectedCondition = rule.condition
                            targetKey = rule.targetKey
                            expectedValue = rule.expectedValue
                            customLabel = rule.label
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                StudioSummaryPill(label = rule.verdict.name, color = badgeColor)
                                Text(
                                    text = rule.condition.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (stepEval?.isWinner == true) {
                                    StudioSummaryPill(label = "WINNER", color = badgeColor)
                                }
                            }
                            val targetExpr = if (rule.targetKey.isNotBlank()) "${rule.targetKey} → " else ""
                            Text(
                                text = "$targetExpr\"${rule.expectedValue}\"${if (rule.label.isNotBlank()) " (${rule.label})" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            stepEval?.let { st ->
                                Text(
                                    text = if (st.matched) "✓ ${st.detail}" else "· ${st.detail}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (st.matched) badgeColor else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    editingIndex = idx
                                    selectedVerdict = rule.verdict
                                    selectedCondition = rule.condition
                                    targetKey = rule.targetKey
                                    expectedValue = rule.expectedValue
                                    customLabel = rule.label
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit checkpoint",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveCheckpoint(idx, idx - 1) },
                                enabled = idx > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Move checkpoint higher priority",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = 180f }
                                )
                            }
                            IconButton(
                                onClick = { onMoveCheckpoint(idx, idx + 1) },
                                enabled = idx < checkpointRules.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Move checkpoint lower priority",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (editingIndex == idx) editingIndex = -1
                                    onRemoveCheckpoint(idx)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.DeleteOutline,
                                    contentDescription = "Remove checkpoint",
                                    tint = consoleColors.dieAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Live Dry-Run Checkpoint Evaluator
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dry-Run Keycheck Debugger",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                val matchBadge = dryRunEvaluation.winner
                if (matchBadge != null) {
                    val col = when (matchBadge.first) {
                        ResultBucket.LIVE -> consoleColors.liveAccent
                        ResultBucket.DIE -> consoleColors.dieAccent
                        ResultBucket.UNKNOWN -> consoleColors.unknownAccent
                    }
                    StudioSummaryPill(label = "${matchBadge.first.name}: ${matchBadge.second}", color = col)
                } else {
                    StudioSummaryPill(label = "Fallback Keywords", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(200, 402, 429, 500).forEach { code ->
                    Surface(
                        onClick = { onDryRunStatusCodeChange(code) },
                        color = if (dryRunStatusCode == code) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "HTTP $code",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = dryRunBodyText,
                onValueChange = onDryRunBodyChange,
                label = { Text("HTTP Response Body (Auto-filled on Probe)") },
                minLines = 2,
                maxLines = 3,
                textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

    Text(
        text = "Fallback Keyword Lists (Used if no Checkpoint rule matches)",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = liveKeywords,
            onValueChange = onLiveKeywordsChange,
            label = { Text("Live Keywords") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = dieKeywords,
            onValueChange = onDieKeywordsChange,
            label = { Text("Die Keywords") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

// =============================================================================
// TAB 4: PROXY POOL, DIAGNOSTICS & NETWORK ROUTING
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProxyStudioTabContent(
    proxyEnabled: Boolean,
    onProxyEnabledChange: (Boolean) -> Unit,
    proxyProtocol: ProxyProtocol,
    onProxyProtocolChange: (ProxyProtocol) -> Unit,
    proxyRotation: ProxyRotationMode,
    onProxyRotationChange: (ProxyRotationMode) -> Unit,
    proxyListText: String,
    onProxyListTextChange: (String) -> Unit,
    onImportProxyFile: () -> Unit
) {
    val consoleColors = LocalConsoleColors.current
    val parsedPool = remember(proxyListText, proxyProtocol) {
        CustomConfigStudioEngine.parseProxyPool(proxyListText, proxyProtocol)
    }
    val invalidLines = remember(proxyListText, proxyProtocol) {
        CustomConfigStudioEngine.findInvalidProxyLines(proxyListText, proxyProtocol)
    }
    val authCount = remember(parsedPool) { parsedPool.count { it.requiresAuth } }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Route Requests via Proxy Pool",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Supports HTTP/S and SOCKS5 proxies with Basic authentication",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = proxyEnabled,
                onCheckedChange = onProxyEnabledChange,
                modifier = Modifier.testTag("config_studio_proxy_switch")
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Default Protocol", style = MaterialTheme.typography.labelSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProxyProtocol.entries.forEachIndexed { idx, proto ->
                    SegmentedButton(
                        selected = proxyProtocol == proto,
                        onClick = { onProxyProtocolChange(proto) },
                        shape = SegmentedButtonDefaults.itemShape(idx, ProxyProtocol.entries.size)
                    ) {
                        Text(proto.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Rotation Strategy", style = MaterialTheme.typography.labelSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProxyRotationMode.entries.forEachIndexed { idx, rot ->
                    SegmentedButton(
                        selected = proxyRotation == rot,
                        onClick = { onProxyRotationChange(rot) },
                        shape = SegmentedButtonDefaults.itemShape(idx, ProxyRotationMode.entries.size)
                    ) {
                        Text(rot.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${parsedPool.size} valid • $authCount auth" +
                if (invalidLines.isNotEmpty()) " • ${invalidLines.size} invalid" else "",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                invalidLines.isNotEmpty() -> consoleColors.unknownAccent
                parsedPool.isNotEmpty() -> consoleColors.liveAccent
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PresetMiniPill(label = "Import .txt", onClick = onImportProxyFile)
            PresetMiniPill(label = "Clean & Dedupe") {
                val cleaned = parsedPool
                    .distinctBy { "${it.protocol}:${it.host}:${it.port}:${it.username}" }
                    .joinToString("\n") { ep ->
                        val prefix = if (ep.protocol != proxyProtocol) "${ep.protocol.name.lowercase()}://" else ""
                        if (ep.requiresAuth) "$prefix${ep.host}:${ep.port}:${ep.username}:${ep.password}"
                        else "$prefix${ep.host}:${ep.port}"
                    }
                onProxyListTextChange(cleaned)
            }
        }
    }

    if (invalidLines.isNotEmpty()) {
        Surface(
            color = consoleColors.dieContainer.copy(alpha = 0.45f),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(0.75.dp, consoleColors.dieAccent.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Invalid proxy syntax on line(s): " +
                    invalidLines.take(3).joinToString(", ") { "L${it.first} (${it.second})" } +
                    " — tap 'Clean & Dedupe' to strip.",
                style = MaterialTheme.typography.labelSmall,
                color = consoleColors.dieAccent,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }

    OutlinedTextField(
        value = proxyListText,
        onValueChange = onProxyListTextChange,
        label = { Text("Proxy Pool (One per line: host:port or host:port:user:pass)") },
        placeholder = {
            Text(
                "198.51.100.10:8080\n198.51.100.22:1080:myuser:mypass\nsocks5://user:pass@203.0.113.5:9050"
            )
        },
        minLines = 4,
        maxLines = 8,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("config_studio_proxy_list_input")
    )

    if (parsedPool.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            parsedPool.take(4).forEach { ep ->
                StudioSummaryPill(
                    label = ep.displayLabel,
                    color = if (ep.requiresAuth) consoleColors.unknownAccent else consoleColors.telegramAccent
                )
            }
            if (parsedPool.size > 4) {
                StudioSummaryPill(
                    label = "+${parsedPool.size - 4} more",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =============================================================================
// TAB 5: PROFILES, SEARCH & CODE EXPORT (JSON / cURL / LOLISCRIPT)
// =============================================================================

@Composable
private fun ProfilesStudioTabContent(
    currentProfileName: String,
    onProfileNameChange: (String) -> Unit,
    savedProfiles: List<CustomConfigProfile>,
    onSaveCurrentProfile: (String) -> Unit,
    onDuplicateProfile: (CustomConfigProfile) -> Unit,
    onLoadProfile: (CustomConfigProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onCopyJson: () -> Unit,
    onPasteJson: () -> Unit,
    onCopyCurl: () -> Unit,
    onCopyLoliScript: () -> Unit,
    onOpenCurlImporter: () -> Unit
) {
    var profileNote by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Save Current Config Card
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Save Current Config to Library",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentProfileName,
                    onValueChange = onProfileNameChange,
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = profileNote,
                    onValueChange = { profileNote = it },
                    label = { Text("Short Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenCurlImporter,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(AppIcons.Api, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import Request", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCopyJson,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy JSON", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onPasteJson,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(AppIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste JSON", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCopyCurl,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy cURL", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCopyLoliScript,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(AppIcons.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Loli", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = {
                        onSaveCurrentProfile(profileNote)
                        profileNote = ""
                    },
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("config_studio_save_profile_button")
                ) {
                    Text("Save Profile", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (savedProfiles.size > 2) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Filter Profiles by Name or Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    val filteredSaved = remember(savedProfiles, searchQuery) {
        if (searchQuery.isBlank()) savedProfiles
        else savedProfiles.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true) ||
                it.config.url.contains(searchQuery, ignoreCase = true)
        }
    }

    // User Saved Profiles
    if (filteredSaved.isNotEmpty()) {
        Text(
            text = "Saved User Configs (${filteredSaved.size})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            filteredSaved.forEach { profile ->
                ProfileItemCard(
                    profile = profile,
                    onLoad = { onLoadProfile(profile) },
                    onDuplicate = { onDuplicateProfile(profile) },
                    onDelete = { onDeleteProfile(profile.id) }
                )
            }
        }
    }

    // Built-In Starter Templates
    Text(
        text = "Built-in Starter Config Templates",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CustomConfigStudioEngine.BUILT_IN_PROFILES.forEach { preset ->
            ProfileItemCard(
                profile = preset,
                onLoad = { onLoadProfile(preset) },
                onDuplicate = { onDuplicateProfile(preset) },
                onDelete = null
            )
        }
    }
}

@Composable
private fun ProfileItemCard(
    profile: CustomConfigProfile,
    onLoad: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val consoleColors = LocalConsoleColors.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (profile.config.preRequestEnabled) {
                        StudioSummaryPill(
                            label = "2-STEP",
                            color = consoleColors.unknownAccent
                        )
                    }
                    val blockCount = profile.config.parsedBlocks().size
                    if (blockCount > 0) {
                        StudioSummaryPill(
                            label = "${blockCount}BLK",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    StudioSummaryPill(
                        label = profile.config.method.label,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalButton(
                    onClick = onLoad,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Load", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = "Duplicate profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = AppIcons.DeleteOutline,
                            contentDescription = "Delete profile",
                            tint = consoleColors.dieAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetMiniPill(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigProbeTraceCard(
    probe: HttpProbeResult,
    onCopyRawBody: (String) -> Unit
) {
    val consoleColors = LocalConsoleColors.current
    var showRawDetails by rememberSaveable { mutableStateOf(false) }

    val accent = when {
        !probe.isReachable -> consoleColors.dieAccent
        probe.detectedBucket == ResultBucket.LIVE -> consoleColors.liveAccent
        probe.detectedBucket == ResultBucket.DIE -> consoleColors.dieAccent
        else -> consoleColors.unknownAccent
    }

    Surface(
        color = accent.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("config_studio_probe_result_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (probe.step1Executed && probe.step1Summary.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioSummaryPill(
                        label = probe.step1Summary,
                        color = consoleColors.unknownAccent
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = probe.summary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${probe.latencyMs} ms • ${probe.proxyUsed}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (probe.matchedCheckpoint.isNotBlank() || probe.parsedCaptures.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (probe.matchedCheckpoint.isNotBlank()) {
                        StudioSummaryPill(label = probe.matchedCheckpoint, color = accent)
                    }
                    probe.parsedCaptures.forEach { (k, v) ->
                        StudioSummaryPill(label = "$k = $v", color = consoleColors.telegramAccent)
                    }
                }
            }

            if (probe.responsePreview.isNotBlank()) {
                Text(
                    text = probe.responsePreview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showRawDetails = !showRawDetails },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = if (showRawDetails) "Hide Trace Details" else "Inspect Request, Headers & Keychecks",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (probe.rawResponseBody.isNotBlank()) {
                    TextButton(
                        onClick = { onCopyRawBody(probe.rawResponseBody) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Body", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            AnimatedVisibility(visible = showRawDetails) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (probe.blockTrace.isNotEmpty()) {
                            Text(
                                text = "BLOCK STACK TRACE:\n" + probe.blockTrace.joinToString("\n") { st ->
                                    "#${st.index} [${st.status.name}] ${st.block.category.shortBadge}/${st.block.label}: ${st.detail}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (probe.checkpointTrace.isNotEmpty()) {
                            Text(
                                text = "KEYCHECK TRACE:\n" + probe.checkpointTrace.joinToString("\n") { st ->
                                    val mark = when {
                                        st.isWinner -> "[WIN -> ${st.rule.verdict}]"
                                        st.matched -> "[MATCH]"
                                        else -> "[SKIP]"
                                    }
                                    "#${st.index} $mark ${st.rule.condition.label}: ${st.detail}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (probe.requestSummary.isNotBlank()) {
                            Text(
                                text = "REQ: ${probe.requestSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (probe.requestHeadersSummary.isNotBlank()) {
                            Text(
                                text = "REQ HEADERS:\n${probe.requestHeadersSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (probe.requestBodyPreview.isNotBlank()) {
                            Text(
                                text = "REQ BODY:\n${probe.requestBodyPreview}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (probe.responseHeaders.isNotEmpty()) {
                            val headerText = probe.responseHeaders.entries.take(8)
                                .joinToString("\n") { "${it.key}: ${it.value}" }
                            Text(
                                text = "RES HEADERS:\n$headerText",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
