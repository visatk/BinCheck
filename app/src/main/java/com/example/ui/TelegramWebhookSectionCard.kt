package com.example.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.TelegramProbeResult
import com.example.domain.TelegramWebhookClient
import com.example.domain.TelegramWebhookConfig
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun TelegramWebhookSectionCard(
    config: TelegramWebhookConfig,
    isTestingTelegram: Boolean,
    probeResult: TelegramProbeResult?,
    onConfigChange: (TelegramWebhookConfig) -> Unit,
    onDetectChat: () -> Unit,
    onTestWebhook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    val tokenConfigured = TelegramWebhookClient.hasConfiguredToken()

    LaunchedEffect(probeResult?.detectedChatId) {
        val detected = probeResult?.detectedChatId?.trim().orEmpty()
        if (detected.isNotEmpty() && detected != config.chatId) {
            onConfigChange(config.copy(chatId = detected))
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.75.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = consoleColors.telegramContainer,
                        border = BorderStroke(1.dp, consoleColors.telegramAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            TelegramPaperPlaneIcon(
                                color = consoleColors.telegramAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.telegram_webhook_title),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.telegram_webhook_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
                    modifier = Modifier.testTag("telegram_webhook_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (tokenConfigured) consoleColors.liveContainer
                        else consoleColors.unknownContainer
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = if (tokenConfigured) {
                        "@SandBoxAutoBot • Secret Active (BuildConfig)"
                    } else {
                        "@SandBoxAutoBot • Set TELEGRAM_BOT_TOKEN in AI Studio Secrets"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tokenConfigured) consoleColors.liveAccent else consoleColors.unknownAccent,
                    modifier = Modifier.testTag("telegram_token_status_badge")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = config.chatId,
                onValueChange = { onConfigChange(config.copy(chatId = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("telegram_chat_id_input"),
                label = { Text(stringResource(R.string.telegram_chat_id_label)) },
                placeholder = { Text("e.g. 123456789 or -1001234567890") },
                textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Webhook Alert Filter",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            val alertOptions = listOf(true to "Live Hits Only", false to "All Checked")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                alertOptions.forEachIndexed { index, (liveOnly, label) ->
                    SegmentedButton(
                        selected = config.notifyLiveOnly == liveOnly,
                        onClick = { onConfigChange(config.copy(notifyLiveOnly = liveOnly)) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = alertOptions.size
                        ),
                        modifier = Modifier.testTag(
                            if (liveOnly) "telegram_filter_live_only" else "telegram_filter_all"
                        )
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onDetectChat,
                    enabled = !isTestingTelegram,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("telegram_detect_chat_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    if (isTestingTelegram) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Radar,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.telegram_detect_chat),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { onTestWebhook(config.chatId) },
                    enabled = !isTestingTelegram && config.chatId.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("telegram_test_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.telegram_test_bot),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(visible = isTestingTelegram) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = consoleColors.telegramAccent,
                    trackColor = colorScheme.surfaceContainerHighest
                )
            }

            AnimatedVisibility(
                visible = probeResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                probeResult?.let { result ->
                    val accent = if (result.success) consoleColors.liveAccent else colorScheme.error
                    val container = if (result.success) consoleColors.liveContainer else colorScheme.errorContainer.copy(alpha = 0.45f)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("telegram_probe_result_card"),
                        shape = MaterialTheme.shapes.small,
                        color = container,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
