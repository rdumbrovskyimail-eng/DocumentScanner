package com.docs.scanner.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docs.scanner.data.local.database.entity.AnalyticsNoteEntity
import com.docs.scanner.data.local.database.entity.AnalyticsTranslationEntity
import kotlinx.coroutines.flow.Flow

/*
 * ════════════════════════════════════════════════════════════════════════════════
 * ANALYTICS CENTER — DATA ACCESS OBJECTS
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * Conventions used (matches the rest of the project):
 *   - Reactive reads return Flow<...>.
 *   - One-shot reads are suspend.
 *   - Writes are suspend; bulk writes use @Transaction in repository layer.
 *   - FTS search uses MATCH against the mirror tables. The fallback LIKE path
 *     lives in the repository (consistent with DocumentRepositoryImpl).
 * ════════════════════════════════════════════════════════════════════════════════
 */

// ──────────────────────────────────────────────────────────────────────────────
// TRANSLATION ARCHIVE
// ──────────────────────────────────────────────────────────────────────────────

@Dao
interface AnalyticsTranslationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AnalyticsTranslationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AnalyticsTranslationEntity>): List<Long>

    @Update
    suspend fun update(entry: AnalyticsTranslationEntity)

    @Query("DELETE FROM analytics_translations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM analytics_translations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM analytics_translations")
    suspend fun clearAll()

    @Query("SELECT * FROM analytics_translations WHERE id = :id")
    suspend fun getById(id: Long): AnalyticsTranslationEntity?

    @Query("SELECT * FROM analytics_translations WHERE id = :id")
    fun observeById(id: Long): Flow<AnalyticsTranslationEntity?>

    @Query("SELECT * FROM analytics_translations ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<AnalyticsTranslationEntity>>

    @Query("SELECT * FROM analytics_translations ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<AnalyticsTranslationEntity>

    @Query("SELECT COUNT(*) FROM analytics_translations")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM analytics_translations")
    suspend fun getCount(): Int

    @Query("SELECT MAX(updated_at) FROM analytics_translations")
    suspend fun getMaxUpdatedAt(): Long?

    /**
     * FTS4 search. The mirror table column order is (translated_text, original_text).
     * We MATCH the whole document to support both phrase and prefix queries.
     */
    @Query("""
        SELECT t.* FROM analytics_translations AS t
        JOIN analytics_translations_fts AS fts ON t.id = fts.docid
        WHERE analytics_translations_fts MATCH :ftsQuery
        ORDER BY t.updated_at DESC, t.id DESC
        LIMIT :limit
    """)
    fun searchFts(ftsQuery: String, limit: Int = 50): Flow<List<AnalyticsTranslationEntity>>

    /** Fallback for queries that break FTS syntax. */
    @Query("""
        SELECT * FROM analytics_translations
        WHERE translated_text LIKE '%' || :query || '%' ESCAPE '\'
           OR original_text   LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY updated_at DESC, id DESC
        LIMIT :limit
    """)
    fun searchLike(query: String, limit: Int = 50): Flow<List<AnalyticsTranslationEntity>>

    /**
     * Optional filter by source record — used by "View archive entries from this record".
     * NULL `recordId` returns all archive entries (untraced ones included).
     */
    @Query("""
        SELECT * FROM analytics_translations
        WHERE (:recordId IS NULL) OR (source_record_id = :recordId)
        ORDER BY updated_at DESC, id DESC
    """)
    fun observeByRecord(recordId: Long?): Flow<List<AnalyticsTranslationEntity>>

    /** Used by incremental backup. */
    @Query("SELECT * FROM analytics_translations WHERE updated_at > :sinceTimestamp")
    suspend fun getAllModifiedSince(sinceTimestamp: Long): List<AnalyticsTranslationEntity>

}

// ──────────────────────────────────────────────────────────────────────────────
// NOTES
// ──────────────────────────────────────────────────────────────────────────────

@Dao
interface AnalyticsNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: AnalyticsNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<AnalyticsNoteEntity>): List<Long>

    @Update
    suspend fun update(note: AnalyticsNoteEntity)

    @Query("UPDATE analytics_notes SET is_pinned = :pinned, updated_at = :timestamp WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, timestamp: Long)

    @Query("UPDATE analytics_notes SET is_archived = :archived, updated_at = :timestamp WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, timestamp: Long)

    @Query("DELETE FROM analytics_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM analytics_notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM analytics_notes")
    suspend fun clearAll()

    @Query("SELECT * FROM analytics_notes WHERE id = :id")
    suspend fun getById(id: Long): AnalyticsNoteEntity?

    @Query("SELECT * FROM analytics_notes WHERE id = :id")
    fun observeById(id: Long): Flow<AnalyticsNoteEntity?>

    /**
     * Default list view: non-archived notes, pinned first, then most recent.
     */
    @Query("""
        SELECT * FROM analytics_notes
        WHERE is_archived = 0
        ORDER BY is_pinned DESC, updated_at DESC, id DESC
    """)
    fun observeActive(): Flow<List<AnalyticsNoteEntity>>

    @Query("""
        SELECT * FROM analytics_notes
        WHERE is_archived = 1
        ORDER BY updated_at DESC, id DESC
    """)
    fun observeArchived(): Flow<List<AnalyticsNoteEntity>>

    @Query("SELECT * FROM analytics_notes ORDER BY is_pinned DESC, updated_at DESC, id DESC")
    fun observeAll(): Flow<List<AnalyticsNoteEntity>>

    @Query("SELECT COUNT(*) FROM analytics_notes WHERE is_archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM analytics_notes")
    suspend fun getCount(): Int

    /**
     * FTS4 search across notes. Mirror columns: (title, content).
     */
    @Query("""
        SELECT n.* FROM analytics_notes AS n
        JOIN analytics_notes_fts AS fts ON n.id = fts.docid
        WHERE analytics_notes_fts MATCH :ftsQuery
          AND n.is_archived = 0
        ORDER BY n.is_pinned DESC, n.updated_at DESC, n.id DESC
        LIMIT :limit
    """)
    fun searchFts(ftsQuery: String, limit: Int = 50): Flow<List<AnalyticsNoteEntity>>

    /** Fallback for queries that break FTS syntax. */
    @Query("""
        SELECT * FROM analytics_notes
        WHERE is_archived = 0
          AND (title   LIKE '%' || :query || '%' ESCAPE '\'
            OR content LIKE '%' || :query || '%' ESCAPE '\'
            OR tags    LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY is_pinned DESC, updated_at DESC, id DESC
        LIMIT :limit
    """)
    fun searchLike(query: String, limit: Int = 50): Flow<List<AnalyticsNoteEntity>>

    /** Used by incremental backup. */
    @Query("SELECT * FROM analytics_notes WHERE updated_at > :sinceTimestamp")
    suspend fun getAllModifiedSince(sinceTimestamp: Long): List<AnalyticsNoteEntity>

}