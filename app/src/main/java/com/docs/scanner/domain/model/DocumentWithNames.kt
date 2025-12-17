package com.docs.scanner.domain.model

import androidx.room.ColumnInfo

data class DocumentWithNames(
    val id: Long,
    val recordId: Long,
    val imagePath: String,
    val originalText: String?,
    val translatedText: String?,
    val position: Int,
    val processingStatus: Int,
    val createdAt: Long,
    @ColumnInfo(name = "recordName")
    val recordName: String,
    @ColumnInfo(name = "folderName")
    val folderName: String
)
