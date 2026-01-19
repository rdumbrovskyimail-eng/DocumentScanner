/*
 * MlkitSettingsSection.kt
 * Version: 11.0.0 - GEMINI MODEL SELECTION UI (2026)
 * 
 * ✅ NEW IN 11.0.0:
 * - Gemini model selection dropdown
 * - Model display names and descriptions
 * - Visual model selection UI
 * 
 * ✅ NEW IN 10.1.0:
 * - Auto-translation display in OCR results
 * - Translation target language badge
 * - Translation timing display
 * 
 * ✅ NEW IN 10.0.0:
 * - Shows "Scanned by: ML Kit" or "Scanned by: Gemini AI" badge
 * - Color-coded source badge (blue for ML Kit, purple for Gemini)
 * - Gemini fallback reason display with timing
 * - Processing time breakdown
 * 
 * LOCATION: com.docs.scanner.presentation.screens.settings.components
 */

package com.docs.scanner.presentation.screens.settings.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.data.remote.mlkit.OcrTestResult
import com.docs.scanner.data.local.security.ApiKeyEntry
import com.docs.scanner.data.local.preferences.GeminiModelOption
import com.docs.scanner.domain.core.OcrSource

@Composable
fun MlkitSettingsSection(
    state: MlkitSettingsState,
    apiKeys: List<ApiKeyEntry>,
    isLoadingKeys: Boolean,
    onScriptModeChange: (OcrScriptMode) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onConfidenceThresholdChange: (Float) -> Unit,
    onHighlightLowConfidenceChange: (Boolean) -> Unit,
    onShowWordConfidencesChange: (Boolean) -> Unit,
    onImageSelected: (Uri?) -> Unit,
    onTestOcr: () -> Unit,
    onClearTestResult: () -> Unit,
    // Gemini OCR callbacks
    onGeminiOcrEnabledChange: (Boolean) -> Unit,
    onGeminiOcrThresholdChange: (Int) -> Unit,
    onGeminiOcrAlwaysChange: (Boolean) -> Unit,
    onGeminiOcrModelChange: (String) -> Unit,  // ✅ НОВЫЙ ПАРАМЕТР
    // Test Gemini fallback
    onTestGeminiFallbackChange: (Boolean) -> Unit,
    // API Keys callbacks
    onAddApiKey: (key: String, label: String) -> Unit,
    onRemoveApiKey: (key: String) -> Unit,
    onSetPrimaryApiKey: (key: String) -> Unit,
    onResetApiKeyErrors: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // HEADER
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

            // SETTINGS
            Text(
                text = "OCR Script Mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            ScriptModeSelector(
                selectedMode = state.scriptMode,
                onModeSelected = onScriptModeChange
            )

            SettingToggleRow(
                title = "Auto-detect language",
                subtitle = "Automatically detect script and language from image",
                checked = state.autoDetectLanguage,
                onCheckedChange = onAutoDetectChange,
                icon = Icons.Default.AutoAwesome
            )

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

            SettingToggleRow(
                title = "Highlight low confidence words",
                subtitle = "Show red highlighting on words AI is uncertain about",
                checked = state.highlightLowConfidence,
                onCheckedChange = onHighlightLowConfidenceChange,
                icon = Icons.Default.Highlight
            )

            SettingToggleRow(
                title = "Show word confidence scores",
                subtitle = "Display confidence percentage for each word in test results",
                checked = state.showWordConfidences,
                onCheckedChange = onShowWordConfidencesChange,
                icon = Icons.Default.Analytics
            )

            HorizontalDivider()

            // OCR TEST SECTION
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

            // IMAGE PREVIEW WITH OCR OVERLAY
            AnimatedVisibility(
                visible = state.selectedImageUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.selectedImageUri?.let { uri ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    model = uri,
                                    contentDescription = "Selected image for OCR testing",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                
                                if (state.isTestRunning) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(color = Color.White)
                                            Text(
                                                "Processing OCR...",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                                
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
                        
                        state.testResult?.let { result ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickStat("Words", result.totalWords.toString())
                                QuickStat("Confidence", result.confidencePercent)
                                QuickStat("Quality", result.qualityRating)
                                QuickStat("Time", "${result.processingTimeMs}ms")
                            }
                        }
                    }
                }
            }

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

            // ════════════════════════════════════════════════════════════════
            // GEMINI OCR FALLBACK SECTION
            // ════════════════════════════════════════════════════════════════
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            GeminiOcrSettingsSection(
                enabled = state.geminiOcrEnabled,
                threshold = state.geminiOcrThreshold,
                alwaysUseGemini = state.geminiOcrAlways,
                selectedModel = state.selectedGeminiModel,  // ✅ ПЕРЕДАЕМ
                availableModels = state.availableGeminiModels,  // ✅ ПЕРЕДАЕМ
                onEnabledChange = onGeminiOcrEnabledChange,
                onThresholdChange = onGeminiOcrThresholdChange,
                onAlwaysUseGeminiChange = onGeminiOcrAlwaysChange,
                onModelChange = onGeminiOcrModelChange  // ✅ ПЕРЕДАЕМ
            )

            // ════════════════════════════════════════════════════════════════
            // API KEYS SECTION
            // ════════════════════════════════════════════════════════════════
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            ApiKeysSettingsSection(
                keys = apiKeys,
                isLoading = isLoadingKeys,
                onAddKey = onAddApiKey,
                onRemoveKey = onRemoveApiKey,
                onSetPrimary = onSetPrimaryApiKey,
                onResetErrors = onResetApiKeyErrors
            )

            // ════════════════════════════════════════════════════════════════
            // TEST GEMINI FALLBACK CHECKBOX
            // ════════════════════════════════════════════════════════════════
            
            AnimatedVisibility(
                visible = state.geminiOcrEnabled && state.selectedImageUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = state.testGeminiFallback,
                                onCheckedChange = onTestGeminiFallbackChange
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Test Gemini fallback",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Force Gemini OCR to test handwriting recognition",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ✅ ОБНОВЛЕННАЯ СЕКЦИЯ GEMINI OCR С ВЫБОРОМ МОДЕЛИ
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun GeminiOcrSettingsSection(
    enabled: Boolean,
    threshold: Int,
    alwaysUseGemini: Boolean,
    selectedModel: String,  // ✅ НОВЫЙ ПАРАМЕТР
    availableModels: List<GeminiModelOption>,  // ✅ НОВЫЙ ПАРАМЕТР
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onAlwaysUseGeminiChange: (Boolean) -> Unit,
    onModelChange: (String) -> Unit  // ✅ НОВЫЙ CALLBACK
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Gemini AI Fallback",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Use Gemini AI when ML Kit confidence is low or for handwritten text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ENABLE TOGGLE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Gemini fallback",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Automatically use Gemini for low-quality scans",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }

        // ✅ ВЫБОР МОДЕЛИ GEMINI (показываем только если enabled)
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Gemini Model",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                var expanded by remember { mutableStateOf(false) }
                
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
                            Text(
                                text = selectedModelOption?.displayName ?: selectedModel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            selectedModelOption?.description?.let { desc ->
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = model.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (model.id == selectedModel) 
                                                FontWeight.Bold else FontWeight.Normal
                                        )
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
                                    Spacer(Modifier.height(2.dp))
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

        // ALWAYS USE GEMINI TOGGLE
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Always use Gemini",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Skip ML Kit entirely (slower but more accurate)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = alwaysUseGemini,
                    onCheckedChange = onAlwaysUseGeminiChange
                )
            }
        }

        // THRESHOLD SLIDER (только если не always)
        AnimatedVisibility(
            visible = enabled && !alwaysUseGemini,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
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
                            text = "Switch to Gemini when ML Kit confidence is below this",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$threshold%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { onThresholdChange(it.toInt()) },
                    valueRange = 30f..90f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("30%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("90%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // INFO CARD
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (alwaysUseGemini) {
                            "Gemini AI will process all images. This is slower but provides best accuracy for handwritten text."
                        } else {
                            "ML Kit processes images first. If confidence is below $threshold%, Gemini AI takes over for better accuracy."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickStat(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
    
    Text(
        text = selectedMode.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

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

// ════════════════════════════════════════════════════════════════════════════════
// OCR TEST RESULT VIEW
// ════════════════════════════════════════════════════════════════════════════════

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
            // HEADER with Source Badge
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
                        text = "OCR Result",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    SourceBadge(source = result.source)
                }
                
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear result")
                }
            }

            // STATS ROW
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

            // CHIPS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                result.detectedLanguage?.let { lang ->
                    AssistChip(
                        onClick = {},
                        label = { Text("Language: ${lang.displayName}") },
                        leadingIcon = { 
                            Icon(Icons.Default.Language, null, modifier = Modifier.size(18.dp)) 
                        }
                    )
                }
                result.detectedScript?.let { script ->
                    AssistChip(
                        onClick = {},
                        label = { Text("Script: ${script.displayName}") },
                        leadingIcon = { 
                            Icon(Icons.Default.TextFields, null, modifier = Modifier.size(18.dp)) 
                        }
                    )
                }
            }

            // Gemini Fallback Info
            if (result.geminiFallbackTriggered) {
                GeminiFallbackInfoCard(
                    reason = result.geminiFallbackReason,
                    geminiTime = result.geminiProcessingTimeMs,
                    success = result.source == OcrSource.GEMINI
                )
            }

            // LOW CONFIDENCE WARNING
            if (result.lowConfidenceWords > 0 && result.source == OcrSource.ML_KIT) {
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

            // RECOGNIZED TEXT
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
                        } else if (highlightLowConfidence && result.lowConfidenceRanges.isNotEmpty() && result.source == OcrSource.ML_KIT) {
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

            // AUTO-TRANSLATION
            if (result.translatedText != null) {
                HorizontalDivider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Translation:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    
                    result.translationTargetLang?.let { lang ->
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                if (result.translationTimeMs != null) {
                    Text(
                        text = "Translated in ${result.translationTimeMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                SelectionContainer {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = result.translatedText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // WORD CONFIDENCES
            if (showWordConfidences && result.wordConfidences.isNotEmpty() && result.source == OcrSource.ML_KIT) {
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
private fun SourceBadge(source: OcrSource) {
    val (text, color, icon) = when (source) {
        OcrSource.ML_KIT -> Triple("ML Kit", Color(0xFF2196F3), Icons.Default.PhoneAndroid)
        OcrSource.GEMINI -> Triple("Gemini AI", Color(0xFF9C27B0), Icons.Default.AutoAwesome)
        OcrSource.UNKNOWN -> Triple("Unknown", Color(0xFF9E9E9E), Icons.Default.Help)
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun GeminiFallbackInfoCard(
    reason: String?,
    geminiTime: Long?,
    success: Boolean
) {
    val containerColor = if (success) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    }
    
    val iconColor = if (success) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (success) Icons.Default.AutoAwesome else Icons.Default.Warning,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (success) "✨ Gemini AI used" else "⚠️ Gemini fallback failed",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                
                reason?.let {
                    Text(
                        text = "Reason: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                geminiTime?.let {
                    Text(
                        text = "Gemini processing: ${it}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            val safeStart = range.first.coerceIn(0, text.length)
            val safeEnd = (range.last + 1).coerceIn(0, text.length)
            
            if (safeStart > lastEnd && lastEnd < text.length) {
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
        confidence >= 0.9f -> Color(0xFF4CAF50)
        confidence >= 0.7f -> Color(0xFFFF9800)
        confidence >= 0.5f -> Color(0xFFF44336)
        else -> Color(0xFF9C27B0)
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