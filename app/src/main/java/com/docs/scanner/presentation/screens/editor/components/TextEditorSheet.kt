package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docs.scanner.presentation.theme.*

// ============================================
// TEXT EDITOR SHEET (Google Docs Style 2026)
// ============================================

/**
 * Большой редактор текста в виде BottomSheet-подобного диалога.
 * Занимает ~85% экрана, имеет handle для закрытия и Action Bar.
 * 
 * @param initialText Начальный текст для редактирования
 * @param title Заголовок редактора ("Edit OCR Text" / "Edit Translation")
 * @param onDismiss Callback при закрытии без сохранения
 * @param onSave Callback при сохранении с новым текстом
 * @param readOnly Режим только просмотра (без редактирования)
 */
@Composable
fun TextEditorSheet(
    initialText: String,
    title: String = "Edit Text",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    readOnly: Boolean = false
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(
            text = initialText,
            selection = TextRange(initialText.length)
        ))
    }
    
    val hasChanges = textFieldValue.text != initialText
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Auto-focus на текстовое поле
    LaunchedEffect(Unit) {
        if (!readOnly) {
            focusRequester.requestFocus()
        }
    }
    
    Dialog(
        onDismissRequest = {
            if (hasChanges) {
                // TODO: Показать диалог "Discard changes?"
            }
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Sheet Content
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Handle Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                    }
                    
                    // Header
                    EditorHeader(
                        title = title,
                        hasChanges = hasChanges,
                        readOnly = readOnly,
                        onClose = onDismiss,
                        onSave = { onSave(textFieldValue.text) }
                    )
                    
                    HorizontalDivider(color = GoogleDocsBorder)
                    
                    // Action Bar (Google Docs Style)
                    if (!readOnly) {
                        EditorActionBar(
                            onCopy = {
                                if (textFieldValue.selection.length > 0) {
                                    val selectedText = textFieldValue.text.substring(
                                        textFieldValue.selection.start,
                                        textFieldValue.selection.end
                                    )
                                    clipboardManager.setText(AnnotatedString(selectedText))
                                } else {
                                    clipboardManager.setText(AnnotatedString(textFieldValue.text))
                                }
                            },
                            onPaste = {
                                clipboardManager.getText()?.let { clipText ->
                                    val newText = textFieldValue.text.replaceRange(
                                        textFieldValue.selection.start,
                                        textFieldValue.selection.end,
                                        clipText.text
                                    )
                                    val newCursorPos = textFieldValue.selection.start + clipText.text.length
                                    textFieldValue = TextFieldValue(
                                        text = newText,
                                        selection = TextRange(newCursorPos)
                                    )
                                }
                            },
                            onSelectAll = {
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, textFieldValue.text.length)
                                )
                            },
                            onClear = {
                                textFieldValue = TextFieldValue(
                                    text = "",
                                    selection = TextRange(0)
                                )
                            },
                            onUndo = {
                                textFieldValue = TextFieldValue(
                                    text = initialText,
                                    selection = TextRange(initialText.length)
                                )
                            },
                            hasChanges = hasChanges
                        )
                        
                        HorizontalDivider(color = GoogleDocsBorder)
                    }
                    
                    // Text Editor Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (readOnly) {
                            // Read-only mode with scroll
                            Text(
                                text = textFieldValue.text.ifBlank { "No text available" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (textFieldValue.text.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        } else {
                            // Editable text field
                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { textFieldValue = it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(focusRequester)
                                    .verticalScroll(rememberScrollState()),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Default
                                ),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (textFieldValue.text.isEmpty()) {
                                            Text(
                                                text = "Enter text...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                    
                    // Character count
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${textFieldValue.text.length} characters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// HEADER COMPONENT
// ============================================

@Composable
private fun EditorHeader(
    title: String,
    hasChanges: Boolean,
    readOnly: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Close button
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Title with unsaved indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (hasChanges && !readOnly) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(GoogleDocsWarning, RoundedCornerShape(4.dp))
                )
            }
        }
        
        // Save button
        if (!readOnly) {
            TextButton(
                onClick = onSave,
                enabled = hasChanges
            ) {
                Text(
                    text = "Save",
                    color = if (hasChanges) GoogleDocsPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

// ============================================
// ACTION BAR (Google Docs Style)
// ============================================

@Composable
private fun EditorActionBar(
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onUndo: () -> Unit,
    hasChanges: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Undo
        ActionChip(
            icon = Icons.Default.Undo,
            label = "Undo",
            onClick = onUndo,
            enabled = hasChanges
        )
        
        VerticalDivider(
            modifier = Modifier.height(24.dp),
            color = GoogleDocsBorder
        )
        
        // Copy
        ActionChip(
            icon = Icons.Default.ContentCopy,
            label = "Copy",
            onClick = onCopy
        )
        
        // Paste
        ActionChip(
            icon = Icons.Default.ContentPaste,
            label = "Paste",
            onClick = onPaste
        )
        
        VerticalDivider(
            modifier = Modifier.height(24.dp),
            color = GoogleDocsBorder
        )
        
        // Select All
        ActionChip(
            icon = Icons.Default.SelectAll,
            label = "Select All",
            onClick = onSelectAll
        )
        
        // Clear
        ActionChip(
            icon = Icons.Default.Clear,
            label = "Clear",
            onClick = onClear,
            tint = GoogleDocsError
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) tint else tint.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun VerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
    Box(
        modifier = modifier
            .width(1.dp)
            .background(color)
    )
}
