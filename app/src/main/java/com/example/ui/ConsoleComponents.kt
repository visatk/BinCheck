package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.ValidationMode
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConsoleInputCard(
    uiState: ValidatorUiState,
    consoleHeight: Dp?,
    cardPadding: Dp,
    onInputChanged: (String) -> Unit,
    onToggleRun: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFileImport: () -> Unit,
    onQuickGen10x: () -> Unit,
    onExpandWildcards: () -> Unit,
    onPasteOrFill: () -> Unit,
    onClearInput: () -> Unit,
    onClearBanner: () -> Unit,
    onOpenListToolkit: () -> Unit = {},
    onQuickSwitchMode: (ValidationMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    val haptics = LocalHapticFeedback.current
    var engineMenuExpanded by remember { mutableStateOf(false) }

    val cardBorderColor by animateColorAsState(
        targetValue = if (uiState.isRunning) {
            consoleColors.liveAccent.copy(alpha = 0.55f)
        } else {
            colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "console_card_border"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (uiState.isRunning) 1.dp else 0.75.dp,
            color = cardBorderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding)
        ) {
            val inputInteractionSource = remember { MutableInteractionSource() }
            val isInputFocused by inputInteractionSource.collectIsFocusedAsState()
            val outerBorderColor by animateColorAsState(
                targetValue = if (isInputFocused) {
                    colorScheme.primary.copy(alpha = 0.75f)
                } else {
                    colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "console_border"
            )

            val consoleBoxModifier = if (consoleHeight != null) {
                Modifier
                    .fillMaxWidth()
                    .height(consoleHeight)
            } else {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 140.dp)
            }

            Surface(
                modifier = consoleBoxModifier,
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    width = if (isInputFocused) 1.dp else 0.5.dp,
                    color = outerBorderColor
                )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val editorScrollState = rememberScrollState()
                    val displayLineCount = remember(uiState.rawInputText) {
                        uiState.rawInputText.count { it == '\n' }.plus(1).coerceIn(1, 500)
                    }
                    val lineNumbersText = remember(displayLineCount) {
                        buildString(displayLineCount * 4) {
                            for (i in 1..displayLineCount) {
                                if (i > 1) append('\n')
                                if (i < 10) append('0')
                                append(i)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(editorScrollState)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 34.dp)
                                    .background(colorScheme.surfaceContainerLow)
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Text(
                                    text = lineNumbersText,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.End,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }

                            BasicTextField(
                                value = uiState.rawInputText,
                                onValueChange = onInputChanged,
                                interactionSource = inputInteractionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 120.dp)
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .testTag("card_batch_input"),
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    letterSpacing = 0.4.sp,
                                    color = colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (uiState.rawInputText.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.input_placeholder),
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                letterSpacing = 0.5.sp,
                                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceContainerLow)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (uiState.inputLineCount > 0) {
                                "${uiState.inputLineCount} queued"
                            } else {
                                "PAN|MM|YYYY|CVV"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ConsoleMiniActionPill(
                                label = stringResource(R.string.import_file_chip),
                                icon = AppIcons.UploadFile,
                                containerColor = colorScheme.surfaceContainerHigh,
                                contentColor = colorScheme.onSurface,
                                onClick = onOpenFileImport,
                                testTag = "open_file_picker_button"
                            )

                            ConsoleMiniActionPill(
                                label = stringResource(R.string.toolkit_chip),
                                icon = AppIcons.CleaningServices,
                                containerColor = colorScheme.surfaceContainerHigh,
                                contentColor = colorScheme.secondary,
                                onClick = onOpenListToolkit,
                                testTag = "open_list_toolkit_button"
                            )

                            ConsoleMiniActionPill(
                                label = "Gen 10x",
                                icon = AppIcons.Bolt,
                                containerColor = colorScheme.surfaceContainerHigh,
                                contentColor = colorScheme.primary,
                                onClick = onQuickGen10x,
                                testTag = "quick_gen_10x_button"
                            )

                            if (uiState.rawInputText.any { it.equals('x', ignoreCase = true) }) {
                                ConsoleMiniActionPill(
                                    label = "Solve x",
                                    icon = AppIcons.AutoFixHigh,
                                    containerColor = colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    contentColor = colorScheme.onPrimaryContainer,
                                    onClick = onExpandWildcards,
                                    testTag = "quick_expand_wildcards_button"
                                )
                            }

                            if (uiState.rawInputText.isEmpty()) {
                                ConsoleMiniActionPill(
                                    label = stringResource(R.string.paste_clipboard),
                                    icon = AppIcons.ContentPaste,
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    contentColor = colorScheme.onSurfaceVariant,
                                    onClick = onPasteOrFill,
                                    testTag = "paste_or_fill_button"
                                )
                            } else {
                                ConsoleMiniActionPill(
                                    label = "Clear",
                                    icon = AppIcons.DeleteOutline,
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    contentColor = colorScheme.error,
                                    onClick = onClearInput,
                                    testTag = "clear_console_button"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val playDescription = if (uiState.isRunning) {
                stringResource(R.string.action_stop_validation)
            } else {
                stringResource(R.string.action_start_validation)
            }
            val splitLeadingColors = if (uiState.isRunning) {
                ButtonDefaults.buttonColors(
                    containerColor = colorScheme.errorContainer,
                    contentColor = colorScheme.onErrorContainer
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            }
            val chevronRotation by animateFloatAsState(
                targetValue = if (engineMenuExpanded) 180f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "split_chevron_rotation"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleRun()
                                },
                                colors = splitLeadingColors,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .widthIn(min = 190.dp)
                                    .semantics { contentDescription = playDescription }
                                    .testTag("play_validation_button")
                            ) {
                                if (uiState.isRunning) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = AppIcons.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.totalBatchCount > 0) {
                                            "Stop (${uiState.processedCount}/${uiState.totalBatchCount})"
                                        } else {
                                            "Stop Batch"
                                        },
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        maxLines = 1
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.inputLineCount > 0) {
                                            "Validate (${uiState.inputLineCount}) • ${uiState.settings.validationMode.badgeLabel}"
                                        } else {
                                            "Validate • ${uiState.settings.validationMode.badgeLabel}"
                                        },
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        maxLines = 1
                                    )
                                }
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(
                                checked = engineMenuExpanded,
                                onCheckedChange = { engineMenuExpanded = it },
                                colors = splitLeadingColors,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("engine_split_dropdown_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Switch validation engine",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer { rotationZ = chevronRotation }
                                )
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = engineMenuExpanded,
                        onDismissRequest = { engineMenuExpanded = false }
                    ) {
                        ValidationMode.entries.forEach { mode ->
                            val isSelected = uiState.settings.validationMode == mode
                            val delayBadge = mode.fixedDelayMs?.let { "${it / 1000L}s delay" } ?: "Custom"
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.badgeLabel,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                trailingIcon = {
                                    Text(
                                        text = delayBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    engineMenuExpanded = false
                                    onQuickSwitchMode(mode)
                                }
                            )
                        }
                    }
                }

                val settingsDescription = stringResource(R.string.action_open_settings)
                FilledTonalIconButton(
                    onClick = onOpenSettings,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurface
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = settingsDescription }
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = settingsDescription,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.isRunning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val workerPacingLabel = uiState.settings.validationMode.fixedDelayMs?.let { fixedMs ->
                            "${uiState.settings.validationMode.badgeLabel} (${fixedMs / 1000L}s delay)"
                        } ?: "${uiState.settings.validationMode.badgeLabel} (${uiState.settings.concurrency}x)"
                        Text(
                            text = workerPacingLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = consoleColors.liveAccent
                        )
                        Text(
                            text = if (uiState.totalBatchCount > 0) {
                                val pct = ((uiState.processedCount * 100f) / uiState.totalBatchCount).toInt().coerceIn(0, 100)
                                "${uiState.processedCount}/${uiState.totalBatchCount} ($pct%)"
                            } else {
                                "Preparing..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (uiState.totalBatchCount > 0) {
                        val progress = (uiState.processedCount.toFloat() / uiState.totalBatchCount.toFloat())
                            .coerceIn(0f, 1f)
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_wavy_progress_indicator"),
                            color = consoleColors.liveAccent,
                            trackColor = colorScheme.surfaceContainerHighest
                        )
                    } else {
                        LinearWavyProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_wavy_progress_indicator"),
                            color = consoleColors.liveAccent,
                            trackColor = colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.statusBannerMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val msg = uiState.statusBannerMessage.orEmpty()
                if (msg.isNotEmpty()) {
                    Surface(
                        onClick = onClearBanner,
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss status",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleMiniActionPill(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .clip(CircleShape)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
