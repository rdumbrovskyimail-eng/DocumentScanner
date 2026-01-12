/*
 * DocumentScanner - Data Access Objects
 * Version: 6.5.0 - PRODUCTION READY 2026 (WITH POSITION IN SELECT)
 *
 * ✅ Merged all DAOs into single file
 * ✅ Added getAllModifiedSince() methods for incremental backup
 * ✅ Optimized SQL queries with JOINs
 * ✅ Full compatibility with Domain v4.1.0
 * ✅ Added ORDER BY position queries for manual sorting
 * ✅ FIXED: Added position field to all FolderWithCount/RecordWithCount SELECT queries
 */

package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entity.*
import kotlinx.coroutines.flow.Flow

// ══════════════════════════════════════════════════════════════════════════════
// FOLDER DAO
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE folders SET is_pinned = :pinned, updated_at = :timestamp WHERE id = :folderId")
    suspend fun setPinned(folderId: Long, pinned: Boolean, timestamp: Long)

    @Query("UPDATE folders SET is_archived = 1, updated_at = :timestamp WHERE id = :folderId")
    suspend fun archive(folderId: Long, timestamp: Long)

    @Query("UPDATE folders SET is_archived = 0, updated_at = :timestamp WHERE id = :folderId")
    suspend fun unarchive(folderId: Long, timestamp: Long)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteById(folderId: Long)

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getById(folderId: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun observeById(folderId: Long): Flow<FolderEntity?>

    // Получение папки с количеством записей внутри
    @Query("""
        SELECT f.id, f.name, f.description, f.color, f.icon, f.position,
               f.is_pinned AS isPinned, f.is_archived AS isArchived, 
               f.created_at AS createdAt, f.updated_at AS updatedAt,
               COUNT(r.id) AS recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folder_id AND r.is_archived = 0
        WHERE f.id = :folderId
        GROUP BY f.id
    """)
    suspend fun getByIdWithCount(folderId: Long): FolderWithCount?

    // ═══════════════════════════════════════════════════════════════════════════
    // СОРТИРОВКА ПО ДАТЕ (updated_at DESC)
    // ═══════════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT f.id, f.name, f.description, f.color, f.icon, f.position,
               f.is_pinned AS isPinned, f.is_archived AS isArchived, 
               f.created_at AS createdAt, f.updated_at AS updatedAt,
               COUNT(r.id) AS recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folder_id AND r.is_archived = 0
        WHERE f.is_archived = 0
        GROUP BY f.id
        ORDER BY f.is_pinned DESC, f.updated_at DESC
    """)
    fun observeAllWithCount(): Flow<List<FolderWithCount>>

    @Query("""
        SELECT f.id, f.name, f.description, f.color, f.icon, f.position,
               f.is_pinned AS isPinned, f.is_archived AS isArchived, 
               f.created_at AS createdAt, f.updated_at AS updatedAt,
               COUNT(r.id) AS recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folder_id
        GROUP BY f.id
        ORDER BY f.is_pinned DESC, f.updated_at DESC
    """)
    fun observeAllIncludingArchivedWithCount(): Flow<List<FolderWithCount>>

    // ═══════════════════════════════════════════════════════════════════════════
    // СОРТИРОВКА ПО ИМЕНИ (name ASC)
    // ═══════════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT f.id, f.name, f.description, f.color, f.icon, f.position,
               f.is_pinned AS isPinned, f.is_archived AS isArchived, 
               f.created_at AS createdAt, f.updated_at AS updatedAt,
               COUNT(r.id) AS recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folder_id AND r.is_archived = 0
        WHERE f.is_archived = 0
        GROUP BY f.id
        ORDER BY f.is_pinned DESC, f.name COLLATE NOCASE ASC
    """)
    fun observeAllByName(): Flow<List<FolderWithCount>>

    @Query("""
        SELECT f.id, f.name, f.description, f.color, f.icon, f.position,
               f.is_pinned AS isPinned, f.is_archived AS isArchived, 
               f.created_at AS createdAt, f.updated_at AS updatedAt,
               COUNT(r.id) AS recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folder_id
        GROUP BY f.id
        ORDER BY f.is_pinned DESC, f.name COLLATE NOCASE ASC
    """)
    fun observeAllIncludingArchivedByName(): Flow<List<FolderWithCount>>

    // ═══════════════════════════════════════════════════════════════════════════
    // СОРТИРОВКА ПО ПОЗИЦИИ (position ASC) - для ручного режима
    // ═══════════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT f.id, f.name, f.description, f.color, f.icon, f.position,
               f.is_pinned AS isPinned, f.is_archived AS isArchived, 
               f.created_at AS createdAt, f.updated_at AS updatedAt,
               COUNT(r.id) AS recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folder_id AND r.is_archived = 0
        WHERE f.is_archived = 0
        GROUP BY f.id
        ORDER BY f.is_pinned DESC, f.position ASC
    """)
    fun observeAllByPosition(): Flow<List<FolderWithCount>>

    @Query("""
        SELECT f.id, f.name, f.description, f.color, f.icon, f.position,
               f.is_pinned AS isPinned, f.is_archived AS isArchived, 
               f.created_at AS createdAt, f.updated_at AS updatedAt,
               COUNT(r.id) AS recordCount
        FROM folders f
        LEFT JOIN records r ON f.id = r.folder_id
        GROUP BY f.id
        ORDER BY f.is_pinned DESC, f.position ASC
    """)
    fun observeAllIncludingArchivedByPosition(): Flow<List<FolderWithCount>>

    // ═══════════════════════════════════════════════════════════════════════════

    @Query("SELECT EXISTS(SELECT 1 FROM folders WHERE id = :folderId)")
    suspend fun exists(folderId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM folders WHERE LOWER(name) = LOWER(:name) AND id != :excludeId)")
    suspend fun nameExists(name: String, excludeId: Long = 0): Boolean

    @Query("SELECT COUNT(*) FROM folders WHERE is_archived = 0")
    suspend fun getCount(): Int

    @Query("SELECT * FROM folders WHERE updated_at > :timestamp")
    suspend fun getAllModifiedSince(timestamp: Long): List<FolderEntity>

    @Query("UPDATE folders SET position = :position, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM folders WHERE is_archived = 0")
    suspend fun getNextPosition(): Int
}

// ══════════════════════════════════════════════════════════════════════════════
// RECORD DAO
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Query("UPDATE records SET folder_id = :folderId, updated_at = :timestamp WHERE id = :recordId")
    suspend fun moveToFolder(recordId: Long, folderId: Long, timestamp: Long)

    @Query("UPDATE records SET is_pinned = :pinned, updated_at = :timestamp WHERE id = :recordId")
    suspend fun setPinned(recordId: Long, pinned: Boolean, timestamp: Long)

    @Query("UPDATE records SET is_archived = 1, updated_at = :timestamp WHERE id = :recordId")
    suspend fun archive(recordId: Long, timestamp: Long)

    @Query("UPDATE records SET is_archived = 0, updated_at = :timestamp WHERE id = :recordId")
    suspend fun unarchive(recordId: Long, timestamp: Long)

    @Query("UPDATE records SET source_language = :source, target_language = :target, updated_at = :timestamp WHERE id = :recordId")
    suspend fun updateLanguage(recordId: Long, source: String, target: String, timestamp: Long)

    @Query("UPDATE records SET tags = :tags, updated_at = :timestamp WHERE id = :recordId")
    suspend fun updateTags(recordId: Long, tags: String?, timestamp: Long)

    @Query("DELETE FROM records WHERE id = :recordId")
    suspend fun deleteById(recordId: Long)

    @Query("SELECT * FROM records WHERE id = :recordId")
    suspend fun getById(recordId: Long): RecordEntity?

    @Query("SELECT * FROM records WHERE id = :recordId")
    fun observeById(recordId: Long): Flow<RecordEntity?>

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.id = :recordId
        GROUP BY r.id
    """)
    suspend fun getByIdWithCount(recordId: Long): RecordWithCount?

    // ═══════════════════════════════════════════════════════════════════════════
    // СОРТИРОВКА ПО ДАТЕ (updated_at DESC)
    // ═══════════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.folder_id = :folderId AND r.is_archived = 0
        GROUP BY r.id
        ORDER BY r.is_pinned DESC, r.updated_at DESC
    """)
    fun observeByFolderWithCount(folderId: Long): Flow<List<RecordWithCount>>

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.folder_id = :folderId
        GROUP BY r.id
        ORDER BY r.is_pinned DESC, r.updated_at DESC
    """)
    fun observeByFolderIncludingArchivedWithCount(folderId: Long): Flow<List<RecordWithCount>>

    // ═══════════════════════════════════════════════════════════════════════════
    // СОРТИРОВКА ПО ИМЕНИ (name ASC)
    // ═══════════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.folder_id = :folderId AND r.is_archived = 0
        GROUP BY r.id
        ORDER BY r.is_pinned DESC, r.name COLLATE NOCASE ASC
    """)
    fun observeByFolderByName(folderId: Long): Flow<List<RecordWithCount>>

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.folder_id = :folderId
        GROUP BY r.id
        ORDER BY r.is_pinned DESC, r.name COLLATE NOCASE ASC
    """)
    fun observeByFolderIncludingArchivedByName(folderId: Long): Flow<List<RecordWithCount>>

    // ═══════════════════════════════════════════════════════════════════════════
    // СОРТИРОВКА ПО ПОЗИЦИИ (position ASC) - для ручного режима
    // ═══════════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.folder_id = :folderId AND r.is_archived = 0
        GROUP BY r.id
        ORDER BY r.is_pinned DESC, r.position ASC
    """)
    fun observeByFolderByPosition(folderId: Long): Flow<List<RecordWithCount>>

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.folder_id = :folderId
        GROUP BY r.id
        ORDER BY r.is_pinned DESC, r.position ASC
    """)
    fun observeByFolderIncludingArchivedByPosition(folderId: Long): Flow<List<RecordWithCount>>

    // ═══════════════════════════════════════════════════════════════════════════
    // ОСТАЛЬНЫЕ ЗАПРОСЫ
    // ═══════════════════════════════════════════════════════════════════════════

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.is_archived = 0
        GROUP BY r.id
        ORDER BY r.updated_at DESC
    """)
    fun observeAllWithCount(): Flow<List<RecordWithCount>>

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.is_archived = 0
        GROUP BY r.id
        ORDER BY r.updated_at DESC
        LIMIT :limit
    """)
    fun observeRecentWithCount(limit: Int): Flow<List<RecordWithCount>>

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.is_archived = 0 AND r.tags LIKE '%' || :tag || '%'
        GROUP BY r.id
        ORDER BY r.updated_at DESC
    """)
    fun observeByTag(tag: String): Flow<List<RecordWithCount>>

    @Query("SELECT EXISTS(SELECT 1 FROM records WHERE id = :recordId)")
    suspend fun exists(recordId: Long): Boolean

    @Query("SELECT COUNT(*) FROM records WHERE folder_id = :folderId AND is_archived = 0")
    suspend fun getCountByFolder(folderId: Long): Int

    @Query("SELECT COUNT(*) FROM records WHERE is_archived = 0")
    suspend fun getCount(): Int

    @Query("SELECT DISTINCT tags FROM records WHERE tags IS NOT NULL AND tags != '' AND tags != '[]'")
    suspend fun getAllTagsJson(): List<String>

    @Query("""
        SELECT * FROM records
        WHERE is_archived = 0 
        AND (LOWER(name) LIKE LOWER('%' || :query || '%')
             OR LOWER(description) LIKE LOWER('%' || :query || '%'))
        ORDER BY updated_at DESC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 50): List<RecordEntity>

    @Query("""
        SELECT r.id, r.folder_id AS folderId, r.name, r.description, r.tags, r.position,
               r.source_language AS sourceLanguage, r.target_language AS targetLanguage,
               r.is_pinned AS isPinned, r.is_archived AS isArchived,
               r.created_at AS createdAt, r.updated_at AS updatedAt,
               COUNT(d.id) AS documentCount
        FROM records r
        LEFT JOIN documents d ON r.id = d.record_id
        WHERE r.is_archived = 0
          AND (LOWER(r.name) LIKE LOWER('%' || :query || '%')
               OR LOWER(r.description) LIKE LOWER('%' || :query || '%'))
        GROUP BY r.id
        ORDER BY r.updated_at DESC
        LIMIT :limit
    """)
    suspend fun searchWithCount(query: String, limit: Int = 50): List<RecordWithCount>

    @Query("SELECT * FROM records WHERE updated_at > :timestamp")
    suspend fun getAllModifiedSince(timestamp: Long): List<RecordEntity>

    @Query("UPDATE records SET position = :position, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM records WHERE folder_id = :folderId AND is_archived = 0")
    suspend fun getNextPosition(folderId: Long): Int
}

