package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.analytics.FirebaseMetrics
import com.example.domain.CustomConfigStudioEngine
import com.example.domain.CustomHttpConfig
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SpaceGroteskFamily

internal enum class ConfigStudioTab(val title: String) {
    REQUEST("Request"),
    BLOCKS("Blocks"),
    PARSE("Parse"),
    CHECKPOINTS("Checkpoints"),
    PROXY("Proxy"),
    PROFILES("Profiles")
}

internal enum class TokenCategory(val label: String) {
    ALL("All"),
    CARD("Card & BIN"),
    COMBO("Combo"),
    DYNAMIC("Dynamic & Hash"),
    STEP1("Step 1 Captures")
}

internal fun adjustEditingIndexOnMove(editingIndex: Int, fromIndex: Int, toIndex: Int): Int =
    when (editingIndex) {
        fromIndex -> toIndex
        toIndex -> fromIndex
        else -> editingIndex
    }

internal fun adjustEditingIndexOnRemove(editingIndex: Int, removedIndex: Int): Int =
    when {
        editingIndex == removedIndex -> -1
        editingIndex > removedIndex -> editingIndex - 1
        else -> editingIndex
    }

@Composable
internal fun PipelineStagePill(
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
        ),
        modifier = Modifier.minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
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
internal fun StudioSummaryPill(
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
internal fun PresetMiniPill(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        modifier = Modifier.minimumInteractiveComponentSize()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun CurlOrFetchImportDialog(
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
                            BuildConfig.STRIPE_SANDBOX_PUBLISHABLE_KEY,
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
                            BuildConfig.CHECKOUT_SANDBOX_SECRET_KEY,
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .heightIn(min = 40.dp)
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
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("curl_import_confirm_button")
            ) {
                Text("Parse & Load")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel")
            }
        }
    )
}
