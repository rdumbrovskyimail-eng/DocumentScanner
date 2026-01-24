/*
 * DocumentScanner - Data Entities
 * Version: 7.2.0 (Build 720) - PRODUCTION READY 2026
 *
 * ✅ CRITICAL FIXES APPLIED (Session 14):
 * - Added model column to TranslationCacheEntity
 * - Added model index to translation_cache table
 * - Updated generateCacheKey() to include model parameter
 * - Added ModelCacheStats data class
 * - Synchronized with ModelConstants.kt
 *
 * ✅ ALL PREVIOUS FIXES:
 * - Syntax error in generateCacheKey() fixed
 * - Missing MessageDigest import added
 * - ProcessingStatusMapper object added
 * - Duplication removed (parseJsonList/toJsonList consolidated)
 * - Position field added to Folder/Record mappers for drag & drop
 *
 * ✅ Synchronized with Domain v4.1.0
 * ✅ ProcessingStatus sealed interface mapping
 * ✅ Proper New/Existing entity separation
 */

package com.docs.scanner.data.local.database.entity

import androidx.room.*
import com.docs.scanner.domain.core.*
import java.security.MessageDigest

// ══════════════════════════════════════════════════════════════════════════════
// PROCESSING STATUS MAPPER
// ══════════════════════════════════════════════════════════════════════════════

object ProcessingStatusMapper {
    const val PENDING = 0
    const val QUEUED = 1
    const val OCR_IN_PROGRESS = 2
    const val OCR_COMPLETE = 3
    const val OCR_FAILED = 4
    const val TRANSLATION_IN_PROGRESS = 5
    const val TRANSLATION_COMPLETE = 6
    const val TRANSLATION_FAILED = 7
    const val COMPLETE = 8
    const val CANCELLED = 9
    const val ERROR = 10

    fun toInt(status: ProcessingStatus): Int = when (status) {
        is ProcessingStatus.Pending -> PENDING
        is ProcessingStatus.Queued -> QUEUED
        is ProcessingStatus.Ocr.InProgress -> OCR_IN_PROGRESS
        is ProcessingStatus.Ocr.Complete -> OCR_COMPLETE
        is ProcessingStatus.Ocr.Failed -> OCR_FAILED
        is ProcessingStatus.Translation.InProgress -> TRANSLATION_IN_PROGRESS
        is ProcessingStatus.Translation.Complete -> TRANSLATION_COMPLETE
        is ProcessingStatus.Translation.Failed -> TRANSLATION_FAILED
        is ProcessingStatus.Complete -> COMPLETE
        is ProcessingStatus.Cancelled -> CANCELLED
        is ProcessingStatus.Error -> ERROR
    }

    fun fromInt(value: Int): ProcessingStatus = when (value) {
        PENDING -> ProcessingStatus.Pending
        QUEUED -> ProcessingStatus.Queued
        OCR_IN_PROGRESS -> ProcessingStatus.Ocr.InProgress
        OCR_COMPLETE -> ProcessingStatus.Ocr.Complete
        OCR_FAILED -> ProcessingStatus.Ocr.Failed
        TRANSLATION_IN_PROGRESS -> ProcessingStatus.Translation.InProgress
        TRANSLATION_COMPLETE -> ProcessingStatus.Translation.Complete
        TRANSLATION_FAILED -> ProcessingStatus.Translation.Failed
        COMPLETE -> ProcessingStatus.Complete
        CANCELLED -> ProcessingStatus.Cancelled
        ERROR -> ProcessingStatus.Error
        else -> ProcessingStatus.Error
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// FOLDER ENTITY
// ══════════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["name"]),
        Index(value = ["is_pinned"]),
        Index(value = ["is_archived"]),
        Index(value = ["created_at"]),
        Index(value = ["position"])
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "color")
    val color: Int? = null,

    @ColumnInfo(name = "icon")
    val icon: String? = null,

