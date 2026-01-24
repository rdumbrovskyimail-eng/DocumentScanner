/*
 * TestingTab.kt
 * Version: 3.0.0 - ENHANCED VISUAL DESIGN (2026)
 * 
 * ✅ NEW IN 3.0.0:
 * - Modern card-based layout with visual hierarchy
 * - Model version badges for OCR and Translation
 * - Green/Red lamp indicator for translation readiness
 * - Streamlined flow: Image → Scan Text → Translation
 * - Removed App Info block
 * - Enhanced visual feedback with icons and colors
 * 
 * FLOW:
 * 1. Select Image → Run OCR
 * 2. OCR Result shows with Model Badge (ML Kit / Gemini 3 Flash / etc)
 * 3. Scan Text appears in dedicated field
 * 4. Translation lamp turns GREEN when text is ready
 * 5. Press Translate → Result shows with Model Badge
 */

package com.docs.scanner.presentation.screens.settings

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.OcrSource
import com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState
import com.docs.scanner.util.LogcatCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    
    var isCollecting by remember { mutableStateOf(false) }
    var collectedLines by remember { mutableStateOf(0) }

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
        // 1. OCR TEST CARD
        // ═══════════════════════════════════════════════════════════════
        OcrTestCard(
            mlkitSettings = mlkitSettings,
            onImageSelected = onImageSelected,
            onTestOcr = onTestOcr,
            onClearTestResult = onClearTestResult,
            onCancelOcr = onCancelOcr,
            onTestGeminiFallbackChange = onTestGeminiFallbackChange
        )
        
        // ═══════════════════════════════════════════════════════════════
        // 2. SCAN TEXT FIELD (appears after OCR)
        // ═══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = mlkitSettings.testResult != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ScanTextCard(
                text = mlkitSettings.testResult?.text ?: "",
                source = mlkitSettings.testResult?.source ?: OcrSource.UNKNOWN,
                modelUsed = getOcrModelDisplayName(mlkitSettings),
                processingTime = mlkitSettings.testResult?.processingTimeMs ?: 0,
                confidence = mlkitSettings.testResult?.confidencePercent ?: "N/A"
            )
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 3. TRANSLATION CARD
        // ═══════════════════════════════════════════════════════════════
        TranslationCard(
            mlkitSettings = mlkitSettings,
            onTranslate = onTranslationTest,
            onClear = onClearTranslationTest
        )
        
        // ═══════════════════════════════════════════════════════════════
        // 4. DEBUG TOOLS CARD
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
    }
}