// ══════════════════════════════════════════════════════════════════════════════
// DOCUMENT DAO
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<DocumentEntity>): List<Long>

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("UPDATE documents SET processing_status = :status, updated_at = :timestamp WHERE id = :documentId")
    suspend fun updateStatus(documentId: Long, status: Int, timestamp: Long)

    @Query("""
        UPDATE documents SET 
            original_text = :text, 
            detected_language = :language, 
            ocr_confidence = :confidence,
            processing_status = :status, 
            updated_at = :timestamp 
        WHERE id = :documentId
    """)
    suspend fun updateOcrResult(documentId: Long, text: String, language: String?, confidence: Float?, status: Int, timestamp: Long)

    @Query("UPDATE documents SET translated_text = :text, processing_status = :status, updated_at = :timestamp WHERE id = :documentId")
    suspend fun updateTranslation(documentId: Long, text: String, status: Int, timestamp: Long)

    @Query("UPDATE documents SET position = :position, updated_at = :timestamp WHERE id = :documentId")
    suspend fun updatePosition(documentId: Long, position: Int, timestamp: Long)

    @Query("UPDATE documents SET record_id = :recordId, updated_at = :timestamp WHERE id = :documentId")
    suspend fun moveToRecord(documentId: Long, recordId: Long, timestamp: Long)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: Long)

    @Query("DELETE FROM documents WHERE id IN (:documentIds)")
    suspend fun deleteByIds(documentIds: List<Long>): Int

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getById(documentId: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeById(documentId: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE record_id = :recordId ORDER BY position ASC")
    fun observeByRecord(recordId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE record_id = :recordId ORDER BY position ASC")
    suspend fun getByRecord(recordId: Long): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE processing_status IN (0, 1, 2, 5) ORDER BY created_at DESC")
    fun observePending(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE processing_status IN (4, 7, 10) ORDER BY created_at DESC")
    fun observeFailed(): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents
        WHERE (original_text IS NOT NULL AND LOWER(original_text) LIKE LOWER('%' || :query || '%'))
           OR (translated_text IS NOT NULL AND LOWER(translated_text) LIKE LOWER('%' || :query || '%'))
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun searchLike(query: String, limit: Int = 50): Flow<List<DocumentEntity>>

    @Query("""
        SELECT d.id, d.record_id AS recordId, d.image_path AS imagePath, d.thumbnail_path AS thumbnailPath,
               d.original_text AS originalText, d.translated_text AS translatedText,
               d.detected_language AS detectedLanguage, d.source_language AS sourceLanguage,
               d.target_language AS targetLanguage, d.position, d.processing_status AS processingStatus,
               d.ocr_confidence AS ocrConfidence, d.file_size AS fileSize, d.width, d.height,
               d.created_at AS createdAt, d.updated_at AS updatedAt,
               r.name AS recordName, f.name AS folderName
        FROM documents d
        INNER JOIN records r ON d.record_id = r.id
        INNER JOIN folders f ON r.folder_id = f.id
        WHERE (d.original_text IS NOT NULL AND LOWER(d.original_text) LIKE LOWER('%' || :query || '%'))
           OR (d.translated_text IS NOT NULL AND LOWER(d.translated_text) LIKE LOWER('%' || :query || '%'))
        ORDER BY d.created_at DESC
        LIMIT :limit
    """)
    fun searchWithPath(query: String, limit: Int = 50): Flow<List<DocumentWithPath>>

    @Query("""
        SELECT d.id, d.record_id AS recordId, d.image_path AS imagePath, d.thumbnail_path AS thumbnailPath,
               d.original_text AS originalText, d.translated_text AS translatedText,
               d.detected_language AS detectedLanguage, d.source_language AS sourceLanguage,
               d.target_language AS targetLanguage, d.position, d.processing_status AS processingStatus,
               d.ocr_confidence AS ocrConfidence, d.file_size AS fileSize, d.width, d.height,
               d.created_at AS createdAt, d.updated_at AS updatedAt,
               r.name AS recordName, f.name AS folderName
        FROM documents_fts fts
        INNER JOIN documents d ON d.id = fts.rowid
        INNER JOIN records r ON d.record_id = r.id
        INNER JOIN folders f ON r.folder_id = f.id
        WHERE documents_fts MATCH :ftsQuery
          AND r.is_archived = 0
          AND f.is_archived = 0
        ORDER BY d.updated_at DESC
        LIMIT :limit
    """)
    fun searchFtsWithPath(ftsQuery: String, limit: Int = 50): Flow<List<DocumentWithPath>>

    @Query("SELECT EXISTS(SELECT 1 FROM documents WHERE id = :documentId)")
    suspend fun exists(documentId: Long): Boolean

    @Query("SELECT COUNT(*) FROM documents WHERE record_id = :recordId")
    suspend fun getCountByRecord(recordId: Long): Int

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getCount(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM documents WHERE record_id = :recordId")
    suspend fun getNextPosition(recordId: Long): Int

    @Transaction
    suspend fun reorder(documentIds: List<Long>, timestamp: Long) {
        documentIds.forEachIndexed { index, id ->
            updatePosition(id, index, timestamp)
        }
    }

    @Query("SELECT * FROM documents WHERE updated_at > :timestamp")
    suspend fun getAllModifiedSince(timestamp: Long): List<DocumentEntity>
}

// ══════════════════════════════════════════════════════════════════════════════
// TERM DAO
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface TermDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(term: TermEntity): Long

    @Update
    suspend fun update(term: TermEntity)

    @Query("UPDATE terms SET is_completed = 1, completed_at = :timestamp, updated_at = :timestamp WHERE id = :termId")
    suspend fun markCompleted(termId: Long, timestamp: Long)

    @Query("UPDATE terms SET is_completed = 0, completed_at = NULL, updated_at = :timestamp WHERE id = :termId")
    suspend fun markNotCompleted(termId: Long, timestamp: Long)

    @Query("UPDATE terms SET is_cancelled = 1, updated_at = :timestamp WHERE id = :termId")
    suspend fun cancel(termId: Long, timestamp: Long)

    @Query("UPDATE terms SET is_cancelled = 0, updated_at = :timestamp WHERE id = :termId")
    suspend fun restore(termId: Long, timestamp: Long)

    @Query("DELETE FROM terms WHERE id = :termId")
    suspend fun deleteById(termId: Long)

    @Query("DELETE FROM terms WHERE is_completed = 1")
    suspend fun deleteAllCompleted(): Int

    @Query("DELETE FROM terms WHERE is_cancelled = 1")
    suspend fun deleteAllCancelled(): Int

    @Query("SELECT id FROM terms WHERE is_completed = 1")
    suspend fun getCompletedIds(): List<Long>

    @Query("SELECT id FROM terms WHERE is_cancelled = 1")
    suspend fun getCancelledIds(): List<Long>

    @Query("SELECT * FROM terms WHERE id = :termId")
    suspend fun getById(termId: Long): TermEntity?

    @Query("SELECT * FROM terms WHERE id = :termId")
    fun observeById(termId: Long): Flow<TermEntity?>

    @Query("SELECT * FROM terms WHERE is_completed = 0 AND is_cancelled = 0 AND due_date > :now ORDER BY due_date ASC LIMIT 1")
    suspend fun getNextUpcoming(now: Long): TermEntity?

    @Query("SELECT * FROM terms ORDER BY due_date ASC")
    fun observeAll(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE is_completed = 0 AND is_cancelled = 0 ORDER BY due_date ASC")
    fun observeActive(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE is_completed = 1 ORDER BY completed_at DESC")
    fun observeCompleted(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE is_completed = 0 AND is_cancelled = 0 AND due_date < :now ORDER BY due_date ASC")
    fun observeOverdue(now: Long): Flow<List<TermEntity>>

    @Query("""
        SELECT * FROM terms 
        WHERE is_completed = 0 AND is_cancelled = 0
        AND reminder_minutes_before > 0
        AND (due_date - reminder_minutes_before * 60000) <= :now
        AND due_date > :now
        ORDER BY due_date ASC
    """)
    fun observeNeedingReminder(now: Long): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE due_date BETWEEN :start AND :end ORDER BY due_date ASC")
    fun observeInDateRange(start: Long, end: Long): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE document_id = :documentId ORDER BY due_date ASC")
    fun observeByDocument(documentId: Long): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE folder_id = :folderId ORDER BY due_date ASC")
    fun observeByFolder(folderId: Long): Flow<List<TermEntity>>

    @Query("SELECT COUNT(*) FROM terms WHERE is_completed = 0 AND is_cancelled = 0")
    suspend fun getActiveCount(): Int

    @Query("SELECT COUNT(*) FROM terms WHERE is_completed = 0 AND is_cancelled = 0 AND due_date < :now")
    suspend fun getOverdueCount(now: Long): Int

    @Query("SELECT COUNT(*) FROM terms WHERE is_completed = 0 AND is_cancelled = 0 AND due_date BETWEEN :startOfDay AND :endOfDay")
    suspend fun getDueTodayCount(startOfDay: Long, endOfDay: Long): Int
}

// ══════════════════════════════════════════════════════════════════════════════
// TRANSLATION CACHE DAO
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface TranslationCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache WHERE cache_key = :cacheKey")
    suspend fun deleteByKey(cacheKey: String)

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()

    @Query("DELETE FROM translation_cache WHERE timestamp < :expiryTimestamp")
    suspend fun deleteExpired(expiryTimestamp: Long): Int

    @Query("""
        DELETE FROM translation_cache 
        WHERE cache_key IN (
            SELECT cache_key FROM translation_cache 
            ORDER BY timestamp ASC 
            LIMIT :count
        )
    """)
    suspend fun deleteOldest(count: Int)

    @Query("SELECT * FROM translation_cache WHERE cache_key = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): TranslationCacheEntity?

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCount(): Int

    @Query("""
        SELECT COUNT(*) AS totalEntries,
               COALESCE(SUM(LENGTH(original_text)), 0) AS totalOriginalSize,
               COALESCE(SUM(LENGTH(translated_text)), 0) AS totalTranslatedSize,
               MIN(timestamp) AS oldestEntry,
               MAX(timestamp) AS newestEntry
        FROM translation_cache
    """)
    suspend fun getStats(): CacheStatsResult
}

// ══════════════════════════════════════════════════════════════════════════════
// SEARCH HISTORY DAO
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM search_history WHERE LOWER(query) = LOWER(:query)")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    @Query("""
        DELETE FROM search_history
        WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT :limit)
    """)
    suspend fun trimToLimit(limit: Int)

    @Query("DELETE FROM search_history WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}