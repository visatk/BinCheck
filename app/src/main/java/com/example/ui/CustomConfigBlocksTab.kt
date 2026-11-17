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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.BlockCategory
import com.example.domain.BlockPipelineResult
import com.example.domain.BlockStepStatus
import com.example.domain.BlockType
import com.example.domain.CardValidatorEngine
import com.example.domain.ConfigBlock
import com.example.domain.ConfigBlockExecutor
import com.example.domain.ConfigBlockSerialization
import com.example.domain.CustomConfigStudioEngine
import com.example.domain.CustomHttpConfig
import com.example.domain.ResultBucket
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors
import com.example.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
private fun blockCategoryColor(category: BlockCategory): Color {
    val consoleColors = LocalConsoleColors.current
    val cs = MaterialTheme.colorScheme
    return when (category) {
        BlockCategory.BROWSER -> cs.primary
        BlockCategory.CAPTCHAS -> consoleColors.unknownAccent
        BlockCategory.CONDITIONS -> consoleColors.liveAccent
        BlockCategory.FUNCTIONS -> consoleColors.telegramAccent
        BlockCategory.INTEROP -> cs.tertiary
        BlockCategory.PARSING -> cs.secondary
        BlockCategory.REQUESTS -> consoleColors.liveAccent
        BlockCategory.SELENIUM -> consoleColors.unknownAccent
        BlockCategory.UTILITY -> cs.onSurfaceVariant
    }
}

