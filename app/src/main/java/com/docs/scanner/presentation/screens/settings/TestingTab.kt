/*
 * TestingTab.kt
 * Version: 3.0.0 - COMPLETE VISUAL REDESIGN (2026)
 * 
 * ✅ ВСЕЁ ПО ТРЕБОВАНИЯМ:
 * 1. Карточка выбора изображения + кнопки
 * 2. При скане - badge с моделью (ML Kit / Gemini 3 Flash)
 * 3. СРАЗУ НИЖЕ - поле "Scan Text" с результатом
 * 4. Карточка Translation БЕЗ выбора языков
 * 5. Лампочка красная/зеленая
 * 6. При переводе - badge с версией Gemini
 * 7. Убран App Info
 */

package com.docs.scanner.presentation.screens.settings

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1️⃣ OCR CARD - Image selection + scanning
        OcrImageCard(
            mlkitSettings = mlkitSettings,
            onImageSelected = onImageSelected,
            onTestOcr = onTestOcr,
            onClearTestResult = onClearTestResult,
            onCancelOcr = onCancelOcr,
            onTestGeminiFallbackChange = onTestGeminiFallbackChange
        )
        
        // 2️⃣ SCAN TEXT CARD - Shows immediately after OCR with model badge
        AnimatedVisibility(
            visible = mlkitSettings.testResult != null,
            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
        ) {
            ScanTextDisplayCard(
                text = mlkitSettings.testResult?.text ?: "",
                source = mlkitSettings.testResult?.source ?: OcrSource.UNKNOWN,
                modelName = getOcrModelDisplayName(mlkitSettings),
                processingTime = mlkitSettings.testResult?.processingTimeMs ?: 0,
                confidence = mlkitSettings.testResult?.confidencePercent ?: "N/A"
            )
        }
        
        // 3️⃣ TRANSLATION CARD - With lamp indicator, NO language selectors
        TranslationCardWithLamp(
            mlkitSettings = mlkitSettings,
            onTranslate = onTranslationTest,
            onClear = onClearTranslationTest
        )
        
        // 4️⃣ DEBUG TOOLS
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
                            "⚠️ No logs collected",
                            duration = SnackbarDuration.Short
                        )
                        return@launch
                    }
                    logCollector.saveLogsNow()
                    delay(500)
                    snackbarHostState.showSnackbar(
                        "✅ Saved ${logCollector.getCollectedLinesCount()} lines",
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
// 1️⃣ OCR IMAGE CARD
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun OcrImageCard(
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "OCR Test",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Current Settings Info
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Settings from OCR Tab",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    InfoRowCompact(
                        icon = Icons.Default.Language,
                        label = "Auto-detect",
                        value = if (mlkitSettings.autoDetectLanguage) "✓ ON" else "✗ OFF"
                    )
                    
                    InfoRowCompact(
                        icon = Icons.Default.AutoAwesome,
                        label = "Gemini",
                        value = when {
                            mlkitSettings.geminiOcrAlways -> "Always"
                            mlkitSettings.geminiOcrEnabled -> "${mlkitSettings.geminiOcrThreshold}%"
                            else -> "OFF"
                        }
                    )
                    
                    if (mlkitSettings.geminiOcrEnabled) {
                        InfoRowCompact(
                            icon = Icons.Default.Memory,
                            label = "Model",
                            value = mlkitSettings.availableGeminiModels
                                .find { it.id == mlkitSettings.selectedGeminiModel }
                                ?.displayName ?: mlkitSettings.selectedGeminiModel
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Select Image", fontWeight = FontWeight.SemiBold)
                }
                
                Button(
                    onClick = {
                        if (mlkitSettings.isTestRunning) onCancelOcr() else onTestOcr()
                    },
                    enabled = mlkitSettings.selectedImageUri != null,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (mlkitSettings.isTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Run Scan", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Force Gemini Checkbox
            AnimatedVisibility(
                visible = mlkitSettings.geminiOcrEnabled && mlkitSettings.selectedImageUri != null
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
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
                        Column {
                            Text(
                                "🔬 Force Gemini OCR",
                                style = MaterialTheme.typography.bodyLarge,
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
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                
                                if (mlkitSettings.isTestRunning) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(0.75f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(56.dp),
                                                strokeWidth = 5.dp
                                            )
                                            Text(
                                                "Processing OCR...",
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
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
                                        .size(48.dp)
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        
                        // Quick Stats Row
                        mlkitSettings.testResult?.let { result ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatChip(
                                    icon = Icons.Default.TextFields,
                                    value = result.totalWords.toString(),
                                    label = "Words"
                                )
                                StatChip(
                                    icon = Icons.Default.Psychology,
                                    value = result.confidencePercent,
                                    label = "Confidence"
                                )
                                StatChip(
                                    icon = Icons.Default.Star,
                                    value = result.qualityRating,
                                    label = "Quality"
                                )
                                StatChip(
                                    icon = Icons.Default.Speed,
                                    value = "${result.processingTimeMs}ms",
                                    label = "Time"
                                )
                            }
                        }
                    }
                }
            }
            
            // Error Display
            AnimatedVisibility(visible = mlkitSettings.testError != null) {
                mlkitSettings.testError?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                error,
                                style = MaterialTheme.typography.bodyLarge,
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
// 2️⃣ SCAN TEXT DISPLAY CARD - With MODEL BADGE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ScanTextDisplayCard(
    text: String,
    source: OcrSource,
    modelName: String,
    processingTime: Long,
    confidence: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with MODEL BADGE
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
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Scan Result",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // ⭐ MODEL BADGE - Shows which model was used
                ModelBadgeLarge(source = source, modelName = modelName)
            }
            
            // Mini Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MiniStatRow(icon = Icons.Default.Speed, text = "${processingTime}ms")
                MiniStatRow(icon = Icons.Default.Psychology, text = confidence)
            }
            
            HorizontalDivider(thickness = 1.dp)
            
            // Text Content (selectable)
            SelectionContainer {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = text.ifBlank { "No text recognized" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(20.dp),
                        color = if (text.isBlank()) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onSurface,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.3f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 3️⃣ TRANSLATION CARD - With LAMP INDICATOR, NO language selectors
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TranslationCardWithLamp(
    mlkitSettings: MlkitSettingsState,
    onTranslate: () -> Unit,
    onClear: () -> Unit
) {
    // 🚦 ANIMATED LAMP
    val lampColor by animateColorAsState(
        targetValue = if (mlkitSettings.isTranslationReady) {
            Color(0xFF4CAF50) // Green
        } else {
            Color(0xFFF44336) // Red
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lamp_animation"
    )
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with LAMP
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
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Translation",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 🚦 LAMP INDICATOR
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(lampColor, CircleShape)
                            .animateContentSize()
                    )
                    Text(
                        text = if (mlkitSettings.isTranslationReady) "Ready" else "No text",
                        style = MaterialTheme.typography.titleMedium,
                        color = lampColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Settings Info (NO language selectors!)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Settings from Translation Tab",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    InfoRowCompact(
                        icon = Icons.Default.Translate,
                        label = "Route",
                        value = "${mlkitSettings.translationSourceLang.displayName} → ${mlkitSettings.translationTargetLang.displayName}"
                    )
                    
                    InfoRowCompact(
                        icon = Icons.Default.Memory,
                        label = "Model",
                        value = mlkitSettings.availableTranslationModels
                            .find { it.id == mlkitSettings.selectedTranslationModel }
                            ?.displayName ?: mlkitSettings.selectedTranslationModel
                    )
                }
            }
            
            // Auto-filled badge
            AnimatedVisibility(visible = mlkitSettings.isTextFromOcr) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "✨ Text auto-filled from OCR scan",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onTranslate,
                    enabled = mlkitSettings.isTranslationReady && !mlkitSettings.isTranslating,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    if (mlkitSettings.isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Translating...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Translate", fontWeight = FontWeight.Bold)
                    }
                }
                
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear", fontWeight = FontWeight.SemiBold)
                }
            }
            
            // Translation Result with MODEL BADGE
            AnimatedVisibility(
                visible = mlkitSettings.translationResult != null,
                enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(thickness = 1.dp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Translation Result",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // ⭐ TRANSLATION MODEL BADGE
                        TranslationModelBadge(
                            modelName = mlkitSettings.availableTranslationModels
                                .find { it.id == mlkitSettings.selectedTranslationModel }
                                ?.displayName ?: mlkitSettings.selectedTranslationModel
                        )
                    }
                    
                    SelectionContainer {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = mlkitSettings.translationResult ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(20.dp),
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.3f)
                            )
                        }
                    }
                }
            }
            
            // Translation Error
            AnimatedVisibility(visible = mlkitSettings.translationError != null) {
                mlkitSettings.translationError?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyLarge,
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
// 4️⃣ DEBUG TOOLS CARD
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DebugToolsCard(
    isCollecting: Boolean,
    collectedLines: Int,
    onStartStop: () -> Unit,
    onSave: () -> Unit,
    onOpenDebugViewer: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
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
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Debug Tools",
                    style = MaterialTheme.typography.headlineMedium,
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
                            .size(16.dp)
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
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStartStop,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(
                        if (isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null,
                        Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCollecting) "Stop" else "Start", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSave,
                    enabled = collectedLines > 0,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
            
            HorizontalDivider()
            
            Button(
                onClick = onOpenDebugViewer,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Debug Viewer", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UI COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun InfoRowCompact(
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
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
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
}

@Composable
private fun MiniStatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ModelBadgeLarge(source: OcrSource, modelName: String) {
    val (bgColor, textColor, icon) = when (source) {
        OcrSource.ML_KIT -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.2f),
            Color(0xFF1565C0),
            Icons.Default.PhoneAndroid
        )
        OcrSource.GEMINI -> Triple(
            Color(0xFF9C27B0).copy(alpha = 0.2f),
            Color(0xFF6A1B9A),
            Icons.Default.AutoAwesome
        )
        OcrSource.UNKNOWN -> Triple(
            Color(0xFF9E9E9E).copy(alpha = 0.2f),
            Color(0xFF424242),
            Icons.Default.Help
        )
    }
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = BorderStroke(2.dp, textColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = textColor
            )
            Text(
                text = modelName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun TranslationModelBadge(modelName: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF4CAF50).copy(alpha = 0.2f),
        border = BorderStroke(2.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF1B5E20)
            )
            Text(
                text = modelName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20)
            )
        }
    }
}
