package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entities.TranslationCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationCacheDao {
    
    @Query("SELECT * FROM translation_cache WHERE textHash = :hash LIMIT 1")
    suspend fun getCachedTranslation(hash: String): TranslationCacheEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: TranslationCacheEntity)
    
    @Query("DELETE FROM translation_cache WHERE timestamp < :expiryTimestamp")
    suspend fun deleteExpiredCache(expiryTimestamp: Long)
    
    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCacheCount(): Int
    
    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()
    
    // Get all cache entries (for debugging)
    @Query("SELECT * FROM translation_cache ORDER BY timestamp DESC")
    fun getAllCache(): Flow<List<TranslationCacheEntity>>
}
