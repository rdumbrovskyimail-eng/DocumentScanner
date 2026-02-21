package com.docs.scanner.presentation.screens.settings.components

import android.net.Uri
import com.docs.scanner.data.local.preferences.GeminiModelOption
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.data.remote.mlkit.OcrTestResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.OcrSource

/**
 * UI State for MLKit and Gemini OCR settings.
 * 
 * Version: 13.0.0 - GLOBAL SETTINGS SYNCHRONIZATION (2026)
 * 
 * ✅ NEW IN 13.0.0:
 * - State now reflects GLOBAL settings from DataStore
 * - Testing Tab reads (not writes) these settings
 * - Source/Target languages sync from Translation Settings
 * - Model selections sync from OCR/Translation Settings
 * - Changed default scriptMode from AUTO to LATIN
 * - Added documentation about read-only nature in Testing Tab
 * 
 * ✅ PREVIOUS IN 12.0.0:
 * - selectedTranslationModel: Gemini model for translation
 * - availableTranslationModels: List of models for translation dropdown
 * - isTranslationReady: Computed property for green indicator
 * - isTextFromOcr: Badge indicator for OCR-sourced text
 * 
 * ARCHITECTURE:
 * - DataStore → SettingsViewModel → MlkitSettingsState → TestingTab (read-only display)
 * - DataStore → SettingsViewModel → OcrSettingsTab (can modify)
 * - DataStore → SettingsViewModel → TranslationSettingsTab (can modify)
 */
data class MlkitSettingsState(
    // ═══════════════════════════════════════════════════════════════════════════
    // OCR SETTINGS (synced from DataStore)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * OCR script mode (LATIN, CHINESE, JAPANESE, KOREAN, DEVANAGARI)
     * ✅ UPDATED 13.0.0: Default changed from AUTO to LATIN
     * AUTO is no longer selectable in UI (uses autoDetectLanguage toggle instead)
     */
    val scriptMode: OcrScriptMode = OcrScriptMode.LATIN,
    
    /**
     * Auto-detect language from image
     * Synced from SettingsDataStore.autoDetectLanguage
     */
    val autoDetectLanguage: Boolean = true,
    
    /**
     * Confidence threshold for low-confidence word detection (0.0 - 1.0)
     * Synced from SettingsDataStore.confidenceThreshold
     */
    val confidenceThreshold: Float = 0.7f,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UI DISPLAY OPTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Highlight low-confidence words in OCR result */
    val highlightLowConfidence: Boolean = false,
    
    /** Show individual word confidences */
    val showWordConfidences: Boolean = false,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GEMINI OCR FALLBACK SETTINGS (synced from DataStore)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Enable Gemini fallback for poor quality OCR */
    val geminiOcrEnabled: Boolean = true,
    
    /** Confidence threshold below which Gemini is triggered (0-100) */
    val geminiOcrThreshold: Int = 65,
    
    /** Always use Gemini (skip ML Kit) */
    val geminiOcrAlways: Boolean = false,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GEMINI MODEL SELECTION FOR OCR (synced from DataStore)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** 
     * Selected Gemini model for OCR (e.g., "gemini-2.5-flash")
     * Synced from SettingsDataStore.geminiOcrModel
     */
    val selectedGeminiModel: String = "gemini-2.5-flash",
    
    /** Available Gemini models for OCR dropdown */
    val availableGeminiModels: List<GeminiModelOption> = emptyList(),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // OCR TEST STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Selected image URI for testing */
    val selectedImageUri: Uri? = null,
    
    /** OCR test in progress */
    val isTestRunning: Boolean = false,
    
    /** Force Gemini fallback for testing */
    val testGeminiFallback: Boolean = false,
    
    /** OCR test result */
    val testResult: OcrTestResult? = null,
    
    /** OCR test error message */
    val testError: String? = null,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSLATION SETTINGS (synced from DataStore - READ ONLY in Testing)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Text to translate (can be auto-filled from OCR result) */
    val translationTestText: String = "",
    
    /** 
     * ✅ Source language for translation
     * Synced from SettingsDataStore.translationSource
     * READ-ONLY in Testing Tab - change in Translation Settings
     */
    val translationSourceLang: Language = Language.AUTO,
    
    /** 
     * ✅ Target language for translation
     * Synced from SettingsDataStore.translationTarget
     * READ-ONLY in Testing Tab - change in Translation Settings
     */
    val translationTargetLang: Language = Language.ENGLISH,
    
    /** Translation result */
    val translationResult: String? = null,
    
    /** Translation error message */
    val translationError: String? = null,
    
    /** Translation in progress */
    val isTranslating: Boolean = false,
    
    /** 
     * ✅ Selected Gemini model for translation
     * Synced from GeminiModelManager.getGlobalTranslationModel()
     * READ-ONLY in Testing Tab - change in Translation Settings
     */
    val selectedTranslationModel: String = "gemini-2.5-flash-lite",
    
    /** Available Gemini models for translation dropdown */
    val availableTranslationModels: List<GeminiModelOption> = emptyList()
    
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES - OCR
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Can run OCR test (image selected and not running) */
    val canRunTest: Boolean
        get() = selectedImageUri != null && !isTestRunning
    
    /** Has OCR results (success or error) */
    val hasResults: Boolean
        get() = testResult != null || testError != null
    
    /** Gemini fallback is active (enabled but not always) */
    val isGeminiFallbackActive: Boolean
        get() = geminiOcrEnabled && !geminiOcrAlways && geminiOcrThreshold in 0..100
    
    /** Gemini only mode (skip ML Kit) */
    val isGeminiOnlyMode: Boolean
        get() = geminiOcrEnabled && geminiOcrAlways
    
    /** Show Gemini fallback test checkbox */
    val showGeminiFallbackTest: Boolean
        get() = geminiOcrEnabled && selectedImageUri != null
    
    // ═══════════════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES - TRANSLATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * ✅ Translation is ready when OCR text is available
     * Used to show green/red lamp indicator in UI
     */
    val isTranslationReady: Boolean
        get() = translationTestText.isNotBlank()
    
    /**
     * ✅ Indicates if translation test text came from OCR result
     * Used to show "From OCR" badge
     */
    val isTextFromOcr: Boolean
        get() = testResult?.text?.isNotBlank() == true && 
                translationTestText == testResult?.text
    
    /**
     * ✅ Can run translation when:
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
     * Has translation results (success or error)
     */
    val hasTranslationResults: Boolean
        get() = translationResult != null || translationError != null
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DISPLAY HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get display name for OCR model used
     */
    val ocrModelDisplayName: String
        get() = if (testResult?.source == OcrSource.GEMINI) {
            availableGeminiModels.find { it.id == selectedGeminiModel }?.displayName 
                ?: selectedGeminiModel
        } else {
            "ML Kit"
        }
    
    /**
     * Get display name for translation model
     */
    val translationModelDisplayName: String
        get() = availableTranslationModels.find { it.id == selectedTranslationModel }?.displayName 
            ?: selectedTranslationModel
    
    /**
     * Summary of current OCR settings for display
     */
    val ocrSettingsSummary: String
        get() = buildString {
            append("Mode: ${scriptMode.displayName}")
            if (autoDetectLanguage) append(", Auto-detect")
            if (geminiOcrEnabled) {
                if (geminiOcrAlways) {
                    append(", Gemini Always")
                } else {
                    append(", Fallback@${geminiOcrThreshold}%")
                }
            }
        }
    
    /**
     * Summary of current translation settings for display
     */
    val translationSettingsSummary: String
        get() = "${translationSourceLang.displayName} → ${translationTargetLang.displayName}"
}