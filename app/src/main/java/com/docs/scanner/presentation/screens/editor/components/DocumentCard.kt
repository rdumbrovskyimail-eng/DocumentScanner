/*
 * DocumentCard.kt
 * Version: 3.1.0 - PRODUCTION READY (2026) - 100% FIXED
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.docs.scanner.domain.core.ProcessingStatus
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.components.MicroButton
import com.docs.scanner.presentation.theme.*
import java.io.File
import java.util.Locale

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
    
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    
    onSharePage: (() -> Unit)? = null,
    onDeletePage: (() -> Unit)? = null,
    onMoveToRecord: (() -> Unit)? = null,
    
    onCopyText: ((String) -> Unit)? = null,
    onPasteText: ((Boolean) -> Unit)? = null,
    onAiRewrite: ((Boolean) -> Unit)? = null,
    onClearFormatting: ((Boolean) -> Unit)? = null,
    
    confidenceThreshold: Float = 0.7f,
    onWordTap: ((String, Float) -> Unit)? = null,
    
    onStartInlineEditOcr: (() -> Unit)? = null,
    onStartInlineEditTranslation: (() -> Unit)? = null,
    onInlineTextChange: ((String) -> Unit)? = null,
    onInlineEditComplete: (() -> Unit)? = null,
    
    dragModifier: Modifier = Modifier,
    
    modifier: Modifier = Modifier
) {
    var isInlineEditingOcr by remember { mutableStateOf(false) }
    var isInlineEditingTranslation by remember { mutableStateOf(false) }
    var inlineOcrText by remember(document.originalText) { mutableStateOf(document.originalText ?: "") }
    var inlineTranslationText by remember(document.translatedText) { mutableStateOf(document.translatedText ?: "") }
    
    val density = LocalDensity.current
    var photoHeight by remember { mutableStateOf(200.dp) }
    
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    AsyncImage(
                        model = File(document.imagePath),
                        contentDescription = "Document page ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
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
                            LoadingOcrState()
                        }
                        
                        document.processingStatus is ProcessingStatus.Ocr.Failed -> {
                            ErrorOcrState(onRetry = onRetryOcr)
                        }
                        
                        document.originalText.isNullOrBlank() -> {
                            EmptyOcrState()
                        }
                        
                        else -> {
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
                document.detectedLanguage?.let { lang ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = lang.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
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
        
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (isInlineEditing) {
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
                HighlightedConfidenceText(
                    text = document.originalText ?: "",
                    wordConfidences = document.wordConfidences,
                    threshold = confidenceThreshold,
                    onWordTap = onWordTap
                )
            } else {
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
        
        if (onPasteText != null) {
            MicroButton(
                text = "Paste",
                icon = Icons.Default.ContentPaste,
                onClick = {
                    pendingAction = "paste"
                    showTextSelector = true
                },
                enabled = true
            )
        }
        
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
            val cleanWord = word.replace(Regex("[^\\w]"), "")
            val confidence = wordConfidences[cleanWord] ?: wordConfidences[word] ?: 1f
            
            if (confidence < threshold) {
                val bgColor = when {
                    confidence < 0.5f -> Color(0xFFF44336).copy(alpha = 0.3f)
                    confidence < 0.7f -> Color(0xFFFF9800).copy(alpha = 0.3f)
                    else -> Color(0xFFFFC107).copy(alpha = 0.3f)
                }
                
                pushStringAnnotation("word", "$cleanWord|$confidence")
                withStyle(SpanStyle(background = bgColor)) {
                    append(word)
                }
                pop()
            } else {
                append(word)
            }
            
            if (index < words.size - 1) {
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

@Composable
private fun SmallIconButton(
    icon: ImageVector,
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