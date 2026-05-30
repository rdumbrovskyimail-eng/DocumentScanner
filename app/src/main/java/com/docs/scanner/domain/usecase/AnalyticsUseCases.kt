package com.docs.scanner.domain.usecase

import com.docs.scanner.domain.core.AnalyticsNote
import com.docs.scanner.domain.core.AnalyticsNoteId
import com.docs.scanner.domain.core.AnalyticsTranslation
import com.docs.scanner.domain.core.AnalyticsTranslationId
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.NewAnalyticsNote
import com.docs.scanner.domain.core.NewAnalyticsTranslation
import com.docs.scanner.domain.core.RecordId
import com.docs.scanner.domain.core.ValidationError
import com.docs.scanner.domain.repository.AnalyticsNoteRepository
import com.docs.scanner.domain.repository.AnalyticsTranslationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/*
 * ════════════════════════════════════════════════════════════════════════════════
 * ANALYTICS CENTER — USE CASES
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * Thin orchestration layer over the two analytics repositories.
 *
 * Responsibilities here (and only here):
 *   - Input validation (blank/empty checks → ValidationError)
 *   - Marking user edits via `userModified = true` on translation updates
 *   - Wrapping all results in DomainResult so the UI never throws
 *
 * Everything else (timestamps, FTS sanitization, JSON tag encoding) is the
 * repository's job.
 * ════════════════════════════════════════════════════════════════════════════════
 */

// ──────────────────────────────────────────────────────────────────────────────
// TRANSLATION ARCHIVE
// ──────────────────────────────────────────────────────────────────────────────

@Singleton
class AnalyticsTranslationUseCases @Inject constructor(
    private val repo: AnalyticsTranslationRepository
) {

    // ── Observe ──────────────────────────────────────────────────────────
    fun observeAll(): Flow<List<AnalyticsTranslation>> = repo.observeAll()

    fun observeByRecord(recordId: RecordId?): Flow<List<AnalyticsTranslation>> =
        repo.observeByRecord(recordId)

    fun observeById(id: AnalyticsTranslationId): Flow<AnalyticsTranslation?> =
        repo.observeById(id)

    fun count(): Flow<Int> = repo.observeCount()

    // ── Query ────────────────────────────────────────────────────────────
    suspend fun getById(id: AnalyticsTranslationId): DomainResult<AnalyticsTranslation> =
        repo.getById(id)

    fun search(query: String, limit: Int = 50): Flow<List<AnalyticsTranslation>> =
        repo.search(query, limit)

    // ── Mutate ───────────────────────────────────────────────────────────

    /**
     * Mirror a fresh translation event into the archive.
     *
     * Called by `EditorViewModel` whenever a document gets a non-empty
     * translation. The archive entry is autonomous — editing it later does
     * NOT affect the source document.
     */
    suspend fun mirrorFromEditor(entry: NewAnalyticsTranslation): DomainResult<AnalyticsTranslationId> {
        if (entry.translatedText.isBlank()) {
            return DomainResult.failure(
                DomainError.ValidationFailed(ValidationError.EmptyField("translatedText"))
            )
        }
        return repo.create(entry)
    }

    /**
     * User edited an existing archive entry — mark as user-modified.
     */
    suspend fun update(translation: AnalyticsTranslation): DomainResult<Unit> {
        if (translation.translatedText.isBlank()) {
            return DomainResult.failure(
                DomainError.ValidationFailed(ValidationError.EmptyField("translatedText"))
            )
        }
        return repo.update(translation.copy(userModified = true))
    }

    suspend fun delete(id: AnalyticsTranslationId): DomainResult<Unit> = repo.delete(id)

    suspend fun deleteAll(): DomainResult<Unit> = repo.deleteAll()

    // ── Backup helpers ───────────────────────────────────────────────────
    suspend fun getModifiedSince(sinceTimestamp: Long): List<AnalyticsTranslation> =
        repo.getModifiedSince(sinceTimestamp)
}

// ──────────────────────────────────────────────────────────────────────────────
// NOTES
// ──────────────────────────────────────────────────────────────────────────────

@Singleton
class AnalyticsNoteUseCases @Inject constructor(
    private val repo: AnalyticsNoteRepository
) {

    // ── Observe ──────────────────────────────────────────────────────────
    fun observeActive(): Flow<List<AnalyticsNote>> = repo.observeActive()
    fun observeArchived(): Flow<List<AnalyticsNote>> = repo.observeArchived()
    fun observeById(id: AnalyticsNoteId): Flow<AnalyticsNote?> = repo.observeById(id)
    fun activeCount(): Flow<Int> = repo.observeActiveCount()

    // ── Query ────────────────────────────────────────────────────────────
    suspend fun getById(id: AnalyticsNoteId): DomainResult<AnalyticsNote> = repo.getById(id)

    fun search(query: String, limit: Int = 50): Flow<List<AnalyticsNote>> =
        repo.search(query, limit)

    // ── Mutate ───────────────────────────────────────────────────────────

    /**
     * Note must have at least *something* — either a title or content.
     * Completely-empty notes are rejected to prevent accidental clutter.
     */
    suspend fun create(newNote: NewAnalyticsNote): DomainResult<AnalyticsNoteId> {
        if (newNote.title.isBlank() && newNote.content.isBlank()) {
            return DomainResult.failure(
                DomainError.ValidationFailed(ValidationError.EmptyField("title or content"))
            )
        }
        return repo.create(newNote)
    }

    suspend fun update(note: AnalyticsNote): DomainResult<Unit> {
        if (note.title.isBlank() && note.content.isBlank()) {
            return DomainResult.failure(
                DomainError.ValidationFailed(ValidationError.EmptyField("title or content"))
            )
        }
        return repo.update(note)
    }

    suspend fun delete(id: AnalyticsNoteId): DomainResult<Unit> = repo.delete(id)

    suspend fun deleteAll(): DomainResult<Unit> = repo.deleteAll()

    suspend fun setPinned(id: AnalyticsNoteId, pinned: Boolean): DomainResult<Unit> =
        repo.setPinned(id, pinned)

    suspend fun setArchived(id: AnalyticsNoteId, archived: Boolean): DomainResult<Unit> =
        repo.setArchived(id, archived)

    // ── Backup helpers ───────────────────────────────────────────────────
    suspend fun getModifiedSince(sinceTimestamp: Long): List<AnalyticsNote> =
        repo.getModifiedSince(sinceTimestamp)
}