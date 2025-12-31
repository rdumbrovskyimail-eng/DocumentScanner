package com.docs.scanner.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docs.scanner.data.local.database.entities.RecordEntity
import com.docs.scanner.domain.model.Record
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Record entity.
 * 
 * Provides all database operations for records including:
 * - CRUD operations
 * - Record with document count
 * - Search
 * - Move between folders
 */
@Dao
interface RecordDao {

    // ══════════════════════════════════════════════════════════════
    // BASIC CRUD
    // ══════════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RecordEntity>): List<Long>

    @Update
    suspend fun update(record: RecordEntity)

    @Delete
    suspend fun delete(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :recordId")
    suspend fun deleteById(recordId: Long)

    @Query("DELETE FROM records WHERE folderId = :folderId")
    suspend fun deleteByFolderId(folderId: Long)

    // ══════════════════════════════════════════════════════════════
    // SELECT - Single
    // ══════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM records WHERE id = :recordId")
    suspend fun getById(recordId: Long): RecordEntity?

    @Query("SELECT * FROM records WHERE id = :recordId")
    fun getByIdFlow(recordId: Long): Flow<RecordEntity?>

    // ══════════════════════════════════════════════════════════════
    // SELECT - Lists (with document count - OPTIMIZED!)
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Get records by folder with document count.
     * OPTIMIZED: Single query with LEFT JOIN.
     */
    @Query("""
        SELECT 
            r.id,
            r.folderId,
            r.name,
            r.description,
            r.createdAt,
            r.updatedAt,
            COUNT(d.id) as documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.recordId
        WHERE r.folderId = :folderId
        GROUP BY r.id
        ORDER BY r.updatedAt DESC
    """)
    fun getByFolderIdWithDocCount(folderId: Long): Flow<List<RecordWithCount>>

    /**
     * Get records by folder without count (simpler query).
     */
    @Query("SELECT * FROM records WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getByFolderId(folderId: Long): Flow<List<RecordEntity>>

    /**
     * Get single record with document count.
     */
    @Query("""
        SELECT 
            r.id,
            r.folderId,
            r.name,
            r.description,
            r.createdAt,
            r.updatedAt,
            COUNT(d.id) as documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.recordId
        WHERE r.id = :recordId
        GROUP BY r.id
    """)
    suspend fun getByIdWithDocCount(recordId: Long): RecordWithCount?

    // ══════════════════════════════════════════════════════════════
    // SEARCH
    // ══════════════════════════════════════════════════════════════
    
    @Query("""
        SELECT * FROM records 
        WHERE folderId = :folderId
        AND (LOWER(name) LIKE LOWER('%' || :query || '%')
             OR LOWER(description) LIKE LOWER('%' || :query || '%'))
        ORDER BY updatedAt DESC
    """)
    fun searchInFolder(folderId: Long, query: String): Flow<List<RecordEntity>>

    @Query("""
        SELECT * FROM records 
        WHERE LOWER(name) LIKE LOWER('%' || :query || '%')
           OR LOWER(description) LIKE LOWER('%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchAll(query: String): Flow<List<RecordEntity>>

    // ══════════════════════════════════════════════════════════════
    // MOVE & VALIDATION
    // ══════════════════════════════════════════════════════════════
    
    @Query("UPDATE records SET folderId = :newFolderId, updatedAt = :timestamp WHERE id = :recordId")
    suspend fun moveToFolder(recordId: Long, newFolderId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM records 
            WHERE folderId = :folderId 
            AND LOWER(name) = LOWER(:name) 
            AND id != :excludeId
        )
    """)
    suspend fun isNameExistsInFolder(folderId: Long, name: String, excludeId: Long = 0): Boolean

    // ══════════════════════════════════════════════════════════════
    // COUNTS
    // ══════════════════════════════════════════════════════════════
    
    @Query("SELECT COUNT(*) FROM records WHERE folderId = :folderId")
    suspend fun getCountByFolderId(folderId: Long): Int

    @Query("SELECT COUNT(*) FROM records")
    suspend fun getTotalCount(): Int
}

/**
 * Data class for record with document count.
 * Used by JOIN queries to avoid N+1 problem.
 */
data class RecordWithCount(
    val id: Long,
    val folderId: Long,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val documentCount: Int
) {
    /**
     * Convert to domain model.
     */
    fun toDomain(): Record = Record(
        id = id,
        folderId = folderId,
        name = name,
        description = description,
        documentCount = documentCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}