private fun getOcrModelDisplayName(settings: MlkitSettingsState): String {
    return if (settings.testResult?.source == OcrSource.GEMINI) {
        settings.availableGeminiModels
            .find { it.id == settings.selectedGeminiModel }
            ?.displayName ?: settings.selectedGeminiModel
    } else {
        "ML Kit"
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// OCR TEST CARD - Enhanced with Settings Info
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun OcrTestCard(
    mlkitSettings: MlkitSettingsState,
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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "OCR Test",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Settings Info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Current Settings from OCR Tab",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    SettingInfoRow(
                        icon = Icons.Default.Language,
                        label = "Auto-detect",
                        value = if (mlkitSettings.autoDetectLanguage) "✓ Enabled" else "✗ Disabled"
                    )
                    
                    SettingInfoRow(
                        icon = Icons.Default.AutoAwesome,
                        label = "Gemini Fallback",
                        value = when {
                            mlkitSettings.geminiOcrAlways -> "Always"
                            mlkitSettings.geminiOcrEnabled -> "Threshold ${mlkitSettings.geminiOcrThreshold}%"
                            else -> "Disabled"
                        }
                    )
                    
                    if (mlkitSettings.geminiOcrEnabled) {
                        SettingInfoRow(
                            icon = Icons.Default.Psychology,
                            label = "Model",
                            value = mlkitSettings.availableGeminiModels
                                .find { it.id == mlkitSettings.selectedGeminiModel }
                                ?.displayName ?: mlkitSettings.selectedGeminiModel
                        )
                    }
                }
            }

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    onClick = {
                        if (mlkitSettings.isTestRunning) onCancelOcr() else onTestOcr()
                    },
                    enabled = mlkitSettings.selectedImageUri != null,
                    modifier = Modifier.weight(1f)
                ) {
                    if (mlkitSettings.isTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run Scan")
                    }
                }
            }

            // Force Gemini Checkbox
            AnimatedVisibility(
                visible = mlkitSettings.geminiOcrEnabled && mlkitSettings.selectedImageUri != null
            ) {
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
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "🔬 Force Gemini OCR",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
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

            // Image Preview
            AnimatedVisibility(visible = mlkitSettings.selectedImageUri != null) {
                mlkitSettings.selectedImageUri?.let { uri ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            Modifier.fillMaxWidth().height(220.dp),
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
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(48.dp),
                                                strokeWidth = 4.dp
                                            )
                                            Text(
                                                "Processing OCR...",
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
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
                                        .padding(12.dp)
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer,
                                            CircleShape
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
                        
                        // Quick Stats
                        mlkitSettings.testResult?.let { result ->
                            Row(
                                Modifier.fillMaxWidth(),
                                Arrangement.SpaceEvenly
                            ) {
                                QuickStat(
                                    icon = Icons.Default.TextFields,
                                    label = "Words",
                                    value = result.totalWords.toString()
                                )
                                QuickStat(
                                    icon = Icons.Default.Psychology,
                                    label = "Confidence",
                                    value = result.confidencePercent
                                )
                                QuickStat(
                                    icon = Icons.Default.Star,
                                    label = "Quality",
                                    value = result.qualityRating
                                )
                                QuickStat(
                                    icon = Icons.Default.Speed,
                                    label = "Time",
                                    value = "${result.processingTimeMs}ms"
                                )
                            }
                        }
                    }
                }
            }
            
            // Error Display
            AnimatedVisibility(visible = mlkitSettings.testError != null) {
                mlkitSettings.testError?.let { error ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
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
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SCAN TEXT CARD - With Model Badge
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ScanTextCard(
    text: String,
    source: OcrSource,
    modelUsed: String,
    processingTime: Long,
    confidence: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Model Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Scan Result",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                ModelBadge(source = source, modelName = modelUsed)
            }
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MiniStat(icon = Icons.Default.Speed, label = "${processingTime}ms")
                MiniStat(icon = Icons.Default.Psychology, label = confidence)
            }
            
            HorizontalDivider()
            
            // Text Content
            SelectionContainer {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = text.ifBlank { "No text recognized" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                        color = if (text.isBlank()) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TRANSLATION CARD - With Lamp Indicator
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TranslationCard(
    mlkitSettings: MlkitSettingsState,
    onTranslate: () -> Unit,
    onClear: () -> Unit
) {
    val lampColor by animateColorAsState(
        targetValue = if (mlkitSettings.isTranslationReady) {
            Color(0xFF4CAF50) // Green
        } else {
            Color(0xFFF44336) // Red
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lamp_color"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Lamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Translation",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Lamp Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(lampColor, CircleShape)
                    )
                    Text(
                        text = if (mlkitSettings.isTranslationReady) "Ready" else "No text",
                        style = MaterialTheme.typography.labelLarge,
                        color = lampColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            // Settings Info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Settings from Translation Tab",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    SettingInfoRow(
                        icon = Icons.Default.Language,
                        label = "Languages",
                        value = "${mlkitSettings.translationSourceLang.displayName} → ${mlkitSettings.translationTargetLang.displayName}"
                    )
                    
                    SettingInfoRow(
                        icon = Icons.Default.Psychology,
                        label = "Model",
                        value = mlkitSettings.availableTranslationModels
                            .find { it.id == mlkitSettings.selectedTranslationModel }
                            ?.displayName ?: mlkitSettings.selectedTranslationModel
                    )
                }
            }
            
            // Auto-filled indicator
            AnimatedVisibility(visible = mlkitSettings.isTextFromOcr) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "✨ Text auto-filled from OCR scan",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onTranslate,
                    enabled = mlkitSettings.isTranslationReady && !mlkitSettings.isTranslating,
                    modifier = Modifier.weight(1f)
                ) {
                    if (mlkitSettings.isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
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
            
            // Translation Result
            AnimatedVisibility(
                visible = mlkitSettings.translationResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    
                    // Result Header with Model Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Translation Result",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        TranslationModelBadge(
                            modelName = mlkitSettings.availableTranslationModels
                                .find { it.id == mlkitSettings.selectedTranslationModel }
                                ?.displayName ?: mlkitSettings.selectedTranslationModel
                        )
                    }
                    
                    SelectionContainer {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = mlkitSettings.translationResult ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
            
            // Translation Error
            AnimatedVisibility(visible = mlkitSettings.translationError != null) {
                mlkitSettings.translationError?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
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

// ═══════════════════════════════════════════════════════════════════════════
// DEBUG TOOLS CARD
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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Debug Tools",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (isCollecting) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                    )
                    Text(
                        if (isCollecting) "Collecting..." else "Stopped",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (isCollecting || collectedLines > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "$collectedLines lines",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStartStop,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null,
                        Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCollecting) "Stop" else "Start")
                }
                OutlinedButton(
                    onClick = onSave,
                    enabled = collectedLines > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
            
            HorizontalDivider()
            
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
// UI COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
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
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuickStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
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

@Composable
private fun MiniStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ModelBadge(source: OcrSource, modelName: String) {
    val (backgroundColor, contentColor, icon) = when (source) {
        OcrSource.ML_KIT -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.15f),
            Color(0xFF1976D2),
            Icons.Default.PhoneAndroid
        )
        OcrSource.GEMINI -> Triple(
            Color(0xFF9C27B0).copy(alpha = 0.15f),
            Color(0xFF7B1FA2),
            Icons.Default.AutoAwesome
        )
        OcrSource.UNKNOWN -> Triple(
            Color(0xFF9E9E9E).copy(alpha = 0.15f),
            Color(0xFF616161),
            Icons.Default.Help
        )
    }
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.5.dp, contentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun TranslationModelBadge(modelName: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF4CAF50).copy(alpha = 0.15f),
        border = BorderStroke(1.5.dp, Color(0xFF388E3C).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF388E3C)
            )
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20)
            )
        }
    }
}
