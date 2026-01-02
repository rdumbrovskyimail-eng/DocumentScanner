package com.docs.scanner.data.remote.gemini

import android.util.Log
import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranslator @Inject constructor(
    private val geminiApi: GeminiApi,
    private val translationCacheManager: TranslationCacheManager,
    private val encryptedKeyStorage: EncryptedKeyStorage
) {
    companion object {
        private const val TAG = "GeminiTranslator"
        private const val CACHE_TTL_DAYS = 30
    }

    suspend fun translate(
        text: String,
        targetLanguage: String = "ru",
        sourceLanguage: String = "auto",
        useCache: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.Error(Exception("Text cannot be empty"))
        
        val apiKey = encryptedKeyStorage.getActiveApiKey()
            ?: return@withContext Result.Error(Exception("API key not found. Please add it in Settings."))
        
        // STEP 1: Check cache BEFORE API call
        if (useCache) {
            try {
                val cached = translationCacheManager.getCachedTranslation(
                    text = text,
                    sourceLang = sourceLanguage,
                    targetLang = targetLanguage,
                    maxAgeDays = CACHE_TTL_DAYS
                )
                if (cached != null) {
                    Log.d(TAG, "✅ Cache HIT: Translation from cache (saved API call)")
                    geminiApi.recordCacheHit()
                    return@withContext Result.Success(cached)
                }
                Log.d(TAG, "⚠️ Cache MISS: Calling Gemini API")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Cache read error: ${e.message}, falling back to API")
            }
        }
        
        // STEP 2: Call Gemini API
        val targetFull = getLanguageFullName(targetLanguage)
        val sourceFull = if (sourceLanguage != "auto") getLanguageFullName(sourceLanguage) else null
        
        when (val result = geminiApi.translateText(text, apiKey, targetFull, sourceFull)) {
            is GeminiResult.Allowed -> {
                // STEP 3: Save to cache on success
                if (useCache) {
                    try {
                        translationCacheManager.cacheTranslation(
                            originalText = text,
                            translatedText = result.text,
                            sourceLang = sourceLanguage,
                            targetLang = targetLanguage
                        )
                        Log.d(TAG, "✅ Translation cached for future use")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Failed to cache translation: ${e.message}")
                    }
                }
                Result.Success(result.text)
            }
            is GeminiResult.Blocked -> Result.Error(Exception("Translation blocked by Gemini: ${result.reason}"))
            is GeminiResult.Failed -> Result.Error(Exception("Translation failed: ${result.error}"))
        }
    }

    suspend fun fixOcrText(text: String, useCache: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.Error(Exception("Text cannot be empty"))
        
        val apiKey = encryptedKeyStorage.getActiveApiKey()
            ?: return@withContext Result.Error(Exception("API key not found. Please add it in Settings."))
        
        // Optional: Check cache (rarely useful for OCR)
        if (useCache) {
            try {
                val cached = translationCacheManager.getCachedTranslation(
                    text = text,
                    sourceLang = "ocr_fix",
                    targetLang = "corrected",
                    maxAgeDays = 7
                )
                if (cached != null) {
                    Log.d(TAG, "✅ OCR fix from cache (rare)")
                    return@withContext Result.Success(cached)
                }
            } catch (e: Exception) {
                // Ignore cache errors for OCR
            }
        }
        
        when (val result = geminiApi.fixOcrText(text, apiKey)) {
            is GeminiResult.Allowed -> {
                if (useCache) {
                    try {
                        translationCacheManager.cacheTranslation(
                            originalText = text,
                            translatedText = result.text,
                            sourceLang = "ocr_fix",
                            targetLang = "corrected"
                        )
                    } catch (e: Exception) {
                        // Ignore cache errors
                    }
                }
                Result.Success(result.text)
            }
            is GeminiResult.Blocked -> Result.Error(Exception("OCR fix blocked by Gemini: ${result.reason}"))
            is GeminiResult.Failed -> Result.Error(Exception("OCR fix failed: ${result.error}"))
        }
    }

    /**
     * Translate batch of texts (useful for bulk operations).
     * Uses cache for each individual translation.
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
                is Result.Success -> results.add(result.data)
                is Result.Error -> {
                    return@withContext Result.Error(
                        Exception("Batch translation failed at item ${results.size + 1}: ${result.exception.message}")
                    )
                }
                Result.Loading -> { /* Should not happen */ }
            }
        }
        
        Result.Success(results)
    }

    suspend fun getCacheStats(): CacheStatistics {
        return try {
            val stats = translationCacheManager.getCacheStats()
            CacheStatistics(
                totalEntries = stats.totalEntries,
                totalSizeBytes = stats.totalOriginalSize + stats.totalTranslatedSize,
                oldestEntryAge = if (stats.oldestEntry > 0) System.currentTimeMillis() - stats.oldestEntry else 0,
                newestEntryAge = if (stats.newestEntry > 0) System.currentTimeMillis() - stats.newestEntry else 0
            )
        } catch (e: Exception) {
            CacheStatistics(0, 0, 0, 0)
        }
    }

    private fun getLanguageFullName(code: String): String = when (code.lowercase()) {
        "ru" -> "Russian"
        "en" -> "English"
        "zh", "cn" -> "Chinese"
        "ja", "jp" -> "Japanese"
        "ko", "kr" -> "Korean"
        "de" -> "German"
        "fr" -> "French"
        "es" -> "Spanish"
        "uk" -> "Ukrainian"
        else -> code
    }

    data class CacheStatistics(
        val totalEntries: Int,
        val totalSizeBytes: Long,
        val oldestEntryAge: Long,
        val newestEntryAge: Long
    )
}