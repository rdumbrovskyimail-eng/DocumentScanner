package com.docs.scanner.data.repository

import com.docs.scanner.data.local.database.dao.AnalyticsNoteDao
import com.docs.scanner.data.local.database.dao.AnalyticsTranslationDao
import com.docs.scanner.data.local.database.entity.AnalyticsNoteEntity
import com.docs.scanner.data.local.database.entity.AnalyticsTranslationEntity
import com.docs.scanner.domain.core.AnalyticsNote
import com.docs.scanner.domain.core.AnalyticsNoteId
import com.docs.scanner.domain.core.AnalyticsTranslation
import com.docs.scanner.domain.core.AnalyticsTranslationId
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.NewAnalyticsNote
import com.docs.scanner.domain.core.NewAnalyticsTranslation
import com.docs.scanner.domain.core.RecordId
import com.docs.scanner.domain.core.TimeProvider
import com.docs.scanner.domain.repository.AnalyticsNoteRepository
import com.docs.scanner.domain.repository.AnalyticsTranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/*
 * ════════════════════════════════════════════════════════════════════════════════
 * ANALYTICS CENTER — REPOSITORY IMPLEMENTATIONS
 * ════════════════════════════════════════════════════════════════════════════════
 *
 *  - Entity ↔ Domain mapping lives at the bottom of this file as extension
 *    functions, keeping the impl classes themselves slim and readable.
 *
 *  - FTS path: we first try `searchFts(query)` with a sanitized prefix query.
 *    If that emits an error (e.g. the user typed `*` or unbalanced quotes),
 *    we transparently fall back to `searchLike` — same shape, same Flow.
 *
 *  - Timestamps are stamped here (not in UseCases) using the injected
 *    TimeProvider so unit tests can freeze the clock.
 *
 *  - All public methods return DomainResult; failures wrap the underlying
 *    exception in DomainError.StorageFailed (matches the rest of the project).
 * ════════════════════════════════════════════════════════════════════════════════
 */

// ──────────────────────────────────────────────────────────────────────────────
// SHARED HELPERS
// ──────────────────────────────────────────────────────────────────────────────

private val analyticsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val tagListSerializer = ListSerializer(String.serializer())

internal fun encodeTags(tags: List<String>): String =
    if (tags.isEmpty()) "[]" else analyticsJson.encodeToString(tagListSerializer, tags)

internal fun decodeTags(json: String): List<String> = try {
    if (json.isBlank() || json == "[]") emptyList()
    else analyticsJson.decodeFromString(tagListSerializer, json)
} catch (e: Exception) {
    Timber.w(e, "Failed to decode tags: %s", json)
    emptyList()
}

/**
 * Count whitespace-separated tokens. Cheap, locale-agnostic.
 * Returns 0 for blank input.
 */
internal fun countWords(text: String): Int =
    if (text.isBlank()) 0
    else text.trim().split("\\s+".toRegex()).size

/**
 * Convert a free-form user query into a prefix-match FTS4 query.
 *
 *  - Strips characters that have special meaning in FTS (`"` and `*`) so the
 *    parser doesn't choke on user input.
 *  - Joins remaining tokens with AND semantics (`token1* token2*`).
 *  - Returns null when there are no usable tokens (caller falls back to LIKE).
 */
internal fun toFtsPrefixQuery(raw: String): String? {
    val tokens = raw
        .replace('"', ' ')
        .replace('*', ' ')
        .trim()
        .split("\\s+".toRegex())
        .filter { it.length >= 2 }
    if (tokens.isEmpty()) return null
    return tokens.joinToString(separator = " ") { "$it*" }
}

// ──────────────────────────────────────────────────────────────────────────────
// TRANSLATION ARCHIVE
// ──────────────────────────────────────────────────────────────────────────────

