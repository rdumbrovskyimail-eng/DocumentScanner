package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entities.TranslationCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for translation cache with language-aware queries.
 * 
 * Session 3 fixes:
 * - ✅ Changed textHash → cacheKey in queries
 * - ✅ Added language-based queries
 * - ✅ Added detailed statistics query
 * - ✅ Added size-based cleanup (deleteOldestEntries)
 * - ✅ Added batch operations
 * 
 * Supports database version 5 (with sourceLanguage, targetLanguage)
 */
@Dao
interface TranslationCacheDao {
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // BASIC CRUD OPERATIONS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Get cached translation by cache key.
     * 
     * ✅ UPDATED: textHash → cacheKey
     */
    @Query("SELECT * FROM translation_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getCachedTranslation(cacheKey: String): TranslationCacheEntity?
    
    /**
     * Insert or replace cache entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: TranslationCacheEntity)
    
    /**
     * Insert multiple cache entries (batch operation).
     * 
     * ✅ NEW: For bulk caching
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheBatch(caches: List<TranslationCacheEntity>)
    
    /**
     * Delete cache entry by key.
     * 
     * ✅ NEW: For manual deletion
     */
    @Query("DELETE FROM translation_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteCacheByKey(cacheKey: String)
    
    /**
     * Delete all cache entries.
     */
    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // CLEANUP OPERATIONS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Delete expired cache entries (older than timestamp).
     * 
     * @param expiryTimestamp Delete entries older than this
     * @return Number of deleted entries
     */
    @Query("DELETE FROM translation_cache WHERE timestamp < :expiryTimestamp")
    suspend fun deleteExpiredCache(expiryTimestamp: Long): Int
    
    /**
     * Delete oldest N entries (for size-based cleanup).
     * 
     * ✅ NEW: Important for auto-cleanup when cache is full
     * 
     * @param count Number of oldest entries to delete
     */
    @Query("""
        DELETE FROM translation_cache 
        WHERE cacheKey IN (
            SELECT cacheKey FROM translation_cache 
            ORDER BY timestamp ASC 
            LIMIT :count
        )
    """)
    suspend fun deleteOldestEntries(count: Int)
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // QUERY OPERATIONS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Get cache count.
     */
    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCacheCount(): Int
    
    /**
     * Get all cache entries (for debugging).
     */
    @Query("SELECT * FROM translation_cache ORDER BY timestamp DESC")
    fun getAllCache(): Flow<List<TranslationCacheEntity>>
    
    /**
     * Get cache entries by language pair.
     * 
     * ✅ NEW: Filter by source→target languages
     * 
     * Example: Get all English→Russian translations
     */
    @Query("""
        SELECT * FROM translation_cache 
        WHERE sourceLanguage = :sourceLang 
        AND targetLanguage = :targetLang
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getCacheByLanguagePair(
        sourceLang: String,
        targetLang: String,
        limit: Int = 100
    ): List<TranslationCacheEntity>
    
    /**
     * Get cache entries by target language.
     * 
     * ✅ NEW: Useful for "all translations to Russian" queries
     */
    @Query("""
        SELECT * FROM translation_cache 
        WHERE targetLanguage = :targetLang
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getCacheByTargetLanguage(
        targetLang: String,
        limit: Int = 100
    ): List<TranslationCacheEntity>
    
    /**
     * Search cache entries by text content.
     * 
     * ✅ NEW: For cache browser UI
     */
    @Query("""
        SELECT * FROM translation_cache 
        WHERE originalText LIKE '%' || :query || '%'
        OR translatedText LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchCache(
        query: String,
        limit: Int = 50
    ): List<TranslationCacheEntity>
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // STATISTICS OPERATIONS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Get detailed cache statistics.
     * 
     * ✅ NEW: Much more detailed stats (Session 3)
     * 
     * Returns:
     * - Total entries count
     * - Total text size (original + translated)
     * - Oldest/newest entry timestamps
     */
    @Query("""
        SELECT 
            COUNT(*) as totalEntries,
            SUM(LENGTH(originalText)) as totalOriginalSize,
            SUM(LENGTH(translatedText)) as totalTranslatedSize,
            MIN(timestamp) as oldestEntry,
            MAX(timestamp) as newestEntry
        FROM translation_cache
    """)
    suspend fun getCacheStats(): CacheStatsResult
    
    /**
     * Get statistics by language pair.
     * 
     * ✅ NEW: For "cache usage per language" analytics
     */
    @Query("""
        SELECT 
            sourceLanguage,
            targetLanguage,
            COUNT(*) as count
        FROM translation_cache
        GROUP BY sourceLanguage, targetLanguage
        ORDER BY count DESC
    """)
    suspend fun getLanguagePairStats(): List<LanguagePairStat>
    
    /**
     * Get recent cache entries (last 24 hours).
     * 
     * ✅ NEW: For "today's activity" widget
     */
    @Query("""
        SELECT * FROM translation_cache 
        WHERE timestamp > :since
        ORDER BY timestamp DESC
    """)
    fun getRecentCache(
        since: Long = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
    ): Flow<List<TranslationCacheEntity>>
}

/**
 * Result of getCacheStats() query.
 * 
 * ✅ NEW: Detailed statistics (Session 3)
 */
data class CacheStatsResult(
    val totalEntries: Int,
    val totalOriginalSize: Long,
    val totalTranslatedSize: Long,
    val oldestEntry: Long,
    val newestEntry: Long
)

/**
 * Result of getLanguagePairStats() query.
 * 
 * ✅ NEW: Language pair usage statistics
 */
data class LanguagePairStat(
    val sourceLanguage: String,
    val targetLanguage: String,
    val count: Int
)