    @ColumnInfo(name = "position", defaultValue = "0")
    val position: Int = 0,

    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(recordCount: Int = 0): Folder {
        require(id > 0) { "Cannot convert unsaved entity (id=0) to Folder" }
        return Folder(
            id = FolderId(id),
            name = name,
            description = description,
            color = color,
            icon = icon,
            recordCount = recordCount,
            position = position,
            isPinned = isPinned,
            isArchived = isArchived,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun toNewDomain(): NewFolder = NewFolder(
        name = name,
        description = description,
        color = color,
        icon = icon,
        isPinned = isPinned,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(folder: Folder): FolderEntity = FolderEntity(
            id = folder.id.value,
            name = folder.name,
            description = folder.description,
            color = folder.color,
            icon = folder.icon,
            position = folder.position,
            isPinned = folder.isPinned,
            isArchived = folder.isArchived,
            createdAt = folder.createdAt,
            updatedAt = folder.updatedAt
        )

        fun fromNewDomain(newFolder: NewFolder): FolderEntity = FolderEntity(
            id = 0,
            name = newFolder.name,
            description = newFolder.description,
            color = newFolder.color,
            icon = newFolder.icon,
            isPinned = newFolder.isPinned,
            isArchived = false,
            createdAt = newFolder.createdAt,
            updatedAt = newFolder.updatedAt
        )
    }
}

data class FolderWithCount(
    val id: Long,
    val name: String,
    val description: String?,
    val color: Int?,
    val icon: String?,
    val position: Int,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val recordCount: Int
) {
    fun toDomain(): Folder = Folder(
        id = FolderId(id),
        name = name,
        description = description,
        color = color,
        icon = icon,
        recordCount = recordCount,
        position = position,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// RECORD ENTITY
// ══════════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["name"]),
        Index(value = ["is_pinned"]),
        Index(value = ["is_archived"]),
        Index(value = ["created_at"]),
        Index(value = ["position"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "folder_id")
    val folderId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "tags")
    val tags: String? = null,

    @ColumnInfo(name = "source_language", defaultValue = "auto")
    val sourceLanguage: String = "auto",

    @ColumnInfo(name = "target_language", defaultValue = "en")
    val targetLanguage: String = "en",

    @ColumnInfo(name = "position", defaultValue = "0")
    val position: Int = 0,

    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(documentCount: Int = 0): Record {
        require(id > 0) { "Cannot convert unsaved entity (id=0) to Record" }
        return Record(
            id = RecordId(id),
            folderId = FolderId(folderId),
            name = name,
            description = description,
            tags = tags?.let { parseJsonList(it) } ?: emptyList(),
            documentCount = documentCount,
            position = position,
            sourceLanguage = Language.fromCode(sourceLanguage) ?: Language.AUTO,
            targetLanguage = Language.fromCode(targetLanguage) ?: Language.ENGLISH,
            isPinned = isPinned,
            isArchived = isArchived,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun toNewDomain(): NewRecord = NewRecord(
        folderId = FolderId(folderId),
        name = name,
        description = description,
        tags = tags?.let { parseJsonList(it) } ?: emptyList(),
        sourceLanguage = Language.fromCode(sourceLanguage) ?: Language.AUTO,
        targetLanguage = Language.fromCode(targetLanguage) ?: Language.ENGLISH,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(record: Record): RecordEntity = RecordEntity(
            id = record.id.value,
            folderId = record.folderId.value,
            name = record.name,
            description = record.description,
            tags = if (record.tags.isEmpty()) null else toJsonList(record.tags),
            sourceLanguage = record.sourceLanguage.code,
            targetLanguage = record.targetLanguage.code,
            position = record.position,
            isPinned = record.isPinned,
            isArchived = record.isArchived,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt
        )

        fun fromNewDomain(newRecord: NewRecord): RecordEntity = RecordEntity(
            id = 0,
            folderId = newRecord.folderId.value,
            name = newRecord.name,
            description = newRecord.description,
            tags = if (newRecord.tags.isEmpty()) null else toJsonList(newRecord.tags),
            sourceLanguage = newRecord.sourceLanguage.code,
            targetLanguage = newRecord.targetLanguage.code,
            isPinned = false,
            isArchived = false,
            createdAt = newRecord.createdAt,
            updatedAt = newRecord.updatedAt
        )
    }
}

data class RecordWithCount(
    val id: Long,
    val folderId: Long,
    val name: String,
    val description: String?,
    val tags: String?,
    val position: Int,
    val sourceLanguage: String,
    val targetLanguage: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val documentCount: Int
) {
    fun toDomain(): Record = Record(
        id = RecordId(id),
        folderId = FolderId(folderId),
        name = name,
        description = description,
        tags = tags?.let { parseJsonList(it) } ?: emptyList(),
        documentCount = documentCount,
        position = position,
        sourceLanguage = Language.fromCode(sourceLanguage) ?: Language.AUTO,
        targetLanguage = Language.fromCode(targetLanguage) ?: Language.ENGLISH,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// DOCUMENT ENTITY
// ══════════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["record_id"]),
        Index(value = ["processing_status"]),
        Index(value = ["created_at"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "record_id")
    val recordId: Long,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    @ColumnInfo(name = "original_text")
    val originalText: String? = null,

    @ColumnInfo(name = "translated_text")
    val translatedText: String? = null,

    @ColumnInfo(name = "detected_language")
    val detectedLanguage: String? = null,

    @ColumnInfo(name = "source_language", defaultValue = "auto")
    val sourceLanguage: String = "auto",

    @ColumnInfo(name = "target_language", defaultValue = "en")
    val targetLanguage: String = "en",

    @ColumnInfo(name = "position", defaultValue = "0")
    val position: Int = 0,

    @ColumnInfo(name = "processing_status", defaultValue = "0")
    val processingStatus: Int = ProcessingStatusMapper.PENDING,

    @ColumnInfo(name = "ocr_confidence")
    val ocrConfidence: Float? = null,

    @ColumnInfo(name = "file_size", defaultValue = "0")
    val fileSize: Long = 0L,

    @ColumnInfo(name = "width", defaultValue = "0")
    val width: Int = 0,

    @ColumnInfo(name = "height", defaultValue = "0")
    val height: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(recordName: String? = null, folderName: String? = null): Document {
        require(id > 0) { "Cannot convert unsaved entity (id=0) to Document" }
        return Document(
            id = DocumentId(id),
            recordId = RecordId(recordId),
            imagePath = imagePath,
            thumbnailPath = thumbnailPath,
            originalText = originalText,
            translatedText = translatedText,
            detectedLanguage = detectedLanguage?.let { Language.fromCode(it) },
            sourceLanguage = Language.fromCode(sourceLanguage) ?: Language.AUTO,
            targetLanguage = Language.fromCode(targetLanguage) ?: Language.ENGLISH,
            position = position,
            processingStatus = ProcessingStatusMapper.fromInt(processingStatus),
            ocrConfidence = ocrConfidence,
            fileSize = fileSize,
            width = width,
            height = height,
            createdAt = createdAt,
            updatedAt = updatedAt,
            recordName = recordName,
            folderName = folderName
        )
    }

    fun toNewDomain(): NewDocument = NewDocument(
        recordId = RecordId(recordId),
        imagePath = imagePath,
        thumbnailPath = thumbnailPath,
        sourceLanguage = Language.fromCode(sourceLanguage) ?: Language.AUTO,
        targetLanguage = Language.fromCode(targetLanguage) ?: Language.ENGLISH,
        position = position,
        fileSize = fileSize,
        width = width,
        height = height,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(doc: Document): DocumentEntity = DocumentEntity(
            id = doc.id.value,
            recordId = doc.recordId.value,
            imagePath = doc.imagePath,
            thumbnailPath = doc.thumbnailPath,
            originalText = doc.originalText,
            translatedText = doc.translatedText,
            detectedLanguage = doc.detectedLanguage?.code,
            sourceLanguage = doc.sourceLanguage.code,
            targetLanguage = doc.targetLanguage.code,
            position = doc.position,
            processingStatus = ProcessingStatusMapper.toInt(doc.processingStatus),
            ocrConfidence = doc.ocrConfidence,
            fileSize = doc.fileSize,
            width = doc.width,
            height = doc.height,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt
        )

        fun fromNewDomain(newDoc: NewDocument): DocumentEntity = DocumentEntity(
            id = 0,
            recordId = newDoc.recordId.value,
            imagePath = newDoc.imagePath,
            thumbnailPath = newDoc.thumbnailPath,
            sourceLanguage = newDoc.sourceLanguage.code,
            targetLanguage = newDoc.targetLanguage.code,
            position = newDoc.position,
            fileSize = newDoc.fileSize,
            width = newDoc.width,
            height = newDoc.height,
            createdAt = newDoc.createdAt,
            updatedAt = newDoc.updatedAt
        )
    }
}

data class DocumentWithPath(
    val id: Long,
    val recordId: Long,
    val imagePath: String,
    val thumbnailPath: String?,
    val originalText: String?,
    val translatedText: String?,
    val detectedLanguage: String?,
    val sourceLanguage: String,
    val targetLanguage: String,
    val position: Int,
    val processingStatus: Int,
    val ocrConfidence: Float?,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val recordName: String,
    val folderName: String
) {
    fun toDomain(): Document = Document(
        id = DocumentId(id),
        recordId = RecordId(recordId),
        imagePath = imagePath,
        thumbnailPath = thumbnailPath,
        originalText = originalText,
        translatedText = translatedText,
        detectedLanguage = detectedLanguage?.let { Language.fromCode(it) },
        sourceLanguage = Language.fromCode(sourceLanguage) ?: Language.AUTO,
        targetLanguage = Language.fromCode(targetLanguage) ?: Language.ENGLISH,
        position = position,
        processingStatus = ProcessingStatusMapper.fromInt(processingStatus),
        ocrConfidence = ocrConfidence,
        fileSize = fileSize,
        width = width,
        height = height,
        createdAt = createdAt,
        updatedAt = updatedAt,
        recordName = recordName,
        folderName = folderName
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// DOCUMENT FTS ENTITY
// ══════════════════════════════════════════════════════════════════════════════

@Entity(tableName = "documents_fts")
@Fts4(contentEntity = DocumentEntity::class)
data class DocumentFtsEntity(
    @ColumnInfo(name = "original_text")
    val originalText: String?,

    @ColumnInfo(name = "translated_text")
    val translatedText: String?
)

// ══════════════════════════════════════════════════════════════════════════════
// TERM ENTITY
// ══════════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "terms",
    indices = [
        Index(value = ["due_date"]),
        Index(value = ["is_completed"]),
        Index(value = ["is_cancelled"]),
        Index(value = ["is_completed", "due_date"]),
        Index(value = ["document_id"]),
        Index(value = ["folder_id"])
    ]
)
data class TermEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "due_date")
    val dueDate: Long,

    @ColumnInfo(name = "reminder_minutes_before", defaultValue = "60")
    val reminderMinutesBefore: Int = 60,

    @ColumnInfo(name = "priority", defaultValue = "1")
    val priority: Int = TermPriority.NORMAL.ordinal,

    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "is_cancelled", defaultValue = "0")
    val isCancelled: Boolean = false,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "document_id")
    val documentId: Long? = null,

    @ColumnInfo(name = "folder_id")
    val folderId: Long? = null,

    @ColumnInfo(name = "color")
    val color: Int? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Term {
        require(id > 0) { "Cannot convert unsaved entity (id=0) to Term" }
        return Term(
            id = TermId(id),
            title = title,
            description = description,
            dueDate = dueDate,
            reminderMinutesBefore = reminderMinutesBefore,
            priority = TermPriority.entries.getOrElse(priority) { TermPriority.NORMAL },
            isCompleted = isCompleted,
            isCancelled = isCancelled,
            completedAt = completedAt,
            documentId = documentId?.let { DocumentId(it) },
            folderId = folderId?.let { FolderId(it) },
            color = color,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun toNewDomain(): NewTerm = NewTerm(
        title = title,
        description = description,
        dueDate = dueDate,
        reminderMinutesBefore = reminderMinutesBefore,
        priority = TermPriority.entries.getOrElse(priority) { TermPriority.NORMAL },
        documentId = documentId?.let { DocumentId(it) },
        folderId = folderId?.let { FolderId(it) },
        color = color,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(term: Term): TermEntity = TermEntity(
            id = term.id.value,
            title = term.title,
            description = term.description,
            dueDate = term.dueDate,
            reminderMinutesBefore = term.reminderMinutesBefore,
            priority = term.priority.ordinal,
            isCompleted = term.isCompleted,
            isCancelled = term.isCancelled,
            completedAt = term.completedAt,
            documentId = term.documentId?.value,
            folderId = term.folderId?.value,
            color = term.color,
            createdAt = term.createdAt,
            updatedAt = term.updatedAt
        )

        fun fromNewDomain(newTerm: NewTerm): TermEntity = TermEntity(
            id = 0,
            title = newTerm.title,
            description = newTerm.description,
            dueDate = newTerm.dueDate,
            reminderMinutesBefore = newTerm.reminderMinutesBefore,
            priority = newTerm.priority.ordinal,
            documentId = newTerm.documentId?.value,
            folderId = newTerm.folderId?.value,
            color = newTerm.color,
            createdAt = newTerm.createdAt,
            updatedAt = newTerm.updatedAt
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TRANSLATION CACHE ENTITY
// ══════════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "translation_cache",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["source_language", "target_language"]),
        Index(value = ["model"])
    ]
)
data class TranslationCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "cache_key")
    val cacheKey: String,

    @ColumnInfo(name = "original_text")
    val originalText: String,

    @ColumnInfo(name = "translated_text")
    val translatedText: String,

    @ColumnInfo(name = "source_language")
    val sourceLanguage: String,

    @ColumnInfo(name = "target_language")
    val targetLanguage: String,

    @ColumnInfo(name = "model", defaultValue = "gemini-2.5-flash-lite")
    val model: String = com.docs.scanner.domain.core.ModelConstants.DEFAULT_TRANSLATION_MODEL,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlDays: Int = 30): Boolean {
        val expiryTime = timestamp + (ttlDays * 24 * 60 * 60 * 1000L)
        return System.currentTimeMillis() > expiryTime
    }

    companion object {
        /**
         * Генерирует ключ кэша: SHA-256("text|srcLang|tgtLang|model")
         *
         * ✅ CRITICAL FIX (v2.0.0):
         * - Добавлен параметр model в ключ
         * - Теперь кэш различает модели!
         * - "Hello" en→ru flash-lite ≠ "Hello" en→ru pro-preview
         *
         * @param text Original text to translate
         * @param srcLang Source language code
         * @param tgtLang Target language code
         * @param model Translation model used
         * @return SHA-256 hash as hex string (64 characters)
         */
        fun generateCacheKey(
            text: String, 
            srcLang: String, 
            tgtLang: String,
            model: String = com.docs.scanner.domain.core.ModelConstants.DEFAULT_TRANSLATION_MODEL
        ): String {
            val combined = "$text|$srcLang|$tgtLang|$model"
            val bytes = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

data class CacheStatsResult(
    val totalEntries: Int,
    val totalOriginalSize: Long,
    val totalTranslatedSize: Long,
    val oldestEntry: Long?,
    val newestEntry: Long?
)

data class LanguagePairStat(
    val sourceLanguage: String,
    val targetLanguage: String,
    val count: Int
)

data class ModelCacheStats(
    val model: String,
    val entryCount: Int,
    val totalSize: Long
)

// ══════════════════════════════════════════════════════════════════════════════
// SEARCH HISTORY ENTITY
// ══════════════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"]), Index(value = ["timestamp"])]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "query")
    val query: String,

    @ColumnInfo(name = "result_count")
    val resultCount: Int = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

// ══════════════════════════════════════════════════════════════════════════════
// JSON UTILITIES
// ══════════════════════════════════════════════════════════════════════════════

private fun parseJsonList(json: String): List<String> {
    if (json.isBlank() || json == "[]") return emptyList()

    return try {
        json.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun toJsonList(list: List<String>): String {
    if (list.isEmpty()) return "[]"
    return list.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
}