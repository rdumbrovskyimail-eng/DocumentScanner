package com.docs.scanner.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docs.scanner.data.local.database.entities.FolderEntity
import com.docs.scanner.domain.model.Folder
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Folder entity.
 * 
 * Provides all database operations for folders including:
 * - CRUD operations
 * - Folder with record count (optimized JOIN query)
 * - Search
 * - Batch operations
 */
@Dao
interface FolderDao {

    // ══════════════════════════════════════════════════════════════
    // BASIC CRUD
    // ══════════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<FolderEntity>): List<Long>

    @Update
    suspend fun update(folder: FolderEntity)

    @Update
    suspend fun updateAll(folders: List<FolderEntity>)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteById(folderId: Long)

    @Query("DELETE FROM folders WHERE id IN (:folderIds)")
    suspend fun deleteByIds(folderIds: List<Long>)

    // ══════════════════════════════════════════════════════════════
    // SELECT - Single
    // ══════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getById(folderId: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun getByIdFlow(folderId: Long): Flow<FolderEntity?>

    // ══════════════════════════════════════════════════════════════
    // SELECT - Lists (with record count - OPTIMIZED!)
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Get all folders with record count in single query.
     * 
     * OPTIMIZED: Uses LEFT JOIN instead of N+1 queries.
     * Before: SELECT folders + N × (SELECT COUNT(*) FROM records)
     * After: Single query with JOIN and GROUP BY
     */
    @Query("""
        SELECT 
            f.id,
            f.name,
            f.description,
            f.createdAt,
            f.updatedAt,
            COUNT(r.id) as recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folderId
        GROUP BY f.id
        ORDER BY f.updatedAt DESC
    """)
    fun getAllWithRecordCount(): Flow<List<FolderWithCount>>

    /**
     * Get single folder with record count.
     */
    @Query("""
        SELECT 
            f.id,
            f.name,
            f.description,
            f.createdAt,
            f.updatedAt,
            COUNT(r.id) as recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folderId
        WHERE f.id = :folderId
        GROUP BY f.id
    """)
    suspend fun getByIdWithRecordCount(folderId: Long): FolderWithCount?

    /**
     * Get all folders without record count (for simple lists).
     */
    @Query("SELECT * FROM folders ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<FolderEntity>>

    // ══════════════════════════════════════════════════════════════
    // SEARCH
    // ══════════════════════════════════════════════════════════════
    
    @Query("""
        SELECT * FROM folders 
        WHERE LOWER(name) LIKE LOWER('%' || :query || '%')
           OR LOWER(description) LIKE LOWER('%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun search(query: String): Flow<List<FolderEntity>>

    // ══════════════════════════════════════════════════════════════
    // VALIDATION
    // ══════════════════════════════════════════════════════════════
    
    @Query("SELECT EXISTS(SELECT 1 FROM folders WHERE LOWER(name) = LOWER(:name) AND id != :excludeId)")
    suspend fun isNameExists(name: String, excludeId: Long = 0): Boolean

    // ══════════════════════════════════════════════════════════════
    // COUNTS
    // ══════════════════════════════════════════════════════════════
    
    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getTotalCount(): Int
}

/**
 * Data class for folder with record count.
 * Used by JOIN queries to avoid N+1 problem.
 */
data class FolderWithCount(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val recordCount: Int
) {
    /**
     * Convert to domain model.
     */
    fun toDomain(): Folder = Folder(
        id = id,
        name = name,
        description = description,
        recordCount = recordCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}