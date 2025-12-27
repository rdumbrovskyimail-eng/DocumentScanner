package com.docs.scanner.domain.repository

import com.docs.scanner.domain.model.Result

/**
 * Domain repository interface for translation operations.
 * 
 * Session 5 addition:
 * - Separates translation logic from ScannerRepository (SRP)
 * - Handles translation caching
 * - Supports batch translations
 * - Provides cache statistics
 * 
 * Responsibilities:
 * - Text translation via Gemini API
 * - Translation cache management
 * - Batch translation operations
 * - Cache analytics
 */
interface TranslationRepository {
    
    /**
     * Translate text with intelligent caching.
     * 
     * Flow:
     * 1. Check cache
     * 2. If miss: call Gemini API
     * 3. Save to cache
     * 4. Return result
     * 
     * @param text Text to translate
     * @param targetLanguage Target language code (e.g., "ru", "en", "zh")
     * @param sourceLanguage Source language code or "auto" for detection
     * @param useCache Whether to use cache (default: true)
     * @return Result with translated text
     */
    suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String = "auto",
        useCache: Boolean = true
    ): Result<String>
    
    /**
     * Translate multiple texts.
     * Uses cache for each individual translation.
     * 
     * @param texts List of texts to translate
     * @param targetLanguage Target language
     * @param sourceLanguage Source language
     * @return Result with list of translations
     */
    suspend fun translateBatch(
        texts: List<String>,
        targetLanguage: String,
        sourceLanguage: String = "auto"
    ): Result<List<String>>
    
    /**
     * Clear all translation cache.
     */
    suspend fun clearCache()
    
    /**
     * Get translation cache statistics.
     * 
     * @return Cache statistics (entries, size, hit rate)
     */
    suspend fun getCacheStats(): CacheStats
    
    /**
     * Translation cache statistics.
     */
    data class CacheStats(
        val totalEntries: Int,
        val cacheHitRate: Float,
        val totalSizeBytes: Long
    )
}