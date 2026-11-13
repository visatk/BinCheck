package com.example.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppThemeMode
import com.example.data.ValidatorSettings
import com.example.domain.CustomHttpConfig
import com.example.domain.DeviceNetworkStatus
import com.example.domain.HttpProbeResult
import com.example.domain.NetworkTelemetrySnapshot
import com.example.domain.TelegramProbeResult
import com.example.domain.TelegramWebhookConfig
import com.example.domain.ValidationMode
import com.example.ui.theme.LocalConsoleColors
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun SettingsBottomSheet(
    settings: ValidatorSettings,
    networkStats: NetworkTelemetrySnapshot,
    deviceNetworkStatus: DeviceNetworkStatus,
    isTestingEndpoint: Boolean,
    httpProbeResult: HttpProbeResult?,
    isTestingTelegram: Boolean,
    telegramProbeResult: TelegramProbeResult?,
    onDismiss: () -> Unit,
    onTestEndpoint: (CustomHttpConfig) -> Unit,
    onDetectTelegramChat: () -> Unit,
    onTestTelegramWebhook: (String) -> Unit,
    onClearCaches: () -> Unit,
    onSaveSettings: (
        validationMode: ValidationMode,
        httpConfig: CustomHttpConfig,
        telegramConfig: TelegramWebhookConfig,
        autoResolveWildcards: Boolean,
        autoBinLookupForLive: Boolean,
        batchDelayMs: Long,
        concurrency: Int,
        patternTemplate: String,
        generateQuantity: Int,
        themeMode: AppThemeMode,
        useDynamicColor: Boolean,
        deduplicateBatchInputs: Boolean,
        wifiOnlyBatch: Boolean,
        batterySaverMode: Boolean
    ) -> Unit,
    onGenerateBatchToConsole: (pattern: String, quantity: Int) -> Unit,
    onClearAllBuckets: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    var selectedMode by remember(settings) { mutableStateOf(settings.validationMode) }
    var httpConfig by remember(settings) { mutableStateOf(settings.toCustomHttpConfig()) }
    var telegramConfig by remember(settings) { mutableStateOf(settings.toTelegramWebhookConfig()) }
    var autoResolveWildcards by remember(settings) { mutableStateOf(settings.autoResolveWildcards) }
    var autoBinLookupForLive by remember(settings) { mutableStateOf(settings.autoBinLookupForLive) }
    var delayMs by remember(settings) { mutableFloatStateOf(settings.batchDelayMs.toFloat()) }
    var concurrency by remember(settings) { mutableIntStateOf(settings.concurrency) }
    var patternTemplate by remember(settings) { mutableStateOf(settings.patternTemplate) }
    var generateQty by remember(settings) { mutableIntStateOf(settings.generateQuantity) }
    var selectedThemeMode by remember(settings) { mutableStateOf(settings.themeMode) }
    var useDynamicColor by remember(settings) { mutableStateOf(settings.useDynamicColor) }
    var deduplicateBatchInputs by remember(settings) { mutableStateOf(settings.deduplicateBatchInputs) }
    var wifiOnlyBatch by remember(settings) { mutableStateOf(settings.wifiOnlyBatch) }
    var batterySaverMode by remember(settings) { mutableStateOf(settings.batterySaverMode) }

    val channelUrl = stringResource(R.string.developer_channel_url)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "ISO/IEC 7812 Luhn, BIN/IIN, Dynamic Theme & Engine",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close settings",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Developer Channel Card
            Surface(
                onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, channelUrl.toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                shape = MaterialTheme.shapes.medium,
                color = consoleColors.telegramContainer,
                border = BorderStroke(1.dp, consoleColors.telegramAccent.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_developer_channel_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    consoleColors.telegramAccent.copy(alpha = 0.10f),
                                    colorScheme.secondary.copy(alpha = 0.06f)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = consoleColors.telegramAccent.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, consoleColors.telegramAccent.copy(alpha = 0.55f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                TelegramPaperPlaneIcon(
                                    color = consoleColors.telegramAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.developer_channel_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(consoleColors.telegramAccent.copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.developer_channel_handle),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = consoleColors.telegramAccent
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = channelUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = consoleColors.telegramAccent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.open_telegram),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = consoleColors.onTelegramAccent
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = consoleColors.onTelegramAccent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Material 3 Appearance & Dynamic Color Card
            AppearanceSectionCard(
                selectedThemeMode = selectedThemeMode,
                useDynamicColor = useDynamicColor,
                onSelectThemeMode = { selectedThemeMode = it },
                onToggleDynamicColor = { useDynamicColor = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Wildcard Pattern Batch Generator Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.generator_section_title),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Use 'x' for wildcard digits. Computes exact Luhn Mod-10 check digits automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = patternTemplate,
                        onValueChange = { patternTemplate = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pattern_template_input"),
                        label = { Text("Pattern (PAN|MM|YYYY|CVV)") },
                        placeholder = { Text("453225005536xxx3|08|2028|113") },
                        textStyle = MaterialTheme.typography.labelMedium,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Batch Quantity",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val qtyOptions = listOf(5, 10, 25, 50)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        qtyOptions.forEachIndexed { index, countOption ->
                            SegmentedButton(
                                selected = generateQty == countOption,
                                onClick = { generateQty = countOption },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = qtyOptions.size
                                ),
                                modifier = Modifier.testTag("qty_chip_$countOption")
                            ) {
                                Text(
                                    text = "$countOption",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onGenerateBatchToConsole(patternTemplate, generateQty) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_batch_to_console_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.generate_batch_button),
                            style = MaterialTheme.typography.labelLargeEmphasized
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Validation Engine Mode",
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            ModeOptionCard(
                title = stringResource(R.string.mode_local_luhn),
                subtitle = "Verifies ISO Luhn Mod-10 checksum, BIN/IIN brand length, expiry date, and CVV locally (custom delay).",
                icon = Icons.Outlined.Memory,
                selected = selectedMode == ValidationMode.LOCAL_DETERMINISTIC,
                onClick = { selectedMode = ValidationMode.LOCAL_DETERMINISTIC },
                testTag = "mode_local_option"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModeOptionCard(
                title = stringResource(R.string.mode_multi_api),
                subtitle = "Checks via Chkr.cc JSON API first and fails over to Payate Form API. Enforces 5s delay between checks.",
                icon = Icons.Outlined.SyncAlt,
                selected = selectedMode == ValidationMode.MULTI_API_AUTO,
                onClick = { selectedMode = ValidationMode.MULTI_API_AUTO },
                testTag = "mode_multi_api_option"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModeOptionCard(
                title = stringResource(R.string.mode_chkr_cc),
                subtitle = "JSON POST to api.chkr.cc (charge=false) returning Live/Die verdict + Bank & Country BIN data. Enforces 10s delay.",
                icon = Icons.Outlined.Public,
                selected = selectedMode == ValidationMode.CHKR_CC,
                onClick = { selectedMode = ValidationMode.CHKR_CC },
                testTag = "mode_chkr_option"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModeOptionCard(
                title = stringResource(R.string.mode_payate_mock),
                subtitle = "Form POST to mock.payate.com/api.php returning Live (1), Die (2), and Unknown (3) responses. Enforces 3s delay.",
                icon = Icons.Outlined.Api,
                selected = selectedMode == ValidationMode.PAYATE_MOCK,
                onClick = { selectedMode = ValidationMode.PAYATE_MOCK },
                testTag = "mode_payate_option"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModeOptionCard(
                title = stringResource(R.string.mode_remote_webhook),
                subtitle = "Runs local Luhn pre-check, then dispatches HTTP GET/POST to your custom gateway URL (custom delay).",
                icon = Icons.Outlined.CloudSync,
                selected = selectedMode == ValidationMode.REMOTE_WEBHOOK,
                onClick = { selectedMode = ValidationMode.REMOTE_WEBHOOK },
                testTag = "mode_webhook_option"
            )

            AnimatedVisibility(
                visible = selectedMode == ValidationMode.REMOTE_WEBHOOK,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    CustomHttpSectionCard(
                        config = httpConfig,
                        isTestingEndpoint = isTestingEndpoint,
                        probeResult = httpProbeResult,
                        onConfigChange = { httpConfig = it },
                        onTestEndpoint = onTestEndpoint
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TelegramWebhookSectionCard(
                config = telegramConfig,
                isTestingTelegram = isTestingTelegram,
                probeResult = telegramProbeResult,
                onConfigChange = { telegramConfig = it },
                onDetectChat = onDetectTelegramChat,
                onTestWebhook = onTestTelegramWebhook
            )

            Spacer(modifier = Modifier.height(14.dp))

            NetworkOptimizationSectionCard(
                networkStats = networkStats,
                deviceNetworkStatus = deviceNetworkStatus,
                deduplicateBatchInputs = deduplicateBatchInputs,
                wifiOnlyBatch = wifiOnlyBatch,
                batterySaverMode = batterySaverMode,
                onToggleDedupCache = { deduplicateBatchInputs = it },
                onToggleWifiOnly = { wifiOnlyBatch = it },
                onToggleBatterySaver = { batterySaverMode = it },
                onClearCaches = onClearCaches
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Solve 'x' Wildcards on Play",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically resolves 'x' digits to Luhn-valid numbers when running",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoResolveWildcards,
                        onCheckedChange = { autoResolveWildcards = it },
                        modifier = Modifier.testTag("auto_resolve_wildcards_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_bin_lookup_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.auto_bin_lookup_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoBinLookupForLive,
                        onCheckedChange = { autoBinLookupForLive = it },
                        modifier = Modifier.testTag("auto_bin_lookup_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val fixedDelayForMode = selectedMode.fixedDelayMs
            val displayedDelayMs = fixedDelayForMode ?: delayMs.toLong()
            val formattedDelay = if (displayedDelayMs >= 1000L) {
                val secs = displayedDelayMs / 1000f
                if (displayedDelayMs % 1000L == 0L) "${displayedDelayMs / 1000L}s / item"
                else "${String.format(Locale.US, "%.1f", secs)}s / item"
            } else {
                "$displayedDelayMs ms / item"
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Batch Stream Delay",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface
                            )
                            if (fixedDelayForMode != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Fixed rate-limit delay",
                                    tint = colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (fixedDelayForMode != null) {
                                "${fixedDelayForMode / 1000L}s fixed (${selectedMode.badgeLabel})"
                            } else {
                                formattedDelay
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (fixedDelayForMode != null) colorScheme.secondary else colorScheme.primary,
                            modifier = Modifier.testTag("batch_delay_value_label")
                        )
                    }

                    if (fixedDelayForMode != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enforced API rate-limit delay for ${selectedMode.badgeLabel}. Select LUHN or Custom HTTP to customize delay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("fixed_delay_notice")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = fixedDelayForMode.toFloat().coerceIn(0f, 10_000f),
                            onValueChange = {},
                            enabled = false,
                            valueRange = 0f..10_000f,
                            modifier = Modifier.testTag("custom_delay_slider")
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Customize spacing between items for ${selectedMode.badgeLabel} (0ms to 10s).",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = delayMs.coerceIn(0f, 10_000f),
                            onValueChange = { raw ->
                                delayMs = ((raw / 50f).roundToInt() * 50f).coerceIn(0f, 10_000f)
                            },
                            valueRange = 0f..10_000f,
                            modifier = Modifier.testTag("custom_delay_slider")
                        )
                        val presetDelaysMs = listOf(0L, 50L, 500L, 1_000L, 3_000L, 5_000L, 10_000L)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            presetDelaysMs.forEach { preset ->
                                val label = if (preset >= 1000L) "${preset / 1000L}s" else "${preset}ms"
                                FilterChip(
                                    selected = delayMs.toLong() == preset,
                                    onClick = { delayMs = preset.toFloat() },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("delay_preset_$preset")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Simultaneous Worker Concurrency",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = if (fixedDelayForMode != null) "1x paced" else "${concurrency}x parallel",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (fixedDelayForMode != null) {
                            "${selectedMode.badgeLabel} runs sequentially (1x) so the ${fixedDelayForMode / 1000L}s delay is strictly honored between API requests."
                        } else {
                            "Entries validated concurrently against the webhook/API endpoint."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val workerOptions = listOf(1, 2, 4, 6, 8)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        workerOptions.forEachIndexed { index, workers ->
                            SegmentedButton(
                                selected = if (fixedDelayForMode != null) workers == 1 else concurrency == workers,
                                onClick = { concurrency = workers },
                                enabled = fixedDelayForMode == null,
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = workerOptions.size
                                ),
                                modifier = Modifier.testTag("concurrency_chip_$workers")
                            ) {
                                Text(
                                    text = "${workers}x",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClearAllBuckets,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("clear_all_buckets_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Counts", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = {
                        onSaveSettings(
                            selectedMode,
                            httpConfig,
                            telegramConfig,
                            autoResolveWildcards,
                            autoBinLookupForLive,
                            delayMs.toLong(),
                            concurrency,
                            patternTemplate,
                            generateQty,
                            selectedThemeMode,
                            useDynamicColor,
                            deduplicateBatchInputs,
                            wifiOnlyBatch,
                            batterySaverMode
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_settings_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.save_close),
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
            }
        }
    }
}
