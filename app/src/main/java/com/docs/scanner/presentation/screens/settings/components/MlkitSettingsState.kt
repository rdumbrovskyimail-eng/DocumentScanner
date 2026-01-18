/*
 * MlkitSettingsState.kt
 * Version: 10.0.0 - TRANSLATION TEST + GEMINI OCR FALLBACK (2026)
 * 
 * ✅ NEW IN 10.0.0:
 * - Translation test fields (6 новых полей)
 * 
 * ✅ NEW IN 9.0.0:
 * - testGeminiFallback: Boolean = false
 * - showGeminiFallbackTest computed property
 * 
 * ✅ ПОЛНАЯ СПЕЦИФИКАЦИЯ:
 * - 19 полей для UI и логики
 * - Синхронизация с DataStore
 * - Поддержка test режима
 * - Thread-safe копирование
 */

package com.docs.scanner.presentation.screens.settings.components

import android.net.Uri
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.data.remote.mlkit.OcrTestResult
import com.docs.scanner.domain.core.Language

data class MlkitSettingsState(
    // ═══════════════════════════════════════════════════════════════════════════
    // CORE OCR SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════
    
    val scriptMode: OcrScriptMode = OcrScriptMode.AUTO,
    val autoDetectLanguage: Boolean = true,
    val confidenceThreshold: Float = 0.7f,
    val highlightLowConfidence: Boolean = true,
    val showWordConfidences: Boolean = false,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEST MODE STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    val selectedImageUri: Uri? = null,
    val isTestRunning: Boolean = false,
    val testResult: OcrTestResult? = null,
    val testError: String? = null,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GEMINI OCR FALLBACK SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════
    
    val geminiOcrEnabled: Boolean = true,
    val geminiOcrThreshold: Int = 65,
    val geminiOcrAlways: Boolean = false,
    val testGeminiFallback: Boolean = false,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ TRANSLATION TEST (NEW 2026)
    // ═══════════════════════════════════════════════════════════════════════════
    
    val translationTestText: String = "",
    val translationSourceLang: Language = Language.AUTO,
    val translationTargetLang: Language = Language.ENGLISH,
    val translationResult: String? = null,
    val isTranslating: Boolean = false,
    val translationError: String? = null
) {
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