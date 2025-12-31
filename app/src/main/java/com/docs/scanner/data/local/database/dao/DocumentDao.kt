package com.docs.scanner.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.docs.scanner.data.local.database.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Document entity.
 * 
 * Provides all database operations for documents including:
 * - CRUD operations
 * - FTS search (via JOIN with documents_fts)
 * - Batch operations
 * - Status filtering
 */
@Dao
interface DocumentDao {

    // ══════════════════════════════════════════════════════════════
    // BASIC CRUD
    // ══════════════════════════════════════════════════════════════
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<DocumentEntity>): List<Long>

    @Update
    suspend fun update(document: DocumentEntity)

    @Update
    suspend fun updateAll(documents: List<DocumentEntity>)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: Long)

    @Query("DELETE FROM documents WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: Long)

    // ══════════════════════════════════════════════════════════════
    // SELECT - Single
    // ══════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getById(documentId: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun getByIdFlow(documentId: Long): Flow<DocumentEntity?>

    // ══════════════════════════════════════════════════════════════
    // SELECT - Lists
    // ══════════════════════════════════════════════════════════════
    
    @Query("SELECT * FROM documents WHERE recordId = :recordId ORDER BY position ASC")
    fun getByRecordId(recordId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE recordId = :recordId ORDER BY position ASC")
    suspend fun getByRecordIdSync(recordId: Long): List<DocumentEntity>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE processingStatus = :status ORDER BY createdAt DESC")
    fun getByStatus(status: Int): Flow<List<DocumentEntity>>

    // ══════════════════════════════════════════════════════════════
    // FTS SEARCH
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Full-text search using FTS4.
     * Searches in originalText and translatedText.
     */
    @Query("""
        SELECT d.* FROM documents d
        INNER JOIN documents_fts fts ON d.id = fts.rowid
        WHERE documents_fts MATCH :query
        ORDER BY d.createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchFts(query: String, limit: Int = 50, offset: Int = 0): Flow<List<DocumentEntity>>

    /**
     * LIKE search fallback (when FTS unavailable or for simple queries).
     */
    @Query("""
        SELECT * FROM documents
        WHERE (originalText IS NOT NULL AND LOWER(originalText) LIKE LOWER('%' || :query || '%'))
           OR (translatedText IS NOT NULL AND LOWER(translatedText) LIKE LOWER('%' || :query || '%'))
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchLike(query: String, limit: Int = 50, offset: Int = 0): Flow<List<DocumentEntity>>

    /**
     * Search with folder and record names for display.
     */
    @Query("""
        SELECT 
            d.id,
            d.recordId,
            d.imagePath,
            d.originalText,
            d.translatedText,
            d.position,
            d.processingStatus,
            d.createdAt,
            r.name as recordName,
            f.name as folderName
        FROM documents d
        INNER JOIN records r ON d.recordId = r.id
        INNER JOIN folders f ON r.folderId = f.id
        WHERE (d.originalText IS NOT NULL AND LOWER(d.originalText) LIKE LOWER('%' || :query || '%'))
           OR (d.translatedText IS NOT NULL AND LOWER(d.translatedText) LIKE LOWER('%' || :query || '%'))
        ORDER BY d.createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchWithNames(query: String, limit: Int = 50, offset: Int = 0): Flow<List<DocumentWithNamesDto>>

    // ══════════════════════════════════════════════════════════════
    // UPDATE SPECIFIC FIELDS
    // ══════════════════════════════════════════════════════════════
    
    @Query("UPDATE documents SET originalText = :text, processingStatus = :status WHERE id = :documentId")
    suspend fun updateOriginalText(documentId: Long, text: String, status: Int)

    @Query("UPDATE documents SET translatedText = :text, processingStatus = :status WHERE id = :documentId")
    suspend fun updateTranslatedText(documentId: Long, text: String, status: Int)

    @Query("UPDATE documents SET processingStatus = :status WHERE id = :documentId")
    suspend fun updateStatus(documentId: Long, status: Int)

    @Query("UPDATE documents SET position = :position WHERE id = :documentId")
    suspend fun updatePosition(documentId: Long, position: Int)

    // ══════════════════════════════════════════════════════════════
    // COUNTS & STATS
    // ══════════════════════════════════════════════════════════════
    
    @Query("SELECT COUNT(*) FROM documents WHERE recordId = :recordId")
    suspend fun getCountByRecordId(recordId: Long): Int

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE processingStatus = :status")
    suspend fun getCountByStatus(status: Int): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM documents WHERE recordId = :recordId")
    suspend fun getNextPosition(recordId: Long): Int

    @Query("SELECT imagePath FROM documents")
    suspend fun getAllImagePaths(): List<String>

    // ══════════════════════════════════════════════════════════════
    // BATCH OPERATIONS
    // ══════════════════════════════════════════════════════════════
    
    @Transaction
    suspend fun reorderDocuments(documentIds: List<Long>) {
        documentIds.forEachIndexed { index, id ->
            updatePosition(id, index)
        }
    }

    @Query("DELETE FROM documents WHERE id IN (:documentIds)")
    suspend fun deleteByIds(documentIds: List<Long>)
}

/**
 * DTO for search results with folder/record names.
 */
data class DocumentWithNamesDto(
    val id: Long,
    val recordId: Long,
    val imagePath: String,
    val originalText: String?,
    val translatedText: String?,
    val position: Int,
    val processingStatus: Int,
    val createdAt: Long,
    val recordName: String,
    val folderName: String
)
