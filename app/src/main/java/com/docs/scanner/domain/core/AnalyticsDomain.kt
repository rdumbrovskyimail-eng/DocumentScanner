package com.docs.scanner.domain.core

import kotlinx.serialization.Serializable

/*
 * ════════════════════════════════════════════════════════════════════════════════
 * ANALYTICS CENTER — DOMAIN MODELS
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * Pure domain types. No Android, no Room, no JSON. Conversions to/from
 * entities live in the data layer (AnalyticsRepositoryImpl).
 *
 * Two surfaces:
 *   1. Translation Archive  — mirror of every translation
 *   2. Notes ("Information Analysis") — user-authored notes
 *
 * Both are autonomous: deleting a source document does NOT delete the
 * matching archive entry, and vice versa.
 * ════════════════════════════════════════════════════════════════════════════════
 */

// ──────────────────────────────────────────────────────────────────────────────
// VALUE OBJECTS — Type-safe IDs
// ──────────────────────────────────────────────────────────────────────────────

@JvmInline
@Serializable
value class AnalyticsTranslationId(val value: Long) {
    init { require(value > 0L) { "AnalyticsTranslationId must be positive: $value" } }
}

@JvmInline
@Serializable
value class AnalyticsNoteId(val value: Long) {
    init { require(value > 0L) { "AnalyticsNoteId must be positive: $value" } }
}

// ──────────────────────────────────────────────────────────────────────────────
// TRANSLATION ARCHIVE
// ──────────────────────────────────────────────────────────────────────────────

/**
 * One row in the translation archive.
 *
 * `sourceDocumentId` / `sourceRecordId` are pure metadata (no FK in DB).
 * Even if the source document or record is deleted, this archive entry
 * survives — the denormalized name fields keep the entry meaningful.
 *
 * `userModified = true` means the user has edited the text inside the
 * Analytics Center after the entry was first mirrored. UI shows an
 * "edited" badge for such entries.
 */
@Serializable
data class AnalyticsTranslation(
    val id: AnalyticsTranslationId,
    val translatedText: String,
    val originalText: String? = null,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "ru",
    val sourceDocumentId: Long? = null,
    val sourceRecordId: Long? = null,
    val sourceRecordName: String? = null,
    val sourceFolderName: String? = null,
    val userModified: Boolean = false,
    val wordCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Insert payload — no id yet. Repository fills it in on `create()`.
 *
 * `createdAt` / `updatedAt` are written by the repository at insert time
 * (using TimeProvider). Callers don't need to think about timestamps.
 */
@Serializable
data class NewAnalyticsTranslation(
    val translatedText: String,
    val originalText: String? = null,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "ru",
    val sourceDocumentId: Long? = null,
    val sourceRecordId: Long? = null,
    val sourceRecordName: String? = null,
    val sourceFolderName: String? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// NOTES (Information Analysis)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Free-form analysis note.
 *
 * `tags` is a plain `List<String>` (e.g. `["draft", "important"]`).
 * Persistence layer encodes/decodes the JSON representation.
 *
 * `color` is an optional ARGB hex literal like `"#FF8E24AA"` for UI accents.
 */
@Serializable
data class AnalyticsNote(
    val id: AnalyticsNoteId,
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val color: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** Visible label used in list views when title is blank. */
    val displayTitle: String
        get() = title.ifBlank {
            val firstLine = content.lineSequence().firstOrNull()?.trim().orEmpty()
            if (firstLine.isBlank()) "Untitled note" else firstLine.take(60)
        }

    /** Used by previews in list views. */
    val contentPreview: String
        get() = content
            .lineSequence()
            .joinToString(separator = " ") { it.trim() }
            .take(200)
}

@Serializable
data class NewAnalyticsNote(
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val color: String? = null,
    val isPinned: Boolean = false
)