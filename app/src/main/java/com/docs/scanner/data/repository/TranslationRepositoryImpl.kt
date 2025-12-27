package com.docs.scanner.data.repository

import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.data.remote.gemini.GeminiTranslator
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.TranslationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TranslationRepository using Gemini API.
 * 
 * Session 5 addition:
 * - Separates translation from ScannerRepository
 * - Integrates cache management
 * - Supports batch operations
 */
@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val geminiTranslator: GeminiTranslator,
    private val translationCacheManager: TranslationCacheManager,
    private val encryptedKeyStorage: EncryptedKeyStorage
) : TranslationRepository {
    
    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
        useCache: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        
        if (text.isBlank()) {
            return@withContext Result.Error(
                Exception("Text cannot be empty")
            )
        }
        
        // Delegate to GeminiTranslator (already has cache integration)
        geminiTranslator.translate(
            text = text,
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage,
            useCache = useCache
        )
    }
    
    override suspend fun translateBatch(
        texts: List<String>,
        targetLanguage: String,
        sourceLanguage: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        
        if (texts.isEmpty()) {
            return@withContext Result.Success(emptyList())
        }
        
        // Delegate to GeminiTranslator batch operation
        geminiTranslator.translateBatch(
            texts = texts,
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage
        )
    }
    
    override suspend fun clearCache() {
        translationCacheManager.clearAllCache()
    }
    
    override suspend fun getCacheStats(): TranslationRepository.CacheStats {
        val stats = translationCacheManager.getCacheStats()
        
        return TranslationRepository.CacheStats(
            totalEntries = stats.totalEntries,
            cacheHitRate = 0.0f,  // TODO: Track hits/misses
            totalSizeBytes = stats.totalSizeBytes
        )
    }
}