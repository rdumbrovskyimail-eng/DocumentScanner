package com.docs.scanner.data.remote.gemini

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.preferences.SettingsDataStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Vision-based OCR Service.
 * 
 * Version: 2.0.0 - SPEED OPTIMIZED (2026)
 * 
 * ✅ NEW IN 2.0.0:
 * - Dynamic model selection from SettingsDataStore
 * - Minimal OCR prompt for faster processing
 * - Smart image compression (only if >3MB)
 * - Optimized GenerationConfig
 * - Removed nested try-catch for model fallback
 * 
 * Features:
 * - Automatic key failover via GeminiKeyManager
 * - Batch processing with concurrency control
 * - Smart image optimization (compress only when needed)
 * - Fast OCR prompt
 */
@Singleton
class GeminiOcrService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: GeminiApiService,
    private val keyManager: GeminiKeyManager,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "GeminiOcrService"
        
        // ✅ OPTIMIZED: Image constraints - compress only if >3MB
        private const val MAX_IMAGE_DIMENSION = 2048
        private const val JPEG_QUALITY = 80
        private const val MAX_IMAGE_SIZE_BYTES = 3 * 1024 * 1024 // 3MB - trigger for compression
        
        // Batch processing
        private const val DEFAULT_BATCH_CONCURRENCY = 2
        private const val BATCH_REQUEST_DELAY_MS = 300L // Reduced from 500ms
        
        // Response markers
        private const val NO_TEXT_MARKER = "[NO_TEXT_FOUND]"
        
        /**
         * ✅ OPTIMIZED: Minimal OCR prompt for maximum speed
         * Old prompt was ~500 tokens, this is ~30 tokens
         */
        private const val OCR_PROMPT = "Extract all visible text from the image. Fast.\n\nReturn ONLY the extracted text."
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Recognizes text from a single image using Gemini Vision.
     * 
     * ✅ OPTIMIZED:
     * - Uses model from settings (dynamic selection)
     * - Minimal prompt for speed
     * - Smart image compression (only if >3MB)
     * - Fast GenerationConfig
     * 
     * @param uri Image URI (content:// or file://)
     * @return OcrResult with extracted text
     */
    suspend fun recognizeText(uri: Uri): DomainResult<OcrResult> {
        val startTime = System.currentTimeMillis()
        
        return try {
            // ✅ Get selected model from settings
            val selectedModel = try {
                settingsDataStore.geminiOcrModel.first()
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Failed to get model from settings, using default")
                "gemini-2.5-flash"
            }
            
            if (BuildConfig.DEBUG) {
                Timber.d("$TAG: 🔍 Starting Gemini OCR")
                Timber.d("$TAG:    ├─ URI: $uri")
                Timber.d("$TAG:    └─ Model: $selectedModel")
            }
            
            // ✅ Load and encode image (compress only if >3MB)
            val base64Image = withContext(Dispatchers.IO) {
                loadAndEncodeImageOptimized(uri)
            }
            
            val imageLoadTime = System.currentTimeMillis() - startTime
            if (BuildConfig.DEBUG) {
                Timber.d("$TAG: Image loaded in ${imageLoadTime}ms")
            }
            
            // Build request with fast config
            val request = geminiVisionRequest {
                addText(OCR_PROMPT)
                addImage(base64Image, "image/jpeg")
                config(GenerationConfig.OCR_FAST)
            }
            
            // ✅ Execute with automatic key failover (no nested try-catch)
            val response = keyManager.executeWithFailover { apiKey ->
                apiService.generateContentVision(
                    model = selectedModel,
                    apiKey = apiKey,
                    body = request
                )
            }
            
            // Process response
            val processingTime = System.currentTimeMillis() - startTime
            
            if (BuildConfig.DEBUG) {
                Timber.d("$TAG: ✅ OCR completed in ${processingTime}ms")
            }
            
            processResponse(response, processingTime)
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Timber.e(e, "$TAG: ❌ Gemini OCR failed after ${elapsed}ms")
            DomainResult.failure(DomainError.OcrFailed(id = null, cause = e))
        }
    }
    
    /**
     * Batch OCR processing with concurrency control and rate limit protection.
     * 
     * @param uris List of image URIs
     * @param maxConcurrency Maximum parallel requests (default: 2)
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
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        // Rate limit protection - delay between requests
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
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Batch item $index failed")
                        val done = completed.incrementAndGet()
                        onProgress?.invoke(done, uris.size)
                        
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
        val uncertainCount = text.split("[?]").size - 1
        
        // Estimate confidence based on uncertain markers
        val confidence = when {
            uncertainCount == 0 -> 0.95f
            uncertainCount <= 2 -> 0.85f
            uncertainCount <= 5 -> 0.75f
            else -> 0.65f
        }
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: ✅ OCR result: ${text.length} chars, confidence=${(confidence * 100).toInt()}%")
        }
        
        return DomainResult.Success(
            OcrResult(
                text = text,
                detectedLanguage = null,
                confidence = confidence,
                processingTimeMs = processingTimeMs,
                source = OcrSource.GEMINI
            )
        )
    }
    
    /**
     * Universal InputStream opener for any URI type.
     * Supports: content://, file://, absolute paths
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
                // Absolute path without scheme
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
    
    /**
     * ✅ OPTIMIZED: Smart image loading
     * - If file <= 3MB: send as-is (fastest path)
     * - If file > 3MB: compress to fit
     */
    private suspend fun loadAndEncodeImageOptimized(uri: Uri): String = withContext(Dispatchers.IO) {
        // Read raw bytes first
        val rawBytes = openInputStreamForUri(uri).use { it.readBytes() }
        val fileSizeMB = rawBytes.size / (1024f * 1024f)
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: Image size: %.2f MB", fileSizeMB)
        }
        
        // If small enough - send as-is (fastest path)
        if (rawBytes.size <= MAX_IMAGE_SIZE_BYTES) {
            if (BuildConfig.DEBUG) {
                Timber.d("$TAG: ✅ Sending original image (no compression needed)")
            }
            return@withContext Base64.encodeToString(rawBytes, Base64.NO_WRAP)
        }
        
        // Need to compress
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: ⚙️ Compressing image (%.2fMB > 3MB limit)", fileSizeMB)
        }
        
        compressAndEncodeImage(uri)
    }
    
    /**
     * Compresses image to fit within size limit.
     */
    private suspend fun compressAndEncodeImage(uri: Uri): String = withContext(Dispatchers.IO) {
        // Get dimensions first
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openInputStreamForUri(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("Failed to decode image dimensions from URI: $uri")
        }
        
        // Calculate optimal sample size
        val sampleSize = calculateOptimalSampleSize(options.outWidth, options.outHeight)
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: Compressing: ${options.outWidth}x${options.outHeight}, sampleSize=$sampleSize")
        }
        
        // Decode with sample size
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        
        val bitmap = openInputStreamForUri(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IOException("Failed to decode bitmap")
        
        try {
            // Scale if still too large
            val scaled = scaleBitmapIfNeeded(bitmap)
            
            // Compress to JPEG
            val base64 = ByteArrayOutputStream().use { baos ->
                var quality = JPEG_QUALITY
                
                do {
                    baos.reset()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    quality -= 10
                } while (baos.size() > MAX_IMAGE_SIZE_BYTES && quality > 40)
                
                if (BuildConfig.DEBUG) {
                    Timber.d("$TAG: Compressed to ${baos.size() / 1024}KB at quality=$quality")
                }
                
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
            
            if (scaled !== bitmap) {
                scaled.recycle()
            }
            
            base64
        } finally {
            bitmap.recycle()
        }
    }
    
    /**
     * Calculate sample size to reduce memory usage during decode.
     */
    private fun calculateOptimalSampleSize(width: Int, height: Int): Int {
        val maxDim = maxOf(width, height)
        var sampleSize = 1
        
        // Reduce until under MAX_IMAGE_DIMENSION
        while (maxDim / sampleSize > MAX_IMAGE_DIMENSION) {
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