@Singleton
class AnalyticsTranslationRepositoryImpl @Inject constructor(
    private val dao: AnalyticsTranslationDao,
    private val time: TimeProvider
) : AnalyticsTranslationRepository {

    // ── Observe ──────────────────────────────────────────────────────────
    override fun observeAll(): Flow<List<AnalyticsTranslation>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByRecord(recordId: RecordId?): Flow<List<AnalyticsTranslation>> =
        dao.observeByRecord(recordId?.value).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: AnalyticsTranslationId): Flow<AnalyticsTranslation?> =
        dao.observeById(id.value).map { it?.toDomain() }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    // ── Query ────────────────────────────────────────────────────────────
    override suspend fun getById(id: AnalyticsTranslationId): DomainResult<AnalyticsTranslation> =
        runCatchingStorage {
            val entity = dao.getById(id.value)
                ?: return@runCatchingStorage null
            entity.toDomain()
        }.toNonNullResult { "Translation not found: ${id.value}" }

    override suspend fun getCount(): Int = dao.getCount()

    // ── Search ───────────────────────────────────────────────────────────
    override fun search(query: String, limit: Int): Flow<List<AnalyticsTranslation>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts == null) {
            dao.searchLike(query.trim(), limit)
        } else {
            flow {
                try {
                    dao.searchFts(fts, limit).collect { emit(it) }
                } catch (e: Exception) {
                    Timber.w(e, "FTS search failed, falling back to LIKE. query=%s", query)
                    dao.searchLike(query.trim(), limit).collect { emit(it) }
                }
            }
        }.map { list -> list.map { it.toDomain() } }
    }

    // ── Mutate ───────────────────────────────────────────────────────────
    override suspend fun create(entry: NewAnalyticsTranslation): DomainResult<AnalyticsTranslationId> =
        runCatchingStorage {
            val now = time.currentMillis()
            val newId = dao.insert(
                AnalyticsTranslationEntity(
                    translatedText = entry.translatedText,
                    originalText = entry.originalText,
                    sourceLanguage = entry.sourceLanguage,
                    targetLanguage = entry.targetLanguage,
                    sourceDocumentId = entry.sourceDocumentId,
                    sourceRecordId = entry.sourceRecordId,
                    sourceRecordName = entry.sourceRecordName,
                    sourceFolderName = entry.sourceFolderName,
                    userModified = false,
                    wordCount = countWords(entry.translatedText),
                    createdAt = now,
                    updatedAt = now
                )
            )
            require(newId > 0L) { "Insert returned non-positive id: $newId" }
            AnalyticsTranslationId(newId)
        }

    override suspend fun update(entry: AnalyticsTranslation): DomainResult<Unit> =
        runCatchingStorage {
            dao.update(
                entry.copy(
                    updatedAt = time.currentMillis(),
                    wordCount = countWords(entry.translatedText)
                ).toEntity()
            )
            Unit
        }

    override suspend fun delete(id: AnalyticsTranslationId): DomainResult<Unit> =
        runCatchingStorage { dao.deleteById(id.value) }

    override suspend fun deleteAll(): DomainResult<Unit> =
        runCatchingStorage { dao.clearAll() }

    // ── Backup ───────────────────────────────────────────────────────────
    override suspend fun getModifiedSince(sinceTimestamp: Long): List<AnalyticsTranslation> =
        dao.getAllModifiedSince(sinceTimestamp).map { it.toDomain() }
}

// ──────────────────────────────────────────────────────────────────────────────
// NOTES
// ──────────────────────────────────────────────────────────────────────────────

