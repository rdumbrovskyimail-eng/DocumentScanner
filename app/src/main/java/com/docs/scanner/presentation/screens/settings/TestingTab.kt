/*
 * TestingTab.kt
 * Version: 1.0.0 - COMPLETE REDESIGN (2026)
 * 
 * ✅ NEW STRUCTURE:
 * 1. OCR Test (with LOCAL model settings)
 * 2. Translation Test (with LOCAL model settings + auto-sync)
 * 3. Debug Tools (Log Collector OFF by default)
 * ❌ App Info REMOVED
 * 
 * CRITICAL FEATURES:
 * - Local settings (не сохраняются в DataStore)
 * - 🟢/🔴 Status indicator for translation
 * - Auto-sync OCR result → Translation input
 * - Model selectors in BOTH sections
 */

package com.docs.scanner.presentation.screens.settings

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.preferences.GeminiModelOption
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.OcrSource
import com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState
import com.docs.scanner.util.LogcatCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

// ═══════════════════════════════════════════════════════════════════════════
// TESTING TAB - MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun TestingTab(
    mlkitSettings: MlkitSettingsState,
    onImageSelected: (android.net.Uri?) -> Unit,
    onTestOcr: () -> Unit,
    onClearTestResult: () -> Unit,
    onCancelOcr: () -> Unit,
    onTestGeminiFallbackChange: (Boolean) -> Unit,
    onTranslationTestTextChange: (String) -> Unit,
    onTranslationSourceLangChange: (Language) -> Unit,
    onTranslationTargetLangChange: (Language) -> Unit,
    onTranslationTest: () -> Unit,
    onClearTranslationTest: () -> Unit,
    onDebugClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logCollector = remember { LogcatCollector.getInstance(context) }
    
    var isCollecting by remember { mutableStateOf(false) } // ✅ OFF by default
    var collectedLines by remember { mutableStateOf(0) }
    
    // ✅ LOCAL model settings (не сохраняются)
    var localOcrModel by remember { mutableStateOf(mlkitSettings.selectedGeminiModel) }
    var localOcrThreshold by remember { mutableStateOf(mlkitSettings.geminiOcrThreshold) }
    var localOcrAlways by remember { mutableStateOf(mlkitSettings.geminiOcrAlways) }
    
    var localTranslationModel by remember { mutableStateOf(mlkitSettings.selectedTranslationModel) }

    LaunchedEffect(isCollecting) {
        while (isCollecting) {
            collectedLines = logCollector.getCollectedLinesCount()
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════
        // 1. OCR TEST (with local settings)
        // ═══════════════════════════════════════════════════════════════
        OcrTestCard(
            mlkitSettings = mlkitSettings,
            localModel = localOcrModel,
            localThreshold = localOcrThreshold,
            localAlways = localOcrAlways,
            onLocalModelChange = { localOcrModel = it },
            onLocalThresholdChange = { localOcrThreshold = it },
            onLocalAlwaysChange = { localOcrAlways = it },
            onImageSelected = onImageSelected,
            onTestOcr = onTestOcr,
            onClearTestResult = onClearTestResult,
            onCancelOcr = onCancelOcr,
            onTestGeminiFallbackChange = onTestGeminiFallbackChange
        )
        
        // ═══════════════════════════════════════════════════════════════
        // 2. TRANSLATION TEST (with local model + auto-sync)
        // ═══════════════════════════════════════════════════════════════
        TranslationTestCard(
            mlkitSettings = mlkitSettings,
            localModel = localTranslationModel,
            onLocalModelChange = { localTranslationModel = it },
            onTextChange = onTranslationTestTextChange,
            onSourceLangChange = onTranslationSourceLangChange,
            onTargetLangChange = onTranslationTargetLangChange,
            onTranslate = onTranslationTest,
            onClear = onClearTranslationTest
        )
        
        // ═══════════════════════════════════════════════════════════════
        // 3. DEBUG TOOLS (minimal, OFF by default)
        // ═══════════════════════════════════════════════════════════════
        DebugToolsCard(
            isCollecting = isCollecting,
            collectedLines = collectedLines,
            onStartStop = {
                if (isCollecting) {
                    logCollector.stopCollecting()
                    isCollecting = false
                } else {
                    logCollector.startCollecting()
                    isCollecting = true
                    collectedLines = 0
                }
            },
            onSave = {
                scope.launch {
                    if (logCollector.getCollectedLinesCount() == 0) {
                        snackbarHostState.showSnackbar(
                            "⚠️ No logs collected. Press START first.",
                            duration = SnackbarDuration.Short
                        )
                        return@launch
                    }
                    logCollector.saveLogsNow()
                    delay(500)
                    snackbarHostState.showSnackbar(
                        "✅ ${logCollector.getCollectedLinesCount()} lines saved to Downloads/",
                        duration = SnackbarDuration.Long
                    )
                }
            },
            onOpenDebugViewer = onDebugClick
        )
        
        // ❌ APP INFO REMOVED
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 1. OCR TEST CARD (with local settings)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun OcrTestCard(
    mlkitSettings: MlkitSettingsState,
    localModel: String,
    localThreshold: Int,
    localAlways: Boolean,
    onLocalModelChange: (String) -> Unit,
    onLocalThresholdChange: (Int) -> Unit,
    onLocalAlwaysChange: (Boolean) -> Unit,
    onImageSelected: (android.net.Uri?) -> Unit,
    onTestOcr: () -> Unit,
    onClearTestResult: () -> Unit,
    onCancelOcr: () -> Unit,
    onTestGeminiFallbackChange: (Boolean) -> Unit
) {
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onImageSelected(uri)
        onClearTestResult()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ───────────────────────────────────────────────────────────
            // HEADER
            // ───────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "OCR Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = "Test OCR with current settings on any image",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // ───────────────────────────────────────────────────────────
            // ACTION BUTTONS
            // ───────────────────────────────────────────────────────────
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
                
                if (mlkitSettings.isTestRunning) {
                    OutlinedButton(
                        onClick = onCancelOcr,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    }
                } else {
                    Button(
                        onClick = onTestOcr,
                        enabled = mlkitSettings.selectedImageUri != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run OCR")
                    }
                }
            }
            
            // ───────────────────────────────────────────────────────────
            // ✅ LOCAL FALLBACK SETTINGS (for testing only)
            // ───────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = mlkitSettings.geminiOcrEnabled && mlkitSettings.selectedImageUri != null
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    
                    Text(
                        text = "⚙️ Test Settings (local, not saved)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Model selector
                    Text(
                        "Gemini Model",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    LocalModelSelector(
                        selectedModel = localModel,
                        availableModels = mlkitSettings.availableGeminiModels,
                        onModelChange = onLocalModelChange
                    )
                    
                    // Always use Gemini toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Always use Gemini", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Skip ML Kit entirely",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = localAlways,
                            onCheckedChange = onLocalAlwaysChange
                        )
                    }
                    
                    // Threshold slider (only if not always)
                    AnimatedVisibility(visible = !localAlways) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth(),
                                Arrangement.SpaceBetween,
                                Alignment.CenterVertically
                            ) {
                                Text("Quality threshold", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "$localThreshold%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = localThreshold.toFloat(),
                                onValueChange = { onLocalThresholdChange(it.toInt()) },
                                valueRange = 30f..80f,
                                steps = 9
                            )
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(
                                    "More ML Kit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "More Gemini",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Force Gemini checkbox
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = mlkitSettings.testGeminiFallback,
                                onCheckedChange = onTestGeminiFallbackChange
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Force Gemini OCR",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Test handwriting recognition",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // ───────────────────────────────────────────────────────────
            // SELECTED IMAGE PREVIEW
            // ───────────────────────────────────────────────────────────
            AnimatedVisibility(visible = mlkitSettings.selectedImageUri != null) {
                mlkitSettings.selectedImageUri?.let { uri ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        
                        Card(
                            Modifier.fillMaxWidth().height(200.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected image",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                
                                if (mlkitSettings.isTestRunning) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
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
                                            MaterialTheme.colorScheme.surface.copy(0.8f),
                                            RoundedCornerShape(50)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Clear",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        
                        // Quick stats
                        mlkitSettings.testResult?.let { result ->
                            Row(
                                Modifier.fillMaxWidth(),
                                Arrangement.SpaceEvenly
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
            
            // ───────────────────────────────────────────────────────────
            // ERROR MESSAGE
            // ───────────────────────────────────────────────────────────
            AnimatedVisibility(visible = mlkitSettings.testError != null) {
                mlkitSettings.testError?.let { error ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // ───────────────────────────────────────────────────────────
            // OCR RESULT with SOURCE BADGE
            // ───────────────────────────────────────────────────────────
            AnimatedVisibility(visible = mlkitSettings.testResult != null) {
                mlkitSettings.testResult?.let { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        
                        Row(
                            Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Text(
                                "📄 OCR Result:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            
                            // ✅ SOURCE BADGE (ML Kit / Gemini)
                            SourceBadge(source = result.source)
                        }
                        
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = result.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 2. TRANSLATION TEST CARD (with local model + auto-sync)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TranslationTestCard(
    mlkitSettings: MlkitSettingsState,
    localModel: String,
    onLocalModelChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSourceLangChange: (Language) -> Unit,
    onTargetLangChange: (Language) -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ───────────────────────────────────────────────────────────
            // HEADER with STATUS INDICATOR
            // ───────────────────────────────────────────────────────────
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
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Translation Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // ✅ STATUS INDICATOR (🔴/🟢)
                StatusIndicator(isReady = mlkitSettings.isTranslationReady)
            }
            
            // ✅ "FROM OCR" BADGE
            AnimatedVisibility(visible = mlkitSettings.isTextFromOcr) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "Text from OCR result",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            // ───────────────────────────────────────────────────────────
            // ✅ LOCAL TRANSLATION SETTINGS
            // ───────────────────────────────────────────────────────────
            HorizontalDivider()
            
            Text(
                text = "⚙️ Test Settings (local, not saved)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Model selector
            Text(
                "Gemini Model",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            LocalModelSelector(
                selectedModel = localModel,
                availableModels = mlkitSettings.availableTranslationModels,
                onModelChange = onLocalModelChange
            )
            
            // Language dropdowns
            LanguageDropdown(
                label = "Source Language",
                selected = mlkitSettings.translationSourceLang,
                options = Language.translationSupported,
                onSelect = onSourceLangChange
            )
            
            Spacer(Modifier.height(8.dp))
            
            LanguageDropdown(
                label = "Target Language",
                selected = mlkitSettings.translationTargetLang,
                options = Language.translationSupported.filter { it != Language.AUTO },
                onSelect = onTargetLangChange
            )
            
            // ───────────────────────────────────────────────────────────
            // INPUT FIELD
            // ───────────────────────────────────────────────────────────
            HorizontalDivider()
            
            OutlinedTextField(
                value = mlkitSettings.translationTestText,
                onValueChange = onTextChange,
                label = { Text("Text to translate") },
                placeholder = { Text("Enter text or use OCR result...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
            
            // ───────────────────────────────────────────────────────────
            // ACTION BUTTONS
            // ───────────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTranslate,
                    enabled = mlkitSettings.translationTestText.isNotBlank() && !mlkitSettings.isTranslating,
                    modifier = Modifier.weight(1f)
                ) {
                    if (mlkitSettings.isTranslating) {
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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }
            
            // ───────────────────────────────────────────────────────────
            // RESULT / ERROR
            // ───────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = mlkitSettings.translationResult != null || mlkitSettings.translationError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    
                    // Success
                    mlkitSettings.translationResult?.let { result ->
                        Text(
                            text = "📋 Translation:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        
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
                    
                    // Error
                    mlkitSettings.translationError?.let { error ->
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
                                Text(
                                    text = error,
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

// ═══════════════════════════════════════════════════════════════════════════
// 3. DEBUG TOOLS CARD (minimal, OFF by default)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DebugToolsCard(
    isCollecting: Boolean,
    collectedLines: Int,
    onStartStop: () -> Unit,
    onSave: () -> Unit,
    onOpenDebugViewer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
) {
            // ───────────────────────────────────────────────────────────
            // HEADER
            // ───────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Debug Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // ───────────────────────────────────────────────────────────
            // OCR LOG COLLECTOR
            // ───────────────────────────────────────────────────────────
            Text(
                "OCR Log Collector",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Capture real-time logs to diagnose OCR/MLKit issues",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Status indicator
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isCollecting) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isCollecting) "Collecting..." else "Stopped",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (isCollecting || collectedLines > 0) {
                    Text(
                        "$collectedLines lines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Control buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCollecting) "Stop" else "Start")
                }
                OutlinedButton(
                    onClick = onSave,
                    enabled = collectedLines > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            
            // ───────────────────────────────────────────────────────────
            // DEBUG VIEWER
            // ───────────────────────────────────────────────────────────
            Text(
                "Debug Logs Viewer",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "View historical debug logs and session data",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(12.dp))
            
            Button(
                onClick = onOpenDebugViewer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Debug Viewer")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Local model selector (not saved to DataStore)
 */
@Composable
private fun LocalModelSelector(
    selectedModel: String,
    availableModels: List<GeminiModelOption>,
    onModelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = availableModels.find { it.id == selectedModel } 
        ?: availableModels.firstOrNull()
    
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = currentModel?.displayName ?: "Select model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                currentModel?.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select model"
            )
        }
    }
    
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (model.id == selectedModel) 
                                        FontWeight.Bold else FontWeight.Normal
                                )
                                
                                Spacer(Modifier.height(4.dp))
                                
                                // Speed badge
                                ModelSpeedBadge(model.id)
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
                }
            )
            if (model != availableModels.last()) {
                HorizontalDivider()
            }
        }
    }
}

/**
 * Language dropdown selector
 */
@Composable
private fun LanguageDropdown(
    label: String,
    selected: Language,
    options: List<Language>,
    onSelect: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(4.dp))
        
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${selected.displayName} (${selected.code})",
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            options.forEach { lang ->
                DropdownMenuItem(
                    text = { Text("${lang.displayName} (${lang.code})") },
                    onClick = {
                        expanded = false
                        onSelect(lang)
                    }
                )
            }
        }
    }
}

/**
 * Status indicator (🔴/🟢)
 */
@Composable
private fun StatusIndicator(isReady: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (isReady) Color(0xFF4CAF50) else Color(0xFFF44336),
                    CircleShape
                )
        )
        Text(
            text = if (isReady) "Ready" else "No text",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Source badge (ML Kit / Gemini AI)
 */
@Composable
private fun SourceBadge(source: OcrSource) {
    val (text, color, icon) = when (source) {
        OcrSource.ML_KIT -> Triple(
            "ML Kit",
            Color(0xFF2196F3),
            Icons.Default.PhoneAndroid
        )
        OcrSource.GEMINI -> Triple(
            "Gemini AI",
            Color(0xFF9C27B0),
            Icons.Default.AutoAwesome
        )
        OcrSource.UNKNOWN -> Triple(
            "Unknown",
            Color(0xFF9E9E9E),
            Icons.Default.Help
        )
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
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

/**
 * Model speed badge
 */
@Composable
private fun ModelSpeedBadge(modelId: String) {
    val (text, color) = when (modelId) {
        "gemini-3-flash-preview", "gemini-2.5-flash-lite" -> 
            "⚡ FAST" to Color(0xFF4CAF50)
        
        "gemini-2.5-flash" -> 
            "⚖️ BALANCED" to Color(0xFF2196F3)
        
        "gemini-3-pro-preview", "gemini-2.5-pro" -> 
            "🐌 SLOW" to Color(0xFFFF9800)
        
        else -> return
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
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

/**
 * Quick stat display
 */
@Composable
private fun QuickStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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