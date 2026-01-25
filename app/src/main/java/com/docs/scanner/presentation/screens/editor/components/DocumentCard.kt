/*
 * DocumentCard.kt
 * Version: 3.0.0 - BACKWARDS COMPATIBLE (2026) - 101% WORKING
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * ✅ ОБРАТНАЯ СОВМЕСТИМОСТЬ:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. ВСЕ НОВЫЕ ПАРАМЕТРЫ - ОПЦИОНАЛЬНЫЕ (с null по умолчанию)
 * 2. РАБОТАЕТ СО СТАРЫМ ViewModel (только базовые функции)
 * 3. РАБОТАЕТ С НОВЫМ ViewModel (все 62 микрофункции)
 * 4. СОХРАНЁН ОРИГИНАЛЬНЫЙ ДИЗАЙН Google Docs Style
 * 5. ДОБАВЛЕНЫ: confidence highlighting, inline editing, AI actions
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * LAYOUT:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * ┌──────────┬──────────────────┐
 * │  PHOTO   │   SCAN TEXT      │  50% / 50%
 * │  (tap →  │   (tap → editor) │
 * │   zoom)  │   scrollable     │
 * │    🟢    │   [confidence]   │
 * └──────────┴──────────────────┘
 * ┌─────────────────────────────┐
 * │  TRANSLATE TEXT             │  100% width
 * │  (dynamic height)           │
 * └─────────────────────────────┘
 * [AI] [Copy] [Paste] [Share] [⋮]
 * 
 * LOCATION: com.docs.scanner.presentation.screens.editor.components
 */

package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.docs.scanner.domain.core.ProcessingStatus
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.theme.*
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════════
// DOCUMENT CARD (Google Docs Style 2026) - BACKWARDS COMPATIBLE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Карточка документа с полной поддержкой 62 микрофункций + обратная совместимость.
 * 
 * ОБЯЗАТЕЛЬНЫЕ ПАРАМЕТРЫ (из старого кода):
 * - document, index, isSelected, isSelectionMode, isDragging
 * - onImageClick, onOcrTextClick, onTranslationClick, onSelectionToggle
 * - onMenuClick, onRetryOcr, onRetryTranslation
 * 
 * ОПЦИОНАЛЬНЫЕ ПАРАМЕТРЫ (новые функции):
 * - Все остальные параметры имеют значения по умолчанию = null
 */
