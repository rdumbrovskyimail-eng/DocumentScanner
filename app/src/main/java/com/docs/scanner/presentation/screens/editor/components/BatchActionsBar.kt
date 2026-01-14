package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.*

// ============================================
// BATCH ACTIONS BAR (Google Docs Style 2026)
// ============================================

/**
 * Панель действий для режима мульти-выбора.
 * Появляется внизу экрана когда выбрана хотя бы одна страница.
 * 
 * Действия:
 * - Delete: Удалить выбранные
 * - Export: Экспортировать выбранные (PDF/ZIP)
 * - Move: Переместить в другую запись
 */
@Composable
fun BatchActionsBar(
    selectedCount: Int,
    totalCount: Int,
    onDeleteClick: () -> Unit,
    onExportClick: () -> Unit,
    onMoveClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAllSelected = selectedCount == totalCount && totalCount > 0
    
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selection info
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Close button
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Count badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GoogleDocsPrimary
                        ) {
                            Text(
                                text = "$selectedCount",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        
                        Text(
                            text = "selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Select All button
                    TextButton(onClick = onSelectAllClick) {
                        Icon(
                            imageVector = if (isAllSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = GoogleDocsPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAllSelected) "Deselect All" else "Select All",
                            color = GoogleDocsPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Delete
                    BatchActionButton(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        count = selectedCount,
                        color = GoogleDocsError,
                        onClick = onDeleteClick
                    )
                    
                    // Export
                    BatchActionButton(
                        icon = Icons.Default.FileDownload,
                        label = "Export",
                        count = selectedCount,
                        color = GoogleDocsPrimary,
                        onClick = onExportClick
                    )
                    
                    // Move
                    BatchActionButton(
                        icon = Icons.Default.DriveFileMove,
                        label = "Move",
                        count = selectedCount,
                        color = GoogleDocsWarning,
                        onClick = onMoveClick
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchActionButton(
    icon: ImageVector,
    label: String,
    count: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = color.copy(alpha = 0.1f),
                contentColor = color
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================
// SELECTION TOP BAR
// ============================================

/**
 * TopAppBar для режима выбора
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onCloseClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAllSelected = selectedCount == totalCount && totalCount > 0
    
    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection mode"
                )
            }
        },
        actions = {
            TextButton(onClick = onSelectAllClick) {
                Text(
                    text = if (isAllSelected) "Deselect All" else "Select All",
                    color = GoogleDocsPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = GoogleDocsPrimary.copy(alpha = 0.1f)
        ),
        modifier = modifier
    )
}

// ============================================
// SMART RETRY BANNER
// ============================================

/**
 * Баннер для повторной обработки failed документов
 */
@Composable
fun SmartRetryBanner(
    failedCount: Int,
    onRetryClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = failedCount > 0,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = GoogleDocsError.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = GoogleDocsError,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Column {
                        Text(
                            text = "$failedCount document${if (failedCount > 1) "s" else ""} failed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoogleDocsError
                        )
                        Text(
                            text = "Tap to retry processing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Retry button
                    FilledTonalButton(
                        onClick = onRetryClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = GoogleDocsError.copy(alpha = 0.2f),
                            contentColor = GoogleDocsError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                    
                    // Dismiss button
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// PROCESSING PROGRESS BANNER
// ============================================

/**
 * Баннер прогресса пакетной обработки
 */
@Composable
fun BatchProgressBanner(
    processedCount: Int,
    totalCount: Int,
    currentStage: String,
    onCancelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress = if (totalCount > 0) processedCount.toFloat() / totalCount else 0f
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GoogleDocsPrimary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = GoogleDocsPrimary
                    )
                    
                    Column {
                        Text(
                            text = "Processing documents...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = currentStage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Text(
                    text = "$processedCount / $totalCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = GoogleDocsPrimary
                )
            }
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = GoogleDocsPrimary,
                trackColor = GoogleDocsPrimary.copy(alpha = 0.2f),
            )
            
            if (onCancelClick != null) {
                TextButton(
                    onClick = onCancelClick,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
