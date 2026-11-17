package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CustomHttpConfig
import com.example.domain.HttpProbeResult
import com.example.domain.HttpRequestMethod
import com.example.domain.ResultBucket
import com.example.domain.SandboxTestRegistry
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun CustomHttpSectionCard(
    config: CustomHttpConfig,
    isTestingEndpoint: Boolean,
    probeResult: HttpProbeResult?,
    onConfigChange: (CustomHttpConfig) -> Unit,
    onTestEndpoint: (CustomHttpConfig) -> Unit,
    onOpenConfigStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    var showAdvancedOptions by remember {
        mutableStateOf(
            config.headersText.isNotBlank() ||
                config.liveKeywords != CustomHttpConfig.DEFAULT_LIVE_KEYWORDS ||
                config.dieKeywords != CustomHttpConfig.DEFAULT_DIE_KEYWORDS
        )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.Http,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.custom_http_studio_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = { showAdvancedOptions = !showAdvancedOptions },
                    modifier = Modifier.testTag("toggle_http_advanced_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showAdvancedOptions) "Hide Rules" else "Headers & Rules",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val parseRuleCount = remember(config.parseRulesSpec) { config.parsedParseRules().size }
            val checkpointRuleCount = remember(config.checkpointRulesSpec) { config.parsedCheckpointRules().size }
            val proxyCount = remember(config.proxyListText, config.proxyProtocol) { config.parsedProxyPool().size }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(0.75.dp, colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.profileName.ifBlank { "Custom Config" },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        val stepPrefix = if (config.preRequestEnabled) "2-Step • " else ""
                        Text(
                            text = "$stepPrefix$parseRuleCount Parse • $checkpointRuleCount Checkpoints • ${if (config.proxyEnabled) "$proxyCount Proxies" else "Direct Net"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenConfigStudio,
                        modifier = Modifier.testTag("open_full_config_studio_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Config Studio", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = config.url,
                onValueChange = { onConfigChange(config.copy(url = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("webhook_url_input"),
                label = { Text(stringResource(R.string.endpoint_url_label)) },
                placeholder = { Text("https://api.stripe.com/v1/setup_intents") },
                textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap token to append to URL",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            val tokens = listOf("{lista}", "{card}", "{mm}", "{yy}", "{yyyy}", "{cvv}", "{bin}")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tokens.forEach { token ->
                    SuggestionChip(
                        onClick = {
                            val updatedUrl = if (config.url.isEmpty()) {
                                "https://mock.payate.com/api.php?data=$token"
                            } else {
                                config.url + token
                            }
                            onConfigChange(config.copy(url = updatedUrl))
                        },
                        label = {
                            Text(
                                text = token,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = colorScheme.surfaceContainerHigh,
                            labelColor = colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.http_sandbox_presets_label),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SandboxTestRegistry.httpSandboxPresets.forEach { preset ->
                    val isSelected = config.url.equals(preset.url, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onConfigChange(
                                config.copy(
                                    url = preset.url,
                                    method = preset.method,
                                    bodyTemplate = preset.bodyTemplate,
                                    liveKeywords = preset.liveKeywords,
                                    dieKeywords = preset.dieKeywords
                                )
                            )
                        },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("http_sandbox_preset_${preset.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.http_method_label),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            val methods = HttpRequestMethod.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                methods.forEachIndexed { index, method ->
                    SegmentedButton(
                        selected = config.method == method,
                        onClick = { onConfigChange(config.copy(method = method)) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = methods.size
                        ),
                        modifier = Modifier.testTag("http_method_${method.name.lowercase()}")
                    ) {
                        Text(
                            text = method.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = config.method != HttpRequestMethod.GET,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = stringResource(R.string.http_payload_presets_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                val nextMethod = if (config.method == HttpRequestMethod.POST_FORM) {
                                    HttpRequestMethod.POST_JSON
                                } else {
                                    config.method
                                }
                                onConfigChange(
                                    config.copy(
                                        method = nextMethod,
                                        bodyTemplate = CustomHttpConfig.ZERO_DOLLAR_AUTH_JSON_PAYLOAD
                                    )
                                )
                            },
                            label = {
                                Text("$0 Auth JSON", style = MaterialTheme.typography.labelSmall)
                            },
                            modifier = Modifier.testTag("payload_preset_zero_auth")
                        )
                        SuggestionChip(
                            onClick = {
                                val nextMethod = if (config.method == HttpRequestMethod.POST_FORM) {
                                    HttpRequestMethod.POST_JSON
                                } else {
                                    config.method
                                }
                                onConfigChange(
                                    config.copy(
                                        method = nextMethod,
                                        bodyTemplate = CustomHttpConfig.DEFAULT_JSON_PAYLOAD
                                    )
                                )
                            },
                            label = {
                                Text("JSON Card", style = MaterialTheme.typography.labelSmall)
                            },
                            modifier = Modifier.testTag("payload_preset_json_card")
                        )
                        SuggestionChip(
                            onClick = {
                                val nextMethod = if (config.method == HttpRequestMethod.POST_FORM) {
                                    HttpRequestMethod.POST_JSON
                                } else {
                                    config.method
                                }
                                onConfigChange(
                                    config.copy(
                                        method = nextMethod,
                                        bodyTemplate = """{"lista":"{lista}"}"""
                                    )
                                )
                            },
                            label = {
                                Text("Lista JSON", style = MaterialTheme.typography.labelSmall)
                            },
                            modifier = Modifier.testTag("payload_preset_lista")
                        )
                        SuggestionChip(
                            onClick = {
                                onConfigChange(
                                    config.copy(
                                        method = HttpRequestMethod.POST_FORM,
                                        bodyTemplate = CustomHttpConfig.DEFAULT_FORM_PAYLOAD
                                    )
                                )
                            },
                            label = {
                                Text("Form Body", style = MaterialTheme.typography.labelSmall)
                            },
                            modifier = Modifier.testTag("payload_preset_form")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = config.bodyTemplate,
                        onValueChange = { onConfigChange(config.copy(bodyTemplate = it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("http_body_template_input"),
                        label = { Text(stringResource(R.string.http_body_template_label)) },
                        placeholder = {
                            Text(
                                if (config.method == HttpRequestMethod.POST_FORM) {
                                    CustomHttpConfig.DEFAULT_FORM_PAYLOAD
                                } else {
                                    CustomHttpConfig.DEFAULT_JSON_PAYLOAD
                                }
                            )
                        },
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        minLines = 2,
                        maxLines = 5,
                        shape = MaterialTheme.shapes.small
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val payloadTokens = listOf(
                        "{lista}", "{card}", "{card_spaced}", "{mm}", "{yy}", "{yyyy}", "{cvv}", "{scheme}", "{scheme_lower}", "{bin}", "{uuid}"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        payloadTokens.forEach { token ->
                            SuggestionChip(
                                onClick = {
                                    onConfigChange(config.copy(bodyTemplate = config.bodyTemplate + token))
                                },
                                label = {
                                    Text(
                                        text = token,
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    labelColor = colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                modifier = Modifier.testTag(
                                    "payload_token_${token.trim('{', '}')}"
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FilterChip(
                        selected = config.compressBodyGzip,
                        onClick = { onConfigChange(config.copy(compressBodyGzip = !config.compressBodyGzip)) },
                        label = {
                            Text(
                                text = stringResource(R.string.http_gzip_body_label),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = if (config.compressBodyGzip) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag("http_gzip_toggle")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.http_timeout_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val timeouts = listOf(8, 12, 20, 30)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        timeouts.forEachIndexed { index, sec ->
                            SegmentedButton(
                                selected = config.timeoutSeconds == sec,
                                onClick = { onConfigChange(config.copy(timeoutSeconds = sec)) },
                                shape = SegmentedButtonDefaults.itemShape(index, timeouts.size),
                                modifier = Modifier.testTag("http_timeout_$sec")
                            ) {
                                Text("${sec}s", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.http_retries_label),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            val retryOptions = listOf(0, 1, 2, 3)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                retryOptions.forEachIndexed { index, retries ->
                    SegmentedButton(
                        selected = config.maxRetries == retries,
                        onClick = { onConfigChange(config.copy(maxRetries = retries)) },
                        shape = SegmentedButtonDefaults.itemShape(index, retryOptions.size),
                        modifier = Modifier.testTag("http_retries_$retries")
                    ) {
                        Text("${retries}x", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            AnimatedVisibility(
                visible = showAdvancedOptions,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = config.headersText,
                        onValueChange = { onConfigChange(config.copy(headersText = it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("http_headers_input"),
                        label = { Text(stringResource(R.string.http_headers_label)) },
                        placeholder = { Text("Authorization: Bearer token\nX-API-Key: secret") },
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        minLines = 2,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.small
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = config.liveKeywords,
                        onValueChange = { onConfigChange(config.copy(liveKeywords = it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("http_live_keywords_input"),
                        label = { Text(stringResource(R.string.http_live_keywords_label)) },
                        placeholder = { Text(CustomHttpConfig.DEFAULT_LIVE_KEYWORDS) },
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = config.dieKeywords,
                        onValueChange = { onConfigChange(config.copy(dieKeywords = it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("http_die_keywords_input"),
                        label = { Text(stringResource(R.string.http_die_keywords_label)) },
                        placeholder = { Text(CustomHttpConfig.DEFAULT_DIE_KEYWORDS) },
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { onTestEndpoint(config) },
                enabled = !isTestingEndpoint && config.url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_http_endpoint_button"),
                shape = MaterialTheme.shapes.small
            ) {
                if (isTestingEndpoint) {
                    LoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Probing HTTP Endpoint...", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(
                        imageVector = AppIcons.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.test_http_endpoint_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            AnimatedVisibility(visible = isTestingEndpoint) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceContainerHighest
                )
            }

            AnimatedVisibility(
                visible = probeResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                probeResult?.let { result ->
                    val accent = when {
                        !result.isReachable -> colorScheme.error
                        result.detectedBucket == ResultBucket.LIVE -> consoleColors.liveAccent
                        result.detectedBucket == ResultBucket.DIE -> consoleColors.dieAccent
                        else -> consoleColors.unknownAccent
                    }
                    val container = when {
                        !result.isReachable -> colorScheme.errorContainer.copy(alpha = 0.45f)
                        result.detectedBucket == ResultBucket.LIVE -> consoleColors.liveContainer
                        result.detectedBucket == ResultBucket.DIE -> consoleColors.dieContainer
                        else -> consoleColors.unknownContainer
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("http_probe_result_card"),
                        shape = MaterialTheme.shapes.small,
                        color = container,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = result.summary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${result.latencyMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            if (result.responsePreview.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = result.responsePreview,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
