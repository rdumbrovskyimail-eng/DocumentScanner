/*
 * DocumentScanner - Domain Core
 * Version: 4.2.0 - Production Ready 2026 Enhanced
 * 
 * Fixed issues:
 * - 🟠 Серьёзная #12: QUICK_SCANS_ID changed from 1L to -1L (no conflict with autoGenerate)
 * 
 * Improvements v4.2.0:
 * - QUICK_SCANS_ID now uses negative ID (-1L) to avoid conflicts
 * - Separated NewEntity / Entity (no nullable IDs) ✅
 * - Improved ProcessingStatus (sealed interface) ✅
 * - Better error hierarchy (NotFoundError) ✅
 * - FakeTimeProvider for testing ✅
 * - Type-safe Progress types (UploadProgress/DownloadProgress) ✅
 * - formatBytes() helper extension ✅
 */

package com.docs.scanner.domain.core

import com.docs.scanner.domain.core.OcrSource
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ══════════════════════════════════════════════════════════════════════════════
// VALUE OBJECTS - Типобезопасные идентификаторы
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Folder identifier.
 * 
 * FIXED: 🟠 Серьёзная #12 - QUICK_SCANS_ID changed from 1L to -1L
 * 
 * Why -1L?
 * - Room's autoGenerate starts from 1 and increments
 * - Using 1L could conflict with first user-created folder
 * - Negative IDs are reserved for system folders (no conflict)
 * - Alternative: Could use Long.MAX_VALUE, but -1L is clearer
 */
@JvmInline @Serializable
value class FolderId(val value: Long) {
    init { require(value != 0L) { "FolderId cannot be 0" } }
    val isQuickScans: Boolean get() = value == QUICK_SCANS_ID
    
    companion object {
        /**
         * Special system folder ID for quick scans.
         * Uses -1L to avoid conflict with autoGenerate (starts at 1L).
         */
        const val QUICK_SCANS_ID = -1L
        
        /**
         * Default folder ID (first user folder).
         * This will be created by Room's autoGenerate.
         */
        const val DEFAULT_ID = 1L
        
        val QUICK_SCANS = FolderId(QUICK_SCANS_ID)
        val DEFAULT = FolderId(DEFAULT_ID)
    }
}

@JvmInline @Serializable
value class RecordId(val value: Long) {
    init { require(value > 0) { "RecordId must be positive: $value" } }
}

@JvmInline @Serializable
value class DocumentId(val value: Long) {
    init { require(value > 0) { "DocumentId must be positive: $value" } }
}

@JvmInline @Serializable
value class TermId(val value: Long) {
    init { require(value > 0) { "TermId must be positive: $value" } }
}

// ══════════════════════════════════════════════════════════════════════════════
// VALIDATED VALUE OBJECTS
// ══════════════════════════════════════════════════════════════════════════════

@JvmInline @Serializable
value class FolderName private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 100
        fun create(name: String): DomainResult<FolderName> {
            val trimmed = name.trim()
            return when {
                trimmed.isBlank() -> DomainResult.failure(DomainError.ValidationFailed(ValidationError.BlankName))
                trimmed.length > MAX_LENGTH -> DomainResult.failure(DomainError.ValidationFailed(ValidationError.NameTooLong(trimmed.length, MAX_LENGTH)))
                else -> DomainResult.success(FolderName(trimmed))
            }
        }
    }
}

@JvmInline @Serializable
value class RecordName private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 100
        fun create(name: String): DomainResult<RecordName> {
            val trimmed = name.trim()
            return when {
                trimmed.isBlank() -> DomainResult.failure(DomainError.ValidationFailed(ValidationError.BlankName))
                trimmed.length > MAX_LENGTH -> DomainResult.failure(DomainError.ValidationFailed(ValidationError.NameTooLong(trimmed.length, MAX_LENGTH)))
                else -> DomainResult.success(RecordName(trimmed))
            }
        }
    }
}