@Singleton
class AnalyticsNoteRepositoryImpl @Inject constructor(
    private val dao: AnalyticsNoteDao,
    private val time: TimeProvider
) : AnalyticsNoteRepository {

    // ── Observe ──────────────────────────────────────────────────────────
    override fun observeActive(): Flow<List<AnalyticsNote>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeArchived(): Flow<List<AnalyticsNote>> =
        dao.observeArchived().map { list -> list.map { it.toDomain() } }

    override fun observeById(id: AnalyticsNoteId): Flow<AnalyticsNote?> =
        dao.observeById(id.value).map { it?.toDomain() }

    override fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    // ── Query ────────────────────────────────────────────────────────────
    override suspend fun getById(id: AnalyticsNoteId): DomainResult<AnalyticsNote> =
        runCatchingStorage {
            val entity = dao.getById(id.value)
                ?: return@runCatchingStorage null
            entity.toDomain()
        }.toNonNullResult { "Note not found: ${id.value}" }

    override suspend fun getCount(): Int = dao.getCount()

    // ── Search ───────────────────────────────────────────────────────────
    override fun search(query: String, limit: Int): Flow<List<AnalyticsNote>> {
        val fts = toFtsPrefixQuery(query)
        return if (fts == null) {
            dao.searchLike(query.trim(), limit)
        } else {
            flow {
                try {
                    dao.searchFts(fts, limit).collect { emit(it) }
                } catch (e: Exception) {
                    Timber.w(e, "Notes FTS search failed, falling back to LIKE. query=%s", query)
                    dao.searchLike(query.trim(), limit).collect { emit(it) }
                }
            }
        }.map { list -> list.map { it.toDomain() } }
    }

    // ── Mutate ───────────────────────────────────────────────────────────
    override suspend fun create(note: NewAnalyticsNote): DomainResult<AnalyticsNoteId> =
        runCatchingStorage {
            val now = time.currentMillis()
            val newId = dao.insert(
                AnalyticsNoteEntity(
                    title = note.title.trim(),
                    content = note.content,
                    tags = encodeTags(note.tags),
                    color = note.color,
                    isPinned = note.isPinned,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
            require(newId > 0L) { "Insert returned non-positive id: $newId" }
            AnalyticsNoteId(newId)
        }

    override suspend fun update(note: AnalyticsNote): DomainResult<Unit> =
        runCatchingStorage {
            dao.update(note.copy(updatedAt = time.currentMillis()).toEntity())
            Unit
        }

    override suspend fun delete(id: AnalyticsNoteId): DomainResult<Unit> =
        runCatchingStorage { dao.deleteById(id.value) }

    override suspend fun deleteAll(): DomainResult<Unit> =
        runCatchingStorage { dao.clearAll() }

    override suspend fun setPinned(id: AnalyticsNoteId, pinned: Boolean): DomainResult<Unit> =
        runCatchingStorage { dao.setPinned(id.value, pinned, time.currentMillis()) }

    override suspend fun setArchived(id: AnalyticsNoteId, archived: Boolean): DomainResult<Unit> =
        runCatchingStorage { dao.setArchived(id.value, archived, time.currentMillis()) }

    // ── Backup ───────────────────────────────────────────────────────────
    override suspend fun getModifiedSince(sinceTimestamp: Long): List<AnalyticsNote> =
        dao.getAllModifiedSince(sinceTimestamp).map { it.toDomain() }
}

// ──────────────────────────────────────────────────────────────────────────────
// PRIVATE — DomainResult wrapping helpers
// ──────────────────────────────────────────────────────────────────────────────

private inline fun <T> runCatchingStorage(block: () -> T): DomainResult<T> = try {
    DomainResult.success(block())
} catch (e: Exception) {
    Timber.e(e, "Analytics storage operation failed")
    DomainResult.failure(DomainError.StorageFailed(e))
}

private inline fun <T : Any> DomainResult<T?>.toNonNullResult(
    notFoundMessage: () -> String
): DomainResult<T> = when (this) {
    is DomainResult.Success -> {
        val v = data
        if (v != null) DomainResult.success(v)
        else DomainResult.failure(DomainError.StorageFailed(IllegalStateException(notFoundMessage())))
    }
    is DomainResult.Failure -> DomainResult.failure(error)
}

// ──────────────────────────────────────────────────────────────────────────────
// PRIVATE — Entity ↔ Domain mappers
// ──────────────────────────────────────────────────────────────────────────────

private fun AnalyticsTranslationEntity.toDomain(): AnalyticsTranslation =
    AnalyticsTranslation(
        id = AnalyticsTranslationId(id),
        translatedText = translatedText,
        originalText = originalText,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        sourceDocumentId = sourceDocumentId,
        sourceRecordId = sourceRecordId,
        sourceRecordName = sourceRecordName,
        sourceFolderName = sourceFolderName,
        userModified = userModified,
        wordCount = wordCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun AnalyticsTranslation.toEntity(): AnalyticsTranslationEntity =
    AnalyticsTranslationEntity(
        id = id.value,
        translatedText = translatedText,
        originalText = originalText,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        sourceDocumentId = sourceDocumentId,
        sourceRecordId = sourceRecordId,
        sourceRecordName = sourceRecordName,
        sourceFolderName = sourceFolderName,
        userModified = userModified,
        wordCount = wordCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun AnalyticsNoteEntity.toDomain(): AnalyticsNote =
    AnalyticsNote(
        id = AnalyticsNoteId(id),
        title = title,
        content = content,
        tags = decodeTags(tags),
        color = color,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun AnalyticsNote.toEntity(): AnalyticsNoteEntity =
    AnalyticsNoteEntity(
        id = id.value,
        title = title,
        content = content,
        tags = encodeTags(tags),
        color = color,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )