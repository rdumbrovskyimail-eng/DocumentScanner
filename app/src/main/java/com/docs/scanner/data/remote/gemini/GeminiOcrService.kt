package com.docs.scanner.data.remote.gemini

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.docs.scanner.BuildConfig
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.OcrResult
import com.docs.scanner.domain.core.OcrSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Vision-based OCR Service.
 * 
 * Used as fallback when ML Kit produces low-confidence results,
 * especially for handwritten or difficult-to-read text.
 * 
 * Features:
 * - Automatic key failover via GeminiKeyManager
 * - Batch processing with concurrency control
 * - Image optimization for API limits
 * - Universal prompt for printed + handwritten text
 * 
 * ✅ OPTIMIZED in FIX #3:
 * - Rate limit protection with reduced concurrency (5 → 2)
 * - 500ms delay between batch requests
 * - Explicit IO dispatcher for network operations
 * - Improved error handling without batch interruption
 * - Better progress tracking with ETA
 */
@Singleton
class GeminiOcrService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: GeminiApiService,
    private val keyManager: GeminiKeyManager
) {
    companion object {
        private const val TAG = "GeminiOcrService"
        
        // Models
        private const val PRIMARY_MODEL = "gemini-1.5-flash"
        private const val FALLBACK_MODEL = "gemini-2.0-flash-lite"
        
        // Image constraints
        private const val MAX_IMAGE_DIMENSION = 2048
        private const val JPEG_QUALITY = 85
        private const val MAX_IMAGE_SIZE_BYTES = 4 * 1024 * 1024 // 4MB
        
        // Batch processing
        // ✅ OPTIMIZED: Reduced from 5 to 2 to avoid Gemini API rate limits (429)
        // Free tier: ~15 requests/minute → 2 concurrent = safe
        private const val DEFAULT_BATCH_CONCURRENCY = 2
        private const val BATCH_REQUEST_DELAY_MS = 500L // ✅ 500ms delay between requests
        
        // Response markers
        private const val NO_TEXT_MARKER = "[NO_TEXT_FOUND]"
        
        /**
         * Universal OCR prompt.
         * Handles: printed text, handwritten text, mixed content, low quality images.
         */
        private val OCR_PROMPT = """
You are an advanced OCR system. Extract ALL text from this image accurately.

The image may contain:
• Printed text (documents, books, signs, labels, receipts)
• Handwritten text (notes, letters, forms, signatures)
• Mixed content (forms with printed labels and handwritten entries)
• Low quality text (blurry, faded, damaged, partially obscured)
• Text in any language or multiple languages

Instructions:
1. Extract every piece of visible text, including:
   - Main body text
   - Headers, titles, captions
   - Labels, annotations, stamps
   - Numbers, dates, codes
   - Handwritten notes and signatures

2. Preserve the original:
   - Language (do NOT translate anything)
   - Structure (paragraphs, line breaks)
   - Special characters and punctuation

3. For difficult-to-read text:
   - Make your best interpretation
   - If a word is completely illegible, use [?] placeholder
   - For partially readable words, include your best guess

4. Format:
   - Return ONLY the extracted text
   - No explanations, headers, or markdown formatting
   - Maintain natural reading order (top-to-bottom, left-to-right)

If the image contains absolutely no readable text, return exactly: [NO_TEXT_FOUND]
""".trimIndent()
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Recognizes text from a single image using Gemini Vision.
     * 
     * @param uri Image URI (content:// or file://)
     * @return OcrResult with extracted text
     */
    suspend fun recognizeText(uri: Uri): DomainResult<OcrResult> {
        val startTime = System.currentTimeMillis()
        
        return try {
            Timber.d("$TAG: 🔍 Starting Gemini OCR for: $uri")
            
            // Load and encode image
            val base64Image = withContext(Dispatchers.IO) {
                loadAndEncodeImage(uri)
            }
            
            // Build request
            val request = geminiVisionRequest {
                addText(OCR_PROMPT)
                addImage(base64Image, "image/jpeg")
                config(GenerationConfig.OCR)
            }
            
            // Execute with automatic key failover
            val response = keyManager.executeWithFailover { apiKey ->
                try {
                    apiService.generateContentVision(
                        model = PRIMARY_MODEL,
                        apiKey = apiKey,
                        body = request
                    )
                } catch (e: Exception) {
                    // Try fallback model if primary fails
                    Timber.w("$TAG: Primary model failed, trying fallback")
                    apiService.generateContentVision(
                        model = FALLBACK_MODEL,
                        apiKey = apiKey,
                        body = request
                    )
                }
            }
            
            // Process response
            val processingTime = System.currentTimeMillis() - startTime
            processResponse(response, processingTime)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Gemini OCR failed")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }
    
    /**
     * Batch OCR processing with concurrency control and rate limit protection.
     * 
     * ✅ OPTIMIZED in FIX #3:
     * - Reduced maxConcurrency from 5 to 2 (avoid 429 rate limits)
     * - Added 500ms delay between requests
     * - Explicit IO dispatcher
     * - Better progress tracking
     * - Improved error handling
     * 
     * @param uris List of image URIs
     * @param maxConcurrency Maximum parallel requests (default: 2, recommended max: 3)
     * @param onProgress Progress callback (completed, total)
     * @return List of results in same order as input
     */
    suspend fun recognizeTextBatch(
        uris: List<Uri>,
        maxConcurrency: Int = DEFAULT_BATCH_CONCURRENCY,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): List<DomainResult<OcrResult>> = coroutineScope {
        if (uris.isEmpty()) return@coroutineScope emptyList()
        
        Timber.d("$TAG: 📦 Starting batch OCR for ${uris.size} images (concurrency: $maxConcurrency)")
        
        val semaphore = Semaphore(maxConcurrency)
        val completed = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        uris.mapIndexed { index, uri ->
            async(Dispatchers.IO) { // ✅ EXPLICIT: IO dispatcher for network
                semaphore.withPermit {
                    try {
                        // ✅ NEW: Rate limit protection - delay between requests
                        if (index > 0) {
                            delay(BATCH_REQUEST_DELAY_MS)
                        }
                        
                        val result = recognizeText(uri)
                        val done = completed.incrementAndGet()
                        
                        // Progress callback
                        onProgress?.invoke(done, uris.size)
                        
                        if (BuildConfig.DEBUG) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val avgTime = elapsed / done
                            val remaining = (uris.size - done) * avgTime / 1000
                            Timber.d("$TAG: Batch progress: $done/${uris.size} (ETA: ${remaining}s)")
                        }
                        
                        result
                        
                    } catch (e: CancellationException) {
                        throw e // ✅ Rethrow cancellation
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Batch item $index failed")
                        val done = completed.incrementAndGet()
                        onProgress?.invoke(done, uris.size)
                        
                        // ✅ Return Failure instead of crashing entire batch
                        DomainResult.failure<OcrResult>(
                            DomainError.OcrFailed(id = null, cause = e)
                        )
                    }
                }
            }
        }.awaitAll().also {
            val totalTime = System.currentTimeMillis() - startTime
            Timber.d("$TAG: ✅ Batch OCR complete: ${uris.size} images in ${totalTime / 1000}s")
        }
    }
    
    /**
     * Checks if Gemini OCR is available (has valid API key).
     */
    suspend fun isAvailable(): Boolean {
        return keyManager.getHealthyKeyCount() > 0
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════════════════
    
    private fun processResponse(
        response: GeminiVisionResponse,
        processingTimeMs: Long
    ): DomainResult<OcrResult> {
        // Check for blocked content
        if (response.isBlocked()) {
            Timber.w("$TAG: Response blocked: ${response.getBlockReason()}")
            return DomainResult.failure(
                DomainError.OcrFailed(null, Exception("Content blocked: ${response.getBlockReason()}"))
            )
        }
        
        val text = response.extractText()
        
        // Check for no text marker
        if (text == NO_TEXT_MARKER || text.isBlank()) {
            Timber.d("$TAG: No text found in image")
            return DomainResult.Success(
                OcrResult(
                    text = "",
                    detectedLanguage = null,
                    confidence = 0f,
                    processingTimeMs = processingTimeMs,
                    source = OcrSource.GEMINI
                )
            )
        }
        
        // Clean up text (remove [?] placeholders from count for confidence)
        val cleanText = text.replace("[?]", "").trim()
        val uncertainCount = text.split("[?]").size - 1
        
        // Estimate confidence based on uncertain markers
        val confidence = when {
            uncertainCount == 0 -> 0.9f
            uncertainCount <= 2 -> 0.8f
            uncertainCount <= 5 -> 0.7f
            else -> 0.6f
        }
        
        Timber.d("$TAG: ✅ OCR complete: ${text.length} chars, ${processingTimeMs}ms")
        
        return DomainResult.Success(
            OcrResult(
                text = text,
                detectedLanguage = null, // Gemini doesn't return detected language
                confidence = confidence,
                processingTimeMs = processingTimeMs,
                source = OcrSource.GEMINI
            )
        )
    }
    
    private suspend fun loadAndEncodeImage(uri: Uri): String = withContext(Dispatchers.IO) {
        // First pass - get dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IOException("Cannot open image: $uri")
        
        // Calculate sample size for memory efficiency
        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
        
        // Second pass - decode with sample size
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IOException("Cannot decode image: $uri")
        
        try {
            // Scale down if still too large
            val scaled = scaleBitmapIfNeeded(bitmap)
            
            // Encode to JPEG base64
            val base64 = ByteArrayOutputStream().use { baos ->
                var quality = JPEG_QUALITY
                
                // Compress with decreasing quality until under size limit
                do {
                    baos.reset()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    quality -= 10
                } while (baos.size() > MAX_IMAGE_SIZE_BYTES && quality > 30)
                
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
            
            // Clean up scaled bitmap if different from original
            if (scaled !== bitmap) {
                scaled.recycle()
            }
            
            base64
        } finally {
            bitmap.recycle()
        }
    }
    
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val maxDim = maxOf(width, height)
        
        while (maxDim / sampleSize > MAX_IMAGE_DIMENSION * 2) {
            sampleSize *= 2
        }
        
        return sampleSize
    }
    
    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        
        if (maxSide <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }
        
        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxSide
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}