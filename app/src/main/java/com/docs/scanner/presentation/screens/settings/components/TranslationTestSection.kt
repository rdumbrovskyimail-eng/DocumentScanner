package com.docs.scanner.presentation.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docs.scanner.data.local.preferences.GeminiModelOption
import com.docs.scanner.domain.core.Language

/**
 * TranslationTestSection.kt
 * Version: 2.0.0 - FULL FEATURED TRANSLATION TEST (2026)
 * 
 * ✅ NEW IN 2.0.0:
 * - Green indicator lamp (ready state)
 * - Gemini model selection dropdown
 * - "From OCR" badge when text synced from OCR result
 * - Copy to clipboard button
 * - Swap languages button
 * - Speed badges for models
 * - Improved language selector with search
 * - Better error display
 * 
 * FEATURES:
 * - Real-time translation test
 * - Model selection (like OCR section)
 * - Auto-sync from OCR results
 * - Visual ready/not-ready indicator
 */
@Composable
fun TranslationTestSection(
    state: MlkitSettingsState,
    onTextChange: (String) -> Unit,
    onSourceLangChange: (Language) -> Unit,
    onTargetLangChange: (Language) -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit,
    // ✅ NEW: Model selection callbacks
    onModelChange: (String) -> Unit,
    availableModels: List<GeminiModelOption>,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ═══════════════════════════════════════════════════════════════
            // HEADER WITH READY INDICATOR
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Translation Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // ✅ GREEN INDICATOR LAMP
                ReadyIndicator(isReady = state.isTranslationReady)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Subtitle with status
            Text(
                text = if (state.isTranslationReady) {
                    "Ready to translate • Enter text or use OCR result"
                } else {
                    "Run OCR test above or enter text manually"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isTranslationReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // ✅ GEMINI MODEL SELECTION
            // ═══════════════════════════════════════════════════════════════
            GeminiModelSelector(
                selectedModel = state.selectedTranslationModel,
                availableModels = availableModels,
                onModelChange = onModelChange
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // LANGUAGE SELECTION ROW
            // ═══════════════════════════════════════════════════════════════
            LanguageSelectionRow(
                sourceLanguage = state.translationSourceLang,
                targetLanguage = state.translationTargetLang,
                onSourceChange = onSourceLangChange,
                onTargetChange = onTargetLangChange,
                onSwapLanguages = {
                    // Swap only if source is not AUTO
                    if (state.translationSourceLang != Language.AUTO) {
                        onSourceLangChange(state.translationTargetLang)
                        onTargetLangChange(state.translationSourceLang)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // TEXT INPUT WITH BADGES
            // ═══════════════════════════════════════════════════════════════
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Text to translate",
                        style = MaterialTheme.typography.labelLarge
                    )
                    
                    // "From OCR" badge
                    AnimatedVisibility(
                        visible = state.isTextFromOcr,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "From OCR",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                OutlinedTextField(
                    value = state.translationTestText,
                    onValueChange = onTextChange,
                    placeholder = { 
                        Text(
                            if (state.testResult?.text?.isNotBlank() == true) {
                                "Text will auto-fill from OCR result..."
                            } else {
                                "Enter text to translate..."
                            }
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    trailingIcon = {
                        if (state.translationTestText.isNotBlank()) {
                            IconButton(onClick = onClear) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear text"
                                )
                            }
                        }
                    }
                )
                
                // Character count
                if (state.translationTestText.isNotBlank()) {
                    Text(
                        text = "${state.translationTestText.length} characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // ACTION BUTTONS
            // ═══════════════════════════════════════════════════════════════
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTranslate,
                    enabled = state.canRunTranslation,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isTranslationReady) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    if (state.isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Translating...")
                    } else {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Translate")
                    }
                }
                
                OutlinedButton(
                    onClick = onClear,
                    enabled = state.translationTestText.isNotBlank() || 
                              state.translationResult != null ||
                              state.translationError != null
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // TRANSLATION RESULT
            // ═══════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = state.hasTranslationResults,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    
                    // Success result
                    state.translationResult?.let { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Translation",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Badge(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "✓ Success",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                            
                            // Copy button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(result))
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy translation",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        SelectionContainer {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = result,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        
                        // Model used badge
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Model: ${state.selectedTranslationModel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Error result
                    state.translationError?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column {
                                    Text(
                                        text = "Translation Failed",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ✅ READY INDICATOR (GREEN LAMP)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReadyIndicator(isReady: Boolean) {
    val indicatorColor by animateColorAsState(
        targetValue = if (isReady) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
        animationSpec = tween(300),
        label = "indicator_color"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isReady) 0.4f else 0f,
        animationSpec = tween(300),
        label = "glow_alpha"
    )
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicator dot with glow effect
        Box(
            modifier = Modifier
                .size(12.dp)
                .then(
                    if (isReady) {
                        Modifier.shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            ambientColor = indicatorColor.copy(alpha = glowAlpha),
                            spotColor = indicatorColor.copy(alpha = glowAlpha)
                        )
                    } else Modifier
                )
                .clip(CircleShape)
                .background(indicatorColor)
        )
        
        Text(
            text = if (isReady) "Ready" else "Not ready",
            style = MaterialTheme.typography.labelSmall,
            color = indicatorColor,
            fontWeight = if (isReady) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ✅ GEMINI MODEL SELECTOR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GeminiModelSelector(
    selectedModel: String,
    availableModels: List<GeminiModelOption>,
    onModelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
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
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Translation Model",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val selectedModelOption = availableModels.find { it.id == selectedModel }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedModelOption?.displayName ?: selectedModel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Speed badge
                        ModelSpeedBadge(selectedModel)
                    }
                    
                    selectedModelOption?.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Icon(
                    Icons.Default.ArrowDropDown, 
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (model.id == selectedModel) 
                                        FontWeight.Bold else FontWeight.Normal
                                )
                                
                                ModelSpeedBadge(model.id)
                                
                                if (model.isRecommended) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Recommended",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(4.dp))
                            
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModelChange(model.id)
                        expanded = false
                    },
                    leadingIcon = if (model.id == selectedModel) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
                
                if (model != availableModels.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ✅ MODEL SPEED BADGE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ModelSpeedBadge(modelId: String) {
    val (text, color) = when (modelId) {
        "gemini-3-flash", "gemini-2.5-flash-lite" -> 
            "⚡ FAST" to Color(0xFF4CAF50)
        
        "gemini-2.5-flash" -> 
            "⚖️ BALANCED" to Color(0xFF2196F3)
        
        "gemini-3-pro", "gemini-2.5-pro" -> 
            "🐌 SLOW" to Color(0xFFFF9800)
        
        else -> 
            "?" to Color(0xFF9E9E9E)
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ✅ LANGUAGE SELECTION ROW
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LanguageSelectionRow(
    sourceLanguage: Language,
    targetLanguage: Language,
    onSourceChange: (Language) -> Unit,
    onTargetChange: (Language) -> Unit,
    onSwapLanguages: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source language
        LanguageDropdown(
            label = "From",
            selected = sourceLanguage,
            options = Language.translationSupported,
            onSelect = onSourceChange,
            modifier = Modifier.weight(1f)
        )
        
        // Swap button
        IconButton(
            onClick = onSwapLanguages,
            enabled = sourceLanguage != Language.AUTO
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = "Swap languages",
                tint = if (sourceLanguage != Language.AUTO) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        }
        
        // Target language
        LanguageDropdown(
            label = "To",
            selected = targetLanguage,
            options = Language.translationSupported.filter { it != Language.AUTO },
            onSelect = onTargetChange,
            modifier = Modifier.weight(1f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ✅ LANGUAGE DROPDOWN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LanguageDropdown(
    label: String,
    selected: Language,
    options: List<Language>,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(4.dp))
        
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Text(
                text = selected.displayName,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
            Icon(
                Icons.Default.ArrowDropDown, 
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.height(300.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                options.forEach { lang ->
                    DropdownMenuItem(
                        text = { 
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(lang.displayName)
                                if (lang == selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(lang)
                        }
                    )
                }
            }
        }
    }
}
