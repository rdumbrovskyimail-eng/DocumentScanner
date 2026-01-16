/*
 * MLKitScanner.kt
 * Version: 11.0.0 - PRODUCTION READY 2026 - MEMORY-SAFE + SYNCHRONIZED
 * 
 * ✅ CRITICAL FIXES:
 * - Fixed bitmap recycling AFTER MLKit completion (ROOT CAUSE)
 * - Proper async/await handling
 * - Thread-safe recognizer cache with Mutex
 * - Memory optimization for Android 16
 * - DataStore integration for global settings
 * 
 * ✅ SYNCHRONIZATION:
 * - Reads OCR mode from DataStore (single source of truth)
 * - Applied to ALL new documents in Editor
 * - Settings → DataStore → MLKitScanner → Editor
 */

package com.docs.scanner.data.remote.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.OcrResult
import com.docs.scanner.domain.repository.BoundingBox
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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Режимы распознавания текста MLKit.
 * 
 * Каждый режим оптимизирован для определённых скриптов.
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
    val recognizerUsed: String
) {
    val confidencePercent: String get() = "${((overallConfidence ?: 0f) * 100).toInt()}%"
    val qualityRating: String get() = when {
        (overallConfidence ?: 0f) >= 0.9f -> "Excellent"
        (overallConfidence ?: 0f) >= 0.7f -> "Good"
        (overallConfidence ?: 0f) >= 0.5f -> "Fair"
        else -> "Poor"
    }
}

/**
 * ✅ MAIN OCR ENGINE - Thread-safe, Memory-safe, DataStore-synchronized.
 * 
 * Этот класс отвечает за:
 * 1. Чтение настроек из DataStore (global settings)
 * 2. Управление MLKit recognizers (cache + thread-safety)
 * 3. Обработку изображений с оптимизацией памяти
 * 4. Возврат результатов с детальной статистикой
 */
