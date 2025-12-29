package com.docs.scanner.domain.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * Domain models.
 * Session 6 + 11 + Repository fixes applied.
 */

data class Folder(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val recordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // ✅ Validation (Session 6 Problem #6)
    init {
        require(name.isNotBlank()) { "Folder name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Folder name too long (max $MAX_NAME_LENGTH characters)" 
        }
        require(recordCount >= 0) { "Record count cannot be negative" }
    }
    
    fun isValid(): Boolean {
        return name.isNotBlank() && name.length <= MAX_NAME_LENGTH
    }
    
    companion object {
        const val MAX_NAME_LENGTH = 100
    }
}

data class Record(
    val id: Long = 0,
    val folderId: Long,
    val name: String,
    val description: String? = null,
    val documentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // ✅ Validation (Session 6 Problem #6)
    init {
        require(folderId > 0) { "Invalid folder ID: $folderId" }
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
         * 
         * Creates auto-generated name: "Scan 2024-12-28 14:30"
         * 
         * @param folderId Folder ID (typically Quick Scans folder)
         * @return New Record instance
         */
        fun createQuickScanRecord(folderId: Long): Record {
            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat(
                RecordConstants.QUICK_SCAN_DATE_FORMAT,
                Locale.getDefault()
            ).format(Date(timestamp))
            
            return Record(
                folderId = folderId,
                name = "Scan $dateStr",
                description = "Quick scan at $dateStr",
                createdAt = timestamp
            )
        }
    }
}

data class Document(
    val id: Long = 0,
    val recordId: Long,
    val imagePath: String,
    val originalText: String? = null,
    val translatedText: String? = null,
    val position: Int = 0,
    val processingStatus: ProcessingStatus = ProcessingStatus.INITIAL,
    val createdAt: Long = System.currentTimeMillis(),
    
    // ✅ For search results (Session 6 Problem #1)
    val recordName: String? = null,
    val folderName: String? = null
) {
    // ✅ Validation (Session 6 Problem #6)
    init {
        require(recordId > 0) { "Invalid record ID: $recordId" }
        require(imagePath.isNotBlank()) { "Image path cannot be blank" }
        require(position >= 0) { "Position cannot be negative" }
    }
}

/**
 * Document with parent names for search results.
 * 
 * ✅ NEW: Added for FTS5 search results
 * Used when displaying search results to show full path:
 * "Folder Name > Record Name > Document"
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
    fun getFullPath(): String {
        return "$folderName > $recordName"
    }
    
    /**
     * Convert to regular Document (without names).
     */
    fun toDocument(): Document {
        return Document(
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
}

/**
 * Document processing status.
 * 
 * Session 6 enhancement: Added explicit failed states.
 */
enum class ProcessingStatus(val value: Int) {
    INITIAL(0),
    OCR_IN_PROGRESS(1),
    OCR_COMPLETE(2),
    OCR_FAILED(3),
    TRANSLATION_IN_PROGRESS(4),
    TRANSLATION_FAILED(5),
    COMPLETE(6),
    ERROR(-1);
    
    val isError: Boolean
        get() = value < 0 || this == OCR_FAILED || this == TRANSLATION_FAILED
    
    val isInProgress: Boolean
        get() = this == OCR_IN_PROGRESS || this == TRANSLATION_IN_PROGRESS
    
    val isComplete: Boolean
        get() = this == COMPLETE
    
    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: INITIAL
    }
}

/**
 * Result wrapper for repository operations.
 * 
 * Replaces Kotlin's Result type to avoid conflicts and provide better control.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
    
    /**
     * Check if result is successful.
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * Check if result is error.
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Check if result is loading.
     */
    val isLoading: Boolean
        get() = this is Loading
    
    /**
     * Get data or null.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * Get error or null.
     */
    fun errorOrNull(): Exception? = when (this) {
        is Error -> exception
        else -> null
    }
}