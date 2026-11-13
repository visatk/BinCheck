package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
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
                    colorScheme.primary.copy(alpha = 0.78f)
                } else {
                    colorScheme.outlineVariant
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
                    .heightIn(min = 150.dp)
            }

            // Recessed IDE Console Editor using M3 surfaceContainerLowest
            Surface(
                modifier = consoleBoxModifier,
                shape = MaterialTheme.shapes.small,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    width = if (isInputFocused) 1.5.dp else 1.dp,
                    color = outerBorderColor
                ),
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val editorScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .height(IntrinsicSize.Min)
                                .verticalScroll(editorScrollState)
                        ) {
                            val displayLineCount = remember(uiState.rawInputText) {
                                uiState.rawInputText.lines().size.coerceIn(1, 500)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(colorScheme.surfaceContainer)
                                    .padding(horizontal = 9.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                for (lineNum in 1..displayLineCount) {
                                    Text(
                                        text = lineNum.toString().padStart(2, '0'),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 20.sp,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            VerticalDivider(
                                thickness = 1.dp,
                                color = colorScheme.outlineVariant.copy(alpha = 0.65f)
                            )

                            BasicTextField(
                                value = uiState.rawInputText,
                                onValueChange = onInputChanged,
                                interactionSource = inputInteractionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
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
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (uiState.rawInputText.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.input_placeholder),
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                letterSpacing = 0.5.sp,
                                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceContainer.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (uiState.inputLineCount > 0) {
                                "${uiState.inputLineCount} line(s) queued"
                            } else {
                                "PAN|MM|YYYY|CVV or .txt/.html"
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
                                icon = Icons.Outlined.UploadFile,
                                containerColor = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer,
                                onClick = onOpenFileImport,
                                testTag = "open_file_picker_button"
                            )

                            ConsoleMiniActionPill(
                                label = "Gen 10x",
                                icon = Icons.Outlined.Bolt,
                                containerColor = colorScheme.primaryContainer,
                                contentColor = colorScheme.onPrimaryContainer,
                                onClick = onQuickGen10x,
                                testTag = "quick_gen_10x_button"
                            )

                            if (uiState.rawInputText.any { it.equals('x', ignoreCase = true) }) {
                                ConsoleMiniActionPill(
                                    label = "Solve x",
                                    icon = Icons.Outlined.AutoFixHigh,
                                    containerColor = colorScheme.tertiaryContainer,
                                    contentColor = colorScheme.onTertiaryContainer,
                                    onClick = onExpandWildcards,
                                    testTag = "quick_expand_wildcards_button"
                                )
                            }

                            if (uiState.rawInputText.isEmpty()) {
                                ConsoleMiniActionPill(
                                    label = stringResource(R.string.paste_clipboard),
                                    icon = Icons.Outlined.ContentPaste,
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    contentColor = colorScheme.onSurfaceVariant,
                                    onClick = onPasteOrFill,
                                    testTag = "paste_or_fill_button"
                                )
                            } else {
                                ConsoleMiniActionPill(
                                    label = "Clear",
                                    icon = Icons.Outlined.DeleteOutline,
                                    containerColor = colorScheme.errorContainer.copy(alpha = 0.7f),
                                    contentColor = colorScheme.onErrorContainer,
                                    onClick = onClearInput,
                                    testTag = "clear_console_button"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stacked Dual M3 Action Bars (Play/Stop & Settings)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val playInteraction = remember { MutableInteractionSource() }
                    val isPlayPressed by playInteraction.collectIsPressedAsState()
                    val playScale by animateFloatAsState(
                        targetValue = if (isPlayPressed) 0.97f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "play_scale"
                    )
                    val playDescription = if (uiState.isRunning) {
                        stringResource(R.string.action_stop_validation)
                    } else {
                        stringResource(R.string.action_start_validation)
                    }
                    val playAccent = if (uiState.isRunning) consoleColors.liveAccent else colorScheme.primary

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .background(
                                if (uiState.isRunning) {
                                    colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable(
                                interactionSource = playInteraction,
                                indication = androidx.compose.material3.ripple(color = playAccent),
                                onClick = onToggleRun
                            )
                            .semantics { contentDescription = playDescription }
                            .testTag("play_validation_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.scale(playScale),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .drawBehind {
                                        drawCircle(
                                            color = playAccent.copy(alpha = 0.16f),
                                            radius = size.minDimension * 0.52f
                                        )
                                    }
                            ) {
                                OutlinedPlayOrStopGlyph(
                                    isRunning = uiState.isRunning,
                                    color = playAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            if (uiState.isRunning && uiState.totalBatchCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${uiState.processedCount}/${uiState.totalBatchCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = playAccent
                                )
                            }
                        }

                        if (uiState.isRunning && uiState.totalBatchCount > 0) {
                            val progress = (uiState.processedCount.toFloat() / uiState.totalBatchCount.toFloat())
                                .coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .align(Alignment.BottomCenter),
                                color = playAccent,
                                trackColor = colorScheme.surfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = colorScheme.outlineVariant.copy(alpha = 0.85f)
                    )

                    val gearInteraction = remember { MutableInteractionSource() }
                    val isGearPressed by gearInteraction.collectIsPressedAsState()
                    val gearScale by animateFloatAsState(
                        targetValue = if (isGearPressed) 0.97f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "gear_scale"
                    )
                    val settingsDescription = stringResource(R.string.action_open_settings)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .clickable(
                                interactionSource = gearInteraction,
                                indication = androidx.compose.material3.ripple(color = colorScheme.secondary),
                                onClick = onOpenSettings
                            )
                            .semantics { contentDescription = settingsDescription }
                            .testTag("settings_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .scale(gearScale)
                                .size(30.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = colorScheme.secondary.copy(alpha = 0.16f),
                                        radius = size.minDimension * 0.52f
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = colorScheme.secondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.statusBannerMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val msg = uiState.statusBannerMessage.orEmpty()
                if (msg.isNotEmpty()) {
                    Surface(
                        onClick = onClearBanner,
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
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
                                color = colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss status",
                                tint = colorScheme.onSecondaryContainer,
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
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun OutlinedPlayOrStopGlyph(
    isRunning: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.9.dp.toPx()
        if (isRunning) {
            val side = size.minDimension * 0.68f
            val offset = (size.minDimension - side) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(offset, offset),
                size = Size(side, side),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
        } else {
            val path = Path().apply {
                val leftX = size.width * 0.22f
                val rightX = size.width * 0.84f
                val topY = size.height * 0.14f
                val bottomY = size.height * 0.86f
                val midY = size.height * 0.50f

                moveTo(leftX, topY)
                lineTo(rightX, midY)
                lineTo(leftX, bottomY)
                close()
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
