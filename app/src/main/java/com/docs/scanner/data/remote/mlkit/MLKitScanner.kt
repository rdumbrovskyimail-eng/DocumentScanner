/*
 * MLKitScanner.kt
 * Version: 15.1.0 - AUTO-TRANSLATION SUPPORT IN OCR TEST (2026)
 * 
 * ✅ NEW IN 15.1.0:
 * - Auto-translation fields in OcrTestResult
 * - translatedText: String?
 * - translationTargetLang: Language?
 * - translationTimeMs: Long?
 * 
 * ✅ NEW IN 15.0.0:
 * - Gemini Vision API fallback for poor ML Kit results
 * - OcrQualityAnalyzer integration
 * - Source indicator (ML Kit vs Gemini) in OcrTestResult
 * - Fixed file:// URI handling
 * - testGeminiFallback parameter in testOcr()
 * - Added recognizeTextMlKitOnly() and recognizeTextGeminiOnly()
 * 
 * ✅ FIXED in 2026:
 * - Proper bitmap lifecycle management (Android 16)
 * - Improved hybrid OCR error handling
 * - Gemini fallback triggers on ML Kit failure
 * 
 * ✅ ARCHITECTURE:
 * Document → ML Kit (fast, offline) → Quality Analysis → Gemini fallback (if needed)
 *                                                      ↓
 *                                            Return with source indicator
 * 
 * LOCATION: com.docs.scanner.data.remote.mlkit
 */

package com.docs.scanner.data.remote.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.remote.gemini.GeminiOcrService
import com.docs.scanner.domain.repository.BoundingBox
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.OcrResult
import com.docs.scanner.domain.core.OcrSource
import com.docs.scanner.domain.repository.DetailedOcrResult
import com.docs.scanner.domain.repository.TextBlock
import com.docs.scanner.domain.repository.TextLine
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ════════════════════════════════════════════════════════════════════════════════
// ENUMS
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Режимы распознавания текста MLKit.
 */
enum class OcrScriptMode(
    val displayName: String,
    val description: String,
    val supportedLanguages: List<String>
) {
    AUTO("Auto-Detect", "Automatically detect script", listOf("All")),
    LATIN("Latin", "English, Spanish, French, German, etc.", listOf("en", "es", "fr", "de", "pt", "it")),
    CHINESE("Chinese", "Simplified and Traditional Chinese", listOf("zh", "zh-TW")),
    JAPANESE("Japanese", "Hiragana, Katakana, Kanji", listOf("ja")),
    KOREAN("Korean", "Hangul characters", listOf("ko")),
    DEVANAGARI("Devanagari", "Hindi, Marathi, Nepali", listOf("hi", "mr", "ne"))
}

/**
 * Уровни уверенности для слов.
 */
enum class ConfidenceLevel(val minConfidence: Float, val color: Long) {
    HIGH(0.9f, 0xFF4CAF50),
    MEDIUM(0.7f, 0xFFFF9800),
    LOW(0.5f, 0xFFF44336),
    VERY_LOW(0f, 0xFF9C27B0)
}

// ════════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Слово с информацией о уверенности.
 */
data class WordWithConfidence(
    val text: String,
    val confidence: Float,
    val confidenceLevel: ConfidenceLevel,
    val boundingBox: BoundingBox?,
    val startIndex: Int,
    val endIndex: Int
) {
    val needsReview: Boolean get() = confidenceLevel in listOf(ConfidenceLevel.LOW, ConfidenceLevel.VERY_LOW)
}

/**
 * Результат OCR с детальной статистикой.
 */
data class OcrResultWithConfidence(
    val text: String,
    val detectedLanguage: Language?,
    val detectedScript: OcrScriptMode?,
    val overallConfidence: Float?,
    val words: List<WordWithConfidence>,
    val lowConfidenceRanges: List<IntRange>,
    val processingTimeMs: Long,
    val recognizerUsed: OcrScriptMode
) {
    val lowConfidenceCount: Int get() = words.count { it.needsReview }
    val highConfidencePercent: Float get() = 
        if (words.isEmpty()) 100f 
        else (words.count { it.confidenceLevel == ConfidenceLevel.HIGH } * 100f / words.size)
}

/**
 * Результат теста OCR для Settings UI.
 * ✅ UPDATED: Added auto-translation fields.
 */
