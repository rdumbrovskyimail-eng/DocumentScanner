package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entities.RecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getRecordsByFolder(folderId: Long): Flow<List<RecordEntity>>
    
    @Query("SELECT * FROM records WHERE id = :recordId")
    suspend fun getRecordById(recordId: Long): RecordEntity?
    
    @Query("SELECT * FROM records WHERE id = :recordId")
    fun getRecordByIdFlow(recordId: Long): Flow<RecordEntity?>
    
    @Query("""
        SELECT * FROM records 
        WHERE folderId = :folderId 
        AND name LIKE '%' || :query || '%' 
        ORDER BY createdAt DESC
    """)
    fun searchRecordsInFolder(folderId: Long, query: String): Flow<List<RecordEntity>>
    
    @Query("SELECT * FROM records WHERE name LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchAllRecords(query: String): Flow<List<RecordEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordEntity): Long
    
    @Update
    suspend fun updateRecord(record: RecordEntity)
    
    @Delete
    suspend fun deleteRecord(record: RecordEntity)
    
    @Query("DELETE FROM records WHERE id = :recordId")
    suspend fun deleteRecordById(recordId: Long)
    
    @Query("UPDATE records SET folderId = :newFolderId, updatedAt = :timestamp WHERE id = :recordId")
    suspend fun moveRecordToFolder(recordId: Long, newFolderId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM records WHERE folderId = :folderId")
    suspend fun getRecordCountInFolder(folderId: Long): Int
    
    @Query("SELECT COUNT(*) FROM records")
    suspend fun getTotalRecordCount(): Int
    
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM records 
            WHERE folderId = :folderId 
            AND name = :name 
            AND id != :excludeId
        )
    """)
    suspend fun isRecordNameExistsInFolder(folderId: Long, name: String, excludeId: Long = 0): Boolean
}