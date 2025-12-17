package com.docs.scanner.domain.model

data class DocumentWithNames(
    val id: Long,
    val recordId: Long,
    val imagePath: String,
    val originalText: String?,
    val translatedText: String?,
    val position: Int,
    val processingStatus: Int,
    val createdAt: Long,
    val recordName: String,
    val folderName: String
)
