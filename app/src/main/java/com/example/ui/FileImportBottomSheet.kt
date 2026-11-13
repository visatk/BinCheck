package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.FileTextExtractor
import com.example.domain.ImportedFilePreview
import com.example.domain.SupportedFileType
import com.example.ui.theme.ActionButtonBorder
import com.example.ui.theme.ActionButtonSurface
import com.example.ui.theme.CardContainerSurface
import com.example.ui.theme.DieCrimson
import com.example.ui.theme.DieCrimsonBadgeBg
import com.example.ui.theme.GearRoyalBlue
import com.example.ui.theme.InputConsoleSurface
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlayEmerald
import com.example.ui.theme.PlayEmeraldBadgeBg
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.UnknownAmber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        containerColor = CardContainerSurface,
        contentColor = TextPrimaryLight,
        scrimColor = Color.Black.copy(alpha = 0.72f)
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
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryLight,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.import_file_subtitle),
                        fontSize = 12.sp,
                        color = TextSecondaryMuted,
                        lineHeight = 16.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_file_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close file picker",
                        tint = TextSecondaryMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // File Selector Dropzone Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InputConsoleSurface)
                    .border(1.dp, GearRoyalBlue.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .clickable(enabled = !isImporting, onClick = ::launchDocumentPicker)
                    .padding(16.dp)
                    .testTag("file_picker_dropzone")
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GearRoyalBlue.copy(alpha = 0.16f))
                            .border(1.dp, GearRoyalBlue.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = GearRoyalBlue,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = null,
                                tint = GearRoyalBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isImporting) "Reading file from storage..." else stringResource(R.string.select_file_button),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryLight
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatBadge(label = ".TXT", accentColor = PlayEmerald)
                        FormatBadge(label = ".HTML / .HTM", accentColor = GearRoyalBlue)
                        FormatBadge(label = "UTF-8", accentColor = TextSecondaryMuted)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = ::launchDocumentPicker,
                        enabled = !isImporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("browse_storage_file_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = GearRoyalBlue
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (preview == null) "Choose File from Device" else "Pick Another File",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Error display
            AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DieCrimsonBadgeBg)
                        .border(1.dp, DieCrimson.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .testTag("file_import_error_banner")
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        fontSize = 12.sp,
                        color = DieCrimson,
                        lineHeight = 16.sp
                    )
                }
            }

            // Selected File Preview & Import Options
            if (preview != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = ActionButtonBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // Selected File Metadata Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(InputConsoleSurface)
                        .border(1.dp, ActionButtonBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = if (preview.fileType == SupportedFileType.HTML) GearRoyalBlue else PlayEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preview.fileName,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${preview.fileType.label} • ${FileTextExtractor.formatFileSize(preview.fileSizeBytes)} • ${preview.allNonEmptyLines.size} line(s) • ${preview.delimitedRecordLines.size} delimited",
                                fontSize = 11.sp,
                                color = TextSecondaryMuted,
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
                            tint = TextSecondaryMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle: Smart Extract Pipe-Delimited Records (helpful especially for .html files)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(InputConsoleSurface)
                        .border(1.dp, ActionButtonBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Extract Pipe/Delimited Records Only",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryLight
                        )
                        Text(
                            text = if (preview.delimitedRecordLines.isNotEmpty()) {
                                "Filters ${preview.delimitedRecordLines.size} PAN|MM|YYYY|CVV line(s) from ${preview.fileType.label}"
                            } else {
                                "No delimited records detected; will load all ${preview.allNonEmptyLines.size} text line(s)"
                            },
                            fontSize = 11.5.sp,
                            color = TextSecondaryMuted
                        )
                    }
                    Switch(
                        checked = onlyDelimitedRecords,
                        onCheckedChange = { onlyDelimitedRecords = it },
                        enabled = preview.delimitedRecordLines.isNotEmpty(),
                        modifier = Modifier.testTag("extract_delimited_only_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PlayEmerald
                        )
                    )
                }

                if (hasExistingConsoleText) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputConsoleSurface)
                            .border(1.dp, ActionButtonBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Append to Current Console",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimaryLight
                            )
                            Text(
                                text = "Keep existing lines and append imported file below",
                                fontSize = 11.5.sp,
                                color = TextSecondaryMuted
                            )
                        }
                        Switch(
                            checked = appendToConsole,
                            onCheckedChange = { appendToConsole = it },
                            modifier = Modifier.testTag("append_to_console_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GearRoyalBlue
                            )
                        )
                    }
                }

                // Live Preview Snippet
                val previewLines = if (onlyDelimitedRecords && preview.delimitedRecordLines.isNotEmpty()) {
                    preview.delimitedRecordLines
                } else {
                    preview.allNonEmptyLines
                }

                if (previewLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Extracted Preview (${previewLines.size} lines)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0E1014))
                            .border(1.dp, ActionButtonBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        previewLines.take(5).forEachIndexed { idx, line ->
                            Text(
                                text = "${(idx + 1).toString().padStart(2, '0')}  $line",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.5.sp,
                                color = TextPrimaryLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (previewLines.size > 5) {
                            Text(
                                text = "... +${previewLines.size - 5} more line(s)",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = UnknownAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Responsive Action Buttons
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryLight)
                    ) {
                        Text(
                            text = stringResource(R.string.load_into_console),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayEmerald,
                            contentColor = Color(0xFF071712)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.load_and_process),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
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
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (accentColor == PlayEmerald) PlayEmeraldBadgeBg else ActionButtonSurface)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = accentColor
        )
    }
}
