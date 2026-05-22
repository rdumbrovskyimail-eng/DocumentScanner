/*
 * DocumentScanner - Analytics Center DAOs
 * Version: 1.0.0 (Build 720) - PRODUCTION READY 2026
 *
 * Two autonomous surfaces:
 *   1. AnalyticsTranslationDao — archived translation events + FTS search
 *   2. AnalyticsNoteDao        — free-form notes ("Information Analysis") + FTS search
 *
 * Both surfaces are independent of documents/records (no FKs).
 */

package com.docs.scanner.data.local.database.dao

import androidx.room.*
import com.docs.scanner.data.local.database.entity.*
import kotlinx.coroutines.flow.Flow

// ══════════════════════════════════════════════════════════════════════════════
// ANALYTICS TRANSLATION DAO
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface AnalyticsTranslationDao {

    // ─── INSERT / UPDATE / DELETE ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AnalyticsTranslationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AnalyticsTranslationEntity>): List<Long>

    @Update
    suspend fun update(entry: AnalyticsTranslationEntity)

    @Query("""
        UPDATE analytics_translations
        SET translated_text = :text,
            user_modified = 1,
            word_count = :wordCount,
            updated_at = :timestamp
        WHERE id = :id
    """)
    suspend fun updateText(id: Long, text: String, wordCount: Int, timestamp: Long)

    @Query("DELETE FROM analytics_translations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM analytics_translations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM analytics_translations")
    suspend fun clearAll()

    // ─── READ ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM analytics_translations WHERE id = :id")
    suspend fun getById(id: Long): AnalyticsTranslationEntity?

    @Query("SELECT * FROM analytics_translations WHERE id = :id")
    fun observeById(id: Long): Flow<AnalyticsTranslationEntity?>

    @Query("SELECT * FROM analytics_translations ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<AnalyticsTranslationEntity>>

    @Query("SELECT * FROM analytics_translations ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AnalyticsTranslationEntity>>

    @Query("""
        SELECT * FROM analytics_translations
        WHERE source_record_id = :recordId
        ORDER BY updated_at DESC
    """)
    fun observeByRecord(recordId: Long): Flow<List<AnalyticsTranslationEntity>>

    @Query("""
        SELECT * FROM analytics_translations
        WHERE target_language = :targetLang
        ORDER BY updated_at DESC
    """)
    fun observeByTargetLanguage(targetLang: String): Flow<List<AnalyticsTranslationEntity>>

    // ─── SEARCH ──────────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM analytics_translations
        WHERE LOWER(translated_text) LIKE LOWER('%' || :query || '%')
           OR LOWER(original_text) LIKE LOWER('%' || :query || '%')
        ORDER BY updated_at DESC
        LIMIT :limit
    """)
    fun searchLike(query: String, limit: Int = 50): Flow<List<AnalyticsTranslationEntity>>

    @Query("""
        SELECT t.* FROM analytics_translations t
        INNER JOIN analytics_translations_fts fts ON t.id = fts.rowid
        WHERE analytics_translations_fts MATCH :ftsQuery
        ORDER BY t.updated_at DESC
        LIMIT :limit
    """)
    fun searchFts(ftsQuery: String, limit: Int = 50): Flow<List<AnalyticsTranslationEntity>>

    // ─── COUNTERS ────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM analytics_translations")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM analytics_translations WHERE user_modified = 1")
    suspend fun getModifiedCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM analytics_translations WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM analytics_translations
            WHERE source_document_id = :documentId
        )
    """)
    suspend fun existsForDocument(documentId: Long): Boolean
}

// ══════════════════════════════════════════════════════════════════════════════
// ANALYTICS NOTE DAO
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface AnalyticsNoteDao {

    // ─── INSERT / UPDATE / DELETE ─────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: AnalyticsNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<AnalyticsNoteEntity>): List<Long>

    @Update
    suspend fun update(note: AnalyticsNoteEntity)

    @Query("""
        UPDATE analytics_notes
        SET title = :title,
            content = :content,
            updated_at = :timestamp
        WHERE id = :id
    """)
    suspend fun updateContent(id: Long, title: String, content: String, timestamp: Long)

    @Query("UPDATE analytics_notes SET tags = :tags, updated_at = :timestamp WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String, timestamp: Long)

    @Query("UPDATE analytics_notes SET color = :color, updated_at = :timestamp WHERE id = :id")
    suspend fun updateColor(id: Long, color: String?, timestamp: Long)

    @Query("UPDATE analytics_notes SET is_pinned = :pinned, updated_at = :timestamp WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, timestamp: Long)

    @Query("UPDATE analytics_notes SET is_archived = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun archive(id: Long, timestamp: Long)

    @Query("UPDATE analytics_notes SET is_archived = 0, updated_at = :timestamp WHERE id = :id")
    suspend fun unarchive(id: Long, timestamp: Long)

    @Query("DELETE FROM analytics_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM analytics_notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM analytics_notes WHERE is_archived = 1")
    suspend fun deleteAllArchived(): Int

    @Query("DELETE FROM analytics_notes")
    suspend fun clearAll()

    // ─── READ ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM analytics_notes WHERE id = :id")
    suspend fun getById(id: Long): AnalyticsNoteEntity?

    @Query("SELECT * FROM analytics_notes WHERE id = :id")
    fun observeById(id: Long): Flow<AnalyticsNoteEntity?>

    @Query("""
        SELECT * FROM analytics_notes
        WHERE is_archived = 0
        ORDER BY is_pinned DESC, updated_at DESC
    """)
    fun observeAll(): Flow<List<AnalyticsNoteEntity>>

    @Query("""
        SELECT * FROM analytics_notes
        ORDER BY is_pinned DESC, updated_at DESC
    """)
    fun observeAllIncludingArchived(): Flow<List<AnalyticsNoteEntity>>

    @Query("""
        SELECT * FROM analytics_notes
        WHERE is_archived = 1
        ORDER BY updated_at DESC
    """)
    fun observeArchived(): Flow<List<AnalyticsNoteEntity>>

    @Query("""
        SELECT * FROM analytics_notes
        WHERE is_pinned = 1 AND is_archived = 0
        ORDER BY updated_at DESC
    """)
    fun observePinned(): Flow<List<AnalyticsNoteEntity>>

    @Query("""
        SELECT * FROM analytics_notes
        WHERE is_archived = 0 AND tags LIKE '%' || :tag || '%'
        ORDER BY is_pinned DESC, updated_at DESC
    """)
    fun observeByTag(tag: String): Flow<List<AnalyticsNoteEntity>>

    // ─── SEARCH ──────────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM analytics_notes
        WHERE is_archived = 0
          AND (LOWER(title) LIKE LOWER('%' || :query || '%')
               OR LOWER(content) LIKE LOWER('%' || :query || '%'))
        ORDER BY is_pinned DESC, updated_at DESC
        LIMIT :limit
    """)
    fun searchLike(query: String, limit: Int = 50): Flow<List<AnalyticsNoteEntity>>

    @Query("""
        SELECT n.* FROM analytics_notes n
        INNER JOIN analytics_notes_fts fts ON n.id = fts.rowid
        WHERE analytics_notes_fts MATCH :ftsQuery
          AND n.is_archived = 0
        ORDER BY n.is_pinned DESC, n.updated_at DESC
        LIMIT :limit
    """)
    fun searchFts(ftsQuery: String, limit: Int = 50): Flow<List<AnalyticsNoteEntity>>

    // ─── COUNTERS ────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM analytics_notes WHERE is_archived = 0")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM analytics_notes WHERE is_archived = 1")
    suspend fun getArchivedCount(): Int

    @Query("SELECT COUNT(*) FROM analytics_notes WHERE is_pinned = 1 AND is_archived = 0")
    suspend fun getPinnedCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM analytics_notes WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    // ─── TAGS ────────────────────────────────────────────────────────────────

    @Query("""
        SELECT DISTINCT tags FROM analytics_notes
        WHERE tags IS NOT NULL AND tags != '' AND tags != '[]'
    """)
    suspend fun getAllTagsJson(): List<String>
}