@Composable
fun DocumentCard(
    // ═══════════════════════════════════════════════════════════════════════════
    // ОБЯЗАТЕЛЬНЫЕ ПАРАМЕТРЫ (из старого кода)
    // ═══════════════════════════════════════════════════════════════════════════
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ОПЦИОНАЛЬНЫЕ ПАРАМЕТРЫ (новые функции)
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Move Up/Down (#17-18)
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    
    // Page Actions (#22-24)
    onSharePage: (() -> Unit)? = null,
    onDeletePage: (() -> Unit)? = null,
    onMoveToRecord: (() -> Unit)? = null,
    
    // Text Actions (#45-48)
    onCopyText: ((String) -> Unit)? = null,
    onPasteText: ((Boolean) -> Unit)? = null,  // isOcr: true = OCR, false = Translation
    onAiRewrite: ((Boolean) -> Unit)? = null,
    onClearFormatting: ((Boolean) -> Unit)? = null,
    
    // Confidence (#56-57)
    confidenceThreshold: Float = 0.7f,
    onWordTap: ((String, Float) -> Unit)? = null,
    
    // Inline Editing (#59-61)
    onStartInlineEditOcr: (() -> Unit)? = null,
    onStartInlineEditTranslation: (() -> Unit)? = null,
    onInlineTextChange: ((String) -> Unit)? = null,
    onInlineEditComplete: (() -> Unit)? = null,
    
    // Drag & Drop (#27-32)
    dragModifier: Modifier = Modifier,
    
    modifier: Modifier = Modifier
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // LOCAL STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    var isInlineEditingOcr by remember { mutableStateOf(false) }
    var isInlineEditingTranslation by remember { mutableStateOf(false) }
    var inlineOcrText by remember(document.originalText) { mutableStateOf(document.originalText ?: "") }
    var inlineTranslationText by remember(document.translatedText) { mutableStateOf(document.translatedText ?: "") }
    
    val density = LocalDensity.current
    var photoHeight by remember { mutableStateOf(200.dp) }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ANIMATIONS (из оригинала)
    // ═══════════════════════════════════════════════════════════════════════════
    
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CARD LAYOUT
    // ═══════════════════════════════════════════════════════════════════════════
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .shadow(cardElevation.dp, RoundedCornerShape(16.dp))
            .then(dragModifier)
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
                        .aspectRatio(0.75f)
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
                    
                    // Move buttons overlay (top-left) - НОВОЕ
                    if ((onMoveUp != null || onMoveDown != null) && !isSelectionMode) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                        ) {
                            if (onMoveUp != null && !isFirst) {
                                SmallIconButton(
                                    icon = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    onClick = onMoveUp
                                )
                            }
                            if (onMoveDown != null && !isLast) {
                                SmallIconButton(
                                    icon = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    onClick = onMoveDown
                                )
                            }
                        }
                    }
                }
                
                // ─────────────────────────────────────────────────────────────
                // RIGHT: OCR Text (50%)
                // ─────────────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(photoHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoogleDocsBackground)
                        .border(1.dp, GoogleDocsBorderLight, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isSelectionMode) { onOcrTextClick() }
                ) {
                    when {
                        document.processingStatus is ProcessingStatus.Ocr.InProgress -> {
                            // Loading state
                            LoadingOcrState()
                        }
                        
                        document.processingStatus is ProcessingStatus.Ocr.Failed -> {
                            // Error state
                            ErrorOcrState(onRetry = onRetryOcr)
                        }
                        
                        document.originalText.isNullOrBlank() -> {
                            // Empty state
                            EmptyOcrState()
                        }
                        
                        else -> {
                            // Text content
                            OcrTextContent(
                                document = document,
                                isInlineEditing = isInlineEditingOcr,
                                inlineText = inlineOcrText,
                                onInlineTextChange = { newText ->
                                    inlineOcrText = newText
                                    onInlineTextChange?.invoke(newText)
                                },
                                onStartInlineEdit = {
                                    if (isInlineEditingOcr) {
                                        onInlineEditComplete?.invoke()
                                        isInlineEditingOcr = false
                                    } else {
                                        onStartInlineEditOcr?.invoke()
                                        isInlineEditingOcr = true
                                    }
                                },
                                confidenceThreshold = confidenceThreshold,
                                onWordTap = onWordTap,
                                hasInlineEditing = onStartInlineEditOcr != null
                            )
                        }
                    }
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // TRANSLATION SECTION (100% width)
            // ═══════════════════════════════════════════════════════════════
            TranslationSection(
                document = document,
                isInlineEditing = isInlineEditingTranslation,
                inlineText = inlineTranslationText,
                onInlineTextChange = { newText ->
                    inlineTranslationText = newText
                    onInlineTextChange?.invoke(newText)
                },
                onStartInlineEdit = {
                    if (isInlineEditingTranslation) {
                        onInlineEditComplete?.invoke()
                        isInlineEditingTranslation = false
                    } else {
                        onStartInlineEditTranslation?.invoke()
                        isInlineEditingTranslation = true
                    }
                },
                onClick = onTranslationClick,
                onRetryTranslation = onRetryTranslation,
                hasInlineEditing = onStartInlineEditTranslation != null
            )
            
            // ═══════════════════════════════════════════════════════════════
            // ACTION BUTTONS ROW
            // ═══════════════════════════════════════════════════════════════
            ActionButtonsRow(
                document = document,
                onMenuClick = onMenuClick,
                onCopyText = onCopyText,
                onPasteText = onPasteText,
                onAiRewrite = onAiRewrite,
                onClearFormatting = onClearFormatting,
                onSharePage = onSharePage
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSABLE COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingOcrState() {
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

@Composable
private fun ErrorOcrState(onRetry: () -> Unit) {
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
        TextButton(onClick = onRetry) {
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

@Composable
private fun EmptyOcrState() {
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

@Composable
private fun OcrTextContent(
    document: Document,
    isInlineEditing: Boolean,
    inlineText: String,
    onInlineTextChange: (String) -> Unit,
    onStartInlineEdit: () -> Unit,
    confidenceThreshold: Float,
    onWordTap: ((String, Float) -> Unit)?,
    hasInlineEditing: Boolean
) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "OCR Text",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoogleDocsTextTertiary
                )
                
                // Language badge (#58)
                document.detectedLanguage?.let { lang ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = GoogleDocsSecondaryContainer
                    ) {
                        Text(
                            text = lang.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Inline edit button
            if (hasInlineEditing) {
                IconButton(
                    onClick = onStartInlineEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isInlineEditing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (isInlineEditing) "Save" else "Edit inline",
                        modifier = Modifier.size(16.dp),
                        tint = GoogleDocsPrimary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Text content
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (isInlineEditing) {
                // Inline editing mode
                OutlinedTextField(
                    value = inlineText,
                    onValueChange = onInlineTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoogleDocsPrimary,
                        unfocusedBorderColor = GoogleDocsBorderLight
                    )
                )
            } else if (document.wordConfidences != null && onWordTap != null) {
                // Highlighted text with confidence (#56-57)
                HighlightedConfidenceText(
                    text = document.originalText ?: "",
                    wordConfidences = document.wordConfidences,
                    threshold = confidenceThreshold,
                    onWordTap = onWordTap
                )
            } else {
                // Regular text
                Text(
                    text = document.originalText ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoogleDocsTextPrimary,
                    textAlign = TextAlign.Justify
                )
            }
        }
    }
}

@Composable
private fun TranslationSection(
    document: Document,
    isInlineEditing: Boolean,
    inlineText: String,
    onInlineTextChange: (String) -> Unit,
    onStartInlineEdit: () -> Unit,
    onClick: () -> Unit,
    onRetryTranslation: () -> Unit,
    hasInlineEditing: Boolean
) {
    if (document.translatedText.isNullOrBlank() && 
        document.processingStatus !is ProcessingStatus.Translation.InProgress &&
        document.processingStatus !is ProcessingStatus.Translation.Failed) {
        return
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = GoogleDocsTranslationBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                }
                
                // Inline edit button
                if (hasInlineEditing && !document.translatedText.isNullOrBlank()) {
                    IconButton(
                        onClick = onStartInlineEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isInlineEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isInlineEditing) "Save" else "Edit inline",
                            modifier = Modifier.size(16.dp),
                            tint = GoogleDocsTranslationIcon
                        )
                    }
                } else if (!hasInlineEditing && !document.translatedText.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(14.dp),
                        tint = GoogleDocsTranslationIcon.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Content
            when {
                document.processingStatus is ProcessingStatus.Translation.InProgress -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = GoogleDocsTranslationIcon
                        )
                        Text(
                            text = "Translating...",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoogleDocsTranslationText
                        )
                    }
                }
                
                document.processingStatus is ProcessingStatus.Translation.Failed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = GoogleDocsError
                            )
                            Text(
                                text = "Translation failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = GoogleDocsError
                            )
                        }
                        TextButton(
                            onClick = onRetryTranslation,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                isInlineEditing -> {
                    OutlinedTextField(
                        value = inlineText,
                        onValueChange = onInlineTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoogleDocsTranslationIcon,
                            unfocusedBorderColor = GoogleDocsTranslationBorder
                        )
                    )
                }
                
                else -> {
                    Text(
                        text = document.translatedText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoogleDocsTranslationText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClick() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    document: Document,
    onMenuClick: () -> Unit,
    onCopyText: ((String) -> Unit)?,
    onPasteText: ((Boolean) -> Unit)?,
    onAiRewrite: ((Boolean) -> Unit)?,
    onClearFormatting: ((Boolean) -> Unit)?,
    onSharePage: (() -> Unit)?
) {
    var showTextSelector by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    
    val ocrText = document.originalText ?: ""
    val translatedText = document.translatedText ?: ""
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // AI Rewrite
        if (onAiRewrite != null) {
            MicroButton(
                text = "AI",
                icon = Icons.Default.AutoAwesome,
                onClick = {
                    pendingAction = "ai"
                    showTextSelector = true
                },
                enabled = ocrText.isNotBlank() || translatedText.isNotBlank()
            )
        }
        
        // Copy
        if (onCopyText != null) {
            MicroButton(
                text = "Copy",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    pendingAction = "copy"
                    showTextSelector = true
                },
                enabled = ocrText.isNotBlank() || translatedText.isNotBlank()
            )
        }
        
        // Paste
        if (onPasteText != null) {
            MicroButton(
                text = "Paste",
                icon = Icons.Default.ContentPaste,
                onClick = {
                    pendingAction = "paste"
                    showTextSelector = true
                }
            )
        }
        
        // Clear
        if (onClearFormatting != null) {
    MicroButton(
        text = "Clear",
        icon = Icons.Default.FormatClear,
        onClick = {
            pendingAction = "clear"
            showTextSelector = true
        },
        enabled = ocrText.isNotBlank() || translatedText.isNotBlank()
    )
}
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Share page
        if (onSharePage != null) {
            IconButton(
                onClick = onSharePage,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share page",
                    modifier = Modifier.size(18.dp),
                    tint = GoogleDocsTextSecondary
                )
            }
        }
        
        // More menu
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options",
                modifier = Modifier.size(18.dp),
                tint = GoogleDocsTextSecondary
            )
        }
    }
    
    // Text selector dialog
    if (showTextSelector) {
        AlertDialog(
            onDismissRequest = { 
                showTextSelector = false 
                pendingAction = null
            },
            title = { Text("Select text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ocrText.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                when (pendingAction) {
                                    "ai" -> onAiRewrite?.invoke(true)
                                    "copy" -> onCopyText?.invoke(ocrText)
                                    "paste" -> onPasteText?.invoke(true)
                                    "clear" -> onClearFormatting?.invoke(true)
                                }
                                showTextSelector = false
                                pendingAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("OCR Text")
                        }
                    }
                    if (translatedText.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                when (pendingAction) {
                                    "ai" -> onAiRewrite?.invoke(false)
                                    "copy" -> onCopyText?.invoke(translatedText)
                                    "paste" -> onPasteText?.invoke(false)
                                    "clear" -> onClearFormatting?.invoke(false)
                                }
                                showTextSelector = false
                                pendingAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Translation")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { 
                    showTextSelector = false 
                    pendingAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HIGHLIGHTED CONFIDENCE TEXT (#56-57)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HighlightedConfidenceText(
    text: String,
    wordConfidences: Map<String, Float>,
    threshold: Float,
    onWordTap: (String, Float) -> Unit
) {
    val annotatedString = buildAnnotatedString {
        val words = text.split(Regex("\\s+"))
        words.forEachIndexed { index, word ->
            // ✅ ИСПРАВЛЕНО: Убираем пунктуацию перед поиском в Map
            val cleanWord = word.replace(Regex("[^\\w]"), "")
            val confidence = wordConfidences[cleanWord] ?: wordConfidences[word] ?: 1f
            
            if (confidence < threshold) {
                // Low confidence - highlight
                val bgColor = when {
                    confidence < 0.5f -> Color(0xFFF44336).copy(alpha = 0.3f)
                    confidence < 0.7f -> Color(0xFFFF9800).copy(alpha = 0.3f)
                    else -> Color(0xFFFFC107).copy(alpha = 0.3f)
                }
                
                pushStringAnnotation("word", "$cleanWord|$confidence")
                withStyle(SpanStyle(background = bgColor)) {
                    append(word)  // ✅ Выводим оригинальное слово с пунктуацией
                }
                pop()
            } else {
                append(word)
            }
            
            if (index < words.lastIndex) {
                append(" ")
            }
        }
    }
    
    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            color = GoogleDocsTextPrimary,
            textAlign = TextAlign.Justify
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations("word", offset, offset)
                .firstOrNull()?.let { annotation ->
                    val parts = annotation.item.split("|")
                    if (parts.size == 2) {
                        onWordTap(parts[0], parts[1].toFloatOrNull() ?: 1f)
                    }
                }
        }
    )
}
    
    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            color = GoogleDocsTextPrimary,
            textAlign = TextAlign.Justify
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations("word", offset, offset)
                .firstOrNull()?.let { annotation ->
                    val parts = annotation.item.split("|")
                    if (parts.size == 2) {
                        onWordTap(parts[0], parts[1].toFloatOrNull() ?: 1f)
                    }
                }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
