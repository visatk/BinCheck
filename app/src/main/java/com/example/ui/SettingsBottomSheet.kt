package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppThemeMode
import com.example.data.ValidatorSettings
import com.example.domain.ValidationMode
import com.example.ui.theme.LocalConsoleColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: ValidatorSettings,
    onDismiss: () -> Unit,
    onSaveSettings: (
        validationMode: ValidationMode,
        webhookUrl: String,
        autoResolveWildcards: Boolean,
        batchDelayMs: Long,
        concurrency: Int,
        patternTemplate: String,
        generateQuantity: Int,
        themeMode: AppThemeMode,
        useDynamicColor: Boolean
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
    var webhookUrl by remember(settings) { mutableStateOf(settings.webhookUrl) }
    var autoResolveWildcards by remember(settings) { mutableStateOf(settings.autoResolveWildcards) }
    var delayMs by remember(settings) { mutableFloatStateOf(settings.batchDelayMs.toFloat()) }
    var concurrency by remember(settings) { mutableIntStateOf(settings.concurrency) }
    var patternTemplate by remember(settings) { mutableStateOf(settings.patternTemplate) }
    var generateQty by remember(settings) { mutableIntStateOf(settings.generateQuantity) }
    var selectedThemeMode by remember(settings) { mutableStateOf(settings.themeMode) }
    var useDynamicColor by remember(settings) { mutableStateOf(settings.useDynamicColor) }

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
                        style = MaterialTheme.typography.titleLarge,
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl)).apply {
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
                            style = MaterialTheme.typography.titleSmall,
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
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Validation Engine Mode",
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            ModeOptionCard(
                title = stringResource(R.string.mode_local_luhn),
                subtitle = "Verifies ISO Luhn Mod-10 checksum, BIN/IIN brand length, expiry date, and CVV rules locally.",
                icon = Icons.Outlined.Memory,
                selected = selectedMode == ValidationMode.LOCAL_DETERMINISTIC,
                onClick = { selectedMode = ValidationMode.LOCAL_DETERMINISTIC },
                testTag = "mode_local_option"
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModeOptionCard(
                title = stringResource(R.string.mode_remote_webhook),
                subtitle = "Runs local Luhn pre-check, then dispatches HTTP GET/POST to your custom gateway URL.",
                icon = Icons.Outlined.CloudSync,
                selected = selectedMode == ValidationMode.REMOTE_WEBHOOK,
                onClick = { selectedMode = ValidationMode.REMOTE_WEBHOOK },
                testTag = "mode_webhook_option"
            )

            if (selectedMode == ValidationMode.REMOTE_WEBHOOK) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webhook_url_input"),
                    label = { Text(stringResource(R.string.endpoint_url_label)) },
                    placeholder = { Text("https://api.example.com/verify?lista={lista}") },
                    textStyle = MaterialTheme.typography.labelMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
            }

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
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Batch Stream Delay",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "${delayMs.toLong()} ms / item",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary
                        )
                    }
                    Slider(
                        value = delayMs,
                        onValueChange = { delayMs = it },
                        valueRange = 0f..400f,
                        steps = 7
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
                            text = "${concurrency}x parallel",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Entries validated concurrently against the webhook/API endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val workerOptions = listOf(1, 2, 4, 6, 8)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        workerOptions.forEachIndexed { index, workers ->
                            SegmentedButton(
                                selected = concurrency == workers,
                                onClick = { concurrency = workers },
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
                            webhookUrl,
                            autoResolveWildcards,
                            delayMs.toLong(),
                            concurrency,
                            patternTemplate,
                            generateQty,
                            selectedThemeMode,
                            useDynamicColor
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
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
