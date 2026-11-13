package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.R
import com.example.domain.BinInfo
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun BinLookupBottomSheet(
    binInput: String,
    binInfo: BinInfo?,
    isLoading: Boolean,
    recentLiveBins: List<String>,
    onInputChanged: (String) -> Unit,
    onLookup: (String) -> Unit,
    onCopySummary: (BinInfo) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

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
                        text = stringResource(R.string.bin_lookup_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.bin_lookup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_bin_lookup_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close BIN lookup",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = binInput,
                    onValueChange = onInputChanged,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bin_lookup_input"),
                    label = { Text(stringResource(R.string.bin_lookup_search_label)) },
                    placeholder = { Text("453225") },
                    textStyle = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (binInput.length >= 6) onLookup(binInput)
                        }
                    ),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )

                Button(
                    onClick = { onLookup(binInput) },
                    enabled = !isLoading && binInput.length >= 6,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("bin_lookup_submit_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    if (isLoading) {
                        LoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.bin_lookup_action),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }

            if (recentLiveBins.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Live Batch BINs",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentLiveBins.take(10).forEach { bin ->
                        val isSelected = binInput.startsWith(bin)
                        Surface(
                            onClick = { onLookup(bin) },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isSelected) consoleColors.liveContainer else colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) consoleColors.liveAccent else colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                text = bin,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) consoleColors.liveAccent else colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading && binInfo == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ContainedLoadingIndicator(
                                modifier = Modifier.size(44.dp),
                                containerColor = consoleColors.liveContainer,
                                indicatorColor = consoleColors.liveAccent
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Looking up BIN details...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearWavyProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = consoleColors.liveAccent,
                                trackColor = colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
            } else if (binInfo != null) {
                BinDetailsCard(
                    info = binInfo,
                    isRefreshing = isLoading,
                    onRefresh = { onLookup(binInfo.bin) },
                    onCopySummary = { onCopySummary(binInfo) },
                    onOpenBankUrl = { url ->
                        val normalizedUrl = if (url.startsWith("http")) url else "https://$url"
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, normalizedUrl.toUri()).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surfaceContainerLowest,
                    border = BorderStroke(0.75.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = consoleColors.liveContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.CreditCard,
                                    contentDescription = null,
                                    tint = consoleColors.liveAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Check any 6-digit BIN",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Type the first 6 digits of any card to see its issuing bank, tier, country, and currency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BinDetailsCard(
    info: BinInfo,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCopySummary: () -> Unit,
    onOpenBankUrl: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bin_details_card"),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            if (info.isNetworkVerified) consoleColors.liveAccent.copy(alpha = 0.55f) else colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.CreditCard,
                        contentDescription = null,
                        tint = consoleColors.liveAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BIN ${info.bin}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (info.isNetworkVerified) consoleColors.liveContainer else consoleColors.unknownContainer
                ) {
                    Text(
                        text = info.source,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (info.isNetworkVerified) consoleColors.liveAccent else consoleColors.unknownAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (info.scheme.isNotBlank()) {
                    BinAttributePill(label = info.scheme, container = colorScheme.primaryContainer, content = colorScheme.onPrimaryContainer)
                }
                if (info.type.isNotBlank()) {
                    BinAttributePill(label = info.type, container = colorScheme.secondaryContainer, content = colorScheme.onSecondaryContainer)
                }
                if (info.brand.isNotBlank()) {
                    BinAttributePill(label = info.brand, container = colorScheme.tertiaryContainer, content = colorScheme.onTertiaryContainer)
                }
                if (info.isPrepaid != null) {
                    BinAttributePill(
                        label = if (info.isPrepaid) "PREPAID" else "NON-PREPAID",
                        container = colorScheme.surfaceContainerHigh,
                        content = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = AppIcons.AccountBalance,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Issuer / Bank",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = info.bankName.ifBlank { "Bank not listed in registry" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    if (info.bankPhone.isNotBlank()) {
                        Text(
                            text = "Tel: ${info.bankPhone}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    if (info.bankUrl.isNotBlank()) {
                        Surface(
                            onClick = { onOpenBankUrl(info.bankUrl) },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = info.bankUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = AppIcons.OpenInNew,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = AppIcons.Language,
                    contentDescription = null,
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Country & Currency",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    val countryDisplay = listOf(info.countryEmoji, info.countryName, info.countryCode)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifEmpty { "Unknown" }
                    Text(
                        text = if (info.currency.isNotBlank()) "$countryDisplay • ${info.currency}" else countryDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("bin_lookup_refresh_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh", style = MaterialTheme.typography.labelLarge)
                }

                FilledTonalButton(
                    onClick = onCopySummary,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("bin_lookup_copy_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.bin_lookup_copy), style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

@Composable
private fun BinAttributePill(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = container
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
