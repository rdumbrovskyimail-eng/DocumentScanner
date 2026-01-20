package com.docs.scanner.presentation.screens.settings.components

import android.net.Uri
import com.docs.scanner.data.local.preferences.GeminiModelOption
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.data.remote.mlkit.OcrTestResult
import com.docs.scanner.domain.core.Language

/**
 * UI State for MLKit and Gemini OCR settings.
 * 
 * Version: 12.0.0 - TRANSLATION TEST ENHANCEMENT (2026)
 * 
 * ✅ NEW IN 12.0.0:
 * - selectedTranslationModel: Gemini model for translation
 * - availableTranslationModels: List of models for translation dropdown
 * - isTranslationReady: Computed property for green indicator
 * - isTextFromOcr: Badge indicator for OCR-sourced text
 * - canRunTranslation: Validation for translation button
 * - hasTranslationResults: Check for results display
 * - Auto-sync OCR result → Translation test text
 * 
 * ✅ PREVIOUS IN 11.0.0:
 * - selectedGeminiModel for OCR
 * - availableGeminiModels for OCR dropdown
 * 
 * ✅ ПОЛНАЯ СПЕЦИФИКАЦИЯ:
 * - 23 поля для UI и логики (+2 новых для translation)
 * - 10 computed properties (+4 новых)
 * - Синхронизация с DataStore
 * - Поддержка test режима
 * - Выбор Gemini модели для OCR и Translation
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
    // GEMINI MODEL SELECTION FOR OCR
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
    // ✅ TRANSLATION TEST STATE - ENHANCED IN 12.0.0
    // ═══════════════════════════════════════════════════════════════════════════
    
    val translationTestText: String = "",
    val translationSourceLang: Language = Language.AUTO,
    val translationTargetLang: Language = Language.ENGLISH,
    val translationResult: String? = null,
    val translationError: String? = null,
    val isTranslating: Boolean = false,
    
    // ✅ NEW: Gemini model selection for translation
    val selectedTranslationModel: String = "gemini-2.5-flash-lite",
    val availableTranslationModels: List<GeminiModelOption> = emptyList()
    
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES - OCR
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ NEW: COMPUTED PROPERTIES - TRANSLATION (12.0.0)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Translation is ready when:
     * 1. OCR test completed successfully with text, OR
     * 2. User manually entered text
     * 
     * Used to show green indicator lamp in UI.
     */
    val isTranslationReady: Boolean
        get() = translationTestText.isNotBlank()
    
    /**
     * Indicates if translation test text came from OCR result.
     * Used to show "From OCR" badge.
     */
    val isTextFromOcr: Boolean
        get() = testResult?.text?.isNotBlank() == true && 
                translationTestText == testResult?.text
    
    /**
     * Can run translation when:
     * - Text is not blank
     * - Not currently translating
     * - Source and target languages are different (unless source is AUTO)
     */
    val canRunTranslation: Boolean
        get() = translationTestText.isNotBlank() && 
                !isTranslating &&
                (translationSourceLang == Language.AUTO || 
                 translationSourceLang != translationTargetLang)
    
    /**
     * Has translation results (success or error).
     */
    val hasTranslationResults: Boolean
        get() = translationResult != null || translationError != null
}