data class OcrTestResult(
    val text: String,
    val detectedLanguage: Language?,
    val detectedScript: OcrScriptMode?,
    val overallConfidence: Float?,
    val totalWords: Int,
    val highConfidenceWords: Int,
    val lowConfidenceWords: Int,
    val lowConfidenceRanges: List<IntRange>,
    val wordConfidences: List<Pair<String, Float>>,
    val processingTimeMs: Long,
    val recognizerUsed: String,
    
    // ✅ NEW: Source indicator
    val source: OcrSource = OcrSource.ML_KIT,
    
    // ✅ NEW: Gemini fallback info
    val geminiFallbackTriggered: Boolean = false,
    val geminiFallbackReason: String? = null,
    val geminiProcessingTimeMs: Long? = null,
    val geminiAvailable: Boolean = false,
    
    // ✅ NEW 2026: Auto-translation fields
    val translatedText: String? = null,
    val translationTargetLang: Language? = null,
    val translationTimeMs: Long? = null
) {
    val confidencePercent: String get() = "${((overallConfidence ?: 0f) * 100).toInt()}%"
    
    val qualityRating: String get() = when {
        (overallConfidence ?: 0f) >= 0.9f -> "Excellent"
        (overallConfidence ?: 0f) >= 0.7f -> "Good"
        (overallConfidence ?: 0f) >= 0.5f -> "Fair"
        else -> "Poor"
    }
    
    /** Human-readable source name for UI */
    val sourceDisplayName: String get() = when (source) {
        OcrSource.ML_KIT -> "ML Kit"
        OcrSource.GEMINI -> "Gemini AI"
        OcrSource.UNKNOWN -> "Unknown"
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// MAIN CLASS: HYBRID OCR ENGINE
// ════════════════════════════════════════════════════════════════════════════════

/**
 * ✅ HYBRID OCR ENGINE - ML Kit + Gemini Fallback (2026)
 * 
 * Flow:
 * 1. Check "always use Gemini" setting → if true, skip to Gemini
 * 2. Try ML Kit first (fast, offline, free)
 * 3. Analyze quality with OcrQualityAnalyzer
 * 4. If quality is poor → fallback to Gemini Vision API
 * 5. Return unified result with source indicator
 */
@Singleton
class MLKitScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val geminiOcrService: GeminiOcrService,
    private val qualityAnalyzer: OcrQualityAnalyzer
) {
    companion object {
        private const val TAG = "MLKitScanner"
        private const val MAX_IMAGE_DIMENSION = 4096
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.7f
        private const val LANGUAGE_DETECTION_MIN_TEXT_LENGTH = 20
        
        // Memory optimization
        private const val BITMAP_QUALITY = 90
        private const val MIN_SAMPLE_SIZE = 1
        private const val MAX_SAMPLE_SIZE = 8
    }

    // Thread-safe recognizer cache
    private val recognizerLock = Mutex()
    private var cachedRecognizer: TextRecognizer? = null
    private var cachedScriptMode: OcrScriptMode? = null

    // ════════════════════════════════════════════════════════════════════════════════
    // PUBLIC API - HYBRID OCR
    // ════════════════════════════════════════════════════════════════════════════════

    /**
     * ✅ ИСПРАВЛЕНО: Hybrid OCR с правильным error handling
     * 
     * Flow:
     * 1. Check "always Gemini" setting → if true, skip to Gemini
     * 2. Try ML Kit first
     * 3. If ML Kit FAILS → fallback to Gemini (if enabled)
     * 4. If ML Kit succeeds but quality is POOR → fallback to Gemini (if enabled)
     * 5. Return result with source indicator
     */
    suspend fun recognizeText(uri: Uri): DomainResult<OcrResult> {
        val start = System.currentTimeMillis()
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: 🔍 Starting hybrid OCR")
        }
        
        return try {
            // ═══════════════════════════════════════════════════════════════
            // STEP 1: Check "always Gemini" mode
            // ═══════════════════════════════════════════════════════════════
            val alwaysGemini = try {
                settingsDataStore.geminiOcrAlways.first()
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to read geminiOcrAlways, defaulting to false")
                false
            }

            if (alwaysGemini) {
                Timber.d("$TAG: 🤖 Gemini-only mode, skipping ML Kit")
                return geminiOcrService.recognizeText(uri)
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 2: Check Gemini settings BEFORE trying ML Kit
            // ═══════════════════════════════════════════════════════════════
            val geminiEnabled = try {
                settingsDataStore.geminiOcrEnabled.first()
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to read geminiOcrEnabled, defaulting to true")
                true
            }

            val threshold = try {
                settingsDataStore.geminiOcrThreshold.first() / 100f
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to read geminiOcrThreshold, defaulting to 0.5")
                0.5f
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 3: Try ML Kit with fallback on failure
            // ═══════════════════════════════════════════════════════════════
            val mlKitResult = try {
                runOcrWithAutoDetect(uri)
            } catch (e: Exception) {
                Timber.w(e, "$TAG: ML Kit failed")
                
                // ✅ FALLBACK: Если ML Kit упал И Gemini включен → пробуем Gemini
                if (geminiEnabled) {
                    Timber.d("$TAG: 🔄 ML Kit failed, falling back to Gemini")
                    return when (val geminiResult = geminiOcrService.recognizeText(uri)) {
                        is DomainResult.Success -> {
                            Timber.d("$TAG: ✅ Gemini fallback successful")
                            geminiResult
                        }
                        is DomainResult.Failure -> {
                            // Both ML Kit and Gemini failed
                            Timber.e("$TAG: ❌ Both ML Kit and Gemini failed")
                            DomainResult.failure(
                                DomainError.OcrFailed(
                                    id = null,
                                    cause = Exception("ML Kit failed: ${e.message}, Gemini failed: ${geminiResult.error.message}")
                                )
                            )
                        }
                    }
                } else {
                    // Gemini disabled, return ML Kit error
                    throw e
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // STEP 4: Analyze ML Kit quality
            // ═══════════════════════════════════════════════════════════════
            val metrics = qualityAnalyzer.analyze(mlKitResult)
            
            if (BuildConfig.DEBUG) {
                Timber.d("$TAG: 📊 ML Kit quality: ${metrics.quality}, confidence: ${metrics.qualityPercent}%")
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 5: Decide on quality-based fallback
            // ═══════════════════════════════════════════════════════════════
            val shouldFallback = geminiEnabled && (
                metrics.recommendGeminiFallback ||
                metrics.overallConfidence < threshold
            )

            if (shouldFallback) {
                Timber.d("$TAG: 🔄 Low quality ML Kit result, falling back to Gemini")
                
                when (val geminiResult = geminiOcrService.recognizeText(uri)) {
                    is DomainResult.Success -> {
                        Timber.d("$TAG: ✅ Gemini fallback successful")
                        return geminiResult
                    }
                    is DomainResult.Failure -> {
                        // Gemini failed, but ML Kit succeeded → use ML Kit
                        Timber.w("$TAG: ⚠️ Gemini fallback failed, using ML Kit result")
                        return DomainResult.Success(
                            OcrResult(
                                text = mlKitResult.text,
                                detectedLanguage = mlKitResult.detectedLanguage,
                                confidence = mlKitResult.overallConfidence,
                                processingTimeMs = System.currentTimeMillis() - start,
                                source = OcrSource.ML_KIT
                            )
                        )
                    }
                }
            } else {
                // ML Kit quality is good
                if (BuildConfig.DEBUG) {
                    Timber.d("$TAG: ✅ ML Kit quality acceptable")
                }
                
                return DomainResult.Success(
                    OcrResult(
                        text = mlKitResult.text,
                        detectedLanguage = mlKitResult.detectedLanguage,
                        confidence = mlKitResult.overallConfidence,
                        processingTimeMs = System.currentTimeMillis() - start,
                        source = OcrSource.ML_KIT
                    )
                )
            }
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Hybrid OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    /**
     * ✅ NEW: ML Kit only recognition (no Gemini fallback).
     */
    suspend fun recognizeTextMlKitOnly(uri: Uri): DomainResult<OcrResult> {
        val start = System.currentTimeMillis()
        return try {
            val result = runOcrWithAutoDetect(uri)
            DomainResult.Success(
                OcrResult(
                    text = result.text,
                    detectedLanguage = result.detectedLanguage,
                    confidence = result.overallConfidence,
                    processingTimeMs = System.currentTimeMillis() - start,
                    source = OcrSource.ML_KIT
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ ML Kit only OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    /**
     * ✅ NEW: Gemini only recognition (skip ML Kit).
     */
    suspend fun recognizeTextGeminiOnly(uri: Uri): DomainResult<OcrResult> {
        return geminiOcrService.recognizeText(uri)
    }

    /**
     * Распознаёт текст с детальной информацией (blocks, lines, confidence).
     */
    suspend fun recognizeTextDetailed(uri: Uri): DomainResult<DetailedOcrResult> {
        val start = System.currentTimeMillis()
        return try {
            val scriptMode = getPreferredScriptMode()
            val textResult = runOcr(uri, scriptMode)
            
            DomainResult.Success(
                DetailedOcrResult(
                    fullText = textResult.text,
                    blocks = textResult.textBlocks.map { it.toDomain() },
                    detectedLanguage = detectLanguageFromText(textResult.text),
                    confidence = calculateOverallConfidence(textResult),
                    processingTimeMs = System.currentTimeMillis() - start
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Detailed OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    /**
     * Распознаёт текст с информацией о уверенности для каждого слова.
     */
    suspend fun recognizeTextWithConfidence(uri: Uri): DomainResult<OcrResultWithConfidence> {
        val start = System.currentTimeMillis()
        return try {
            val result = runOcrWithAutoDetect(uri)
            DomainResult.Success(result.copy(processingTimeMs = System.currentTimeMillis() - start))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Confidence OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    /**
     * Распознаёт текст с явно указанным режимом скрипта.
     */
    suspend fun recognizeTextWithScript(
        uri: Uri,
        scriptMode: OcrScriptMode
    ): DomainResult<OcrResultWithConfidence> {
        val start = System.currentTimeMillis()
        return try {
            val textResult = runOcr(uri, scriptMode)
            val result = processTextResult(textResult, scriptMode)
            DomainResult.Success(result.copy(processingTimeMs = System.currentTimeMillis() - start))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Script OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    /**
     * Определяет язык изображения.
     */
    suspend fun detectLanguage(uri: Uri): DomainResult<Language> = withContext(Dispatchers.IO) {
        try {
            val textResult = runOcr(uri, OcrScriptMode.LATIN)
            val text = textResult.text.trim()
            
            if (text.isBlank()) {
                return@withContext DomainResult.Success(Language.AUTO)
            }
            
            val language = detectLanguageFromText(text)
            DomainResult.Success(language ?: Language.AUTO)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "$TAG: ⚠️ Language detection failed")
            DomainResult.Success(Language.AUTO)
        }
    }

    // ═════════════

════════════════════════════════════
// ✅ TEST METHOD WITH GEMINI FALLBACK
// ════════════════════════════════════════════════════════════════════════════════
/**
 * ✅ TEST METHOD for Settings UI with REAL Gemini fallback.
 * 
 * Flow:
 * 1. Run ML Kit OCR
 * 2. Analyze quality with OcrQualityAnalyzer
 * 3. If quality is poor OR testGeminiFallback=true → call Gemini
 * 4. Return result with source indicator
 * 
 * @param uri Image URI
 * @param scriptMode OCR script mode
 * @param autoDetectLanguage Auto-detect language from image
 * @param confidenceThreshold Threshold for low-confidence words
 * @param testGeminiFallback Force Gemini fallback for testing
 */
suspend fun testOcr(
    uri: Uri,
    scriptMode: OcrScriptMode,
    autoDetectLanguage: Boolean,
    confidenceThreshold: Float,
    testGeminiFallback: Boolean = false
): DomainResult<OcrTestResult> {
    val start = System.currentTimeMillis()

    if (BuildConfig.DEBUG) {
        Timber.d("$TAG: 🧪 Starting OCR test")
        Timber.d("$TAG:    ├─ Mode: $scriptMode")
        Timber.d("$TAG:    ├─ Auto-detect: $autoDetectLanguage")
        Timber.d("$TAG:    ├─ Threshold: ${(confidenceThreshold * 100).toInt()}%")
        Timber.d("$TAG:    └─ Force Gemini: $testGeminiFallback")
    }

    return try {
        // ═══════════════════════════════════════════════════════════════
        // STEP 1: Determine effective script mode
        // ═══════════════════════════════════════════════════════════════
        val effectiveMode = if (autoDetectLanguage && scriptMode == OcrScriptMode.AUTO) {
            detectScriptFromImage(uri) ?: OcrScriptMode.LATIN
        } else {
            scriptMode
        }

        // ═══════════════════════════════════════════════════════════════
        // STEP 2: Run ML Kit OCR
        // ═══════════════════════════════════════════════════════════════
        val textResult = runOcr(uri, effectiveMode)
        val mlKitProcessed = processTextResult(textResult, effectiveMode)
        val mlKitTime = System.currentTimeMillis() - start
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: 📊 ML Kit result:")
            Timber.d("$TAG:    ├─ Text length: ${mlKitProcessed.text.length}")
            Timber.d("$TAG:    ├─ Confidence: ${((mlKitProcessed.overallConfidence ?: 0f) * 100).toInt()}%")
            Timber.d("$TAG:    └─ Time: ${mlKitTime}ms")
        }

        // ═══════════════════════════════════════════════════════════════
        // STEP 3: Analyze quality
        // ═══════════════════════════════════════════════════════════════
        val metrics = qualityAnalyzer.analyze(mlKitProcessed)
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: 📈 Quality analysis:")
            Timber.d("$TAG:    ├─ Quality: ${metrics.quality}")
            Timber.d("$TAG:    ├─ Handwritten: ${metrics.isLikelyHandwritten}")
            Timber.d("$TAG:    └─ Recommend Gemini: ${metrics.recommendGeminiFallback}")
        }

        // ═══════════════════════════════════════════════════════════════
        // STEP 4: Check Gemini availability & settings
        // ═══════════════════════════════════════════════════════════════
        val geminiAvailable = try {
            geminiOcrService.isAvailable()
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Gemini availability check failed")
            false
        }

        val geminiEnabled = try {
            settingsDataStore.geminiOcrEnabled.first()
        } catch (e: Exception) {
            true
        }

        val geminiThreshold = try {
            settingsDataStore.geminiOcrThreshold.first() / 100f
        } catch (e: Exception) {
            0.5f
        }

        // ═══════════════════════════════════════════════════════════════
        // STEP 5: Decide if Gemini should be used
        // ═══════════════════════════════════════════════════════════════
        val mlKitConfidence = mlKitProcessed.overallConfidence ?: 0f
        
        val shouldUseGemini = geminiAvailable && geminiEnabled && (
            testGeminiFallback ||
            metrics.recommendGeminiFallback ||
            mlKitConfidence < geminiThreshold
        )

        val fallbackReason: String? = when {
            !geminiAvailable -> null
            !geminiEnabled -> null
            testGeminiFallback -> "Manual test requested"
            metrics.recommendGeminiFallback -> metrics.fallbackReasons.joinToString(", ")
            mlKitConfidence < geminiThreshold -> 
                "Confidence ${(mlKitConfidence * 100).toInt()}% < threshold ${(geminiThreshold * 100).toInt()}%"
            else -> null
        }

        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: 🎯 Gemini decision:")
            Timber.d("$TAG:    ├─ Available: $geminiAvailable")
            Timber.d("$TAG:    ├─ Enabled: $geminiEnabled")
            Timber.d("$TAG:    ├─ Threshold: ${(geminiThreshold * 100).toInt()}%")
            Timber.d("$TAG:    ├─ Should use: $shouldUseGemini")
            Timber.d("$TAG:    └─ Reason: $fallbackReason")
        }

        // ═══════════════════════════════════════════════════════════════
        // STEP 6: Execute Gemini fallback if needed
        // ═══════════════════════════════════════════════════════════════
        var finalText = mlKitProcessed.text
        var finalConfidence = mlKitProcessed.overallConfidence
        var finalSource = OcrSource.ML_KIT
        var geminiTime: Long? = null
        var geminiFallbackTriggered = false

        if (shouldUseGemini) {
            Timber.d("$TAG: 🤖 Calling Gemini OCR...")
            val geminiStart = System.currentTimeMillis()
            
            when (val geminiResult = geminiOcrService.recognizeText(uri)) {
                is DomainResult.Success -> {
                    geminiTime = System.currentTimeMillis() - geminiStart
                    geminiFallbackTriggered = true
                    
                    // ✅ Use Gemini result
                    finalText = geminiResult.data.text
                    finalConfidence = geminiResult.data.confidence
                    finalSource = OcrSource.GEMINI
                    
                    Timber.d("$TAG: ✅ Gemini success:")
                    Timber.d("$TAG:    ├─ Text length: ${finalText.length}")
                    Timber.d("$TAG:    └─ Time: ${geminiTime}ms")
                }
                
                is DomainResult.Failure -> {
                    geminiTime = System.currentTimeMillis() - geminiStart
                    geminiFallbackTriggered = true
                    
                    Timber.w("$TAG: ⚠️ Gemini failed: ${geminiResult.error.message}")
                    // Keep ML Kit result
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // STEP 7: Build final result
        // ═══════════════════════════════════════════════════════════════
        val totalTime = System.currentTimeMillis() - start
        
        val finalWordCount = if (finalSource == OcrSource.GEMINI) {
            finalText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        } else {
            mlKitProcessed.words.size
        }
        
        val lowConfidenceWords = mlKitProcessed.words.filter { it.confidence < confidenceThreshold }

        DomainResult.Success(
            OcrTestResult(
                text = finalText,
                detectedLanguage = mlKitProcessed.detectedLanguage,
                detectedScript = effectiveMode,
                overallConfidence = finalConfidence,
                totalWords = finalWordCount,
                highConfidenceWords = mlKitProcessed.words.size - lowConfidenceWords.size,
                lowConfidenceWords = lowConfidenceWords.size,
                lowConfidenceRanges = mlKitProcessed.lowConfidenceRanges,
                wordConfidences = mlKitProcessed.words.map { it.text to it.confidence },
                processingTimeMs = totalTime,
                recognizerUsed = effectiveMode.displayName,
                source = finalSource,
                geminiFallbackTriggered = geminiFallbackTriggered,
                geminiFallbackReason = fallbackReason,
                geminiProcessingTimeMs = geminiTime,
                geminiAvailable = geminiAvailable,
                // ✅ NEW: Auto-translation fields (null for now, will be populated later)
                translatedText = null,
                translationTargetLang = null,
                translationTimeMs = null
            )
        )
        
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "$TAG: ❌ OCR test failed")
        DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
    }
}

/**
 * Возвращает список доступных режимов OCR.
 */
fun getAvailableScriptModes(): List<OcrScriptMode> = OcrScriptMode.entries

/**
 * Очищает cache recognizers (освобождает память).
 */
suspend fun clearCache() {
    recognizerLock.withLock {
        cachedRecognizer?.close()
        cachedRecognizer = null
        cachedScriptMode = null
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: 🧹 Recognizer cache cleared")
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// CORE OCR ENGINE
// ════════════════════════════════════════════════════════════════════════════════

private suspend fun runOcrWithAutoDetect(uri: Uri): OcrResultWithConfidence = withContext(Dispatchers.IO) {
    coroutineContext.ensureActive()
    
    val scriptMode = getPreferredScriptMode()
    coroutineContext.ensureActive()
    
    if (BuildConfig.DEBUG) {
        Timber.d("$TAG: 📝 OCR mode from DataStore: $scriptMode")
    }
    
    val effectiveMode = if (scriptMode == OcrScriptMode.AUTO) {
        val detected = detectScriptFromImage(uri)
        if (BuildConfig.DEBUG && detected != null) {
            Timber.d("$TAG:    └─ Auto-detected: $detected")
        }
        detected ?: OcrScriptMode.LATIN
    } else {
        scriptMode
    }
    
    coroutineContext.ensureActive()
    
    val textResult = runOcr(uri, effectiveMode)
    coroutineContext.ensureActive()
    
    processTextResult(textResult, effectiveMode)
}

/**
 * ✅ ИСПРАВЛЕНО: Правильный lifecycle Bitmap для Android 16
 * Bitmap recycling ПОСЛЕ завершения ML Kit обработки
 */
private suspend fun runOcr(uri: Uri, scriptMode: OcrScriptMode): Text {
    var bitmap: Bitmap? = null
    
    return try {
        val (inputImage, bmp) = loadImageSafe(uri)
        bitmap = bmp
        
        coroutineContext.ensureActive()
        
        val recognizer = getRecognizer(scriptMode)
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: ⚙️ Processing with ${scriptMode.displayName} recognizer...")
        }
        
        // ✅ ML Kit завершит работу ДО recycling
        val result = recognizer.process(inputImage).await()
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: ✅ MLKit processing complete")
        }
        
        result
        
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "$TAG: ❌ MLKit processing failed")
        throw e
    } finally {
        // ✅ Гарантированная очистка ПОСЛЕ завершения ML Kit
        bitmap?.recycle()
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// ✅ FIXED: Universal URI Loading (file:// + content://)
// ════════════════════════════════════════════════════════════════════════════════

private suspend fun loadImageSafe(uri: Uri): Pair<InputImage, Bitmap> = withContext(Dispatchers.IO) {
    val scheme = uri.scheme?.lowercase()
    
    if (BuildConfig.DEBUG) {
        Timber.d("$TAG: 📷 Loading image: $uri (scheme: $scheme)")
    }
    
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    
    openInputStreamForUri(uri).use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
    
    if (options.outWidth <= 0 || options.outHeight <= 0) {
        throw IOException("Failed to decode image dimensions from URI: $uri")
    }
    
    val sampleSize = calculateInSampleSize(
        options.outWidth,
        options.outHeight,
        MAX_IMAGE_DIMENSION,
        MAX_IMAGE_DIMENSION
    )
    
    if (BuildConfig.DEBUG) {
        Timber.d("$TAG:    ├─ Dimensions: ${options.outWidth}x${options.outHeight}")
        Timber.d("$TAG:    └─ Sample size: $sampleSize")
    }
    
    options.inJustDecodeBounds = false
    options.inSampleSize = sampleSize
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    
    val bitmap = openInputStreamForUri(uri).use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: throw IOException("Failed to decode bitmap from URI: $uri")
    
    val inputImage = InputImage.fromBitmap(bitmap, 0)
    Pair(inputImage, bitmap)
}

/**
 * ✅ Opens InputStream for ANY URI type.
 * Handles content://, file://, and absolute paths.
 */
private fun openInputStreamForUri(uri: Uri): InputStream {
    val scheme = uri.scheme?.lowercase()
    
    return when (scheme) {
        "content" -> {
            context.contentResolver.openInputStream(uri)
                ?: throw IOException("ContentResolver returned null for: $uri")
        }
        
        "file" -> {
            val path = uri.path 
                ?: throw IOException("File URI has no path: $uri")
            
            val file = File(path)
            if (!file.exists()) {
                throw FileNotFoundException("File does not exist: $path")
            }
            if (!file.canRead()) {
                throw IOException("Cannot read file: $path")
            }
            FileInputStream(file)
        }
        
        null, "" -> {
            val path = uri.toString()
            val file = File(path)
            if (!file.exists()) {
                throw FileNotFoundException("File does not exist: $path")
            }
            FileInputStream(file)
        }
        
        else -> {
            throw IOException("Unsupported URI scheme '$scheme': $uri")
        }
    }
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    var inSampleSize = MIN_SAMPLE_SIZE
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        
        while ((halfHeight / inSampleSize) >= reqHeight && 
               (halfWidth / inSampleSize) >= reqWidth &&
               inSampleSize < MAX_SAMPLE_SIZE) {
            inSampleSize *= 2
        }
    }
    
    return inSampleSize
}

private suspend fun getPreferredScriptMode(): OcrScriptMode = withContext(Dispatchers.IO) {
    try {
        val mode = settingsDataStore.ocrLanguage.first().trim().uppercase()
        
        when (mode) {
            "LATIN" -> OcrScriptMode.LATIN
            "CHINESE" -> OcrScriptMode.CHINESE
            "JAPANESE" -> OcrScriptMode.JAPANESE
            "KOREAN" -> OcrScriptMode.KOREAN
            "DEVANAGARI" -> OcrScriptMode.DEVANAGARI
            else -> OcrScriptMode.AUTO
        }
    } catch (e: Exception) {
        Timber.w(e, "$TAG: ⚠️ Failed to read OCR mode from DataStore, using AUTO")
        OcrScriptMode.AUTO
    }
}

private suspend fun getRecognizer(scriptMode: OcrScriptMode): TextRecognizer = recognizerLock.withLock {
    if (cachedScriptMode == scriptMode && cachedRecognizer != null) {
        return@withLock cachedRecognizer!!
    }
    
    cachedRecognizer?.close()
    
    val recognizer = when (scriptMode) {
        OcrScriptMode.AUTO, OcrScriptMode.LATIN -> 
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        OcrScriptMode.CHINESE -> 
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        OcrScriptMode.JAPANESE -> 
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        OcrScriptMode.KOREAN -> 
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        OcrScriptMode.DEVANAGARI -> 
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }
    
    cachedRecognizer = recognizer
    cachedScriptMode = scriptMode
    
    if (BuildConfig.DEBUG) {
        Timber.d("$TAG: ✨ Created new recognizer: $scriptMode")
    }
    
    recognizer
}

// ════════════════════════════════════════════════════════════════════════════════
// HELPER METHODS
// ════════════════════════════════════════════════════════════════════════════════

private suspend fun detectScriptFromImage(uri: Uri): OcrScriptMode? = withContext(Dispatchers.IO) {
    try {
        coroutineContext.ensureActive()
        
        val latinResult = runOcr(uri, OcrScriptMode.LATIN)
        val text = latinResult.text.trim()
        
        if (text.isBlank()) return@withContext null
        
        val scriptCounts = mutableMapOf<OcrScriptMode, Int>()
        
        for (char in text) {
            when (Character.UnicodeBlock.of(char)) {
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS -> 
                    scriptCounts[OcrScriptMode.CHINESE] = (scriptCounts[OcrScriptMode.CHINESE] ?: 0) + 1
                
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA -> 
                    scriptCounts[OcrScriptMode.JAPANESE] = (scriptCounts[OcrScriptMode.JAPANESE] ?: 0) + 1
                
                Character.UnicodeBlock.HANGUL_SYLLABLES,
                Character.UnicodeBlock.HANGUL_JAMO -> 
                    scriptCounts[OcrScriptMode.KOREAN] = (scriptCounts[OcrScriptMode.KOREAN] ?: 0) + 1
                
                Character.UnicodeBlock.DEVANAGARI -> 
                    scriptCounts[OcrScriptMode.DEVANAGARI] = (scriptCounts[OcrScriptMode.DEVANAGARI] ?: 0) + 1
                
                Character.UnicodeBlock.BASIC_LATIN,
                Character.UnicodeBlock.LATIN_1_SUPPLEMENT -> 
                    scriptCounts[OcrScriptMode.LATIN] = (scriptCounts[OcrScriptMode.LATIN] ?: 0) + 1
                
                else -> { /* ignore */ }
            }
        }
        
        scriptCounts.maxByOrNull { it.value }?.key
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "$TAG: Script detection failed")
        null
    }
}

private suspend fun detectLanguageFromText(text: String): Language? = withContext(Dispatchers.IO) {
    val trimmed = text.trim()
    if (trimmed.length < LANGUAGE_DETECTION_MIN_TEXT_LENGTH) {
        return@withContext null
    }
    
    try {
        val options = LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
        
        val identifier = LanguageIdentification.getClient(options)
        
        val code = suspendCancellableCoroutine<String> { cont ->
            identifier.identifyLanguage(trimmed)
                .addOnSuccessListener { result ->
                    if (cont.isActive) {
                        cont.resume(if (result == "und") "auto" else result)
                    }
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
            
            cont.invokeOnCancellation { identifier.close() }
        }
        
        identifier.close()
        Language.fromCode(code)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "$TAG: Language detection failed")
        null
    }
}

private fun processTextResult(textResult: Text, scriptMode: OcrScriptMode): OcrResultWithConfidence {
    val words = mutableListOf<WordWithConfidence>()
    var currentIndex = 0
    
    val fullText = buildString {
        for (block in textResult.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val wordText = element.text
                    val confidence = element.confidence ?: 0.9f
                    val confidenceLevel = classifyConfidence(confidence)
                    
                    val startIdx = currentIndex
                    val endIdx = currentIndex + wordText.length
                    
                    words.add(
                        WordWithConfidence(
                            text = wordText,
                            confidence = confidence,
                            confidenceLevel = confidenceLevel,
                            boundingBox = element.boundingBox?.toDomain(),
                            startIndex = startIdx,
                            endIndex = endIdx
                        )
                    )
                    
                    append(wordText)
                    currentIndex = endIdx
                    
                    if (element != line.elements.lastOrNull()) {
                        append(" ")
                        currentIndex++
                    }
                }
                
                if (line != block.lines.lastOrNull()) {
                    append("\n")
                    currentIndex++
                }
            }
            
            if (block != textResult.textBlocks.lastOrNull()) {
                append("\n\n")
                currentIndex += 2
            }
        }
    }
    
    val lowConfidenceRanges = words
        .filter { it.needsReview }
        .map { it.startIndex..it.endIndex }
    
    return OcrResultWithConfidence(
        text = fullText,
        detectedLanguage = null,
        detectedScript = scriptMode,
        overallConfidence = calculateOverallConfidence(textResult),
        words = words,
        lowConfidenceRanges = lowConfidenceRanges,
        processingTimeMs = 0L,
        recognizerUsed = scriptMode
    )
}

private fun classifyConfidence(confidence: Float): ConfidenceLevel = when {
    confidence >= ConfidenceLevel.HIGH.minConfidence -> ConfidenceLevel.HIGH
    confidence >= ConfidenceLevel.MEDIUM.minConfidence -> ConfidenceLevel.MEDIUM
    confidence >= ConfidenceLevel.LOW.minConfidence -> ConfidenceLevel.LOW
    else -> ConfidenceLevel.VERY_LOW
}

private fun calculateOverallConfidence(textResult: Text): Float {
    val confidences = textResult.textBlocks
        .flatMap { it.lines }
        .flatMap { it.elements }
        .mapNotNull { it.confidence }
    
    return if (confidences.isEmpty()) 0f else confidences.average().toFloat()
}
}
// ════════════════════════════════════════════════════════════════════════════════
// EXTENSIONS
// ════════════════════════════════════════════════════════════════════════════════
private fun Text.TextBlock.toDomain(): TextBlock = TextBlock(
text = text,
lines = lines.map { it.toDomain() },
confidence = null,
boundingBox = boundingBox?.toDomain()
)
private fun Text.Line.toDomain(): TextLine = TextLine(
text = text,
confidence = null,
boundingBox = boundingBox?.toDomain()
)
private fun android.graphics.Rect.toDomain(): BoundingBox = BoundingBox(
left = left,
top = top,
right = right,
bottom = bottom
)
private suspend fun  com.google.android.gms.tasks.Task.await(): T =
suspendCancellableCoroutine { cont ->
addOnSuccessListener {
if (cont.isActive) cont.resume(it)
}
addOnFailureListener {
if (cont.isActive) cont.resumeWithException(it)
}
addOnCanceledListener {
cont.cancel()
}
}