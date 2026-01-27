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