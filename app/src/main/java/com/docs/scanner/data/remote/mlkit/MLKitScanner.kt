package com.docs.scanner.data.remote.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.remote.gemini.GeminiOcrService
import com.docs.scanner.domain.core.BoundingBox
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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ✅ HYBRID OCR ENGINE - ML Kit + Gemini Fallback (2026)
 * 
 * Flow:
 * 1. Try ML Kit first (fast, offline, free)
 * 2. Analyze quality with OcrQualityAnalyzer
 * 3. If quality is poor → fallback to Gemini Vision API
 * 4. Return unified OcrResult with source indicator
 * 
 * Settings synced via DataStore:
 * - Script mode (Latin, Chinese, Japanese, etc.)
 * - Gemini fallback enabled/disabled
 * - Confidence threshold for fallback trigger
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
        private const val LANGUAGE_DETECTION_MIN_TEXT_LENGTH = 20
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
     * ✅ MAIN METHOD: Hybrid OCR with automatic Gemini fallback.
     * 
     * This is the primary method used throughout the app.
     * Automatically decides between ML Kit and Gemini based on quality.
     * 
     * @param uri Image URI
     * @return OcrResult with text and source indicator
     */
    suspend fun recognizeText(uri: Uri): DomainResult<OcrResult> {
        val start = System.currentTimeMillis()
        
        return try {
            // Check if Gemini-only mode is enabled
            val alwaysGemini = settingsDataStore.geminiOcrAlways.first()
            
            if (alwaysGemini) {
                Timber.d("$TAG: 🤖 Gemini-only mode, skipping ML Kit")
                return geminiOcrService.recognizeText(uri)
            }
            
            // Step 1: Try ML Kit first
            Timber.d("$TAG: 🔍 Starting hybrid OCR")
            val mlKitResult = runMlKitOcr(uri)
            
            // Step 2: Analyze quality
            val metrics = qualityAnalyzer.analyze(mlKitResult)
            Timber.d("$TAG: 📊 ML Kit quality: ${metrics.quality}, confidence: ${metrics.qualityPercent}%")
            
            // Step 3: Decide on fallback
            val geminiEnabled = settingsDataStore.geminiOcrEnabled.first()
            val threshold = settingsDataStore.geminiOcrThreshold.first() / 100f
            
            val shouldFallback = geminiEnabled && (
                metrics.recommendGeminiFallback ||
                metrics.overallConfidence < threshold
            )
            
            if (shouldFallback) {
                Timber.d("$TAG: 🔄 Quality below threshold, falling back to Gemini")
                Timber.d("$TAG:    Reasons: ${metrics.fallbackReasons.joinToString(", ")}")
                
                // Try Gemini
                val geminiResult = geminiOcrService.recognizeText(uri)
                
                // Return Gemini result if successful, otherwise return ML Kit result
                when (geminiResult) {
                    is DomainResult.Success -> {
                        Timber.d("$TAG: ✅ Gemini OCR successful")
                        geminiResult
                    }
                    is DomainResult.Failure -> {
                        Timber.w("$TAG: ⚠️ Gemini failed, using ML Kit result")
                        DomainResult.Success(mlKitResult.toOcrResult(System.currentTimeMillis() - start))
                    }
                }
            } else {
                Timber.d("$TAG: ✅ ML Kit quality acceptable, no fallback needed")
                DomainResult.Success(mlKitResult.toOcrResult(System.currentTimeMillis() - start))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Hybrid OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    /**
     * ML Kit only OCR (no Gemini fallback).
     * Used when you explicitly want ML Kit results.
     */
    suspend fun recognizeTextMlKitOnly(uri: Uri): DomainResult<OcrResult> {
        val start = System.currentTimeMillis()
        
        return try {
            val result = runMlKitOcr(uri)
            DomainResult.Success(result.toOcrResult(System.currentTimeMillis() - start))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ ML Kit OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    /**
     * Gemini only OCR (skip ML Kit).
     * Used for known handwritten documents.
     */
    suspend fun recognizeTextGeminiOnly(uri: Uri): DomainResult<OcrResult> {
        return geminiOcrService.recognizeText(uri)
    }

    /**
     * Detailed OCR with block/line structure.
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
     * OCR with word-level confidence data.
     */
    suspend fun recognizeTextWithConfidence(uri: Uri): DomainResult<OcrResultWithConfidence> {
        val start = System.currentTimeMillis()
        return try {
            val result = runMlKitOcr(uri)
            DomainResult.Success(result.copy(processingTimeMs = System.currentTimeMillis() - start))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Confidence OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }

    /**
     * OCR with explicit script mode (for testing).
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
     * ✅ TEST METHOD for Settings UI.
     * Tests OCR with current settings and returns detailed results.
     * Includes Gemini fallback test if enabled.
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
            Timber.d("$TAG:    └─ Test Gemini: $testGeminiFallback")
        }
        
        return try {
            // Determine effective mode
            val effectiveMode = if (autoDetectLanguage && scriptMode == OcrScriptMode.AUTO) {
                detectScriptFromImage(uri) ?: OcrScriptMode.LATIN
            } else {
                scriptMode
            }
            
            // Run ML Kit
            val textResult = runOcr(uri, effectiveMode)
            val processed = processTextResult(textResult, effectiveMode)
            
            // Analyze quality
            val metrics = qualityAnalyzer.analyze(processed)
            
            // Optionally test Gemini
            var geminiText: String? = null
            var geminiTime: Long? = null
            
            if (testGeminiFallback || metrics.recommendGeminiFallback) {
                val geminiStart = System.currentTimeMillis()
                val geminiResult = geminiOcrService.recognizeText(uri)
                geminiTime = System.currentTimeMillis() - geminiStart
                
                if (geminiResult is DomainResult.Success) {
                    geminiText = geminiResult.data.text
                }
            }
            
            // Build result
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
                    recognizerUsed = effectiveMode.displayName,
                    // NEW: Quality metrics
                    qualityMetrics = metrics,
                    geminiAvailable = geminiOcrService.isAvailable(),
                    geminiText = geminiText,
                    geminiProcessingTimeMs = geminiTime
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
     * Returns available script modes.
     */
    fun getAvailableScriptModes(): List<OcrScriptMode> = OcrScriptMode.entries

    /**
     * Clears recognizer cache.
     */
    suspend fun clearCache() {
        recognizerLock.withLock {
            cachedRecognizer?.close()
            cachedRecognizer = null
            cachedScriptMode = null
            Timber.d("$TAG: 🧹 Recognizer cache cleared")
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // CORE ML KIT ENGINE
    // ════════════════════════════════════════════════════════════════════════════════

    /**
     * Runs ML Kit OCR with auto-detection and returns detailed result.
     */
    private suspend fun runMlKitOcr(uri: Uri): OcrResultWithConfidence = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        
        val scriptMode = getPreferredScriptMode()
        coroutineContext.ensureActive()
        
        val effectiveMode = if (scriptMode == OcrScriptMode.AUTO) {
            val detected = detectScriptFromImage(uri)
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
     * Low-level ML Kit OCR execution.
     */
    private suspend fun runOcr(uri: Uri, scriptMode: OcrScriptMode): Text = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        
        val (inputImage, bitmap) = loadImageSafe(uri)
        coroutineContext.ensureActive()
        
        val recognizer = getRecognizer(scriptMode)
        coroutineContext.ensureActive()
        
        try {
            val result = recognizer.process(inputImage).await()
            bitmap.recycle()
            result
        } catch (e: Exception) {
            bitmap.recycle()
            throw e
        }
    }

    /**
     * Loads image safely for ML Kit.
     */
    private suspend fun loadImageSafe(uri: Uri): Pair<InputImage, Bitmap> = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        
        val sampleSize = calculateInSampleSize(
            options.outWidth, options.outHeight,
            MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION
        )
        
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IOException("Failed to decode bitmap from URI")
        
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        Pair(inputImage, bitmap)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
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
     * Gets preferred script mode from DataStore.
     */
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
            Timber.w(e, "$TAG: Failed to read OCR mode, using AUTO")
            OcrScriptMode.AUTO
        }
    }

    /**
     * Thread-safe recognizer management.
     */
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

/**
 * Converts OcrResultWithConfidence to simple OcrResult.
 */
private fun OcrResultWithConfidence.toOcrResult(processingTimeMs: Long): OcrResult = OcrResult(
    text = text,
    detectedLanguage = detectedLanguage,
    confidence = overallConfidence,
    processingTimeMs = processingTimeMs,
    source = OcrSource.ML_KIT
)