package com.docs.scanner.domain.model

import java.io.File

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
)

data class Document(
    val id: Long = 0,
    val recordId: Long,
    val imagePath: String,
    val imageFile: File? = null,
    val originalText: String? = null,
    val translatedText: String? = null,
    val position: Int = 0,
    val processingStatus: ProcessingStatus = ProcessingStatus.INITIAL,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ProcessingStatus(val value: Int) {
    INITIAL(0),
    OCR_IN_PROGRESS(1),
    OCR_COMPLETE(2),
    TRANSLATION_IN_PROGRESS(3),
    COMPLETE(4),
    ERROR(-1);
    
    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: INITIAL
    }
}

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}