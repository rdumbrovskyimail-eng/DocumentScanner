/*
 * TestingTab.kt
 * Version: 5.0.0 - ФИНАЛЬНАЯ РАБОЧАЯ ВЕРСИЯ (2026)
 * 
 * ✅ ВСЁ ПО ТРЕБОВАНИЯМ НА 101%:
 * 1. OCR Card с выбором изображения
 * 2. При скане - MODEL BADGE (ML Kit / Gemini 3 Flash)
 * 3. СРАЗУ НИЖЕ - поле Scan Text (только для чтения)
 * 4. Translation Card БЕЗ TextField (берет текст автоматом из OCR)
 * 5. Лампочка красная→зеленая
 * 6. Translation Result с MODEL BADGE (Gemini 2.5 Flash Lite)
 * 7. БЕЗ App Info полностью
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ══════════════════════════════════════════════════════════════
        // 1️⃣ OCR CARD
        // ══════════════════════════════════════════════════════════════
        OcrTestCard(
            mlkitSettings = mlkitSettings,
            onImageSelected = onImageSelected,
            onTestOcr = onTestOcr,
            onClearTestResult = onClearTestResult,
            onCancelOcr = onCancelOcr,
            onTestGeminiFallbackChange = onTestGeminiFallbackChange
        )
        
        // ══════════════════════════════════════════════════════════════
        // 2️⃣ SCAN TEXT - СРАЗУ ПОСЛЕ OCR (с MODEL BADGE)
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = mlkitSettings.testResult != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ScanTextCard(
                text = mlkitSettings.testResult?.text ?: "",
                source = mlkitSettings.testResult?.source ?: OcrSource.UNKNOWN,
                modelUsed = getOcrModelDisplayName(mlkitSettings)
            )
        }
        
        // ══════════════════════════════════════════════════════════════
        // 3️⃣ TRANSLATION CARD - БЕЗ TEXTFIELD (с лампочкой)
        // ══════════════════════════════════════════════════════════════
        TranslationTestCard(
            mlkitSettings = mlkitSettings,
            onTranslate = onTranslationTest,
            onClear = onClearTranslationTest
        )
        
        // ══════════════════════════════════════════════════════════════
        // 4️⃣ DEBUG TOOLS (без изменений)
        // ══════════════════════════════════════════════════════════════
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
        
        // ❌ APP INFO УДАЛЕН ПОЛНОСТЬЮ
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

// ══════════════════════════════════════════════════════════════════════════
// 1️⃣ OCR TEST CARD
// ══════════════════════════════════════════════════════════════════════════

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
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "OCR Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📋 Using Settings from OCR Tab:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    SettingInfoRow(
                        label = "Auto-detect",
                        value = if (mlkitSettings.autoDetectLanguage) "Enabled" else "Disabled"
                    )
                    
                    SettingInfoRow(
                        label = "Gemini Fallback",
                        value = when {
                            mlkitSettings.geminiOcrAlways -> "Always"
                            mlkitSettings.geminiOcrEnabled -> "Threshold: ${mlkitSettings.geminiOcrThreshold}%"
                            else -> "Disabled"
                        }
                    )
                    
                    if (mlkitSettings.geminiOcrEnabled) {
                        SettingInfoRow(
                            label = "Gemini Model",
                            value = mlkitSettings.availableGeminiModels
                                .find { it.id == mlkitSettings.selectedGeminiModel }
                                ?.displayName ?: mlkitSettings.selectedGeminiModel
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = Modifier.size(16.dp),
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
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 2️⃣ SCAN TEXT CARD - С MODEL BADGE
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun ScanTextCard(
    text: String,
    source: OcrSource,
    modelUsed: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📄 Scan Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SourceBadge(source = source)
                    
                    // ⭐⭐⭐ MODEL BADGE - показывает версию
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = modelUsed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            SelectionContainer {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = text.ifBlank { "(No text recognized)" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
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

// ══════════════════════════════════════════════════════════════════════════
// 3️⃣ TRANSLATION CARD - БЕЗ TEXTFIELD, С ЛАМПОЧКОЙ
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun TranslationTestCard(
    mlkitSettings: MlkitSettingsState,
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
                    
                    // 🚦🚦🚦 ЛАМПОЧКА 🚦🚦🚦
                    StatusIndicator(isReady = mlkitSettings.isTranslationReady)
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📋 Using Settings from Translation Tab:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    SettingInfoRow(
                        label = "Source Language",
                        value = "${mlkitSettings.translationSourceLang.displayName} (${mlkitSettings.translationSourceLang.code})"
                    )
                    
                    SettingInfoRow(
                        label = "Target Language",
                        value = "${mlkitSettings.translationTargetLang.displayName} (${mlkitSettings.translationTargetLang.code})"
                    )
                    
                    SettingInfoRow(
                        label = "Gemini Model",
                        value = mlkitSettings.availableTranslationModels
                            .find { it.id == mlkitSettings.selectedTranslationModel }
                            ?.displayName ?: mlkitSettings.selectedTranslationModel
                    )
                }
            }
            
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
                            "Text auto-filled from OCR scan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            // ❌ БЕЗ OutlinedTextField - текст берется автоматом из OCR
            
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
            
            AnimatedVisibility(
                visible = mlkitSettings.translationResult != null || mlkitSettings.translationError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    
                    mlkitSettings.translationResult?.let { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📋 Translation:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            
                            // ⭐⭐⭐ TRANSLATION MODEL BADGE - показывает версию
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = mlkitSettings.availableTranslationModels
                                        .find { it.id == mlkitSettings.selectedTranslationModel }
                                        ?.displayName ?: mlkitSettings.selectedTranslationModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        
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
                    }
                    
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

// ══════════════════════════════════════════════════════════════════════════
// 4️⃣ DEBUG TOOLS CARD
// ══════════════════════════════════════════════════════════════════════════

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

// ══════════════════════════════════════════════════════════════════════════
// HELPER COMPONENTS
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

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

@Composable
private fun SourceBadge(source: OcrSource) {
    val (text, color, icon) = when (source) {
        OcrSource.ML_KIT -> Triple("ML Kit", Color(0xFF2196F3), Icons.Default.PhoneAndroid)
        OcrSource.GEMINI -> Triple("Gemini AI", Color(0xFF9C27B0), Icons.Default.AutoAwesome)
        OcrSource.UNKNOWN -> Triple("Unknown", Color(0xFF9E9E9E), Icons.Default.Help)
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f)
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
