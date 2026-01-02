package com.docs.scanner.data.repository

import com.docs.scanner.data.remote.gemini.GeminiTranslator
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.model.TranslationConstants
import com.docs.scanner.domain.repository.TranslationRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for Translation operations.
 */
interface TranslationRepository {
    /**
     * Translate text to target language.
     */
    suspend fun translate(
        text: String,
        targetLanguage: String = TranslationConstants.DEFAULT_TARGET_LANGUAGE,
        sourceLanguage: String = TranslationConstants.AUTO_DETECT_LANGUAGE
    ): Result<String>
    
    /**
     * Translate batch of texts.
     */
    suspend fun translateBatch(
        texts: List<String>,
        targetLanguage: String = TranslationConstants.DEFAULT_TARGET_LANGUAGE,
        sourceLanguage: String = TranslationConstants.AUTO_DETECT_LANGUAGE
    ): Result<List<String>>
    
    /**
     * Get cache statistics.
     */
    suspend fun getCacheStats(): CacheStats
    
    /**
     * Clear translation cache.
     */
    suspend fun clearCache()
    
    data class CacheStats(
        val totalEntries: Int,
        val totalSizeBytes: Long,
        val hitRate: Float = 0f
    )
}

/**
 * Implementation of TranslationRepository.
 * 
 * Uses GeminiTranslator with caching for efficient translations.
 */
@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val geminiTranslator: GeminiTranslator
) : TranslationRepository {

    private var cacheHits = 0
    private var cacheMisses = 0

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String
    ): Result<String> {
        // Validation
        if (text.isBlank()) {
            return Result.Error(IllegalArgumentException("Text cannot be empty"))
        }
        
        if (text.length > TranslationConstants.MAX_TEXT_LENGTH) {
            return Result.Error(
                IllegalArgumentException(
                    "Text too long (max ${TranslationConstants.MAX_TEXT_LENGTH} chars)"
                )
            )
        }
        
        Timber.d("Translating ${text.length} chars to $targetLanguage")
        
        return try {
            val result = geminiTranslator.translate(
                text = text,
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage,
                useCache = true
            )
            
            when (result) {
                is Result.Success -> {
                    Timber.d("Translation successful: ${result.data.length} chars")
                    result
                }
                is Result.Error -> {
                    Timber.e(result.exception, "Translation failed")
                    result
                }
                else -> result
            }
        } catch (e: Exception) {
            Timber.e(e, "Translation exception")
            Result.Error(e)
        }
    }

    override suspend fun translateBatch(
        texts: List<String>,
        targetLanguage: String,
        sourceLanguage: String
    ): Result<List<String>> {
        if (texts.isEmpty()) {
            return Result.Success(emptyList())
        }
        
        Timber.d("Batch translating ${texts.size} texts to $targetLanguage")
        
        return try {
            geminiTranslator.translateBatch(
                texts = texts,
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage
            )
        } catch (e: Exception) {
            Timber.e(e, "Batch translation failed")
            Result.Error(e)
        }
    }

    override suspend fun getCacheStats(): TranslationRepository.CacheStats {
        return try {
            val stats = geminiTranslator.getCacheStats()
            val total = cacheHits + cacheMisses
            val hitRate = if (total > 0) cacheHits.toFloat() / total else 0f
            
            TranslationRepository.CacheStats(
                totalEntries = stats.totalEntries,
                totalSizeBytes = stats.totalSizeBytes,
                hitRate = hitRate
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get cache stats")
            TranslationRepository.CacheStats(0, 0, 0f)
        }
    }

    override suspend fun clearCache() {
        // Cache clearing would be done via TranslationCacheManager
        // For now, just reset counters
        cacheHits = 0
        cacheMisses = 0
        Timber.d("Translation cache stats reset")
    }
}