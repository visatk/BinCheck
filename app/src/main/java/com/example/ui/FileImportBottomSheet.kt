package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.analytics.FirebaseMetrics
import com.example.domain.FileTextExtractor
import com.example.domain.ImportedFilePreview
import com.example.domain.SupportedFileType
import com.example.ui.theme.LocalConsoleColors

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun FileImportBottomSheet(
    isImporting: Boolean,
    errorMessage: String?,
    preview: ImportedFilePreview?,
    hasExistingConsoleText: Boolean,
    onDismiss: () -> Unit,
    onFileSelected: (Uri) -> Unit,
    onClearPreview: () -> Unit,
    onApplyImportedFile: (append: Boolean, onlyDelimitedRecords: Boolean, startImmediately: Boolean) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    LaunchedEffect(Unit) {
        FirebaseMetrics.logScreenView("FileImport", "MainActivity")
    }

    var appendToConsole by remember(preview) { mutableStateOf(false) }
    var onlyDelimitedRecords by remember(preview) {
        mutableStateOf(
            preview?.fileType == SupportedFileType.HTML || (preview?.delimitedRecordLines?.isNotEmpty() == true)
        )
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onFileSelected(uri)
        }
    }

    fun launchDocumentPicker() {
        openDocumentLauncher.launch(
            arrayOf(
                "text/plain",
                "text/html",
                "application/xhtml+xml"
            )
        )
    }

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
                        text = stringResource(R.string.import_file_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.import_file_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_file_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close file picker",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // File Selector Dropzone Card
            Surface(
                onClick = { if (!isImporting) launchDocumentPicker() },
                enabled = !isImporting,
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_picker_dropzone")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isImporting) {
                                ContainedLoadingIndicator(
                                    modifier = Modifier.size(36.dp),
                                    containerColor = colorScheme.secondaryContainer,
                                    indicatorColor = colorScheme.onSecondaryContainer
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.UploadFile,
                                    contentDescription = null,
                                    tint = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isImporting) "Reading your file..." else stringResource(R.string.select_file_button),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = colorScheme.onSurface
                    )

                    if (isImporting) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colorScheme.secondary,
                            trackColor = colorScheme.surfaceContainerHighest
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatBadge(
                            label = ".TXT",
                            containerColor = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer
                        )
                        FormatBadge(
                            label = ".HTML / .HTM",
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
                        FormatBadge(
                            label = "UTF-8",
                            containerColor = colorScheme.surfaceContainerHigh,
                            contentColor = colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    FilledTonalButton(
                        onClick = ::launchDocumentPicker,
                        enabled = !isImporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("browse_storage_file_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = AppIcons.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (preview == null) "Browse Files" else "Pick a Different File",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.errorContainer,
                    border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("file_import_error_banner")
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (preview != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Description,
                                contentDescription = null,
                                tint = if (preview.fileType == SupportedFileType.HTML) {
                                    colorScheme.secondary
                                } else {
                                    colorScheme.primary
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preview.fileName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${preview.fileType.label} • ${FileTextExtractor.formatFileSize(preview.fileSizeBytes)} • ${preview.totalLineCount} line(s) • ${preview.delimitedLineCount} delimited",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onClearPreview,
                            modifier = Modifier.testTag("clear_selected_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selected file",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep Card Lines Only",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (preview.delimitedLineCount > 0) {
                                    "Pulls ${preview.delimitedLineCount} card line(s) and skips extra text"
                                } else {
                                    "No formatted cards spotted — will import all ${preview.totalLineCount} line(s)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = onlyDelimitedRecords,
                            onCheckedChange = { onlyDelimitedRecords = it },
                            enabled = preview.delimitedRecordLines.isNotEmpty(),
                            modifier = Modifier.testTag("extract_delimited_only_switch")
                        )
                    }
                }

                if (hasExistingConsoleText) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Add Below Existing Lines",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "Keep what's in the console and append this file underneath",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = appendToConsole,
                                onCheckedChange = { appendToConsole = it },
                                modifier = Modifier.testTag("append_to_console_switch")
                            )
                        }
                    }
                }

                val previewLines = if (onlyDelimitedRecords && preview.delimitedRecordLines.isNotEmpty()) {
                    preview.delimitedRecordLines
                } else {
                    preview.allNonEmptyLines
                }

                val fullSelectedCount = if (onlyDelimitedRecords && preview.delimitedLineCount > 0) {
                    preview.delimitedLineCount
                } else {
                    preview.totalLineCount
                }

                if (previewLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Extracted Preview ($fullSelectedCount total)",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            previewLines.take(5).forEachIndexed { idx, line ->
                                Text(
                                    text = "${(idx + 1).toString().padStart(2, '0')}  $line",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = com.example.ui.theme.JetBrainsMonoFamily,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (fullSelectedCount > 5) {
                                Text(
                                    text = "... +${fullSelectedCount - 5} more line(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = com.example.ui.theme.JetBrainsMonoFamily,
                                    color = consoleColors.unknownAccent
                                )
                            }
                        }
                    }
                }

                if (preview.isLargeStagedFile) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Large file (${FileTextExtractor.formatFileSize(preview.fileSizeBytes)}) staged on disk: \"Load & Process\" streams all $fullSelectedCount lines directly; \"Load into Console\" loads a safe 5,000-line slice (open Toolkit to browse/edit all lines).",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onApplyImportedFile(appendToConsole, onlyDelimitedRecords, false)
                        },
                        enabled = previewLines.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("load_file_to_console_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(R.string.load_into_console),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            onApplyImportedFile(appendToConsole, onlyDelimitedRecords, true)
                        },
                        enabled = previewLines.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("load_and_process_file_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.load_and_process),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatBadge(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
