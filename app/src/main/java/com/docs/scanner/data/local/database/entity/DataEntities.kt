/*
 * DocumentScanner - Data Entities
 * Version: 7.0.0 - Production Ready 2026 (FULLY SYNCHRONIZED)
 * 
 * ✅ ALL CRITICAL FIXES APPLIED:
 *    - Syntax error in generateCacheKey() fixed
 *    - Missing MessageDigest import added
 *    - ProcessingStatusMapper object added
 *    - Duplication removed (parseJsonList/toJsonList consolidated)
 * 
 * ✅ Synchronized with Domain v4.1.0
 * ✅ ProcessingStatus sealed interface mapping
 * ✅ Proper New/Existing entity separation
 * 
 * 🔴 FIXED ISSUES:
 *    - Critical #1: Syntax error in TranslationCacheEntity.generateCacheKey()
 *    - Critical #2: Missing import java.security.MessageDigest
 *    - Critical #6: Missing ProcessingStatusMapper object
 *    - Medium #5: Removed parseJsonList/toJsonList duplication
 * 
 * ⚠️ ARCHITECTURAL TODOs (Phase 3 - Requires DB Migration):
 *    - TODO: Normalize tags (create TagEntity, RecordTagCrossRef)
 *    - TODO: Remove UI formatting from Domain (if any exists)
 */

package com.docs.scanner.data.local.database.entity

import androidx.room.*
import com.docs.scanner.domain.core.*
import java.security.MessageDigest

// ══════════════════════════════════════════════════════════════════════════════
// PROCESSING STATUS MAPPER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Maps between ProcessingStatus sealed interface (Domain) and Int (Database).
 * 
 * ✅ CRITICAL FIX #6: This object was MISSING in original code.
 * Used throughout DataDaos.kt and DataRepositories.kt but never defined.
 * 
 * Domain v4.1.0 uses sealed interface instead of enum, so we map to stable ordinals.
 * These ordinals MUST NEVER CHANGE (database schema stability).
 * 
 * @since v7.0.0
 */
object ProcessingStatusMapper {
    // Stable ordinals (immutable for database compatibility)
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
    
    /**
     * Converts ProcessingStatus domain model to database integer.
     * @param status Domain model status
     * @return Database integer representation
     */
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
    
