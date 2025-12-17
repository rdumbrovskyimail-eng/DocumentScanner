package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entities.ApiKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY createdAt DESC")
    fun getAllKeys(): Flow<List<ApiKeyEntity>>
    
    @Query("SELECT * FROM api_keys WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveKey(): ApiKeyEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: ApiKeyEntity): Long
    
    @Query("UPDATE api_keys SET isActive = 0")
    suspend fun deactivateAll()
    
    @Transaction
    suspend fun activateKey(keyId: Long) {
        deactivateAll()
        activateKeyById(keyId)
    }
    
    @Query("UPDATE api_keys SET isActive = 1 WHERE id = :keyId")
    suspend fun activateKeyById(keyId: Long)
    
    @Delete
    suspend fun deleteKey(key: ApiKeyEntity)
    
    @Query("DELETE FROM api_keys WHERE id = :keyId")
    suspend fun deleteKeyById(keyId: Long)
}
