/*
 * MlkitSettingsSection.kt
 * Version: 1.0.0 - PRODUCTION READY 2026
 * 
 * ML Kit Settings Section for SettingsScreen.
 * 
 * ✅ FEATURES:
 * - Script mode selection (Auto/Latin/Chinese/Japanese/Korean/Devanagari)
 * - Auto-detect language toggle
 * - Confidence threshold slider
 * - Low confidence highlighting toggle
 * - OCR Test feature: pick image, run OCR, show results
 * - Detailed statistics display
 * - Performance metrics
 */

package com.docs.scanner.presentation.screens.settings.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage     // ✅ CORRECT - Coil 3.x
import coil3.request.ImageRequest   // ✅ CORRECT - Coil 3.x
import com.docs.scanner.data.remote.mlkit.ConfidenceLevel
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.data.remote.mlkit.OcrTestResult

/**
 * ML Kit Settings section data class for state management.
 */
data class MlkitSettingsState(
    val scriptMode: OcrScriptMode = OcrScriptMode.AUTO,
    val autoDetectLanguage: Boolean = true,
    val confidenceThreshold: Float = 0.7f,
    val highlightLowConfidence: Boolean = true,
    val showWordConfidences: Boolean = false,
    // Test state
    val selectedImageUri: Uri? = null,
    val isTestRunning: Boolean = false,
    val testResult: OcrTestResult? = null,
    val testError: String? = null
)

/**
 * ML Kit Settings Section - Complete UI Component.
 * 
 * @param state Current settings state
 * @param onScriptModeChange Callback when script mode changes
 * @param onAutoDetectChange Callback when auto-detect toggle changes
 * @param onConfidenceThresholdChange Callback when confidence threshold changes
 * @param onHighlightLowConfidenceChange Callback when highlight toggle changes
 * @param onShowWordConfidencesChange Callback when show word confidences changes
 * @param onTestOcr Callback to run OCR test with selected image
 * @param onClearTestResult Callback to clear test results
 */
