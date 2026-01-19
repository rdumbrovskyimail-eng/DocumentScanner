package com.docs.scanner.presentation.screens.settings.components

import android.net.Uri
import com.docs.scanner.data.local.preferences.GeminiModelOption
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.OcrTestResult

/**
 * UI State for MLKit and Gemini OCR settings.
 * 
 * Version: 11.0.0 - GEMINI MODEL SELECTION (2026)
 * 
 * ✅ NEW IN 11.0.0:
 * - selectedGeminiModel: Currently selected Gemini model ID
 * - availableGeminiModels: List of available models for UI
 * 
 * ✅ ПОЛНАЯ СПЕЦИФИКАЦИЯ:
 * - 21 полей для UI и логики
 * - Синхронизация с DataStore
 * - Поддержка test режима
 * - Выбор Gemini модели
 * - Thread-safe копирование
 */
data class MlkitSettingsState(
    // ═══════════════════════════════════════════════════════════════════════════
    // CORE OCR SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════
    
    val scriptMode: OcrScriptMode = OcrScriptMode.AUTO,
    val autoDetectLanguage: Boolean = true,
    val confidenceThreshold: Float = 0.7f,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UI DISPLAY OPTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    val highlightLowConfidence: Boolean = false,
    val showWordConfidences: Boolean = false,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GEMINI OCR FALLBACK SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════
    
    val geminiOcrEnabled: Boolean = true,
    val geminiOcrThreshold: Int = 65,
    val geminiOcrAlways: Boolean = false,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ GEMINI MODEL SELECTION (NEW 2026)
    // ═══════════════════════════════════════════════════════════════════════════
    
    val selectedGeminiModel: String = "gemini-2.5-flash",
    val availableGeminiModels: List<GeminiModelOption> = emptyList(),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // OCR TEST STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    val selectedImageUri: Uri? = null,
    val isTestRunning: Boolean = false,
    val testGeminiFallback: Boolean = false,
    val testResult: OcrTestResult? = null,
    val testError: String? = null,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSLATION TEST STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    val translationTestText: String = "",
    val translationSourceLang: Language = Language.AUTO,
    val translationTargetLang: Language = Language.ENGLISH,
    val translationResult: String? = null,
    val translationError: String? = null,
    val isTranslating: Boolean = false
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    val canRunTest: Boolean
        get() = selectedImageUri != null && !isTestRunning
    
    val hasResults: Boolean
        get() = testResult != null || testError != null
    
    val isGeminiFallbackActive: Boolean
        get() = geminiOcrEnabled && !geminiOcrAlways && geminiOcrThreshold in 0..100
    
    val isGeminiOnlyMode: Boolean
        get() = geminiOcrEnabled && geminiOcrAlways
    
    val showGeminiFallbackTest: Boolean
        get() = geminiOcrEnabled && selectedImageUri != null
}