@Singleton
class MLKitScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
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

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * ✅ MAIN METHOD: Распознаёт текст из изображения.
     * 
     * Используется в Editor при добавлении документов.
     * Читает настройки из DataStore (global settings).
     * 
     * @param uri URI изображения
     * @return OcrResult с текстом и метаданными
     */
    suspend fun recognizeText(uri: Uri): DomainResult<OcrResult> {
        val start = System.currentTimeMillis()
        
        if (BuildConfig.DEBUG) {
            Timber.d("🔍 Starting OCR recognition")
            Timber.d("   └─ Reading settings from DataStore...")
        }
        
        return try {
            val result = runOcrWithAutoDetect(uri)
            
            if (BuildConfig.DEBUG) {
                Timber.d("✅ OCR completed: ${result.text.length} chars, ${result.processingTimeMs}ms")
            }
            
            DomainResult.Success(
                OcrResult(
                    text = result.text,
                    detectedLanguage = result.detectedLanguage,
                    confidence = result.overallConfidence,
                    processingTimeMs = System.currentTimeMillis() - start
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "❌ OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
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
            Timber.e(e, "❌ Detailed OCR failed")
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
            Timber.e(e, "❌ Confidence OCR failed")
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
            Timber.e(e, "❌ Script OCR failed")
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
            Timber.w(e, "⚠️ Language detection failed")
            DomainResult.Success(Language.AUTO)
        }
    }

    /**
     * ✅ TEST METHOD: Запускает OCR тест с кастомными параметрами.
     * 
     * Используется в Settings для диагностики.
     * НЕ сохраняет настройки - только для preview.
     */
    suspend fun testOcr(
        uri: Uri,
        scriptMode: OcrScriptMode,
        autoDetectLanguage: Boolean,
        confidenceThreshold: Float
    ): DomainResult<OcrTestResult> {
        val start = System.currentTimeMillis()
        
        if (BuildConfig.DEBUG) {
            Timber.d("🧪 Starting OCR test")
            Timber.d("   ├─ Mode: $scriptMode")
            Timber.d("   ├─ Auto-detect: $autoDetectLanguage")
            Timber.d("   └─ Threshold: ${(confidenceThreshold * 100).toInt()}%")
        }
        
        return try {
            val effectiveMode = if (autoDetectLanguage && scriptMode == OcrScriptMode.AUTO) {
                detectScriptFromImage(uri) ?: OcrScriptMode.LATIN
            } else {
                scriptMode
            }
            
            val textResult = runOcr(uri, effectiveMode)
            val processed = processTextResult(textResult, effectiveMode)
            
            val filteredWords = processed.words.filter { it.confidence >= confidenceThreshold }
            val lowConfidenceWords = processed.words.filter { it.confidence < confidenceThreshold }
            
            DomainResult.Success(
                OcrTestResult(
                    text = processed.text,
                    detectedLanguage = processed.detectedLanguage,
                    detectedScript = effectiveMode,
                    overallConfidence = processed.overallConfidence,
                    totalWords = processed.words.size,
                    highConfidenceWords = filteredWords.size,
                    lowConfidenceWords = lowConfidenceWords.size,
                    lowConfidenceRanges = processed.lowConfidenceRanges,
                    wordConfidences = processed.words.map { it.text to it.confidence },
                    processingTimeMs = System.currentTimeMillis() - start,
                    recognizerUsed = effectiveMode.displayName
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "❌ OCR test failed")
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
                Timber.d("🧹 Recognizer cache cleared")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ✅ CORE OCR ENGINE - SYNCHRONIZED WITH DATASTORE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * ✅ CRITICAL: Запускает OCR с настройками из DataStore.
     * 
     * Это главный метод, который читает глобальные настройки
     * и применяет их к распознаванию.
     */
    private suspend fun runOcrWithAutoDetect(uri: Uri): OcrResultWithConfidence = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        
        // ✅ CRITICAL: Читаем настройки из DataStore (global settings)
        val scriptMode = getPreferredScriptMode()
        coroutineContext.ensureActive()
        
        if (BuildConfig.DEBUG) {
            Timber.d("📝 OCR mode from DataStore: $scriptMode")
        }
        
        // Определяем effective mode (с учётом auto-detect)
        val effectiveMode = if (scriptMode == OcrScriptMode.AUTO) {
            val detected = detectScriptFromImage(uri)
            if (BuildConfig.DEBUG && detected != null) {
                Timber.d("   └─ Auto-detected: $detected")
            }
            detected ?: OcrScriptMode.LATIN
        } else {
            scriptMode
        }
        
        coroutineContext.ensureActive()
        
        if (BuildConfig.DEBUG) {
            Timber.d("🔍 Using OCR mode: $effectiveMode")
        }
        
        // Запускаем OCR
        val textResult = runOcr(uri, effectiveMode)
        coroutineContext.ensureActive()
        
        // Обрабатываем результат
        processTextResult(textResult, effectiveMode)
    }

    /**
     * ⚠️ CRITICAL METHOD - PROPER BITMAP LIFECYCLE FOR ANDROID 16
     * 
     * ВАЖНО: Bitmap НЕ recycled до завершения MLKit обработки!
     * 
     * На Android 16 + MLKit 19.1.0:
     * - InputImage.fromBitmap() НЕ создаёт копию
     * - Держит REFERENCE на original bitmap
     * - Если recycle() раньше времени → crash "bitmap recycled"
     * 
     * РЕШЕНИЕ: Recycle ПОСЛЕ .await()
     */
    private suspend fun runOcr(uri: Uri, scriptMode: OcrScriptMode): Text = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        
        // ✅ STEP 1: Load image (returns BOTH InputImage AND Bitmap)
        val (inputImage, bitmap) = loadImageSafe(uri)
        coroutineContext.ensureActive()
        
        // ✅ STEP 2: Get recognizer
        val recognizer = getRecognizer(scriptMode)
        coroutineContext.ensureActive()
        
        // ✅ STEP 3: Process with MLKit
        try {
            if (BuildConfig.DEBUG) {
                Timber.d("⚙️ Processing with ${scriptMode.displayName} recognizer...")
            }
            
            // Wait for MLKit to finish
            val result = recognizer.process(inputImage).await()
            
            // ✅ NOW it's safe to recycle - MLKit has finished
            bitmap.recycle()
            
            if (BuildConfig.DEBUG) {
                Timber.d("✅ MLKit processing complete")
            }
            
            result
        } catch (e: Exception) {
            // Always recycle on error
            bitmap.recycle()
            Timber.e(e, "❌ MLKit processing failed")
            throw e
        }
    }

    /**
     * ✅ CRITICAL FIX for Android 16 + MLKit 19.1.0
     * 
     * PROBLEM: InputImage.fromBitmap() does NOT create a copy.
     * If we recycle() immediately, MLKit crashes.
     * 
     * SOLUTION: Return BOTH InputImage AND Bitmap.
     * Recycle AFTER processing completes.
     */
    private suspend fun loadImageSafe(uri: Uri): Pair<InputImage, Bitmap> = withContext(Dispatchers.IO) {
        // First pass - get dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        
        // Calculate sample size
        val sampleSize = calculateInSampleSize(
            options.outWidth,
            options.outHeight,
            MAX_IMAGE_DIMENSION,
            MAX_IMAGE_DIMENSION
        )
        
        if (BuildConfig.DEBUG) {
            Timber.d("📷 Image: ${options.outWidth}x${options.outHeight}")
            Timber.d("   └─ Sample size: $sampleSize")
        }
        
        // Second pass - decode with sample size
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IOException("Failed to decode bitmap from URI")
        
        // Create InputImage - on Android 16, this DOES NOT COPY
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        // ⚠️ CRITICAL: Return BOTH so bitmap can be recycled AFTER processing
        Pair(inputImage, bitmap)
    }

    /**
     * Calculate optimal sample size (power-of-2).
     */
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

    /**
     * ✅ CRITICAL: Читает предпочитаемый режим OCR из DataStore.
     * 
     * Это единственный источник истины для настроек OCR.
     * Settings → DataStore → MLKitScanner → Editor
     */
    private suspend fun getPreferredScriptMode(): OcrScriptMode = withContext(Dispatchers.IO) {
        try {
            val mode = settingsDataStore.ocrLanguage.first().trim().uppercase()
            
            val scriptMode = when (mode) {
                "LATIN" -> OcrScriptMode.LATIN
                "CHINESE" -> OcrScriptMode.CHINESE
                "JAPANESE" -> OcrScriptMode.JAPANESE
                "KOREAN" -> OcrScriptMode.KOREAN
                "DEVANAGARI" -> OcrScriptMode.DEVANAGARI
                else -> OcrScriptMode.AUTO
            }
            
            if (BuildConfig.DEBUG) {
                Timber.d("📖 Read OCR mode from DataStore: $mode → $scriptMode")
            }
            
            scriptMode
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to read OCR mode from DataStore, using AUTO")
            OcrScriptMode.AUTO
        }
    }

    /**
     * Thread-safe recognizer cache with Mutex.
     */
    private suspend fun getRecognizer(scriptMode: OcrScriptMode): TextRecognizer = recognizerLock.withLock {
        if (cachedScriptMode == scriptMode && cachedRecognizer != null) {
            if (BuildConfig.DEBUG) {
                Timber.d("♻️ Using cached recognizer: $scriptMode")
            }
            return@withLock cachedRecognizer!!
        }
        
        // Close old recognizer
        cachedRecognizer?.close()
        
        // Create new recognizer
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
            Timber.d("✨ Created new recognizer: $scriptMode")
        }
        
        recognizer
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Определяет скрипт из содержимого изображения.
     */
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
                }
            }
            
            scriptCounts.maxByOrNull { it.value }?.key
        } catch (e: Exception) {
            Timber.w(e, "Script detection failed")
            null
        }
    }

    /**
     * Определяет язык из текста с помощью ML Kit Language ID.
     */
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
        } catch (e: Exception) {
            Timber.w(e, "Language detection failed")
            null
        }
    }

    /**
     * Обрабатывает результат MLKit Text в OcrResultWithConfidence.
     */
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

    /**
     * Классифицирует уровень уверенности.
     */
    private fun classifyConfidence(confidence: Float): ConfidenceLevel = when {
        confidence >= ConfidenceLevel.HIGH.minConfidence -> ConfidenceLevel.HIGH
        confidence >= ConfidenceLevel.MEDIUM.minConfidence -> ConfidenceLevel.MEDIUM
        confidence >= ConfidenceLevel.LOW.minConfidence -> ConfidenceLevel.LOW
        else -> ConfidenceLevel.VERY_LOW
    }

    /**
     * Вычисляет общую уверенность из всех элементов.
     */
    private fun calculateOverallConfidence(textResult: Text): Float {
        val confidences = textResult.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .mapNotNull { it.confidence }
        
        return if (confidences.isEmpty()) 0f else confidences.average().toFloat()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// EXTENSIONS
// ══════════════════════════════════════════════════════════════════════════════

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

/**
 * Suspend extension для Google Tasks API.
 */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
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