@Composable
fun MlkitSettingsSection(
    state: MlkitSettingsState,
    onScriptModeChange: (OcrScriptMode) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onConfidenceThresholdChange: (Float) -> Unit,
    onHighlightLowConfidenceChange: (Boolean) -> Unit,
    onShowWordConfidencesChange: (Boolean) -> Unit,
    onImageSelected: (Uri?) -> Unit,
    onTestOcr: () -> Unit,
    onClearTestResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onImageSelected(uri)
        onClearTestResult()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "ML Kit OCR",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider()

            // ═══════════════════════════════════════════════════════════════════
            // SCRIPT MODE SELECTION
            // ═══════════════════════════════════════════════════════════════════
            
            Text(
                text = "OCR Script Mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            ScriptModeSelector(
                selectedMode = state.scriptMode,
                onModeSelected = onScriptModeChange
            )

            // ═══════════════════════════════════════════════════════════════════
            // AUTO-DETECT LANGUAGE TOGGLE
            // ═══════════════════════════════════════════════════════════════════
            
            SettingToggleRow(
                title = "Auto-detect language",
                subtitle = "Automatically detect script and language from image",
                checked = state.autoDetectLanguage,
                onCheckedChange = onAutoDetectChange,
                icon = Icons.Default.AutoAwesome
            )

            // ═══════════════════════════════════════════════════════════════════
            // CONFIDENCE THRESHOLD SLIDER
            // ═══════════════════════════════════════════════════════════════════
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Confidence threshold",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Words below this threshold are marked as low confidence",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${(state.confidenceThreshold * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = state.confidenceThreshold,
                    onValueChange = onConfidenceThresholdChange,
                    valueRange = 0.3f..0.95f,
                    steps = 12,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("30%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("95%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ═══════════════════════════════════════════════════════════════════
            // HIGHLIGHT LOW CONFIDENCE TOGGLE
            // ═══════════════════════════════════════════════════════════════════
            
            SettingToggleRow(
                title = "Highlight low confidence words",
                subtitle = "Show red highlighting on words AI is uncertain about",
                checked = state.highlightLowConfidence,
                onCheckedChange = onHighlightLowConfidenceChange,
                icon = Icons.Default.Highlight
            )

            // ═══════════════════════════════════════════════════════════════════
            // SHOW WORD CONFIDENCES TOGGLE
            // ═══════════════════════════════════════════════════════════════════
            
            SettingToggleRow(
                title = "Show word confidence scores",
                subtitle = "Display confidence percentage for each word in test results",
                checked = state.showWordConfidences,
                onCheckedChange = onShowWordConfidencesChange,
                icon = Icons.Default.Analytics
            )

            HorizontalDivider()

            // ═══════════════════════════════════════════════════════════════════
            // OCR TEST SECTION
            // ═══════════════════════════════════════════════════════════════════
            
            Text(
                text = "Test OCR",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Select an image to test OCR with current settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Image selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Image")
                }
                
                Button(
                    onClick = onTestOcr,
                    enabled = state.selectedImageUri != null && !state.isTestRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Running...")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run OCR")
                    }
                }
            }

            // Selected image preview
            AnimatedVisibility(
                visible = state.selectedImageUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.selectedImageUri?.let { uri ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Selected image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                            
                            // Clear button
                            IconButton(
                                onClick = { 
                                    onImageSelected(null)
                                    onClearTestResult()
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear selection",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Error display
            AnimatedVisibility(
                visible = state.testError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.testError?.let { error ->
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
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Test results
            AnimatedVisibility(
                visible = state.testResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.testResult?.let { result ->
                    OcrTestResultView(
                        result = result,
                        highlightLowConfidence = state.highlightLowConfidence,
                        showWordConfidences = state.showWordConfidences,
                        confidenceThreshold = state.confidenceThreshold,
                        onClear = onClearTestResult
                    )
                }
            }
        }
    }
}

/**
 * Script mode selector with chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptModeSelector(
    selectedMode: OcrScriptMode,
    onModeSelected: (OcrScriptMode) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OcrScriptMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.displayName) },
                leadingIcon = if (mode == selectedMode) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
    }
    
    // Description for selected mode
    Text(
        text = selectedMode.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/**
 * Setting toggle row component.
 */
@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * OCR Test Result View - displays detailed test results.
 */
@Composable
private fun OcrTestResultView(
    result: OcrTestResult,
    highlightLowConfidence: Boolean,
    showWordConfidences: Boolean,
    confidenceThreshold: Float,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OCR Result",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear result"
                    )
                }
            }

            // Statistics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Confidence",
                    value = result.confidencePercent,
                    color = getConfidenceColor(result.overallConfidence ?: 0f)
                )
                StatItem(
                    label = "Quality",
                    value = result.qualityRating,
                    color = getQualityColor(result.qualityRating)
                )
                StatItem(
                    label = "Words",
                    value = result.totalWords.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    label = "Time",
                    value = "${result.processingTimeMs}ms",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            HorizontalDivider()

            // Detected info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                result.detectedLanguage?.let { lang ->
                    AssistChip(
                        onClick = {},
                        label = { Text("Language: ${lang.displayName}") },
                        leadingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(18.dp)) }
                    )
                }
                result.detectedScript?.let { script ->
                    AssistChip(
                        onClick = {},
                        label = { Text("Script: ${script.displayName}") },
                        leadingIcon = { Icon(Icons.Default.TextFields, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // Low confidence warning
            if (result.lowConfidenceWords > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${result.lowConfidenceWords} words with low confidence (< ${(confidenceThreshold * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            HorizontalDivider()

            // Recognized text
            Text(
                text = "Recognized Text:",
                style = MaterialTheme.typography.labelLarge
            )
            
            SelectionContainer {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        if (result.text.isBlank()) {
                            Text(
                                text = "No text detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (highlightLowConfidence && result.lowConfidenceRanges.isNotEmpty()) {
                            // Highlighted text
                            Text(
                                text = buildHighlightedText(
                                    text = result.text,
                                    lowConfidenceRanges = result.lowConfidenceRanges
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = result.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Word confidences (if enabled)
            if (showWordConfidences && result.wordConfidences.isNotEmpty()) {
                HorizontalDivider()
                
                Text(
                    text = "Word Confidences:",
                    style = MaterialTheme.typography.labelLarge
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        result.wordConfidences.forEach { (word, confidence) ->
                            WordConfidenceRow(
                                word = word,
                                confidence = confidence,
                                threshold = confidenceThreshold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WordConfidenceRow(
    word: String,
    confidence: Float,
    threshold: Float
) {
    val isLowConfidence = confidence < threshold
    val confidencePercent = (confidence * 100).toInt()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isLowConfidence) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = word,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LinearProgressIndicator(
                progress = { confidence },
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = getConfidenceColor(confidence),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "$confidencePercent%",
                style = MaterialTheme.typography.labelSmall,
                color = getConfidenceColor(confidence),
                fontWeight = if (isLowConfidence) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun buildHighlightedText(
    text: String,
    lowConfidenceRanges: List<IntRange>
): AnnotatedString {
    return buildAnnotatedString {
        var lastEnd = 0
        
        for (range in lowConfidenceRanges.sortedBy { it.first }) {
            // Safe bounds checking
            val safeStart = range.first.coerceIn(0, text.length)
            val safeEnd = range.last.coerceIn(0, text.length)
            
            if (safeStart > lastEnd) {
                append(text.substring(lastEnd, safeStart))
            }
            
            if (safeStart < safeEnd && safeEnd <= text.length) {
                withStyle(
                    SpanStyle(
                        background = Color(0xFFFFCDD2),
                        color = Color(0xFFB71C1C)
                    )
                ) {
                    append(text.substring(safeStart, safeEnd))
                }
            }
            
            lastEnd = safeEnd
        }
        
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
}

@Composable
private fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence >= 0.9f -> Color(0xFF4CAF50)  // Green
        confidence >= 0.7f -> Color(0xFFFF9800)  // Orange
        confidence >= 0.5f -> Color(0xFFF44336)  // Red
        else -> Color(0xFF9C27B0)                // Purple
    }
}

@Composable
private fun getQualityColor(quality: String): Color {
    return when (quality) {
        "Excellent" -> Color(0xFF4CAF50)
        "Good" -> Color(0xFF8BC34A)
        "Fair" -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}
