package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ValidatorSettings
import com.example.domain.ValidationMode
import com.example.ui.theme.ActionButtonBorder
import com.example.ui.theme.ActionButtonSurface
import com.example.ui.theme.CardContainerSurface
import com.example.ui.theme.DieCrimson
import com.example.ui.theme.GearRoyalBlue
import com.example.ui.theme.InputConsoleSurface
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlayEmerald
import com.example.ui.theme.TelegramSkyBlue
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryMuted

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
        patternTemplate: String,
        generateQuantity: Int
    ) -> Unit,
    onGenerateBatchToConsole: (pattern: String, quantity: Int) -> Unit,
    onClearAllBuckets: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedMode by remember(settings) { mutableStateOf(settings.validationMode) }
    var webhookUrl by remember(settings) { mutableStateOf(settings.webhookUrl) }
    var autoResolveWildcards by remember(settings) { mutableStateOf(settings.autoResolveWildcards) }
    var delayMs by remember(settings) { mutableFloatStateOf(settings.batchDelayMs.toFloat()) }
    var patternTemplate by remember(settings) { mutableStateOf(settings.patternTemplate) }
    var generateQty by remember(settings) { mutableIntStateOf(settings.generateQuantity) }

    val channelUrl = stringResource(R.string.developer_channel_url)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardContainerSurface,
        contentColor = TextPrimaryLight,
        scrimColor = Color.Black.copy(alpha = 0.72f)
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
                Column {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryLight,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ISO/IEC 7812 Luhn, BIN/IIN, Expiry & Pattern Engine",
                        fontSize = 12.sp,
                        color = TextSecondaryMuted
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close settings",
                        tint = TextSecondaryMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Developer Channel Highlight Card (https://t.me/drkingbd)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                TelegramSkyBlue.copy(alpha = 0.16f),
                                GearRoyalBlue.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = TelegramSkyBlue.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("settings_developer_channel_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TelegramSkyBlue.copy(alpha = 0.22f))
                                .border(1.dp, TelegramSkyBlue.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            TelegramPaperPlaneIcon(
                                color = TelegramSkyBlue,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.developer_channel_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryLight
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TelegramSkyBlue.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.developer_channel_handle),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TelegramSkyBlue
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = channelUrl,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = TextSecondaryMuted
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TelegramSkyBlue)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.open_telegram),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF06131C)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF06131C),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Wildcard Pattern Batch Generator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputConsoleSurface)
                    .border(1.dp, ActionButtonBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = PlayEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.generator_section_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryLight
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Use 'x' for wildcard digits. The mathematical solver computes exact Luhn Mod-10 check digits automatically.",
                        fontSize = 11.5.sp,
                        color = TextSecondaryMuted,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = patternTemplate,
                        onValueChange = { patternTemplate = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pattern_template_input"),
                        label = { Text("Pattern (PAN|MM|YYYY|CVV)", fontSize = 12.sp) },
                        placeholder = { Text("453225005536xxx3|08|2028|113", fontSize = 12.sp) },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            color = TextPrimaryLight
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PlayEmerald,
                            unfocusedBorderColor = ActionButtonBorder,
                            focusedLabelColor = PlayEmerald,
                            unfocusedLabelColor = TextSecondaryMuted,
                            cursorColor = PlayEmerald
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quantity:",
                            fontSize = 12.sp,
                            color = TextSecondaryMuted
                        )
                        listOf(5, 10, 25, 50).forEach { countOption ->
                            val isSelected = generateQty == countOption
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) PlayEmerald.copy(alpha = 0.2f) else ActionButtonSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) PlayEmerald else ActionButtonBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { generateQty = countOption }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("qty_chip_$countOption")
                            ) {
                                Text(
                                    text = "$countOption",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) PlayEmerald else TextPrimaryLight
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onGenerateBatchToConsole(patternTemplate, generateQty)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_batch_to_console_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayEmerald,
                            contentColor = Color(0xFF071712)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.generate_batch_button),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = ActionButtonBorder)
            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Validation Engine Mode
            Text(
                text = "Validation Engine Mode",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryLight
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
                    label = { Text(stringResource(R.string.endpoint_url_label), fontSize = 11.5.sp) },
                    placeholder = { Text("https://api.example.com/verify?lista={lista}", fontSize = 12.sp) },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.5.sp,
                        color = TextPrimaryLight
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GearRoyalBlue,
                        unfocusedBorderColor = ActionButtonBorder,
                        focusedLabelColor = GearRoyalBlue,
                        unfocusedLabelColor = TextSecondaryMuted,
                        cursorColor = GearRoyalBlue
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-resolve wildcards switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputConsoleSurface)
                    .border(1.dp, ActionButtonBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Solve 'x' Wildcards on Play",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimaryLight
                    )
                    Text(
                        text = "Automatically resolves 'x' digits to Luhn-valid numbers when running",
                        fontSize = 11.5.sp,
                        color = TextSecondaryMuted
                    )
                }
                Switch(
                    checked = autoResolveWildcards,
                    onCheckedChange = { autoResolveWildcards = it },
                    modifier = Modifier.testTag("auto_resolve_wildcards_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PlayEmerald
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Batch Delay Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputConsoleSurface)
                    .border(1.dp, ActionButtonBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Batch Stream Delay",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimaryLight
                    )
                    Text(
                        text = "${delayMs.toLong()} ms / item",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = PlayEmerald
                    )
                }
                Slider(
                    value = delayMs,
                    onValueChange = { delayMs = it },
                    valueRange = 0f..400f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = GearRoyalBlue,
                        activeTrackColor = GearRoyalBlue
                    )
                )
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
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DieCrimson)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Counts", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        onSaveSettings(
                            selectedMode,
                            webhookUrl,
                            autoResolveWildcards,
                            delayMs.toLong(),
                            patternTemplate,
                            generateQty
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_settings_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GearRoyalBlue,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.save_close),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (selected) GearRoyalBlue else ActionButtonBorder
    val bgColor = if (selected) GearRoyalBlue.copy(alpha = 0.12f) else InputConsoleSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) GearRoyalBlue else TextSecondaryMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryLight
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = TextSecondaryMuted,
                lineHeight = 15.sp
            )
        }
    }
}