@JvmInline @Serializable
value class Tag private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 30
        private val VALID_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
        fun create(tag: String): DomainResult<Tag> {
            val trimmed = tag.trim().lowercase()
            return when {
                trimmed.isBlank() -> DomainResult.failure(DomainError.ValidationFailed(ValidationError.BlankTag))
                trimmed.length > MAX_LENGTH -> DomainResult.failure(DomainError.ValidationFailed(ValidationError.TagTooLong(trimmed.length, MAX_LENGTH)))
                !VALID_PATTERN.matches(trimmed) -> DomainResult.failure(DomainError.ValidationFailed(ValidationError.InvalidTagFormat))
                else -> DomainResult.success(Tag(trimmed))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DOMAIN RESULT - Functional Error Handling
// ══════════════════════════════════════════════════════════════════════════════

sealed class DomainResult<T> {
    data class Success<T>(val data: T) : DomainResult<T>()
    data class Failure<T>(val error: DomainError) : DomainResult<T>()
    
    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): DomainError? = (this as? Failure)?.error
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error.toException()
    }
    
    inline fun getOrElse(block: (DomainError) -> T): T = when (this) {
        is Success -> data
        is Failure -> block(error)
    }
    
    inline fun <R> map(transform: (T) -> R): DomainResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> Failure(error)
    }
    
    inline fun <R> flatMap(transform: (T) -> DomainResult<R>): DomainResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> Failure(error)
    }
    
    inline fun onSuccess(action: (T) -> Unit): DomainResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onFailure(action: (DomainError) -> Unit): DomainResult<T> {
        if (this is Failure) action(error)
        return this
    }
    
    companion object {
        fun <T> success(data: T): DomainResult<T> = Success(data)
        fun <T> failure(error: DomainError): DomainResult<T> = Failure(error)
        
        inline fun <T> catching(block: () -> T): DomainResult<T> = try {
            Success(block())
        } catch (e: DomainException) {
            Failure(e.error)
        } catch (e: Exception) {
            Failure(DomainError.Unknown(e))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DOMAIN ERRORS - Enhanced Type-Safe Hierarchy
// ══════════════════════════════════════════════════════════════════════════════

sealed class DomainError {
    abstract val message: String
    
    // Validation
    data class ValidationFailed(val error: ValidationError) : DomainError() {
        override val message: String = error.message
    }
    
    // Not Found - Type-safe
    sealed class NotFoundError : DomainError() {
        data class Folder(val id: FolderId) : NotFoundError() {
            override val message = "Folder not found: ${id.value}"
        }
        data class Record(val id: RecordId) : NotFoundError() {
            override val message = "Record not found: ${id.value}"
        }
        data class Document(val id: DocumentId) : NotFoundError() {
            override val message = "Document not found: ${id.value}"
        }
        data class Term(val id: TermId) : NotFoundError() {
            override val message = "Term not found: ${id.value}"
        }
    }
    
    // Already Exists
    data class AlreadyExists(val name: String) : DomainError() {
        override val message: String = "Already exists: $name"
    }
    
    // Business Rules - Folder
    data class CannotDeleteSystemFolder(val id: FolderId) : DomainError() {
        override val message: String = "Cannot delete system folder: ${id.value}"
    }
    
    data class CannotDeleteNonEmptyFolder(val id: FolderId, val count: Int) : DomainError() {
        override val message: String = "Folder contains $count records. Delete contents first or use deleteContents=true"
    }
    
    data class CannotModifyQuickScansFolder(val operation: String) : DomainError() {
        override val message: String = "Cannot $operation Quick Scans folder"
    }
    
    // Business Rules - Term
    data class CannotModifyCompletedTerm(val id: TermId) : DomainError() {
        override val message: String = "Cannot modify completed term: ${id.value}"
    }
    
    data class CannotModifyCancelledTerm(val id: TermId) : DomainError() {
        override val message: String = "Cannot modify cancelled term: ${id.value}"
    }
    
    // Processing
    data class OcrFailed(val id: DocumentId?, val cause: Throwable?) : DomainError() {
        override val message: String = "OCR failed${id?.let { " for document ${it.value}" } ?: ""}: ${cause?.message ?: "Unknown error"}"
    }
    
    data class TranslationFailed(val from: Language, val to: Language, val cause: String) : DomainError() {
        override val message: String = "Translation $from→$to failed: $cause"
    }
    
    data class UnsupportedLanguagePair(val from: Language, val to: Language) : DomainError() {
        override val message: String = "Unsupported language pair: $from → $to"
    }
    
    // Infrastructure
    data object MissingApiKey : DomainError() {
        override val message: String = "No API key configured"
    }

    data class StorageFailed(val cause: Throwable?) : DomainError() {
        override val message: String = "Storage error: ${cause?.message ?: "Unknown"}"
    }
    
    data class NetworkFailed(val cause: Throwable?) : DomainError() {
        override val message: String = "Network error: ${cause?.message ?: "Unknown"}"
    }
    
    data class FileTooLarge(val size: Long, val max: Long) : DomainError() {
        override val message: String = "File size ${size.formatBytes()} exceeds limit ${max.formatBytes()}"
    }
    
    data class FileNotFound(val path: String) : DomainError() {
        override val message: String = "File not found: $path"
    }
    
    // Generic
    data class Unknown(val cause: Throwable?) : DomainError() {
        override val message: String = cause?.message ?: "Unknown error"
    }
    
    fun toException(): DomainException = DomainException(this)
}

sealed class ValidationError(val message: String) {
    /**
     * Invalid input with detailed context
     */
    data class InvalidInput(
        val field: String,
        val value: String,
        val reason: String
    ) : ValidationError("Invalid $field: $reason (value: $value)")
    
    data object BlankName : ValidationError("Name cannot be blank")
    data object BlankTag : ValidationError("Tag cannot be blank")
    data object InvalidTagFormat : ValidationError("Tag can only contain a-z, 0-9, _, -")
    
    data class NameTooLong(val len: Int, val max: Int) : ValidationError("Name too long: $len > $max")
    data class TagTooLong(val len: Int, val max: Int) : ValidationError("Tag too long: $len > $max")
    data class TooManyTags(val count: Int, val max: Int) : ValidationError("Too many tags: $count > $max")
    
    data object DueDateInPast : ValidationError("Due date must be in future")
    
    data class EmptyField(val fieldName: String) : ValidationError("Field '$fieldName' cannot be empty")
    
    data object Unknown : ValidationError("Unknown validation error")
}

class DomainException(val error: DomainError) : Exception(error.message)

// Extension for formatting bytes
fun Long.formatBytes(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "${this / 1024} KB"
    this < 1024 * 1024 * 1024 -> String.format("%.1f MB", this / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", this / (1024.0 * 1024.0 * 1024.0))
}

// ══════════════════════════════════════════════════════════════════════════════
// LANGUAGE SYSTEM
// ══════════════════════════════════════════════════════════════════════════════

@Serializable
enum class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val supportsOcr: Boolean = true,
    val supportsTranslation: Boolean = true
) {
    // Latin
    ENGLISH("en", "English", "English"),
    SPANISH("es", "Spanish", "Español"),
    FRENCH("fr", "French", "Français"),
    GERMAN("de", "German", "Deutsch"),
    ITALIAN("it", "Italian", "Italiano"),
    PORTUGUESE("pt", "Portuguese", "Português"),
    DUTCH("nl", "Dutch", "Nederlands"),
    POLISH("pl", "Polish", "Polski"),
    ROMANIAN("ro", "Romanian", "Română"),
    CZECH("cs", "Czech", "Čeština"),
    HUNGARIAN("hu", "Hungarian", "Magyar"),
    SWEDISH("sv", "Swedish", "Svenska"),
    DANISH("da", "Danish", "Dansk"),
    NORWEGIAN("no", "Norwegian", "Norsk"),
    FINNISH("fi", "Finnish", "Suomi"),
    TURKISH("tr", "Turkish", "Türkçe"),
    VIETNAMESE("vi", "Vietnamese", "Tiếng Việt"),
    INDONESIAN("id", "Indonesian", "Bahasa Indonesia"),
    MALAY("ms", "Malay", "Bahasa Melayu"),
    TAGALOG("tl", "Tagalog", "Tagalog"),
    
    // Cyrillic
    RUSSIAN("ru", "Russian", "Русский"),
    UKRAINIAN("uk", "Ukrainian", "Українська"),
    BULGARIAN("bg", "Bulgarian", "Български"),
    SERBIAN("sr", "Serbian", "Српски"),
    CROATIAN("hr", "Croatian", "Hrvatski"),
    SLOVAK("sk", "Slovak", "Slovenčina"),
    SLOVENIAN("sl", "Slovenian", "Slovenščina"),
    
    // CJK
    CHINESE_SIMPLIFIED("zh", "Chinese (Simplified)", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "Chinese (Traditional)", "繁體中文"),
    JAPANESE("ja", "Japanese", "日本語"),
    KOREAN("ko", "Korean", "한국어"),
    
    // Devanagari
    HINDI("hi", "Hindi", "हिन्दी"),
    MARATHI("mr", "Marathi", "मराठी"),
    NEPALI("ne", "Nepali", "नेपाली"),
    SANSKRIT("sa", "Sanskrit", "संस्कृत"),
    
    // Other Scripts
    ARABIC("ar", "Arabic", "العربية", supportsOcr = false),
    HEBREW("he", "Hebrew", "עברית", supportsOcr = false),
    THAI("th", "Thai", "ไทย"),
    GREEK("el", "Greek", "Ελληνικά"),
    BENGALI("bn", "Bengali", "বাংলা", supportsOcr = false),
    TAMIL("ta", "Tamil", "தமிழ்", supportsOcr = false),
    
    // Special
    AUTO("auto", "Auto-detect", "Auto", supportsTranslation = false);

    val isAutoDetect: Boolean get() = this == AUTO
    
    companion object {
        fun fromCode(code: String): Language? = entries.find { it.code.equals(code, ignoreCase = true) }
        val ocrSupported: List<Language> get() = entries.filter { it.supportsOcr && !it.isAutoDetect }
        val translationSupported: List<Language> get() = entries.filter { it.supportsTranslation }
        val ocrSourceOptions: List<Language> get() = listOf(AUTO) + ocrSupported
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PROCESSING STATUS - Sealed Interface (Type-Safe)
// ══════════════════════════════════════════════════════════════════════════════

@Serializable
sealed interface ProcessingStatus {
    @Serializable data object Pending : ProcessingStatus
    @Serializable data object Queued : ProcessingStatus
    
    sealed interface Ocr : ProcessingStatus {
        @Serializable data object InProgress : Ocr
        @Serializable data object Complete : Ocr
        @Serializable data object Failed : Ocr
    }
    
    sealed interface Translation : ProcessingStatus {
        @Serializable data object InProgress : Translation
        @Serializable data object Complete : Translation
        @Serializable data object Failed : Translation
    }
    
    @Serializable data object Complete : ProcessingStatus
    @Serializable data object Cancelled : ProcessingStatus
    @Serializable data object Error : ProcessingStatus
    
    val isInProgress: Boolean get() = this is Ocr.InProgress || this is Translation.InProgress || this is Queued
    val isFailed: Boolean get() = this is Ocr.Failed || this is Translation.Failed || this is Error
    val isComplete: Boolean get() = this is Complete
    val canRetry: Boolean get() = isFailed || this is Cancelled
}

// ══════════════════════════════════════════════════════════════════════════════
// DOMAIN MODELS - Separated New/Existing Entities
// ══════════════════════════════════════════════════════════════════════════════

/**
 * New folder (before persistence - no ID)
 */
@Serializable
data class NewFolder(
    val name: String,
    val description: String? = null,
    val color: Int? = null,
    val icon: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Persisted folder (with ID)
 */
@Serializable
data class Folder(
    val id: FolderId,
    val name: String,
    val description: String? = null,
    val color: Int? = null,
    val icon: String? = null,
    val recordCount: Int = 0,
    val position: Int = 0,  // ✅ ADDED for drag & drop
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isQuickScans: Boolean get() = id.isQuickScans
    val isEmpty: Boolean get() = recordCount == 0

    companion object {
        fun quickScans(name: String, timestamp: Long) = Folder(
            id = FolderId.QUICK_SCANS,
            name = name,
            isPinned = true,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}

@Serializable
data class NewRecord(
    val folderId: FolderId,
    val name: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ENGLISH,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class Record(
    val id: RecordId,
    val folderId: FolderId,
    val name: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val documentCount: Int = 0,
    val position: Int = 0,  // ✅ ADDED for drag & drop
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ENGLISH,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isQuickScan: Boolean get() = folderId.isQuickScans
    val isEmpty: Boolean get() = documentCount == 0
}

@Serializable
data class NewDocument(
    val recordId: RecordId,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ENGLISH,
    val position: Int = 0,
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class Document(
    val id: DocumentId,
    val recordId: RecordId,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val originalText: String? = null,
    val translatedText: String? = null,
    val detectedLanguage: Language? = null,
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ENGLISH,
    val position: Int = 0,
    val processingStatus: ProcessingStatus = ProcessingStatus.Pending,
    val ocrConfidence: Float? = null,
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    // Denormalized for UI
    val recordName: String? = null,
    val folderName: String? = null
) {
    val hasOcrText: Boolean get() = !originalText.isNullOrBlank()
    val hasTranslation: Boolean get() = !translatedText.isNullOrBlank()
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 1f
    val fullPath: String? get() = listOfNotNull(folderName, recordName).takeIf { it.isNotEmpty() }?.joinToString(" > ")
}

// ══════════════════════════════════════════════════════════════════════════════
// TERM MODEL
// ══════════════════════════════════════════════════════════════════════════════

@Serializable
enum class TermPriority(val level: Int) { 
    LOW(0), NORMAL(1), HIGH(2), CRITICAL(3) 
}

@Serializable
enum class TermStatus { 
    PENDING, UPCOMING, DUE_TODAY, OVERDUE, COMPLETED, CANCELLED 
}

@Serializable
data class NewTerm(
    val title: String,
    val description: String? = null,
    val dueDate: Long,
    val reminderMinutesBefore: Int = 60,
    val priority: TermPriority = TermPriority.NORMAL,
    val documentId: DocumentId? = null,
    val folderId: FolderId? = null,
    val color: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class Term(
    val id: TermId,
    val title: String,
    val description: String? = null,
    val dueDate: Long,
    val reminderMinutesBefore: Int = 60,
    val priority: TermPriority = TermPriority.NORMAL,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val completedAt: Long? = null,
    val documentId: DocumentId? = null,
    val folderId: FolderId? = null,
    val color: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun computeStatus(now: Long): TermStatus = when {
        isCancelled -> TermStatus.CANCELLED
        isCompleted -> TermStatus.COMPLETED
        dueDate < now -> TermStatus.OVERDUE
        isDueToday(now) -> TermStatus.DUE_TODAY
        isInReminderWindow(now) -> TermStatus.UPCOMING
        else -> TermStatus.PENDING
    }
    
    fun isDueToday(now: Long): Boolean {
        val nowDate = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val dueDateTime = Instant.ofEpochMilli(dueDate).atZone(ZoneId.systemDefault()).toLocalDate()
        return nowDate == dueDateTime
    }
    
    fun isInReminderWindow(now: Long): Boolean {
        if (reminderMinutesBefore <= 0) return false
        val reminderTime = dueDate - (reminderMinutesBefore * 60 * 1000L)
        return now >= reminderTime && now < dueDate
    }
    
    val reminderTime: Long get() = dueDate - (reminderMinutesBefore * 60 * 1000L)
    
    companion object {
        const val TITLE_MAX_LENGTH = 200
        const val MAX_REMINDER_MINUTES = 43200 // 30 days
        
        fun create(
            title: String,
            dueDate: Long,
            description: String? = null,
            reminderMinutesBefore: Int = 60,
            priority: TermPriority = TermPriority.NORMAL,
            documentId: DocumentId? = null,
            folderId: FolderId? = null,
            color: Int? = null,
            timestamp: Long
        ) = NewTerm(
            title = title,
            description = description,
            dueDate = dueDate,
            reminderMinutesBefore = reminderMinutesBefore,
            priority = priority,
            documentId = documentId,
            folderId = folderId,
            color = color,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SUPPORTING TYPES
// ══════════════════════════════════════════════════════════════════════════════

data class OcrResult(
    val text: String,
    val detectedLanguage: Language?,
    val confidence: Float?,
    val processingTimeMs: Long,
    val source: OcrSource = OcrSource.UNKNOWN
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val fromCache: Boolean,
    val processingTimeMs: Long
)

data class UploadProgress(
    val uploaded: Long,
    val total: Long
) {
    val percent: Int get() = if (total > 0) ((uploaded * 100) / total).toInt() else 0
    val isComplete: Boolean get() = uploaded >= total
    val remaining: Long get() = (total - uploaded).coerceAtLeast(0)
    
    fun formatProgress(): String = "${uploaded.formatBytes()} / ${total.formatBytes()} ($percent%)"
}

data class DownloadProgress(
    val downloaded: Long,
    val total: Long
) {
    val percent: Int get() = if (total > 0) ((downloaded * 100) / total).toInt() else 0
    val isComplete: Boolean get() = downloaded >= total
    
    fun formatProgress(): String = "${downloaded.formatBytes()} / ${total.formatBytes()} ($percent%)"
}

data class SearchHistoryItem(
    val id: Long,
    val query: String,
    val resultCount: Int,
    val timestamp: Long
)

data class BackupInfo(
    val id: String,
    val name: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val folderCount: Int,
    val recordCount: Int,
    val documentCount: Int
) {
    fun formatSize(): String = sizeBytes.formatBytes()
}

/**
 * Translation cache statistics.
 * 
 * Used by TranslationCacheManager/GeminiTranslationService.
 */
data class TranslationCacheStats(
    val totalEntries: Int,
    val hitRate: Float,
    val totalSizeBytes: Long,
    val oldestEntryTimestamp: Long?,
    val newestEntryTimestamp: Long?
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ImageQuality(val percent: Int) { LOW(60), MEDIUM(80), HIGH(95), ORIGINAL(100) }

// ══════════════════════════════════════════════════════════════════════════════
// TIME PROVIDER - with FakeTimeProvider for Testing
// ══════════════════════════════════════════════════════════════════════════════

interface TimeProvider {
    fun currentMillis(): Long
    fun currentInstant(): Instant = Instant.ofEpochMilli(currentMillis())
    
    companion object {
        val SYSTEM: TimeProvider = SystemTimeProvider()
    }
}

/**
 * Production time provider
 */
class SystemTimeProvider : TimeProvider {
    override fun currentMillis(): Long = System.currentTimeMillis()
}

/**
 * Fake time provider for testing
 */
class FakeTimeProvider(private var time: Long = 0L) : TimeProvider {
    override fun currentMillis(): Long = time
    
    fun setTime(millis: Long) {
        time = millis
    }
    
    fun advance(millis: Long) {
        time += millis
    }
    
    fun advanceSeconds(seconds: Int) {
        advance(seconds * 1000L)
    }
    
    fun advanceMinutes(minutes: Int) {
        advance(minutes * 60 * 1000L)
    }
    
    fun advanceHours(hours: Int) {
        advance(hours * 60 * 60 * 1000L)
    }
    
    fun advanceDays(days: Int) {
        advance(days * 24 * 60 * 60 * 1000L)
    }
    
    companion object {
        fun fixedAt(millis: Long): FakeTimeProvider = FakeTimeProvider(millis)
        fun fixedNow(): FakeTimeProvider = FakeTimeProvider(System.currentTimeMillis())
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ══════════════════════════════════════════════════════════════════════════════

object DomainConstants {
    const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024L // 10MB
    const val MAX_OCR_TEXT_LENGTH = 100_000
    const val MAX_TAGS_PER_RECORD = 10
    const val MIN_SEARCH_QUERY_LENGTH = 2
    const val MAX_BATCH_SIZE = 50
    const val DEFAULT_BATCH_CONCURRENCY = 3
}