@Composable
private fun blockCategoryIcon(category: BlockCategory): ImageVector = when (category) {
    BlockCategory.BROWSER -> AppIcons.Public
    BlockCategory.CAPTCHAS -> AppIcons.VerifiedUser
    BlockCategory.CONDITIONS -> AppIcons.CheckCircle
    BlockCategory.FUNCTIONS -> AppIcons.AutoFixHigh
    BlockCategory.INTEROP -> AppIcons.SyncAlt
    BlockCategory.PARSING -> AppIcons.FilterList
    BlockCategory.REQUESTS -> AppIcons.Http
    BlockCategory.SELENIUM -> AppIcons.Radar
    BlockCategory.UTILITY -> AppIcons.Bolt
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlocksStudioTabContent(
    blocks: List<ConfigBlock>,
    sampleTestLine: String,
    dryRunBodyText: String,
    dryRunStatusCode: Int,
    currentConfig: CustomHttpConfig,
    onAddBlock: (ConfigBlock) -> Unit,
    onUpdateBlock: (Int, ConfigBlock) -> Unit,
    onMoveBlock: (Int, Int) -> Unit,
    onRemoveBlock: (Int) -> Unit,
    onReplaceAllBlocks: (List<ConfigBlock>) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    val scope = rememberCoroutineScope()

    var selectedCategory by rememberSaveable { mutableStateOf(BlockCategory.BROWSER) }
    var blockSearchQuery by rememberSaveable { mutableStateOf("") }
    var editingIndex by rememberSaveable { mutableIntStateOf(-1) }
    var dryRunResult by remember { mutableStateOf<BlockPipelineResult?>(null) }
    var isRunningDryRun by rememberSaveable { mutableStateOf(false) }

    // Editor state for selected / editing block
    var editType by rememberSaveable { mutableStateOf(BlockType.BROWSER_OPEN) }
    var editLabel by rememberSaveable { mutableStateOf("") }
    var editInput by rememberSaveable { mutableStateOf("") }
    var editSecondary by rememberSaveable { mutableStateOf("") }
    var editTertiary by rememberSaveable { mutableStateOf("") }
    var editOutputVar by rememberSaveable { mutableStateOf("") }
    var editCapture by rememberSaveable { mutableStateOf(false) }
    var editVerdict by rememberSaveable { mutableStateOf(ResultBucket.LIVE) }
    var editExtraHeaders by rememberSaveable { mutableStateOf("") }
    var editFlagBool by rememberSaveable { mutableStateOf(false) }

    fun loadBlockForEdit(index: Int, blk: ConfigBlock) {
        editingIndex = index
        editType = blk.type
        editLabel = blk.label
        editInput = blk.inputTarget
        editSecondary = blk.secondaryParam
        editTertiary = blk.tertiaryParam
        editOutputVar = blk.outputVar
        editCapture = blk.captureToOutput
        editVerdict = blk.verdictOnMatch
        editExtraHeaders = blk.extraParams["headers"].orEmpty()
        editFlagBool = blk.extraParams["fetchHtml"] == "true" ||
            blk.extraParams["regex"] == "true" ||
            blk.extraParams["recursive"] == "true"
    }

    fun buildEditedBlock(existingId: String? = null): ConfigBlock {
        val extras = LinkedHashMap<String, String>()
        if (editType == BlockType.REQUEST_HTTP && editExtraHeaders.isNotBlank()) {
            extras["headers"] = editExtraHeaders.trim()
        }
        if (editFlagBool) {
            when (editType) {
                BlockType.BROWSER_NAVIGATE -> extras["fetchHtml"] = "true"
                BlockType.FUNC_STRING_REPLACE -> extras["regex"] = "true"
                BlockType.PARSE_LR, BlockType.PARSE_JSON, BlockType.PARSE_REGEX -> extras["recursive"] = "true"
                else -> Unit
            }
        }
        return ConfigBlock(
            id = existingId ?: UUID.randomUUID().toString().take(8),
            type = editType,
            label = editLabel.trim().ifEmpty { editType.displayName },
            enabled = true,
            inputTarget = editInput,
            secondaryParam = editSecondary,
            tertiaryParam = editTertiary,
            outputVar = editOutputVar.trim(),
            captureToOutput = editCapture,
            verdictOnMatch = editVerdict,
            extraParams = extras
        )
    }

    Text(
        text = "Compose multi-step Config Maker block pipelines across all 9 categories. Blocks execute in order during Dry-Run, Probe, and Batch runs.",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant
    )

    // Quick Starter Recipes
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "1-Tap Block Stack Templates",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = cs.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConfigBlockSerialization.BUILT_IN_RECIPES.forEach { recipe ->
                Surface(
                    onClick = {
                        onReplaceAllBlocks(recipe.blocks.map { it.copy(id = UUID.randomUUID().toString().take(8)) })
                        editingIndex = -1
                        dryRunResult = null
                    },
                    color = cs.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = "+ ${recipe.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }

    // 9-Category Block Library Palette
    Surface(
        color = cs.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("config_studio_block_palette")
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
                    text = "Block Library (9 Categories)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
                Text(
                    text = "${BlockType.entries.size} blocks",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = cs.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = blockSearchQuery,
                onValueChange = { blockSearchQuery = it },
                label = { Text("Search blocks across all 9 categories") },
                placeholder = { Text("e.g. captcha, hash, selenium, parse, cookie...") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_studio_block_search_input")
            )

            // 9 Category Selector Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BlockCategory.entries.forEach { cat ->
                    val selected = selectedCategory == cat && blockSearchQuery.isBlank()
                    val accent = blockCategoryColor(cat)
                    Surface(
                        onClick = {
                            selectedCategory = cat
                            blockSearchQuery = ""
                        },
                        color = if (selected) accent.copy(alpha = 0.16f) else cs.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            width = if (selected) 1.25.dp else 0.75.dp,
                            color = if (selected) accent else cs.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.testTag("block_category_${cat.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = blockCategoryIcon(cat),
                                contentDescription = null,
                                tint = if (selected) accent else cs.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = cat.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) accent else cs.onSurface
                            )
                        }
                    }
                }
            }

            // Active Category Header & Description
            if (blockSearchQuery.isBlank()) {
                val catAccent = blockCategoryColor(selectedCategory)
                Surface(
                    color = catAccent.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(0.75.dp, catAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = selectedCategory.shortBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = catAccent
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedCategory.displayName} Blocks",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            Text(
                                text = selectedCategory.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Concrete Blocks in Selected Category (or Search Results)
            val catalogBlocks = remember(selectedCategory, blockSearchQuery) {
                val q = blockSearchQuery.trim()
                if (q.isEmpty()) {
                    BlockType.forCategory(selectedCategory)
                } else {
                    BlockType.entries.filter {
                        it.displayName.contains(q, ignoreCase = true) ||
                            it.summary.contains(q, ignoreCase = true) ||
                            it.category.displayName.contains(q, ignoreCase = true) ||
                            it.category.description.contains(q, ignoreCase = true)
                    }
                }
            }

            if (catalogBlocks.isEmpty()) {
                Text(
                    text = "No matching blocks found for \"$blockSearchQuery\".",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    catalogBlocks.forEach { bType ->
                        val catAccent = blockCategoryColor(bType.category)
                        Surface(
                            color = cs.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newBlock = ConfigBlock(
                                        type = bType,
                                        label = bType.displayName,
                                        inputTarget = bType.primaryPlaceholder,
                                        secondaryParam = bType.secondaryPlaceholder,
                                        tertiaryParam = bType.tertiaryPlaceholder,
                                        outputVar = bType.defaultOutputVar,
                                        captureToOutput = bType.category == BlockCategory.PARSING
                                    )
                                    onAddBlock(newBlock)
                                    loadBlockForEdit(blocks.size, newBlock)
                                }
                                .testTag("add_block_type_${bType.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = bType.category.shortBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = catAccent
                                        )
                                        Text(
                                            text = bType.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = cs.onSurface
                                        )
                                    }
                                    Text(
                                        text = bType.summary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val newBlock = ConfigBlock(
                                            type = bType,
                                            label = bType.displayName,
                                            inputTarget = bType.primaryPlaceholder,
                                            secondaryParam = bType.secondaryPlaceholder,
                                            tertiaryParam = bType.tertiaryPlaceholder,
                                            outputVar = bType.defaultOutputVar,
                                            captureToOutput = bType.category == BlockCategory.PARSING
                                        )
                                        onAddBlock(newBlock)
                                        loadBlockForEdit(blocks.size, newBlock)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // In-Place Block Inspector / Editor when a block is selected
    AnimatedVisibility(
        visible = editingIndex in blocks.indices,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val catAccent = blockCategoryColor(editType.category)
        Surface(
            color = cs.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.25.dp, catAccent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("config_studio_block_editor_card")
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
                        text = "Editing Block #${editingIndex + 1}: ${editType.category.displayName} / ${editType.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = catAccent,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { editingIndex = -1 },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Close", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = editLabel,
                    onValueChange = { editLabel = it },
                    label = { Text("Block Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editInput,
                    onValueChange = { editInput = it },
                    label = { Text(editType.primaryLabel) },
                    placeholder = { Text(editType.primaryPlaceholder) },
                    minLines = 1,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("block_editor_primary_input")
                )

                if (editType.secondaryLabel.isNotEmpty()) {
                    OutlinedTextField(
                        value = editSecondary,
                        onValueChange = { editSecondary = it },
                        label = { Text(editType.secondaryLabel) },
                        placeholder = { Text(editType.secondaryPlaceholder) },
                        minLines = 1,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (editType.tertiaryLabel.isNotEmpty()) {
                    OutlinedTextField(
                        value = editTertiary,
                        onValueChange = { editTertiary = it },
                        label = { Text(editType.tertiaryLabel) },
                        placeholder = { Text(editType.tertiaryPlaceholder) },
                        minLines = 1,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (editType == BlockType.REQUEST_HTTP) {
                    OutlinedTextField(
                        value = editExtraHeaders,
                        onValueChange = { editExtraHeaders = it },
                        label = { Text("Request Headers (Key: Value per line)") },
                        placeholder = { Text("Authorization: Bearer {cap:token}") },
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (editType == BlockType.CONDITION_KEYCHECK) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ResultBucket.entries.forEachIndexed { idx, bucket ->
                            SegmentedButton(
                                selected = editVerdict == bucket,
                                onClick = { editVerdict = bucket },
                                shape = SegmentedButtonDefaults.itemShape(idx, ResultBucket.entries.size)
                            ) {
                                Text("On Match: ${bucket.name}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editOutputVar,
                        onValueChange = { editOutputVar = it },
                        label = { Text("Output Var (e.g. token / SOURCE)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = editCapture,
                        onClick = { editCapture = !editCapture },
                        label = { Text("CAP Output", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                if (editType == BlockType.BROWSER_NAVIGATE ||
                    editType == BlockType.FUNC_STRING_REPLACE ||
                    editType == BlockType.PARSE_LR ||
                    editType == BlockType.PARSE_JSON ||
                    editType == BlockType.PARSE_REGEX
                ) {
                    val flagLabel = when (editType) {
                        BlockType.BROWSER_NAVIGATE -> "Fetch Page HTML in Live Probe"
                        BlockType.FUNC_STRING_REPLACE -> "Use Regex Mode"
                        else -> "Recursive Match (All)"
                    }
                    FilterChip(
                        selected = editFlagBool,
                        onClick = { editFlagBool = !editFlagBool },
                        label = { Text(flagLabel, style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (editingIndex in blocks.indices) {
                                val updated = buildEditedBlock(blocks[editingIndex].id)
                                onUpdateBlock(editingIndex, updated)
                                editingIndex = -1
                            }
                        },
                        modifier = Modifier.testTag("block_editor_save_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply Block Changes", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    // Ordered Block Stack Header + Dry-Run Button
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Pipeline Stack (${blocks.size} block${if (blocks.size == 1) "" else "s"})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (blocks.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        onReplaceAllBlocks(emptyList())
                        editingIndex = -1
                        dryRunResult = null
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = {
                        if (isRunningDryRun) return@FilledTonalButton
                        isRunningDryRun = true
                        scope.launch {
                            val cleanSample = sampleTestLine.trim().ifEmpty { "4532250055360003|08|2028|113" }
                            val isCombo = CustomConfigStudioEngine.isComboInputLine(cleanSample, currentConfig)
                            val parts = cleanSample.split('|', ':', '/')
                            val rawPan = parts.getOrElse(0) { "4532250055360003" }.trim()
                            val pan = if (!isCombo && rawPan.any { it.equals('x', ignoreCase = true) }) {
                                CardValidatorEngine.resolveWildcardPan(rawPan)
                            } else rawPan
                            val mm = parts.getOrElse(1) { "08" }.trim()
                            val yyyy = parts.getOrElse(2) { "2028" }.trim()
                            val cvv = parts.getOrElse(3) { "113" }.trim()
                            val scheme = CardValidatorEngine.detectScheme(pan)

                            val res = ConfigBlockExecutor.executeStack(
                                blocks = blocks,
                                normalizedLine = cleanSample,
                                pan = pan,
                                mm = mm,
                                yyyy = yyyy,
                                cvv = cvv,
                                scheme = scheme,
                                config = currentConfig,
                                initialSourceBody = dryRunBodyText,
                                initialStatusCode = dryRunStatusCode,
                                dryRunNetwork = true
                            )
                            dryRunResult = res
                            isRunningDryRun = false
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("config_studio_dry_run_blocks_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dry-Run Stack", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // Active Blocks Stack List
    if (blocks.isEmpty()) {
        Text(
            text = "No blocks added yet. Pick any category above (Browser, Captchas, Conditions, Functions, Interop, Parsing, Requests, Selenium, Utility) or tap a 1-tap stack template.",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocks.forEachIndexed { idx, blk ->
                val catAccent = blockCategoryColor(blk.category)
                val isEditing = editingIndex == idx
                Surface(
                    color = if (isEditing) cs.primaryContainer.copy(alpha = 0.3f) else cs.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        width = if (isEditing) 1.25.dp else 0.75.dp,
                        color = if (isEditing) catAccent else cs.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { loadBlockForEdit(idx, blk) }
                        .testTag("block_stack_item_$idx")
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
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "#${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = cs.onSurfaceVariant
                                )
                                Surface(
                                    color = catAccent.copy(alpha = 0.14f),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = blk.category.shortBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = catAccent,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = blk.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = blk.compactSummary(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { loadBlockForEdit(idx, blk) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit block", modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onMoveBlock(idx, idx - 1) },
                                enabled = idx > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Move up",
                                    modifier = Modifier
                                        .size(15.dp)
                                        .graphicsLayer { rotationZ = 180f }
                                )
                            }
                            IconButton(
                                onClick = { onMoveBlock(idx, idx + 1) },
                                enabled = idx < blocks.lastIndex,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Switch(
                                checked = blk.enabled,
                                onCheckedChange = { checked ->
                                    onUpdateBlock(idx, blk.copy(enabled = checked))
                                }
                            )
                            IconButton(
                                onClick = {
                                    if (editingIndex == idx) editingIndex = -1
                                    onRemoveBlock(idx)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove block",
                                    tint = consoleColors.dieAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dry-Run Execution Trace Card
    AnimatedVisibility(visible = dryRunResult != null) {
        dryRunResult?.let { res ->
            Surface(
                color = cs.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_studio_block_dry_run_trace")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Block Stack Dry-Run Trace (${res.totalDurationMs}ms)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                        res.verdict?.let { (bucket, tag) ->
                            val badgeColor = when (bucket) {
                                ResultBucket.LIVE -> consoleColors.liveAccent
                                ResultBucket.DIE -> consoleColors.dieAccent
                                ResultBucket.UNKNOWN -> consoleColors.unknownAccent
                            }
                            Text(
                                text = "${bucket.name}: $tag",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }

                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.35f))

                    res.steps.forEach { st ->
                        val stColor = when (st.status) {
                            BlockStepStatus.MATCHED -> consoleColors.liveAccent
                            BlockStepStatus.SUCCESS -> cs.primary
                            BlockStepStatus.SIMULATED -> consoleColors.unknownAccent
                            BlockStepStatus.SKIPPED -> cs.onSurfaceVariant
                            BlockStepStatus.FAILED -> consoleColors.dieAccent
                        }
                        Text(
                            text = "#${st.index} [${st.status.name}] ${st.block.category.shortBadge}/${st.block.label}: ${st.detail}" +
                                if (st.outputPreview.isNotBlank()) " => ${st.outputPreview.take(48)}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = stColor
                        )
                    }

                    if (res.captures.isNotEmpty()) {
                        Text(
                            text = "CAPTURED OUTPUTS: " + res.captures.entries.joinToString("  ") { "${it.key}=${it.value}" },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = consoleColors.telegramAccent
                        )
                    }

                    if (res.logs.isNotEmpty()) {
                        Text(
                            text = "LOGS:\n" + res.logs.joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
