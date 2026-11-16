package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.ValidationMode
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors
import com.example.ui.theme.PlusJakartaSansFamily

/**
 * High-performance 1:1 syntax highlighter for pipe/slash/colon delimited card batches
 * and `email:pass` credential lines without altering character offsets (`OffsetMapping.Identity`).
 */
private class CardBatchVisualTransformation(
    private val panColor: Color,
    private val wildcardColor: Color,
    private val delimiterColor: Color,
    private val dateColor: Color,
    private val cvvColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty() || raw.length > 18_000) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val delimiterStyle = SpanStyle(color = delimiterColor, fontWeight = FontWeight.Bold)
        val wildcardStyle = SpanStyle(color = wildcardColor, fontWeight = FontWeight.Bold)
        val panStyle = SpanStyle(color = panColor, fontWeight = FontWeight.Medium)
        val dateStyle = SpanStyle(color = dateColor)
        val cvvStyle = SpanStyle(color = cvvColor, fontWeight = FontWeight.Medium)

        val annotated = buildAnnotatedString {
            append(raw)
            var fieldIndex = 0
            var segmentStart = 0

            fun styleSegment(start: Int, end: Int, field: Int) {
                if (start >= end) return
                val baseStyle = when (field) {
                    0 -> panStyle
                    1, 2 -> dateStyle
                    else -> cvvStyle
                }
                addStyle(baseStyle, start, end)
                for (idx in start until end) {
                    val c = raw[idx]
                    if (c == 'x' || c == 'X') {
                        addStyle(wildcardStyle, idx, idx + 1)
                    } else if (c == '@') {
                        addStyle(dateStyle, idx, end)
                        break
                    }
                }
            }

            for (i in raw.indices) {
                val ch = raw[i]
                when (ch) {
                    '\n', '\r' -> {
                        styleSegment(segmentStart, i, fieldIndex)
                        fieldIndex = 0
                        segmentStart = i + 1
                    }
                    '|', '/', ':', ';' -> {
                        styleSegment(segmentStart, i, fieldIndex)
                        addStyle(delimiterStyle, i, i + 1)
                        fieldIndex++
                        segmentStart = i + 1
                    }
                }
            }
            styleSegment(segmentStart, raw.length, fieldIndex)
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

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
    onOpenConfigStudio: () -> Unit = {},
    onQuickSwitchMode: (ValidationMode) -> Unit = {},
    onQuickLoadSandbox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    val haptics = LocalHapticFeedback.current
    var engineMenuExpanded by remember { mutableStateOf(false) }

    val inputInteractionSource = remember { MutableInteractionSource() }
    val isInputFocused by inputInteractionSource.collectIsFocusedAsState()

    val hasWildcard = remember(uiState.rawInputText) {
        val sample = if (uiState.rawInputText.length > 4096) {
            uiState.rawInputText.substring(0, 4096)
        } else {
            uiState.rawInputText
        }
        sample.any { it.equals('x', ignoreCase = true) }
    }
    val hasEmailCombo = remember(uiState.rawInputText) {
        val sample = if (uiState.rawInputText.length > 4096) {
            uiState.rawInputText.substring(0, 4096)
        } else {
            uiState.rawInputText
        }
        sample.contains('@') && (sample.contains(':') || sample.contains('|'))
    }

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            uiState.isRunning -> consoleColors.liveAccent.copy(alpha = 0.6f)
            isInputFocused -> colorScheme.primary.copy(alpha = 0.55f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "console_card_border"
    )

    val outerBorderColor by animateColorAsState(
        targetValue = when {
            uiState.isRunning -> consoleColors.liveAccent.copy(alpha = 0.65f)
            isInputFocused -> colorScheme.primary.copy(alpha = 0.85f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.52f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "console_inner_border"
    )

    val innerBorderWidth by animateDpAsState(
        targetValue = if (isInputFocused || uiState.isRunning) 1.25.dp else 0.75.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "console_inner_border_width"
    )

    val editorHeaderColor by animateColorAsState(
        targetValue = when {
            uiState.isRunning -> consoleColors.liveContainer.copy(alpha = 0.34f)
            isInputFocused -> colorScheme.primaryContainer.copy(alpha = 0.30f)
            else -> colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "editor_header_bg"
    )

    val syntaxTransformation = remember(
        colorScheme.onSurface,
        colorScheme.primary,
        colorScheme.tertiary,
        colorScheme.secondary
    ) {
        CardBatchVisualTransformation(
            panColor = colorScheme.onSurface,
            wildcardColor = colorScheme.primary,
            delimiterColor = colorScheme.outline,
            dateColor = colorScheme.secondary,
            cvvColor = colorScheme.tertiary
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surfaceContainer,
        tonalElevation = if (isInputFocused) 2.dp else 0.dp,
        border = BorderStroke(
            width = if (uiState.isRunning || isInputFocused) 1.dp else 0.75.dp,
            color = cardBorderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding)
        ) {
            val consoleBoxModifier = if (consoleHeight != null) {
                Modifier
                    .fillMaxWidth()
                    .height(consoleHeight)
            } else {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 152.dp)
            }

            Surface(
                modifier = consoleBoxModifier,
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    width = innerBorderWidth,
                    color = outerBorderColor
                )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Sleek Integrated Console Status Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(editorHeaderColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            val indicatorDotColor by animateColorAsState(
                                targetValue = when {
                                    uiState.isRunning -> consoleColors.liveAccent
                                    isInputFocused -> colorScheme.primary
                                    uiState.inputLineCount > 0 -> consoleColors.liveAccent.copy(alpha = 0.85f)
                                    else -> colorScheme.outline
                                },
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "console_status_dot"
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(indicatorDotColor)
                            )
                            Text(
                                text = uiState.loadedFileName ?: "BATCH STREAM",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isInputFocused) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                            if (hasWildcard) {
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.primaryContainer.copy(alpha = 0.78f)
                                ) {
                                    Text(
                                        text = "x-MASK",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (hasEmailCombo) {
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.secondaryContainer.copy(alpha = 0.78f)
                                ) {
                                    Text(
                                        text = "EMAIL:PASS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = uiState.inputLineCount,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "console_line_counter"
                        ) { lineCount ->
                            Text(
                                text = if (lineCount > 0) {
                                    "$lineCount queued"
                                } else {
                                    "PAN|MM|YYYY|CVV"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = if (lineCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (lineCount > 0) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

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
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        colorScheme.surfaceContainerLowest,
                                        colorScheme.surfaceContainerLow.copy(alpha = 0.35f)
                                    )
                                )
                            )
                            .verticalScroll(editorScrollState)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 116.dp)
                        ) {
                            // Tactile Line Number Gutter
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 36.dp)
                                    .background(colorScheme.surfaceContainerLow.copy(alpha = 0.65f))
                                    .padding(horizontal = 8.dp, vertical = 11.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Text(
                                    text = lineNumbersText,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.End,
                                    color = if (isInputFocused) {
                                        colorScheme.primary.copy(alpha = 0.72f)
                                    } else {
                                        colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(0.75.dp)
                                    .heightIn(min = 116.dp)
                                    .background(colorScheme.outlineVariant.copy(alpha = 0.3f))
                            )

                            BasicTextField(
                                value = uiState.rawInputText,
                                onValueChange = onInputChanged,
                                interactionSource = inputInteractionSource,
                                visualTransformation = syntaxTransformation,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Default
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 116.dp)
                                    .padding(horizontal = 12.dp, vertical = 11.dp)
                                    .testTag("card_batch_input"),
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    letterSpacing = 0.35.sp,
                                    color = colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (uiState.rawInputText.isEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = stringResource(R.string.input_placeholder),
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 13.sp,
                                                    lineHeight = 20.sp,
                                                    letterSpacing = 0.4.sp,
                                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                                                )
                                                Text(
                                                    text = "Paste pipe/colon rows, x-masks, or tap Gen 10x below",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                                )
                                            }
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = colorScheme.outlineVariant.copy(alpha = 0.42f)
                    )

                    // Bottom Quick-Action Pill Bar
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceContainerLow)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ConsoleMiniActionPill(
                                label = stringResource(R.string.import_file_chip),
                                icon = AppIcons.UploadFile,
                                containerColor = colorScheme.surfaceContainerHigh,
                                contentColor = colorScheme.onSurface,
                                borderColor = colorScheme.outlineVariant.copy(alpha = 0.45f),
                                onClick = onOpenFileImport,
                                testTag = "open_file_picker_button"
                            )

                            ConsoleMiniActionPill(
                                label = stringResource(R.string.toolkit_chip),
                                icon = AppIcons.CleaningServices,
                                containerColor = colorScheme.secondaryContainer.copy(alpha = 0.52f),
                                contentColor = colorScheme.onSecondaryContainer,
                                borderColor = colorScheme.secondary.copy(alpha = 0.3f),
                                onClick = onOpenListToolkit,
                                testTag = "open_list_toolkit_button"
                            )

                            ConsoleMiniActionPill(
                                label = "Gen 10x",
                                icon = AppIcons.Bolt,
                                containerColor = colorScheme.primaryContainer.copy(alpha = 0.58f),
                                contentColor = colorScheme.onPrimaryContainer,
                                borderColor = colorScheme.primary.copy(alpha = 0.32f),
                                onClick = onQuickGen10x,
                                testTag = "quick_gen_10x_button"
                            )

                            ConsoleMiniActionPill(
                                label = stringResource(R.string.config_studio_chip),
                                icon = AppIcons.Tune,
                                containerColor = colorScheme.tertiaryContainer.copy(alpha = 0.56f),
                                contentColor = colorScheme.onTertiaryContainer,
                                borderColor = colorScheme.tertiary.copy(alpha = 0.34f),
                                onClick = onOpenConfigStudio,
                                testTag = "open_config_studio_button"
                            )

                            ConsoleMiniActionPill(
                                label = "Sandbox",
                                icon = AppIcons.CheckCircle,
                                containerColor = colorScheme.tertiaryContainer.copy(alpha = 0.56f),
                                contentColor = colorScheme.onTertiaryContainer,
                                borderColor = colorScheme.tertiary.copy(alpha = 0.32f),
                                onClick = onQuickLoadSandbox,
                                testTag = "quick_load_sandbox_button"
                            )

                            AnimatedVisibility(visible = hasWildcard) {
                                ConsoleMiniActionPill(
                                    label = "Solve x",
                                    icon = AppIcons.AutoFixHigh,
                                    containerColor = colorScheme.primaryContainer,
                                    contentColor = colorScheme.onPrimaryContainer,
                                    borderColor = colorScheme.primary.copy(alpha = 0.45f),
                                    onClick = onExpandWildcards,
                                    testTag = "quick_expand_wildcards_button"
                                )
                            }
                        }

                        if (uiState.rawInputText.isEmpty()) {
                            ConsoleMiniActionPill(
                                label = stringResource(R.string.paste_clipboard),
                                icon = AppIcons.ContentPaste,
                                containerColor = colorScheme.surfaceContainerHigh,
                                contentColor = colorScheme.onSurfaceVariant,
                                borderColor = colorScheme.outlineVariant.copy(alpha = 0.45f),
                                onClick = onPasteOrFill,
                                testTag = "paste_or_fill_button"
                            )
                        } else {
                            ConsoleMiniActionPill(
                                label = "Clear",
                                icon = AppIcons.DeleteOutline,
                                containerColor = colorScheme.errorContainer.copy(alpha = 0.48f),
                                contentColor = colorScheme.error,
                                borderColor = colorScheme.error.copy(alpha = 0.28f),
                                onClick = onClearInput,
                                testTag = "clear_console_button"
                            )
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
            val leadingContainerColor by animateColorAsState(
                targetValue = if (uiState.isRunning) colorScheme.errorContainer else colorScheme.primary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "split_leading_bg"
            )
            val leadingContentColor by animateColorAsState(
                targetValue = if (uiState.isRunning) colorScheme.onErrorContainer else colorScheme.onPrimary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "split_leading_fg"
            )
            val splitLeadingColors = ButtonDefaults.buttonColors(
                containerColor = leadingContainerColor,
                contentColor = leadingContentColor
            )
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
                                        color = leadingContentColor
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
                                        fontFamily = PlusJakartaSansFamily,
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
                                        fontFamily = PlusJakartaSansFamily,
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
                            val delayBadge = mode.fixedDelayMs?.let { "${it / 1000L}s pace" } ?: "Custom"
                            val modeIcon = when (mode) {
                                ValidationMode.LOCAL_DETERMINISTIC -> AppIcons.Memory
                                ValidationMode.SANDBOX_SUITE -> AppIcons.CheckCircle
                                ValidationMode.MULTI_API_AUTO -> AppIcons.SyncAlt
                                ValidationMode.CHKR_CC -> AppIcons.Public
                                ValidationMode.PAYATE_MOCK -> AppIcons.Api
                                ValidationMode.REMOTE_WEBHOOK -> AppIcons.CloudSync
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.badgeLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Outlined.Check else modeIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    Text(
                                        text = delayBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
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
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium))
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
                            fontWeight = FontWeight.SemiBold,
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
                        val rawProgress = (uiState.processedCount.toFloat() / uiState.totalBatchCount.toFloat())
                            .coerceIn(0f, 1f)
                        val animatedProgress by animateFloatAsState(
                            targetValue = rawProgress,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "batch_smooth_progress"
                        )
                        LinearWavyProgressIndicator(
                            progress = { animatedProgress },
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
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium))
            ) {
                val msg = uiState.statusBannerMessage.orEmpty()
                if (msg.isNotEmpty()) {
                    Surface(
                        onClick = onClearBanner,
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.surfaceContainerHigh,
                        border = BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
    borderColor: Color = Color.Transparent,
    onClick: () -> Unit,
    testTag: String
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = CircleShape,
        color = containerColor,
        border = if (borderColor != Color.Transparent) BorderStroke(0.75.dp, borderColor) else null,
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = label
            }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
