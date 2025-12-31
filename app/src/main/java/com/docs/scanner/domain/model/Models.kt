package com.docs.scanner.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Domain models for Clean Architecture.
 * 
 * These models are used in:
 * - Use Cases (domain layer)
 * - ViewModels (presentation layer)
 * 
 * Data layer uses Entity classes which are mapped to these models.
 */

// ══════════════════════════════════════════════════════════════════════════════
// FOLDER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Domain model for Folder.
 * 
 * Represents a container for records.
 */
data class Folder(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val recordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Folder name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Folder name too long (max $MAX_NAME_LENGTH characters)" 
        }
        require(recordCount >= 0) { "Record count cannot be negative" }
    }
    
    fun isValid(): Boolean = name.isNotBlank() && name.length <= MAX_NAME_LENGTH
    
    companion object {
        const val MAX_NAME_LENGTH = 100
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RECORD
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Domain model for Record.
 * 
 * Represents a container for documents within a folder.
 */
data class Record(
    val id: Long = 0,
    val folderId: Long,
    val name: String,
    val description: String? = null,
    val documentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        // FIX E1: Allow QUICK_SCANS_FOLDER_ID (-1) for quick scans
        require(folderId != 0L) { "Folder ID cannot be zero" }
        require(folderId > 0 || folderId == FolderConstants.QUICK_SCANS_FOLDER_ID) {
            "Invalid folder ID: $folderId"
        }
        require(name.isNotBlank()) { "Record name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Record name too long (max $MAX_NAME_LENGTH characters)" 
        }
        require(documentCount >= 0) { "Document count cannot be negative" }
    }
    
    companion object {
        const val MAX_NAME_LENGTH = 100
        
        /**
         * Factory method for Quick Scan records.
         * Uses thread-safe DateTimeFormatter instead of SimpleDateFormat.
         */
        fun createQuickScanRecord(folderId: Long = FolderConstants.QUICK_SCANS_FOLDER_ID): Record {
            val now = Instant.now()
            val formatter = DateTimeFormatter
                .ofPattern(RecordConstants.QUICK_SCAN_DATE_FORMAT)
                .withZone(ZoneId.systemDefault())
            val dateStr = formatter.format(now)
            
            return Record(
                folderId = folderId,
                name = "Scan $dateStr",
                description = "Quick scan at $dateStr",
                createdAt = now.toEpochMilli()
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DOCUMENT
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Domain model for Document.
 * 
 * Represents a scanned document image with OCR text and translation.
 */
data class Document(
    val id: Long = 0,
    val recordId: Long,
    val imagePath: String,
    val originalText: String? = null,
    val translatedText: String? = null,
    val position: Int = 0,
    val processingStatus: ProcessingStatus = ProcessingStatus.INITIAL,
    val createdAt: Long = System.currentTimeMillis(),
    val recordName: String? = null,
    val folderName: String? = null
) {
    init {
        require(recordId > 0) { "Invalid record ID: $recordId" }
        require(imagePath.isNotBlank()) { "Image path cannot be blank" }
        require(position >= 0) { "Position cannot be negative" }
    }
    
    /**
     * Check if document has OCR text.
     */
    val hasOcrText: Boolean
        get() = !originalText.isNullOrBlank()
    
    /**
     * Check if document has translation.
     */
    val hasTranslation: Boolean
        get() = !translatedText.isNullOrBlank()
    
    /**
     * Check if document processing is complete.
     */
    val isProcessingComplete: Boolean
        get() = processingStatus == ProcessingStatus.COMPLETE
    
    /**
     * Check if document has an error.
     */
    val hasError: Boolean
        get() = processingStatus.isError
}

// ══════════════════════════════════════════════════════════════════════════════
// DOCUMENT WITH NAMES (for search results)
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Document with parent folder and record names.
 * Used for displaying search results with full path.
 */
data class DocumentWithNames(
    val id: Long,
    val recordId: Long,
    val imagePath: String,
    val originalText: String?,
    val translatedText: String?,
    val position: Int,
    val processingStatus: ProcessingStatus,
    val createdAt: Long,
    val recordName: String,
    val folderName: String
) {
    /**
     * Get full path string.
     * Example: "Work Documents > Invoice 2024"
     */
    fun getFullPath(): String = "$folderName > $recordName"
    
    /**
     * Convert to regular Document.
     */
    fun toDocument(): Document = Document(
        id = id,
        recordId = recordId,
        imagePath = imagePath,
        originalText = originalText,
        translatedText = translatedText,
        position = position,
        processingStatus = processingStatus,
        createdAt = createdAt,
        recordName = recordName,
        folderName = folderName
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// PROCESSING STATUS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Document processing status.
 * FIX E4: Added CANCELLED state.
 */
enum class ProcessingStatus(val value: Int) {
    INITIAL(0),
    OCR_IN_PROGRESS(1),
    OCR_COMPLETE(2),
    OCR_FAILED(3),
    TRANSLATION_IN_PROGRESS(4),
    TRANSLATION_FAILED(5),
    COMPLETE(6),
    CANCELLED(7),
    ERROR(-1);
    
    val isError: Boolean
        get() = this == ERROR || this == OCR_FAILED || this == TRANSLATION_FAILED
    
    val isInProgress: Boolean
        get() = this == OCR_IN_PROGRESS || this == TRANSLATION_IN_PROGRESS
    
    val isComplete: Boolean
        get() = this == COMPLETE
    
    val isCancelled: Boolean
        get() = this == CANCELLED
    
    companion object {
        fun fromInt(value: Int): ProcessingStatus = 
            entries.find { it.value == value } ?: INITIAL
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RESULT WRAPPER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Result wrapper for repository operations.
 * 
 * FIX E9: Renamed to avoid conflict with kotlin.Result
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): Exception? = (this as? Error)?.exception
    fun messageOrNull(): String? = (this as? Error)?.let { it.message ?: it.exception.message }
    
    /**
     * Map success value to another type.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }
    
    /**
     * Execute action on success.
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Execute action on error.
     */
    inline fun onError(action: (Exception) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }
}