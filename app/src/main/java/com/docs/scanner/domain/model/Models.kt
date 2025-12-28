package com.docs.scanner.domain.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * Domain models.
 * Session 6 + 11 fixes applied.
 */

data class Folder(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val recordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Record(
    val id: Long = 0,
    val folderId: Long,
    val name: String,
    val description: String? = null,
    val documentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
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
    // ❌ REMOVED: val imageFile: File? = null  (Android dependency in domain)
    val originalText: String? = null,
    val translatedText: String? = null,
    val position: Int = 0,
    val processingStatus: ProcessingStatus = ProcessingStatus.INITIAL,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Document processing status.
 * 
 * Session 6 enhancement: Added explicit failed states.
 */
enum class ProcessingStatus(val value: Int) {
    INITIAL(0),
    OCR_IN_PROGRESS(1),
    OCR_COMPLETE(2),
    OCR_FAILED(3),               // ✅ ADDED
    TRANSLATION_IN_PROGRESS(4),
    TRANSLATION_FAILED(5),        // ✅ ADDED
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

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}