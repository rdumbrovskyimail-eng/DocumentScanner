package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.docs.scanner.domain.core.ProcessingStatus
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.theme.*
import java.io.File

// ============================================
// DOCUMENT CARD (Google Docs Style 2026)
// ============================================

/**
 * Карточка документа по макету:
 * ┌──────────┬──────────────────┐
 * │  PHOTO   │   SCAN TEXT      │  50% / 50%
 * │  (tap →  │   (tap → editor) │
 * │   zoom)  │   scrollable     │
 * │    🟢    │                  │
 * └──────────┴──────────────────┘
 * ┌─────────────────────────────┐
 * │  TRANSLATE TEXT             │  100% width
 * │  (dynamic height)           │
 * └─────────────────────────────┘
 * [AI] [Copy] [Paste] [Share] [⋮]
 */
@Composable
fun DocumentCard(
    document: Document,
    index: Int,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isDragging: Boolean,
    onImageClick: () -> Unit,
    onOcrTextClick: () -> Unit,
    onTranslationClick: () -> Unit,
    onSelectionToggle: () -> Unit,
    onMenuClick: () -> Unit,
    onRetryOcr: () -> Unit,
    onRetryTranslation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var photoHeight by remember { mutableStateOf(200.dp) }
    
    // Animation states
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = spring(),
        label = "card_scale"
    )
    
    val cardElevation by animateFloatAsState(
        targetValue = if (isDragging) 12f else 2f,
        animationSpec = spring(),
        label = "card_elevation"
    )
    
    val selectionBorderColor by animateColorAsState(
        targetValue = if (isSelected) GoogleDocsPrimary else Color.Transparent,
        label = "selection_border"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .shadow(cardElevation.dp, RoundedCornerShape(16.dp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, selectionBorderColor, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══════════════════════════════════════════════════════════════
            // TOP ROW: Photo (50%) | OCR Text (50%)
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─────────────────────────────────────────────────────────────
                // LEFT: Photo (50%)
                // ─────────────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.75f) // 3:4 aspect ratio for documents
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoogleDocsSurfaceVariant)
                        .clickable(enabled = !isSelectionMode) { onImageClick() }
                        .onGloballyPositioned { coordinates ->
                            with(density) {
                                photoHeight = coordinates.size.height.toDp()
                            }
                        }
                ) {
                    // Image
                    AsyncImage(
                        model = File(document.imagePath),
                        contentDescription = "Document page ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Selection checkbox overlay
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable { onSelectionToggle() },
                            contentAlignment = Alignment.Center
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onSelectionToggle() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = GoogleDocsPrimary,
                                    uncheckedColor = Color.White
                                )
                            )
                        }
                    }
                    
                    // Status badge (top-right corner)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        StatusBadge(
                            status = document.processingStatus,
                            showLabel = false
                        )
                    }
                    
                    // Page number (bottom-left corner)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                // ─────────────────────────────────────────────────────────────
                // RIGHT: OCR Text (50%)
                // ─────────────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(photoHeight) // Match photo height
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoogleDocsBackground)
                        .border(1.dp, GoogleDocsBorderLight, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isSelectionMode) { onOcrTextClick() }
                ) {
                    when {
                        document.processingStatus is ProcessingStatus.Ocr.InProgress -> {
                            // Loading state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = GoogleDocsPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Scanning...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoogleDocsTextSecondary
                                )
                            }
                        }
                        
                        document.processingStatus is ProcessingStatus.Ocr.Failed -> {
                            // Error state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = GoogleDocsError
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "OCR Failed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoogleDocsError
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = onRetryOcr) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry")
                                }
                            }
                        }
                        
                        document.originalText.isNullOrBlank() -> {
                            // Empty state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = GoogleDocsTextTertiary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No text detected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoogleDocsTextTertiary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        
                        else -> {
                            // Text content
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                // Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "OCR Text",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoogleDocsTextTertiary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        modifier = Modifier.size(14.dp),
                                        tint = GoogleDocsTextTertiary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Scrollable text
                                Text(
                                    text = document.originalText ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoogleDocsTextPrimary,
                                    textAlign = TextAlign.Justify,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // TRANSLATION FIELD (dynamic height)
            // ═══════════════════════════════════════════════════════════════
            TranslationSection(
                document = document,
                onClick = onTranslationClick,
                onRetryTranslation = onRetryTranslation
            )
            
            // ═══════════════════════════════════════════════════════════════
            // ACTION BUTTONS ROW
            // ═══════════════════════════════════════════════════════════════
            DocumentActionButtons(
                text = document.originalText ?: document.translatedText ?: "",
                onMenuClick = onMenuClick,
                onRetryOcr = if (document.processingStatus.canRetryOcr()) onRetryOcr else null,
                onRetryTranslation = if (document.processingStatus.canRetryTranslation()) onRetryTranslation else null
            )
        }
    }
}

// ============================================
// TRANSLATION SECTION
// ============================================

@Composable
private fun TranslationSection(
    document: Document,
    onClick: () -> Unit,
    onRetryTranslation: () -> Unit
) {
    when {
        document.processingStatus is ProcessingStatus.Translation.InProgress -> {
            // Loading state
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = GoogleDocsTranslationBackground
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = GoogleDocsTranslationIcon,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Translating...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoogleDocsTranslationTitle
                    )
                }
            }
        }
        
        document.processingStatus is ProcessingStatus.Translation.Failed -> {
            // Error state
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = GoogleDocsError.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = GoogleDocsError
                        )
                        Text(
                            text = "Translation failed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoogleDocsError
                        )
                    }
                    
                    TextButton(onClick = onRetryTranslation) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
            }
        }
        
        !document.translatedText.isNullOrBlank() -> {
            // Translation content
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClick() },
                color = GoogleDocsTranslationBackground
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = GoogleDocsTranslationIcon
                        )
                        Text(
                            text = "Translation",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoogleDocsTranslationTitle
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(14.dp),
                            tint = GoogleDocsTranslationIcon.copy(alpha = 0.6f)
                        )
                    }
                    
                    // Content (dynamic height)
                    Text(
                        text = document.translatedText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoogleDocsTranslationText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // No translation - don't show anything
    }
}

// ============================================
// DOCUMENT ACTION BUTTONS
// ============================================

@Composable
private fun DocumentActionButtons(
    text: String,
    onMenuClick: () -> Unit,
    onRetryOcr: (() -> Unit)?,
    onRetryTranslation: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Action buttons row
        ActionButtonsRow(
            text = text,
            onRetry = onRetryOcr ?: onRetryTranslation,
            modifier = Modifier.weight(1f)
        )
        
        // More menu
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = GoogleDocsTextSecondary
            )
        }
    }
}

// ============================================
// HELPER EXTENSIONS
// ============================================

private fun ProcessingStatus.canRetryOcr(): Boolean =
    this is ProcessingStatus.Ocr.Failed || this is ProcessingStatus.Error

private fun ProcessingStatus.canRetryTranslation(): Boolean =
    this is ProcessingStatus.Translation.Failed
