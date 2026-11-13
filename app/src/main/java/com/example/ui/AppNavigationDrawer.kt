package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppThemeMode
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

enum class CompactWorkspacePane(val labelRes: Int) {
    SPLIT(R.string.pane_split),
    CONSOLE(R.string.pane_console),
    RESULTS(R.string.pane_results)
}

@Composable
fun BinCheckNavigationShell(
    isExpandedWidth: Boolean,
    drawerState: DrawerState,
    uiState: ValidatorUiState,
    activePane: CompactWorkspacePane,
    channelUrl: String,
    onSelectPane: (CompactWorkspacePane) -> Unit,
    onOpenBinLookup: () -> Unit,
    onOpenFileImport: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onOpenDeveloperChannel: () -> Unit,
    onCloseDrawer: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isExpandedWidth) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier
                        .width(284.dp)
                        .testTag("permanent_navigation_drawer"),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    DrawerSheetContent(
                        uiState = uiState,
                        activePane = activePane,
                        isExpandedWidth = true,
                        channelUrl = channelUrl,
                        onSelectPane = onSelectPane,
                        onOpenBinLookup = onOpenBinLookup,
                        onOpenFileImport = onOpenFileImport,
                        onOpenHistory = onOpenHistory,
                        onOpenSettings = onOpenSettings,
                        onSelectThemeMode = onSelectThemeMode,
                        onToggleDynamicColor = onToggleDynamicColor,
                        onOpenDeveloperChannel = onOpenDeveloperChannel,
                        onCloseDrawer = onCloseDrawer
                    )
                }
            },
            content = content
        )
    } else {
        BackHandler(enabled = drawerState.isOpen) {
            onCloseDrawer()
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(304.dp)
                        .testTag("modal_navigation_drawer"),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    drawerContentColor = MaterialTheme.colorScheme.onSurface,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    DrawerSheetContent(
                        uiState = uiState,
                        activePane = activePane,
                        isExpandedWidth = false,
                        channelUrl = channelUrl,
                        onSelectPane = onSelectPane,
                        onOpenBinLookup = onOpenBinLookup,
                        onOpenFileImport = onOpenFileImport,
                        onOpenHistory = onOpenHistory,
                        onOpenSettings = onOpenSettings,
                        onSelectThemeMode = onSelectThemeMode,
                        onToggleDynamicColor = onToggleDynamicColor,
                        onOpenDeveloperChannel = onOpenDeveloperChannel,
                        onCloseDrawer = onCloseDrawer
                    )
                }
            },
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DrawerSheetContent(
    uiState: ValidatorUiState,
    activePane: CompactWorkspacePane,
    isExpandedWidth: Boolean,
    channelUrl: String,
    onSelectPane: (CompactWorkspacePane) -> Unit,
    onOpenBinLookup: () -> Unit,
    onOpenFileImport: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onOpenDeveloperChannel: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    val totalResultsCount = uiState.liveRecords.size + uiState.dieRecords.size + uiState.unknownRecords.size

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        // Branded Material 3 Expressive Header Card
        Surface(
            shape = MaterialTheme.shapes.large,
            color = colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colorScheme.primaryContainer.copy(alpha = 0.55f),
                                colorScheme.tertiaryContainer.copy(alpha = 0.30f)
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = MaterialShapes.Cookie7Sided.toShape(),
                            color = colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "BC",
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = colorScheme.onSurface
                            )
                            val drawerModeSummary = uiState.settings.validationMode.fixedDelayMs?.let { fixedMs ->
                                "${uiState.settings.validationMode.badgeLabel} • ${fixedMs / 1000L}s delay"
                            } ?: "${uiState.settings.validationMode.badgeLabel} • ${uiState.settings.concurrency}x"
                            Text(
                                text = drawerModeSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.isRunning || uiState.loadingBins.isNotEmpty()) {
                        LoadingIndicator(
                            modifier = Modifier.size(26.dp),
                            color = consoleColors.liveAccent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "WORKSPACE",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        val itemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = colorScheme.secondaryContainer,
            selectedIconColor = colorScheme.onSecondaryContainer,
            selectedTextColor = colorScheme.onSecondaryContainer,
            unselectedContainerColor = colorScheme.surfaceContainerLow,
            unselectedIconColor = colorScheme.onSurfaceVariant,
            unselectedTextColor = colorScheme.onSurface
        )

        val isConsoleSelected = isExpandedWidth || activePane == CompactWorkspacePane.SPLIT || activePane == CompactWorkspacePane.CONSOLE
        NavigationDrawerItem(
            selected = isConsoleSelected,
            onClick = {
                onSelectPane(
                    if (activePane == CompactWorkspacePane.RESULTS) CompactWorkspacePane.SPLIT else activePane
                )
                onCloseDrawer()
            },
            icon = {
                Icon(
                    imageVector = if (isConsoleSelected) Icons.Filled.Terminal else Icons.Outlined.Terminal,
                    contentDescription = null
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_workspace),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            badge = {
                if (uiState.inputLineCount > 0) {
                    Badge(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    ) {
                        Text(uiState.inputLineCount.toString())
                    }
                }
            },
            shape = MaterialTheme.shapes.medium,
            colors = itemColors,
            modifier = Modifier
                .height(50.dp)
                .testTag("drawer_item_workspace")
        )

        if (!isExpandedWidth) {
            Spacer(modifier = Modifier.height(4.dp))
            val isResultsSelected = activePane == CompactWorkspacePane.RESULTS
            NavigationDrawerItem(
                selected = isResultsSelected,
                onClick = {
                    onSelectPane(CompactWorkspacePane.RESULTS)
                    onCloseDrawer()
                },
                icon = {
                    Icon(
                        imageVector = if (isResultsSelected) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_results),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                badge = {
                    if (totalResultsCount > 0) {
                        Badge(
                            containerColor = if (uiState.liveRecords.isNotEmpty()) consoleColors.liveContainer else colorScheme.surfaceContainerHighest,
                            contentColor = if (uiState.liveRecords.isNotEmpty()) consoleColors.liveAccent else colorScheme.onSurfaceVariant
                        ) {
                            Text(totalResultsCount.toString())
                        }
                    }
                },
                shape = MaterialTheme.shapes.medium,
                colors = itemColors,
                modifier = Modifier
                    .height(50.dp)
                    .testTag("drawer_item_results")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "TOOLS & REGISTRY",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        NavigationDrawerItem(
            selected = uiState.isBinSheetOpen,
            onClick = {
                onCloseDrawer()
                onOpenBinLookup()
            },
            icon = {
                Icon(
                    imageVector = if (uiState.isBinSheetOpen) Icons.Filled.AccountBalance else Icons.Outlined.AccountBalance,
                    contentDescription = null
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_bin_lookup),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            badge = {
                if (uiState.binLookupMap.isNotEmpty()) {
                    Badge(
                        containerColor = consoleColors.liveContainer,
                        contentColor = consoleColors.liveAccent
                    ) {
                        Text(uiState.binLookupMap.size.toString())
                    }
                }
            },
            shape = MaterialTheme.shapes.medium,
            colors = itemColors,
            modifier = Modifier
                .height(50.dp)
                .testTag("drawer_item_bin_lookup")
        )

        Spacer(modifier = Modifier.height(4.dp))

        NavigationDrawerItem(
            selected = uiState.isFileImportOpen,
            onClick = {
                onCloseDrawer()
                onOpenFileImport()
            },
            icon = {
                Icon(
                    imageVector = if (uiState.isFileImportOpen) Icons.Filled.UploadFile else Icons.Outlined.UploadFile,
                    contentDescription = null
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_file_import),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            badge = {
                if (!uiState.loadedFileName.isNullOrBlank()) {
                    Badge(
                        containerColor = colorScheme.tertiaryContainer,
                        contentColor = colorScheme.onTertiaryContainer
                    ) {
                        Text("1")
                    }
                }
            },
            shape = MaterialTheme.shapes.medium,
            colors = itemColors,
            modifier = Modifier
                .height(50.dp)
                .testTag("drawer_item_file_import")
        )

        Spacer(modifier = Modifier.height(4.dp))

        NavigationDrawerItem(
            selected = uiState.isHistoryOpen,
            onClick = {
                onCloseDrawer()
                onOpenHistory()
            },
            icon = {
                Icon(
                    imageVector = if (uiState.isHistoryOpen) Icons.Filled.History else Icons.Outlined.History,
                    contentDescription = null
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_history),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            badge = {
                Badge(
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurfaceVariant
                ) {
                    Text(uiState.historySummary.totalCount.toString())
                }
            },
            shape = MaterialTheme.shapes.medium,
            colors = itemColors,
            modifier = Modifier
                .height(50.dp)
                .testTag("drawer_item_history")
        )

        Spacer(modifier = Modifier.height(4.dp))

        NavigationDrawerItem(
            selected = uiState.isSettingsOpen,
            onClick = {
                onCloseDrawer()
                onOpenSettings()
            },
            icon = {
                Icon(
                    imageVector = if (uiState.isSettingsOpen) Icons.Filled.Tune else Icons.Outlined.Tune,
                    contentDescription = null
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            badge = {
                Badge(
                    containerColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer
                ) {
                    val badgeText = uiState.settings.validationMode.fixedDelayMs?.let { fixedMs ->
                        "${fixedMs / 1000L}s"
                    } ?: "${uiState.settings.concurrency}x"
                    Text(badgeText)
                }
            },
            shape = MaterialTheme.shapes.medium,
            colors = itemColors,
            modifier = Modifier
                .height(50.dp)
                .testTag("drawer_item_settings")
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))
        Spacer(modifier = Modifier.height(12.dp))

        // Quick Theme Switcher inside Drawer
        Text(
            text = "QUICK THEME",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        val modes = AppThemeMode.entries
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.settings.themeMode == mode,
                    onClick = { onSelectThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        FilterChip(
            selected = uiState.settings.useDynamicColor,
            onClick = { onToggleDynamicColor(!uiState.settings.useDynamicColor) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = if (uiState.settings.useDynamicColor) "Material You: ON" else "Material You: OFF",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f, fill = true))
        Spacer(modifier = Modifier.height(12.dp))

        // Developer Channel Pin in Drawer Footer
        Surface(
            onClick = {
                onCloseDrawer()
                onOpenDeveloperChannel()
            },
            shape = MaterialTheme.shapes.medium,
            color = consoleColors.telegramContainer,
            border = BorderStroke(1.dp, consoleColors.telegramAccent.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_developer_channel_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = consoleColors.telegramAccent.copy(alpha = 0.2f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            TelegramPaperPlaneIcon(
                                color = consoleColors.telegramAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.developer_channel_handle),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = consoleColors.telegramAccent
                        )
                        Text(
                            text = channelUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "Open Telegram Channel",
                    tint = consoleColors.telegramAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
