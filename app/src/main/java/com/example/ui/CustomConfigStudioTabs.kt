package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.domain.CheckpointCondition
import com.example.domain.CheckpointEvaluationResult
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

// =============================================================================
// TAB 1: HTTP REQUEST PIPELINE (+ 2-STEP HANDSHAKE & LIVE REQUEST PREVIEW)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RequestStudioTabContent(
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
                                BuildConfig.STRIPE_SANDBOX_PUBLISHABLE_KEY,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(ParseRuleMode.JSON, ParseRuleMode.REGEX, ParseRuleMode.COOKIE, ParseRuleMode.HEADER).forEach { m ->
                            FilterChip(
                                selected = step1VarMode == m,
                                onClick = { step1VarMode = m },
                                label = { Text(m.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

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
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Step 1 Capture", modifier = Modifier.size(16.dp))
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
                                    border = BorderStroke(1.dp, consoleColors.unknownAccent.copy(alpha = 0.4f)),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                                            modifier = Modifier.size(14.dp),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = token,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
                    .minimumInteractiveComponentSize()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParseStudioTabContent(
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
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
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
                        .minimumInteractiveComponentSize()
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
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit parse rule",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveRule(idx, idx - 1) },
                                enabled = idx > 0,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
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
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
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
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckpointsStudioTabContent(
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
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
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = cond.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
                        .minimumInteractiveComponentSize()
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
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit checkpoint",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveCheckpoint(idx, idx - 1) },
                                enabled = idx > 0,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
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
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
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
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
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
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = "HTTP $code",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
internal fun ProxyStudioTabContent(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfilesStudioTabContent(
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenCurlImporter,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                ) {
                    Icon(AppIcons.Api, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import Request", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCopyJson,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy JSON", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onPasteJson,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                ) {
                    Icon(AppIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste JSON", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCopyCurl,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy cURL", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCopyLoliScript,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
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
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 38.dp)
                ) {
                    Text("Load", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = "Duplicate profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                    ) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConfigProbeTraceCard(
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = if (showRawDetails) "Hide Trace Details" else "Inspect Request, Headers & Keychecks",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (probe.rawResponseBody.isNotBlank()) {
                    TextButton(
                        onClick = { onCopyRawBody(probe.rawResponseBody) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
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
