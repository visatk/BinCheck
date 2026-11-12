package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.CardScheme
import com.example.domain.CardValidatorEngine
import com.example.domain.ResultBucket
import com.example.domain.ValidationMode
import com.example.domain.ValidationRecord
import com.example.ui.theme.ActionButtonBorder
import com.example.ui.theme.ActionButtonHoverSurface
import com.example.ui.theme.ActionButtonSurface
import com.example.ui.theme.BadgeNeutralBg
import com.example.ui.theme.CardContainerBorder
import com.example.ui.theme.CardContainerSurface
import com.example.ui.theme.CardSpecularHighlight
import com.example.ui.theme.ConsoleBackground
import com.example.ui.theme.ConsoleBackgroundBottom
import com.example.ui.theme.DieCrimson
import com.example.ui.theme.DieCrimsonBadgeBg
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
import com.example.ui.theme.TabActiveSurface
import com.example.ui.theme.TabInactiveSurface
import com.example.ui.theme.TelegramSkyBlue
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.TopDividerColor
import com.example.ui.theme.UnknownAmber
import com.example.ui.theme.UnknownAmberBadgeBg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ValidatorScreen(
    viewModel: ValidatorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copiedFeedback by remember { mutableStateOf<String?>(null) }

    val channelUrl = stringResource(R.string.developer_channel_url)

    // Detect scheme from first line of input or placeholder for real-time telemetry chip
    val detectedPreviewInfo by remember(uiState.rawInputText) {
        derivedStateOf {
            val sampleLine = uiState.rawInputText.lines().firstOrNull { it.isNotBlank() }
                ?: "453225005536xxx3|08|2028|113"
            val panToken = sampleLine.split('|', '/', ':').firstOrNull()?.trim().orEmpty()
            val binPrefix = panToken.filter { it.isDigit() }.take(6)
            val scheme = CardValidatorEngine.detectScheme(panToken)
            Pair(binPrefix.ifEmpty { "453225" }, scheme)
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        copiedFeedback = "Copied to clipboard"
        scope.launch {
            delay(1800)
            copiedFeedback = null
        }
    }

    fun pasteFromClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        return item?.coerceToText(context)?.toString()
    }

    fun openDeveloperChannel() {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    if (uiState.isSettingsOpen) {
        SettingsBottomSheet(
            settings = uiState.settings,
            onDismiss = viewModel::closeSettings,
            onSaveSettings = viewModel::saveSettings,
            onGenerateBatchToConsole = viewModel::generateBatchIntoConsole,
            onClearAllBuckets = viewModel::clearAllResults
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ConsoleBackground, ConsoleBackgroundBottom)
                )
            )
            // Subtle atmospheric ambient lighting behind the dark console cards
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PlayEmerald.copy(alpha = 0.055f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.25f, size.height * 0.18f),
                        radius = size.minDimension * 0.65f
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.18f),
                    radius = size.minDimension * 0.65f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GearRoyalBlue.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.78f, size.height * 0.55f),
                        radius = size.minDimension * 0.65f
                    ),
                    center = Offset(size.width * 0.78f, size.height * 0.55f),
                    radius = size.minDimension * 0.65f
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subtle top full-width hairline divider matching the screenshot header line
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = TopDividerColor
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Compact Top Telemetry & Developer Channel Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Engine Mode & Live BIN Scheme Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                        .background(if (uiState.isRunning) PlayEmerald else GearRoyalBlue)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.settings.validationMode == ValidationMode.LOCAL_DETERMINISTIC) {
                                        "LUHN MOD-10"
                                    } else {
                                        "WEBHOOK API"
                                    },
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondaryMuted
                                )
                            }
                        }

                        val (binDigits, scheme) = detectedPreviewInfo
                        if (scheme != CardScheme.UNKNOWN) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(PlayEmeraldBadgeBg.copy(alpha = 0.7f))
                                    .border(1.dp, PlayEmerald.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "BIN $binDigits • ${scheme.displayName}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PlayEmerald
                                )
                            }
                        }
                    }

                    // Interactive Developer Channel Pill (@drkingbd -> https://t.me/drkingbd)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(TelegramSkyBlue.copy(alpha = 0.14f))
                            .border(1.dp, TelegramSkyBlue.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                            .clickable(onClick = ::openDeveloperChannel)
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

                // Upper Main Console Card (Matching the exact screenshot geometry + realistic depth)
                Surface(
                    modifier = Modifier
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
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        val inputInteractionSource = remember { MutableInteractionSource() }
                        val isInputFocused by inputInteractionSource.collectIsFocusedAsState()
                        val outerBorderColor by animateColorAsState(
                            targetValue = if (isInputFocused) PlayEmerald.copy(alpha = 0.55f) else InputConsoleBorderOuter,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "console_border"
                        )

                        // Recessed Dark Console Input Box with Inner Shadow & Line Number Gutter
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
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
                                // Tactile top & left inner shadow for recessed terminal depth
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
                                    // Subtle IDE Line Number Gutter
                                    val displayLineCount = remember(uiState.rawInputText) {
                                        val count = uiState.rawInputText.lines().size
                                        count.coerceIn(1, 12)
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

                                    // Monospace Batch Input Area
                                    BasicTextField(
                                        value = uiState.rawInputText,
                                        onValueChange = viewModel::onInputTextChanged,
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

                                // Subtle Bottom Console Quick-Action Bar
                                HorizontalDivider(thickness = 1.dp, color = Color(0xFF181B22))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(InputConsoleGutterSurface.copy(alpha = 0.9f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.inputLineCount > 0) {
                                            "${uiState.inputLineCount} line(s) queued"
                                        } else {
                                            "Format: PAN|MM|YYYY|CVV"
                                        },
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = InputPlaceholderColor
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1-Tap Generate 10x Valid Luhn Batch from Pattern
                                        ConsoleMiniActionPill(
                                            label = "Gen 10x",
                                            icon = Icons.Outlined.Bolt,
                                            tint = PlayEmerald,
                                            onClick = {
                                                val patternToUse = uiState.rawInputText.lines()
                                                    .firstOrNull { it.isNotBlank() }
                                                    ?: uiState.settings.patternTemplate
                                                viewModel.generateBatchIntoConsole(patternToUse, 10)
                                            },
                                            testTag = "quick_gen_10x_button"
                                        )

                                        if (uiState.rawInputText.any { it.equals('x', ignoreCase = true) }) {
                                            ConsoleMiniActionPill(
                                                label = "Solve x",
                                                icon = Icons.Outlined.AutoFixHigh,
                                                tint = PlayEmerald,
                                                onClick = viewModel::expandCurrentConsoleWildcards,
                                                testTag = "quick_expand_wildcards_button"
                                            )
                                        }

                                        if (uiState.rawInputText.isEmpty()) {
                                            ConsoleMiniActionPill(
                                                label = "Paste",
                                                icon = Icons.Outlined.ContentPaste,
                                                tint = TextSecondaryMuted,
                                                onClick = {
                                                    val pasted = pasteFromClipboard()
                                                    if (!pasted.isNullOrBlank()) {
                                                        viewModel.onInputTextChanged(pasted)
                                                    } else {
                                                        viewModel.expandCurrentConsoleWildcards()
                                                    }
                                                },
                                                testTag = "paste_or_fill_button"
                                            )
                                        } else {
                                            ConsoleMiniActionPill(
                                                label = "Clear",
                                                icon = Icons.Outlined.DeleteOutline,
                                                tint = TextSecondaryMuted,
                                                onClick = { viewModel.onInputTextChanged("") },
                                                testTag = "clear_console_button"
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stacked Dual Action Bars (Play & Settings) matching screenshot with tactile depth
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
                            // First Bar: Outlined Green Play / Stop Button
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
                                        onClick = viewModel::toggleValidationRun
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

                            // Crisp recessed divider between the two stacked buttons
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFF13151A)
                            )

                            // Second Bar: Outlined Royal Blue Settings Gear Button
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
                                        onClick = viewModel::openSettings
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

                        // Status / Feedback Banner
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
                                        .clickable { viewModel.clearStatusBanner() }
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

                Spacer(modifier = Modifier.height(24.dp))

                // Lower Results Card (Live 0 | Die 0 | Unknown 0) with 3D Sculpted Tabs
                Surface(
                    modifier = Modifier
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
                                    CardSpecularHighlight.copy(alpha = 0.65f),
                                    CardContainerBorder.copy(alpha = 0.45f)
                                )
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    color = CardContainerSurface,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 3-Tab Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(TabInactiveSurface),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ResultTabItem(
                                label = stringResource(R.string.tab_live),
                                count = uiState.liveRecords.size,
                                selected = uiState.selectedTab == ResultBucket.LIVE,
                                labelColor = PlayEmerald,
                                badgeBackgroundColor = PlayEmeraldBadgeBg,
                                badgeTextColor = PlayEmerald,
                                topAccentColor = PlayEmerald,
                                onClick = { viewModel.selectTab(ResultBucket.LIVE) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("tab_live")
                            )

                            ResultTabItem(
                                label = stringResource(R.string.tab_die),
                                count = uiState.dieRecords.size,
                                selected = uiState.selectedTab == ResultBucket.DIE,
                                labelColor = TextPrimaryLight,
                                badgeBackgroundColor = if (uiState.dieRecords.isNotEmpty()) DieCrimsonBadgeBg else BadgeNeutralBg,
                                badgeTextColor = if (uiState.dieRecords.isNotEmpty()) DieCrimson else TextPrimaryLight,
                                topAccentColor = DieCrimson,
                                onClick = { viewModel.selectTab(ResultBucket.DIE) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("tab_die")
                            )

                            ResultTabItem(
                                label = stringResource(R.string.tab_unknown),
                                count = uiState.unknownRecords.size,
                                selected = uiState.selectedTab == ResultBucket.UNKNOWN,
                                labelColor = TextPrimaryLight,
                                badgeBackgroundColor = if (uiState.unknownRecords.isNotEmpty()) UnknownAmberBadgeBg else BadgeNeutralBg,
                                badgeTextColor = if (uiState.unknownRecords.isNotEmpty()) UnknownAmber else TextPrimaryLight,
                                topAccentColor = UnknownAmber,
                                onClick = { viewModel.selectTab(ResultBucket.UNKNOWN) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("tab_unknown")
                            )
                        }

                        // Subtle horizontal shadow separator under tab header
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFF13151A)
                        )

                        // Results Body
                        val activeList = uiState.activeTabRecords
                        if (activeList.isEmpty()) {
                            // Matches the sleek dark lower section of the screenshot when count is 0
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(ActionButtonSurface)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = when (uiState.selectedTab) {
                                        ResultBucket.LIVE -> "No Live records yet — press ▷ to validate batch"
                                        ResultBucket.DIE -> "No declined / invalid records"
                                        ResultBucket.UNKNOWN -> "No unknown format records"
                                    },
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = InputPlaceholderColor.copy(alpha = 0.75f)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ActionButtonSurface)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                // Action bar for non-empty tab
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tabTitle = when (uiState.selectedTab) {
                                        ResultBucket.LIVE -> "LIVE RECORDS (${activeList.size})"
                                        ResultBucket.DIE -> "DIE RECORDS (${activeList.size})"
                                        ResultBucket.UNKNOWN -> "UNKNOWN RECORDS (${activeList.size})"
                                    }
                                    Text(
                                        text = tabTitle,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondaryMuted
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(InputConsoleSurface)
                                                .border(1.dp, ActionButtonBorder, RoundedCornerShape(4.dp))
                                                .clickable {
                                                    val joined = activeList.joinToString("\n") { it.normalizedLine }
                                                    copyToClipboard(tabTitle, joined)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("copy_all_records_button")
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCopy,
                                                    contentDescription = stringResource(R.string.copy_all),
                                                    tint = PlayEmerald,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.copy_all),
                                                    fontSize = 11.sp,
                                                    color = TextPrimaryLight
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(InputConsoleSurface)
                                                .border(1.dp, ActionButtonBorder, RoundedCornerShape(4.dp))
                                                .clickable(onClick = viewModel::clearActiveTabRecords)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("clear_tab_records_button")
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                    contentDescription = stringResource(R.string.clear_tab),
                                                    tint = DieCrimson,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.clear_tab),
                                                    fontSize = 11.sp,
                                                    color = TextSecondaryMuted
                                                )
                                            }
                                        }
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(
                                        items = activeList,
                                        key = { it.id }
                                    ) { record ->
                                        ValidationRecordRow(
                                            record = record,
                                            onCopy = {
                                                copyToClipboard("Record", record.normalizedLine)
                                            },
                                            onRemove = {
                                                viewModel.removeRecord(record.id, record.bucket)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sleek Developer Channel Footer Card (https://t.me/drkingbd)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardContainerSurface.copy(alpha = 0.85f))
                        .border(
                            width = 1.dp,
                            color = CardContainerBorder.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable(onClick = ::openDeveloperChannel)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("footer_developer_channel_card"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                color = TextPrimaryLight
                            )
                            Text(
                                text = channelUrl,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.5.sp,
                                color = TelegramSkyBlue
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy channel URL",
                            tint = TextSecondaryMuted,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    copyToClipboard("Developer Channel", channelUrl)
                                }
                                .padding(5.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = "Open Telegram Channel",
                            tint = TelegramSkyBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ConsoleMiniActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            .padding(horizontal = 6.5.dp, vertical = 2.5.dp)
            .testTag(testTag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.5.dp))
            Text(
                text = label,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = tint
            )
        }
    }
}

@Composable
private fun ResultTabItem(
    label: String,
    count: Int,
    selected: Boolean,
    labelColor: Color,
    badgeBackgroundColor: Color,
    badgeTextColor: Color,
    topAccentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerModifier = if (selected) {
        modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(3.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .background(TabActiveSurface)
            .border(
                width = 1.dp,
                color = Color(0xFF2B303C),
                shape = RoundedCornerShape(3.dp)
            )
            .drawWithContent {
                drawContent()
                // Subtle top luminous accent bar on the raised active tab
                drawRect(
                    color = topAccentColor.copy(alpha = 0.85f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, 2.dp.toPx())
                )
            }
    } else {
        modifier.background(TabInactiveSurface)
    }

    Box(
        modifier = containerModifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.5.sp,
                color = labelColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBackgroundColor)
                    .padding(horizontal = 6.5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "badge_count_$label"
                ) { targetCount ->
                    Text(
                        text = targetCount.toString(),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = badgeTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationRecordRow(
    record: ValidationRecord,
    onCopy: () -> Unit,
    onRemove: () -> Unit
) {
    val accentColor = when (record.bucket) {
        ResultBucket.LIVE -> PlayEmerald
        ResultBucket.DIE -> DieCrimson
        ResultBucket.UNKNOWN -> UnknownAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(5.dp))
            .background(InputConsoleSurface)
            .border(1.dp, ActionButtonBorder, RoundedCornerShape(5.dp))
            .clickable(onClick = onCopy)
            .testTag("record_row_${record.pan}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left status indicator stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.normalizedLine,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimaryLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = record.scheme.displayName,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                    Text(
                        text = "${record.message} (${record.latencyMs}ms)",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        color = TextSecondaryMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy line",
                    tint = TextSecondaryMuted,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onCopy)
                        .padding(6.dp)
                )
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove line",
                    tint = TextSecondaryMuted,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onRemove)
                        .padding(6.dp)
                )
            }
        }
    }
}

/**
 * Custom Canvas-drawn crisp outlined Play triangle (or Stop square when active)
 * matching the screenshot's emerald outlined play icon.
 */
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

/**
 * Custom Canvas-drawn Telegram paper-plane vector glyph for the @drkingbd Developer Channel badge.
 */
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
