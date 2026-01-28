package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docs.scanner.domain.core.ProcessingStatus
import com.docs.scanner.presentation.theme.*

// ============================================
// DOCUMENT PAGE MENU (Google Docs Style 2026)
// ============================================

/**
 * Меню действий для отдельной страницы документа.
 * 
 * Действия:
 * - Retry OCR (если failed)
 * - Retry Translation (если failed)
 * - Move to another record
 * - Share page
 * - Delete page
 */
@Composable
fun DocumentPageMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    processingStatus: ProcessingStatus,
    onRetryOcr: () -> Unit,
    onRetryTranslation: () -> Unit,
    onMoveToRecord: () -> Unit,
    onSharePage: () -> Unit,
    onDeletePage: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // Retry OCR (if failed)
        if (processingStatus is ProcessingStatus.Ocr.Failed || processingStatus is ProcessingStatus.Error) {
            DropdownMenuItem(
                text = { Text("Retry OCR") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = GoogleDocsWarning
                    )
                },
                onClick = {
                    onRetryOcr()
                    onDismiss()
                }
            )
        }
        
        // Retry Translation (if failed)
        if (processingStatus is ProcessingStatus.Translation.Failed) {
            DropdownMenuItem(
                text = { Text("Retry Translation") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = GoogleDocsWarning
                    )
                },
                onClick = {
                    onRetryTranslation()
                    onDismiss()
                }
            )
        }
        
        // Divider if retry options shown
        if (processingStatus.isFailed) {
            HorizontalDivider()
        }
        
        // Move to another record
        DropdownMenuItem(
            text = { Text("Move to another record") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.DriveFileMove,
                    contentDescription = null
                )
            },
            onClick = {
                onMoveToRecord()
                onDismiss()
            }
        )
        
        // Share page
        DropdownMenuItem(
            text = { Text("Share page") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null
                )
            },
            onClick = {
                onSharePage()
                onDismiss()
            }
        )
        
        HorizontalDivider()
        
        // Delete page
        DropdownMenuItem(
            text = { 
                Text(
                    text = "Delete page",
                    color = GoogleDocsError
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = GoogleDocsError
                )
            },
            onClick = {
                onDeletePage()
                onDismiss()
            }
        )
    }
}

// ============================================
// RECORD MENU (для меню записи в TopBar)
// ============================================

/**
 * Меню действий для всей записи.
 * 
 * Действия:
 * - Rename
 * - Edit description
 * - Manage tags
 * - Change languages
 * - Share as PDF
 * - Share images (ZIP)
 * - Select pages (enter selection mode)
 */
@Composable
fun RecordMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onEditDescription: () -> Unit,
    onManageTags: () -> Unit,
    onChangeLanguages: () -> Unit,
    onSharePdf: () -> Unit,
    onShareZip: () -> Unit,
    onSelectPages: () -> Unit,
    hasDocuments: Boolean
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        
        
        // Manage tags
        DropdownMenuItem(
            text = { Text("Manage tags") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = null
                )
            },
            onClick = {
                onManageTags()
                onDismiss()
            }
        )
        
        // Change languages
        DropdownMenuItem(
            text = { Text("Change languages") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null
                )
            },
            onClick = {
                onChangeLanguages()
                onDismiss()
            }
        )
        
        HorizontalDivider()
        
        // Select pages (enter selection mode)
        if (hasDocuments) {
            DropdownMenuItem(
                text = { Text("Select pages") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null
                    )
                },
                onClick = {
                    onSelectPages()
                    onDismiss()
                }
            )
            
            HorizontalDivider()
        }
        
        // Share as PDF
        DropdownMenuItem(
            text = { Text("Share as PDF") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = GoogleDocsError
                )
            },
            onClick = {
                onSharePdf()
                onDismiss()
            },
            enabled = hasDocuments
        )
        
        // Share images (ZIP)
        DropdownMenuItem(
            text = { Text("Share images (ZIP)") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = GoogleDocsWarning
                )
            },
            onClick = {
                onShareZip()
                onDismiss()
            },
            enabled = hasDocuments
        )
    }
}

// ============================================
// MOVE TO RECORD DIALOG
// ============================================

/**
 * Диалог выбора записи для перемещения страницы.
 */
@Composable
fun MoveToRecordDialog(
    records: List<RecordItem>,
    currentRecordId: Long,
    onDismiss: () -> Unit,
    onRecordSelected: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to another record") },
        text = {
            if (records.isEmpty() || records.all { it.id == currentRecordId }) {
                Text("No other records available in this folder")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select destination record:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    records
                        .filter { it.id != currentRecordId }
                        .forEach { record ->
                            Surface(
                                onClick = { onRecordSelected(record.id) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = GoogleDocsPrimary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = record.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${record.documentCount} pages",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Data class для списка записей в диалоге перемещения
 */
data class RecordItem(
    val id: Long,
    val name: String,
    val documentCount: Int
)

// ============================================
// EXPORT OPTIONS DIALOG
// ============================================

/**
 * Диалог выбора формата экспорта
 */
@Composable
fun ExportOptionsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onExportPdf: () -> Unit,
    onExportZip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                tint = GoogleDocsPrimary
            )
        },
        title = { Text("Export $selectedCount page${if (selectedCount > 1) "s" else ""}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // PDF option
                Surface(
                    onClick = {
                        onExportPdf()
                        onDismiss()
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = GoogleDocsError.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = GoogleDocsError,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PDF Document",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Multi-page PDF with images",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // ZIP option
                Surface(
                    onClick = {
                        onExportZip()
                        onDismiss()
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = GoogleDocsWarning.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = GoogleDocsWarning,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ZIP Archive",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Original images in archive",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ============================================
// DELETE CONFIRMATION DIALOG
// ============================================

/**
 * Диалог подтверждения удаления страниц
 */
@Composable
fun DeletePagesDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = GoogleDocsError
            )
        },
        title = { Text("Delete $count page${if (count > 1) "s" else ""}?") },
        text = {
            Text("This action cannot be undone. The selected page${if (count > 1) "s" else ""} will be permanently deleted.")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = GoogleDocsError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
