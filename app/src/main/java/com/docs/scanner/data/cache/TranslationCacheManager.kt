package com.docs.scanner.data.cache

import com.docs.scanner.data.local.database.dao.TranslationCacheDao
import com.docs.scanner.data.local.database.entities.TranslationCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ✅ НОВАЯ ФУНКЦИЯ: Управление кэшем переводов
 * 
 * Преимущества:
 * - 100x быстрее повторных переводов
 * - Экономия API quota
 * - Автоматическая очистка устаревших данных
 * 
 * Использование:
 * ```
 * val cached = cacheManager.getCachedTranslation(text)
 * if (cached != null) {
 *     return Result.Success(cached)
 * }
 * 
 * val translation = geminiApi.translate(text)
 * cacheManager.cacheTranslation(text, translation)
 * ```
 */
@Singleton
class TranslationCacheManager @Inject constructor(
    private val cacheDao: TranslationCacheDao
) {
    
    /**
     * Получить закэшированный перевод
     * 
     * @param text Исходный текст
     * @param maxAgeMinutes Максимальный возраст кэша в минутах (по умолчанию 30 дней)
     * @return Переведённый текст или null если кэш устарел/отсутствует
     */
    suspend fun getCachedTranslation(
        text: String,
        maxAgeMinutes: Int = DEFAULT_TTL_DAYS * 24 * 60
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        
        val hash = TranslationCacheEntity.generateHash(text)
        val cached = cacheDao.getCachedTranslation(hash) ?: return@withContext null
        
        // Проверка на устаревание
        val isExpired = TranslationCacheEntity.isExpired(
            cached.timestamp,
            maxAgeMinutes / (24 * 60)
        )
        
        if (isExpired) {
            // Удаляем устаревший кэш
            cacheDao.deleteExpiredCache(cached.timestamp)
            return@withContext null
        }
        
        println("✅ Cache HIT: ${text.take(50)}...")
        cached.translatedText
    }
    
    /**
     * Сохранить перевод в кэш
     * 
     * @param originalText Исходный текст
     * @param translatedText Переведённый текст
     */
    suspend fun cacheTranslation(
        originalText: String,
        translatedText: String
    ) = withContext(Dispatchers.IO) {
        if (originalText.isBlank() || translatedText.isBlank()) return@withContext
        
        try {
            val hash = TranslationCacheEntity.generateHash(originalText)
            val entity = TranslationCacheEntity(
                textHash = hash,
                originalText = originalText,
                translatedText = translatedText,
                timestamp = System.currentTimeMillis()
            )
            
            cacheDao.insertCache(entity)
            println("✅ Cache MISS: Cached translation for ${originalText.take(50)}...")
        } catch (e: Exception) {
            println("⚠️ Failed to cache translation: ${e.message}")
        }
    }
    
    /**
     * Очистить устаревший кэш
     * 
     * @param ttlDays Время жизни кэша в днях (по умолчанию 30 дней)
     */
    suspend fun cleanupExpiredCache(ttlDays: Int = DEFAULT_TTL_DAYS) = withContext(Dispatchers.IO) {
        try {
            val expiryTimestamp = System.currentTimeMillis() - (ttlDays * 24 * 60 * 60 * 1000L)
            cacheDao.deleteExpiredCache(expiryTimestamp)
            
            val remainingCount = cacheDao.getCacheCount()
            println("🧹 Cleanup complete. Remaining cache entries: $remainingCount")
        } catch (e: Exception) {
            println("⚠️ Failed to cleanup cache: ${e.message}")
        }
    }
    
    /**
     * Полная очистка кэша
     */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        try {
            cacheDao.clearAll()
            println("🧹 All cache cleared")
        } catch (e: Exception) {
            println("⚠️ Failed to clear cache: ${e.message}")
        }
    }
    
    /**
     * Получить статистику кэша
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        try {
            val count = cacheDao.getCacheCount()
            CacheStats(
                totalEntries = count,
                isHealthy = count < MAX_CACHE_ENTRIES
            )
        } catch (e: Exception) {
            CacheStats(0, false)
        }
    }
    
    /**
     * Проверить, не переполнен ли кэш
     * Если переполнен, автоматически очистить старые записи
     */
    suspend fun checkAndCleanIfNeeded() = withContext(Dispatchers.IO) {
        val stats = getCacheStats()
        
        if (!stats.isHealthy) {
            println("⚠️ Cache is full (${stats.totalEntries}). Cleaning up...")
            cleanupExpiredCache(ttlDays = 7)  // Агрессивная очистка (7 дней вместо 30)
        }
    }
    
    companion object {
        private const val DEFAULT_TTL_DAYS = 30
        private const val MAX_CACHE_ENTRIES = 10_000
    }
}

/**
 * Статистика кэша
 */
data class CacheStats(
    val totalEntries: Int,
    val isHealthy: Boolean
)
