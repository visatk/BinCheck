package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.CardScheme
import com.example.domain.ValidationMode
import com.example.ui.theme.ActionButtonBorder
import com.example.ui.theme.ActionButtonHoverSurface
import com.example.ui.theme.ActionButtonSurface
import com.example.ui.theme.CardContainerBorder
import com.example.ui.theme.CardContainerSurface
import com.example.ui.theme.CardSpecularHighlight
import com.example.ui.theme.GearRoyalBlue
import com.example.ui.theme.InputConsoleBorderInner
import com.example.ui.theme.InputConsoleBorderOuter
import com.example.ui.theme.InputConsoleGutterSurface
import com.example.ui.theme.InputConsoleSurface
import com.example.ui.theme.InputPlaceholderColor
import com.example.ui.theme.InputTextColor
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlayEmerald
import com.example.ui.theme.PlayEmeraldBadgeBg
import com.example.ui.theme.TelegramSkyBlue
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopTelemetryStrip(
    isRunning: Boolean,
    validationMode: ValidationMode,
    concurrency: Int,
    binDigits: String,
    detectedScheme: CardScheme,
    loadedFileName: String?,
    historyCount: Int,
    onOpenHistory: () -> Unit,
    onOpenDeveloperChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(CardContainerSurface)
                    .border(1.dp, CardContainerBorder, RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) PlayEmerald else GearRoyalBlue)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (validationMode == ValidationMode.LOCAL_DETERMINISTIC) {
                            "LUHN • ${concurrency}x"
                        } else {
                            "WEBHOOK • ${concurrency}x"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryMuted
                    )
                }
            }

            if (detectedScheme != CardScheme.UNKNOWN) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(PlayEmeraldBadgeBg.copy(alpha = 0.7f))
                        .border(1.dp, PlayEmerald.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BIN $binDigits • ${detectedScheme.displayName}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = PlayEmerald
                    )
                }
            }

            if (!loadedFileName.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(GearRoyalBlue.copy(alpha = 0.14f))
                        .border(1.dp, GearRoyalBlue.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = GearRoyalBlue,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = loadedFileName,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = TextPrimaryLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Interactive Local History Log Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(CardContainerSurface)
                    .border(1.dp, CardContainerBorder, RoundedCornerShape(5.dp))
                    .clickable(onClick = onOpenHistory)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("open_history_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = stringResource(R.string.history_chip),
                        tint = TextPrimaryLight,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${stringResource(R.string.history_chip)} ($historyCount)",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryLight
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(TelegramSkyBlue.copy(alpha = 0.14f))
                    .border(1.dp, TelegramSkyBlue.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                    .clickable(onClick = onOpenDeveloperChannel)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("header_developer_channel_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TelegramPaperPlaneIcon(
                        color = TelegramSkyBlue,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.developer_channel_handle),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TelegramSkyBlue
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsoleInputCard(
    uiState: ValidatorUiState,
    copiedFeedback: String?,
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(6.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CardSpecularHighlight.copy(alpha = 0.7f),
                        CardContainerBorder.copy(alpha = 0.45f)
                    )
                ),
                shape = RoundedCornerShape(6.dp)
            ),
        color = CardContainerSurface,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding)
        ) {
            val inputInteractionSource = remember { MutableInteractionSource() }
            val isInputFocused by inputInteractionSource.collectIsFocusedAsState()
            val outerBorderColor by animateColorAsState(
                targetValue = if (isInputFocused) PlayEmerald.copy(alpha = 0.55f) else InputConsoleBorderOuter,
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

            // Recessed Dark Console Input Box with Inner Shadow & Line Number Gutter
            Box(
                modifier = consoleBoxModifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(InputConsoleSurface)
                    .border(
                        width = 1.dp,
                        color = outerBorderColor,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(1.dp)
                    .border(
                        width = 1.dp,
                        color = InputConsoleBorderInner,
                        shape = RoundedCornerShape(3.dp)
                    )
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = 18.dp.toPx()
                            )
                        )
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val displayLineCount = remember(uiState.rawInputText) {
                            uiState.rawInputText.lines().size.coerceIn(1, 14)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(InputConsoleGutterSurface)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (lineNum in 1..displayLineCount) {
                                Text(
                                    text = lineNum.toString().padStart(2, '0'),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 20.sp,
                                    color = InputPlaceholderColor.copy(alpha = 0.55f)
                                )
                            }
                        }

                        VerticalDivider(
                            thickness = 1.dp,
                            color = Color(0xFF1A1E26)
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
                                color = InputTextColor
                            ),
                            cursorBrush = SolidColor(PlayEmerald),
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
                                            color = InputPlaceholderColor
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // Responsive Bottom Console Quick-Action Bar with File Picker Trigger
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFF181B22))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(InputConsoleGutterSurface.copy(alpha = 0.9f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (uiState.inputLineCount > 0) {
                                "${uiState.inputLineCount} line(s) queued"
                            } else {
                                "PAN|MM|YYYY|CVV or .txt/.html"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = InputPlaceholderColor,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ConsoleMiniActionPill(
                                label = stringResource(R.string.import_file_chip),
                                icon = Icons.Outlined.UploadFile,
                                tint = GearRoyalBlue,
                                onClick = onOpenFileImport,
                                testTag = "open_file_picker_button"
                            )

                            ConsoleMiniActionPill(
                                label = "Gen 10x",
                                icon = Icons.Outlined.Bolt,
                                tint = PlayEmerald,
                                onClick = onQuickGen10x,
                                testTag = "quick_gen_10x_button"
                            )

                            if (uiState.rawInputText.any { it.equals('x', ignoreCase = true) }) {
                                ConsoleMiniActionPill(
                                    label = "Solve x",
                                    icon = Icons.Outlined.AutoFixHigh,
                                    tint = PlayEmerald,
                                    onClick = onExpandWildcards,
                                    testTag = "quick_expand_wildcards_button"
                                )
                            }

                            if (uiState.rawInputText.isEmpty()) {
                                ConsoleMiniActionPill(
                                    label = "Paste",
                                    icon = Icons.Outlined.ContentPaste,
                                    tint = TextSecondaryMuted,
                                    onClick = onPasteOrFill,
                                    testTag = "paste_or_fill_button"
                                )
                            } else {
                                ConsoleMiniActionPill(
                                    label = "Clear",
                                    icon = Icons.Outlined.DeleteOutline,
                                    tint = TextSecondaryMuted,
                                    onClick = onClearInput,
                                    testTag = "clear_console_button"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stacked Dual Action Bars (Play & Settings)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(4.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(ActionButtonSurface)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                CardSpecularHighlight.copy(alpha = 0.5f),
                                ActionButtonBorder
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                val playInteraction = remember { MutableInteractionSource() }
                val isPlayPressed by playInteraction.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (isPlayPressed) 0.98f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "play_scale"
                )
                val playDescription = if (uiState.isRunning) {
                    stringResource(R.string.action_stop_validation)
                } else {
                    stringResource(R.string.action_start_validation)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .background(if (isPlayPressed) ActionButtonHoverSurface else ActionButtonSurface)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
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
                                .size(28.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = PlayEmerald.copy(alpha = 0.12f),
                                        radius = size.minDimension * 0.52f
                                    )
                                }
                        ) {
                            OutlinedPlayOrStopGlyph(
                                isRunning = uiState.isRunning,
                                color = PlayEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (uiState.isRunning && uiState.totalBatchCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${uiState.processedCount}/${uiState.totalBatchCount}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PlayEmerald
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
                                .height(2.dp)
                                .align(Alignment.BottomCenter),
                            color = PlayEmerald,
                            trackColor = Color.Transparent
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFF13151A)
                )

                val gearInteraction = remember { MutableInteractionSource() }
                val isGearPressed by gearInteraction.collectIsPressedAsState()
                val gearScale by animateFloatAsState(
                    targetValue = if (isGearPressed) 0.98f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "gear_scale"
                )
                val settingsDescription = stringResource(R.string.action_open_settings)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .background(if (isGearPressed) ActionButtonHoverSurface else ActionButtonSurface)
                        .clickable(
                            interactionSource = gearInteraction,
                            indication = null,
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
                            .size(28.dp)
                            .drawBehind {
                                drawCircle(
                                    color = GearRoyalBlue.copy(alpha = 0.12f),
                                    radius = size.minDimension * 0.52f
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = GearRoyalBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = (copiedFeedback != null || uiState.statusBannerMessage != null),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val msg = copiedFeedback ?: uiState.statusBannerMessage.orEmpty()
                if (msg.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(InputConsoleSurface)
                            .border(1.dp, ActionButtonBorder, RoundedCornerShape(4.dp))
                            .clickable(onClick = onClearBanner)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            fontFamily = InterFontFamily,
                            fontSize = 11.5.sp,
                            color = TextSecondaryMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperChannelFooter(
    channelUrl: String,
    onOpenChannel: () -> Unit,
    onCopyChannelUrl: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CardContainerSurface.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = CardContainerBorder.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onOpenChannel)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("footer_developer_channel_card"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(TelegramSkyBlue.copy(alpha = 0.16f))
                    .border(1.dp, TelegramSkyBlue.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                TelegramPaperPlaneIcon(
                    color = TelegramSkyBlue,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Developer Channel • @drkingbd",
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channelUrl,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.5.sp,
                    color = TelegramSkyBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onCopyChannelUrl)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy channel URL",
                    tint = TextSecondaryMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open Telegram Channel",
                tint = TelegramSkyBlue,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ConsoleMiniActionPill(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(CardContainerSurface)
            .border(1.dp, ActionButtonBorder, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.5.dp))
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = tint
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
        val strokeWidth = 1.75.dp.toPx()
        if (isRunning) {
            val side = size.minDimension * 0.68f
            val offset = (size.minDimension - side) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(offset, offset),
                size = Size(side, side),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
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

@Composable
fun TelegramPaperPlaneIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.48f)
            lineTo(w * 0.92f, h * 0.14f)
            lineTo(w * 0.74f, h * 0.88f)
            lineTo(w * 0.46f, h * 0.64f)
            lineTo(w * 0.32f, h * 0.78f)
            lineTo(w * 0.35f, h * 0.56f)
            lineTo(w * 0.76f, h * 0.26f)
            lineTo(w * 0.28f, h * 0.53f)
            close()
        }
        drawPath(
            path = path,
            color = color
        )
    }
}