    /**
     * Converts database integer to ProcessingStatus domain model.
     * @param value Database integer value
     * @return Corresponding ProcessingStatus, defaults to Error if unknown
     */
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
        else -> ProcessingStatus.Error // Safe fallback for corrupted data
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
        Index(value = ["created_at"])
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
    
    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,
    
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * ✅ FIXED: Folder.id is NON-NULL in Domain v4.1.0
     */
    fun toDomain(recordCount: Int = 0): Folder {
        require(id > 0) { "Cannot convert unsaved entity (id=0) to Folder" }
        return Folder(
            id = FolderId(id),
            name = name,
            description = description,
            color = color,
            icon = icon,
            recordCount = recordCount,
            isPinned = isPinned,
            isArchived = isArchived,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    /**
     * ✅ NEW: Separate mapper for NewFolder
     */
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
            isPinned = folder.isPinned,
            isArchived = folder.isArchived,
            createdAt = folder.createdAt,
            updatedAt = folder.updatedAt
        )
        
        /**
         * ✅ NEW: Mapper from NewFolder
         */
        fun fromNewDomain(newFolder: NewFolder): FolderEntity = FolderEntity(
            id = 0, // Will be auto-generated
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

/** DTO для запросов с подсчётом записей */
data class FolderWithCount(
    val id: Long,
    val name: String,
    val description: String?,
    val color: Int?,
    val icon: String?,
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
        Index(value = ["created_at"])
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
    
    /**
     * ⚠️ ARCHITECTURAL DEBT: Tags stored as JSON string.
     * 
     * This is an anti-pattern (violates database normalization).
     * Should be in separate TagEntity table with Many-to-Many relation.
     * 
     * TODO Phase 3: Create TagEntity, RecordTagCrossRef tables.
     * Requires database migration from v17 to v18.
     * 
     * Current format: ["tag1", "tag2", "tag3"]
     */
    @ColumnInfo(name = "tags")
    val tags: String? = null,
    
    @ColumnInfo(name = "source_language", defaultValue = "auto")
    val sourceLanguage: String = "auto",
    
    @ColumnInfo(name = "target_language", defaultValue = "en")
    val targetLanguage: String = "en",
    
    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,
    
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * ✅ FIXED: Record.id is NON-NULL in Domain v4.1.0
     */
    fun toDomain(documentCount: Int = 0): Record {
        require(id > 0) { "Cannot convert unsaved entity (id=0) to Record" }
        return Record(
            id = RecordId(id),
            folderId = FolderId(folderId),
            name = name,
            description = description,
            tags = tags?.let { parseJsonList(it) } ?: emptyList(),
            documentCount = documentCount,
            sourceLanguage = Language.fromCode(sourceLanguage) ?: Language.AUTO,
            targetLanguage = Language.fromCode(targetLanguage) ?: Language.ENGLISH,
            isPinned = isPinned,
            isArchived = isArchived,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    /**
     * ✅ NEW: Mapper for NewRecord
     */
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
            isPinned = record.isPinned,
            isArchived = record.isArchived,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt
        )
        
        /**
         * ✅ NEW: Mapper from NewRecord
         */
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

/** DTO для запросов с подсчётом документов */
data class RecordWithCount(
    val id: Long,
    val folderId: Long,
    val name: String,
    val description: String?,
    val tags: String?,
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
    
    /**
     * ✅ FIXED: Changed to Int (mapped via ProcessingStatusMapper)
     * Previously was enum, now uses sealed interface mapping.
     */
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
    /**
     * ✅ FIXED: Document.id is NON-NULL + ProcessingStatus mapping
     */
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
    
    /**
     * ✅ NEW: Mapper for NewDocument
     */
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
        
        /**
         * ✅ NEW: Mapper from NewDocument
         */
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

/** DTO для поиска с именами папки и записи */
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
// DOCUMENT FTS ENTITY (Full-Text Search)
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
    /**
     * ✅ FIXED: Term.id is NON-NULL in Domain v4.1.0
     */
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
    
    /**
     * ✅ NEW: Mapper for NewTerm
     */
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
        
        /**
         * ✅ NEW: Mapper from NewTerm
         */
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
        Index(value = ["source_language", "target_language"])
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
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlDays: Int = 30): Boolean {
        val expiryTime = timestamp + (ttlDays * 24 * 60 * 60 * 1000L)
        return System.currentTimeMillis() > expiryTime
    }
    
    companion object {
        /**
         * Генерирует ключ кэша: SHA-256("text|srcLang|tgtLang")
         * 
         * ✅ CRITICAL FIX #1: Syntax error fixed.
         * Original had duplicate function signature causing compilation failure.
         * 
         * ✅ CRITICAL FIX #2: MessageDigest import added at file top.
         * 
         * Разные языковые пары для одного текста = разные ключи.
         * 
         * @param text Original text to translate
         * @param srcLang Source language code
         * @param tgtLang Target language code
         * @return SHA-256 hash as hex string (64 characters)
         */
        fun generateCacheKey(text: String, srcLang: String, tgtLang: String): String {
            val combined = "$text|$srcLang|$tgtLang"
            val bytes = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/** Статистика кэша */
data class CacheStatsResult(
    val totalEntries: Int,
    val totalOriginalSize: Long,
    val totalTranslatedSize: Long,
    val oldestEntry: Long?,
    val newestEntry: Long?
)

/** Статистика по языковым парам */
data class LanguagePairStat(
    val sourceLanguage: String,
    val targetLanguage: String,
    val count: Int
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
// JSON UTILITIES (CONSOLIDATED - NO DUPLICATION)
// ══════════════════════════════════════════════════════════════════════════════

/**
 * ✅ MEDIUM FIX #5: Consolidated JSON parsing utilities.
 * Original code had multiple duplicate implementations scattered throughout.
 * Now there's only ONE canonical version.
 * 
 * These functions handle the tags field in RecordEntity.
 * 
 * ⚠️ NOTE: This is a temporary solution. In Phase 3, tags should be normalized
 * into a separate TagEntity table with proper Many-to-Many relations.
 */

/**
 * Converts JSON array string to List<String>.
 * 
 * Handles formats:
 * - ["tag1", "tag2", "tag3"]
 * - ["tag1","tag2","tag3"] (no spaces)
 * - [] (empty array)
 * - null/blank strings
 * 
 * @param json JSON string representation of string array
 * @return List of tags, empty list if parsing fails or input is null/blank
 */
private fun parseJsonList(json: String): List<String> {
    if (json.isBlank() || json == "[]") return emptyList()
    
    return try {
        json.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        // Fail silently - return empty list on corrupted data
        emptyList()
    }
}

/**
 * Converts List<String> to JSON array string.
 * 
 * Output format: ["tag1", "tag2", "tag3"]
 * 
 * @param list List of tags
 * @return JSON string representation, "[]" if list is empty
 */
private fun toJsonList(list: List<String>): String {
    if (list.isEmpty()) return "[]"
    return list.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
}

// ══════════════════════════════════════════════════════════════════════════════
// ARCHITECTURAL TODOs FOR PHASE 3 (Requires Database Migration v17→v18)
// ══════════════════════════════════════════════════════════════════════════════

/**
 * TODO Phase 3: Normalize Tags (DB Migration Required)
 * 
 * Current anti-pattern: tags stored as JSON string in RecordEntity.
 * This violates database normalization and makes queries inefficient.
 * 
 * Proper solution:
 * 
 * 1. Create TagEntity:
 * ```kotlin
 * @Entity(
 *     tableName = "tags",
 *     indices = [Index(value = ["name"], unique = true)]
 * )
 * data class TagEntity(
 *     @PrimaryKey(autoGenerate = true) val id: Long = 0,
 *     @ColumnInfo(name = "name") val name: String,
 *     @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
 * )
 * ```
 * 
 * 2. Create junction table:
 * ```kotlin
 * @Entity(
 *     tableName = "record_tags",
 *     primaryKeys = ["record_id", "tag_id"],
 *     foreignKeys = [
 *         ForeignKey(entity = RecordEntity::class, ...),
 *         ForeignKey(entity = TagEntity::class, ...)
 *     ],
 *     indices = [Index("record_id"), Index("tag_id")]
 * )
 * data class RecordTagCrossRef(
 *     @ColumnInfo(name = "record_id") val recordId: Long,
 *     @ColumnInfo(name = "tag_id") val tagId: Long
 * )
 * ```
 * 
 * 3. Create relation data class:
 * ```kotlin
 * data class RecordWithTags(
 *     @Embedded val record: RecordEntity,
 *     @Relation(
 *         parentColumn = "id",
 *         entityColumn = "id",
 *         associateBy = Junction(
 *             RecordTagCrossRef::class,
 *             parentColumn = "record_id",
 *             entityColumn = "tag_id"
 *         )
 *     )
 *     val tags: List<TagEntity>
 * )
 * ```
 * 
 * 4. Migration v17→v18:
 * ```kotlin
 * val MIGRATION_17_18 = object : Migration(17, 18) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         // Create new tables
 *         db.execSQL("CREATE TABLE tags (...)")
 *         db.execSQL("CREATE TABLE record_tags (...)")
 *         
 *         // Migrate existing JSON data
 *         // 1. Extract all unique tags from records.tags JSON
 *         // 2. Insert into tags table
 *         // 3. Create record_tags relations
 *         // 4. Drop records.tags column
 *     }
 * }
 * ```
 * 
 * Benefits:
 * - Efficient tag search (indexed queries instead of JSON LIKE)
 * - Autocomplete support (SELECT DISTINCT name FROM tags)
 * - Tag usage statistics (COUNT records per tag)
 * - No parsing overhead in RecyclerView
 * 
 * Estimated effort: ~2 hours + migration testing
 * Risk: Medium (requires data migration, can't rollback easily)
 * Priority: Low (current solution works, optimize later)
 */

// ══════════════════════════════════════════════════════════════════════════════
// END OF FILE - SUMMARY OF FIXES APPLIED
// ══════════════════════════════════════════════════════════════════════════════

/**
 * ✅ ALL FIXES APPLIED TO DataEntities.kt:
 * 
 * 🔴 CRITICAL FIXES (3/3 completed):
 *    ✅ #1: Syntax error in TranslationCacheEntity.generateCacheKey() - FIXED
 *    ✅ #2: Missing import java.security.MessageDigest - ADDED
 *    ✅ #6: Missing ProcessingStatusMapper object - CREATED
 * 
 * 🟡 MEDIUM FIXES (1/1 completed):
 *    ✅ #5: Removed parseJsonList/toJsonList duplication - CONSOLIDATED
 * 
 * 🟠 ARCHITECTURAL IMPROVEMENTS (documented for Phase 3):
 *    📋 Tag normalization (TagEntity, RecordTagCrossRef) - TODO with migration
 *    📋 Remove UI formatting from Domain layer (if any exists) - TODO
 * 
 * NEXT FILES TO FIX:
 *    1. DataRepositories.kt (Critical #4, #5 + Serious #1-15)
 *    2. DatabaseModule.kt (Critical #3)
 *    3. build.gradle.kts root (Critical #7)
 * 
 * Current compilation status: 4/7 critical issues remain (other files)
 * This file is now: ✅ PRODUCTION READY
 */