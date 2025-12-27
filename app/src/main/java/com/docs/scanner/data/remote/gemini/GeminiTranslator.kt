package com.docs.scanner.data.remote.gemini

import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level wrapper for Gemini API translation and OCR correction.
 * 
 * 🔴 CRITICAL SESSION 4 & 5 FIXES:
 * - ✅ Integrated TranslationCacheManager (was missing - every call = API usage!)
 * - ✅ Integrated EncryptedKeyStorage (no more API key as parameter)
 * - ✅ Added language parameters (targetLanguage, sourceLanguage)
 * - ✅ Added useCache parameter
 * - ✅ Cache check BEFORE API call
 * - ✅ Cache save AFTER successful API call
 * 
 * Benefits:
 * - Reduces API quota usage (free tier: 15 RPM, 1M TPM)
 * - Works offline for cached translations
 * - Faster responses (no network latency)
 * - Better UX (instant results for repeated translations)
 */
@Singleton
class GeminiTranslator @Inject constructor(
    private val geminiApi: GeminiApi,
    private val translationCacheManager: TranslationCacheManager,
    private val encryptedKeyStorage: EncryptedKeyStorage
) {
    
    /**
     * Translate text using Gemini API with intelligent caching.
     * 
     * @param text Text to translate
     * @param targetLanguage Target language code (e.g., "ru", "en", "zh")
     * @param sourceLanguage Source language code or "auto" for auto-detection
     * @param useCache Whether to use cache (default: true)
     * @return Result with translated text or error
     * 
     * Flow:
     * 1. Check if API key exists
     * 2. If useCache: check cache
     * 3. If cache miss: call API
     * 4. If success: save to cache
     * 5. Return result
     */
    suspend fun translate(
        text: String,
        targetLanguage: String = "ru",
        sourceLanguage: String = "auto",
        useCache: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        
        // Validation
        if (text.isBlank()) {
            return@withContext Result.Error(
                Exception("Text cannot be empty")
            )
        }
        
        // ✅ Get API key from encrypted storage
        val apiKey = encryptedKeyStorage.getActiveApiKey()
        if (apiKey == null) {
            return@withContext Result.Error(
                Exception("API key not found. Please add it in Settings.")
            )
        }
        
        // ✅ STEP 1: Check cache BEFORE API call
        if (useCache) {
            try {
                val cached = translationCacheManager.getCachedTranslation(
                    text = text,
                    sourceLang = sourceLanguage,
                    targetLang = targetLanguage,
                    maxAgeDays = 30
                )
                
                if (cached != null) {
                    android.util.Log.d(
                        "GeminiTranslator",
                        "✅ Cache HIT: Translation from cache (saved API call)"
                    )
                    return@withContext Result.Success(cached)
                }
                
                android.util.Log.d(
                    "GeminiTranslator",
                    "⚠️ Cache MISS: Calling Gemini API"
                )
            } catch (e: Exception) {
                android.util.Log.w(
                    "GeminiTranslator",
                    "⚠️ Cache read error: ${e.message}, falling back to API"
                )
            }
        }
        
        // ✅ STEP 2: Call Gemini API
        val result = geminiApi.translateText(
            text = text,
            apiKey = apiKey,
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage
        )
        
        // ✅ STEP 3: Handle result
        when (result) {
            is GeminiResult.Allowed -> {
                val translation = result.text
                
                // ✅ STEP 4: Save to cache on success
                if (useCache) {
                    try {
                        translationCacheManager.cacheTranslation(
                            originalText = text,
                            translatedText = translation,
                            sourceLang = sourceLanguage,
                            targetLang = targetLanguage
                        )
                        android.util.Log.d(
                            "GeminiTranslator",
                            "✅ Translation cached for future use"
                        )
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "GeminiTranslator",
                            "⚠️ Failed to cache translation: ${e.message}"
                        )
                        // Don't fail the request if caching fails
                    }
                }
                
                Result.Success(translation)
            }
            
            is GeminiResult.Blocked -> {
                Result.Error(
                    Exception("Translation blocked by Gemini: ${result.reason}")
                )
            }
            
            is GeminiResult.Failed -> {
                Result.Error(
                    Exception("Translation failed: ${result.error}")
                )
            }
        }
    }
    
    /**
     * Fix OCR errors using Gemini API.
     * 
     * Note: OCR correction is typically NOT cached because:
     * - Each document is unique (different errors)
     * - Results depend on context (surrounding text)
     * - Less frequent operation (not called repeatedly)
     * 
     * @param text Raw OCR text to fix
     * @param useCache Whether to cache (default: false for OCR)
     * @return Result with corrected text or error
     */
    suspend fun fixOcrText(
        text: String,
        useCache: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        
        // Validation
        if (text.isBlank()) {
            return@withContext Result.Error(
                Exception("Text cannot be empty")
            )
        }
        
        // Get API key
        val apiKey = encryptedKeyStorage.getActiveApiKey()
        if (apiKey == null) {
            return@withContext Result.Error(
                Exception("API key not found. Please add it in Settings.")
            )
        }
        
        // Optional: Check cache (rarely useful for OCR)
        if (useCache) {
            try {
                val cached = translationCacheManager.getCachedTranslation(
                    text = text,
                    sourceLang = "ocr_fix",
                    targetLang = "corrected",
                    maxAgeDays = 7 // Shorter TTL for OCR
                )
                
                if (cached != null) {
                    android.util.Log.d(
                        "GeminiTranslator",
                        "✅ OCR fix from cache (rare)"
                    )
                    return@withContext Result.Success(cached)
                }
            } catch (e: Exception) {
                // Ignore cache errors for OCR
            }
        }
        
        // Call API
        val result = geminiApi.fixOcrText(text, apiKey)
        
        // Handle result
        when (result) {
            is GeminiResult.Allowed -> {
                val corrected = result.text
                
                // Optional: Cache OCR correction
                if (useCache) {
                    try {
                        translationCacheManager.cacheTranslation(
                            originalText = text,
                            translatedText = corrected,
                            sourceLang = "ocr_fix",
                            targetLang = "corrected"
                        )
                    } catch (e: Exception) {
                        // Ignore cache errors
                    }
                }
                
                Result.Success(corrected)
            }
            
            is GeminiResult.Blocked -> {
                Result.Error(
                    Exception("OCR fix blocked by Gemini: ${result.reason}")
                )
            }
            
            is GeminiResult.Failed -> {
                Result.Error(
                    Exception("OCR fix failed: ${result.error}")
                )
            }
        }
    }
    
    /**
     * Translate batch of texts (useful for bulk operations).
     * Uses cache for each individual translation.
     * 
     * @param texts List of texts to translate
     * @param targetLanguage Target language
     * @param sourceLanguage Source language
     * @return Result with list of translations or error
     */
    suspend fun translateBatch(
        texts: List<String>,
        targetLanguage: String = "ru",
        sourceLanguage: String = "auto"
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        
        if (texts.isEmpty()) {
            return@withContext Result.Success(emptyList())
        }
        
        val results = mutableListOf<String>()
        
        for (text in texts) {
            when (val result = translate(text, targetLanguage, sourceLanguage)) {
                is Result.Success -> {
                    results.add(result.data)
                }
                is Result.Error -> {
                    // Return error on first failure
                    return@withContext Result.Error(
                        Exception("Batch translation failed at item ${results.size + 1}: ${result.exception.message}")
                    )
                }
                Result.Loading -> {
                    // Should not happen
                }
            }
        }
        
        Result.Success(results)
    }
    
    /**
     * Get statistics about cache usage.
     * Useful for Settings screen to show cache efficiency.
     */
    suspend fun getCacheStats(): CacheStatistics {
        return try {
            val stats = translationCacheManager.getCacheStats()
            CacheStatistics(
                totalEntries = stats.totalEntries,
                totalSizeBytes = stats.totalOriginalSize + stats.totalTranslatedSize,
                oldestEntryAge = System.currentTimeMillis() - stats.oldestEntry,
                newestEntryAge = System.currentTimeMillis() - stats.newestEntry
            )
        } catch (e: Exception) {
            CacheStatistics(0, 0, 0, 0)
        }
    }
    
    data class CacheStatistics(
        val totalEntries: Int,
        val totalSizeBytes: Long,
        val oldestEntryAge: Long,
        val newestEntryAge: Long
    )
}