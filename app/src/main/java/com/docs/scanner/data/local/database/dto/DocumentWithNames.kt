package com.docs.scanner.data.local.database.dto

import androidx.room.ColumnInfo

/**
 * DTO для search results с данными из joined таблиц.
 * 
 * ⚠️ Session 6 Fix: Moved from domain/model to data/local/database/dto
 * Room annotations (@ColumnInfo) belong in data layer, not domain!
 */
data class DocumentWithNames(
    val id: Long,
    val recordId: Long,
    val imagePath: String,
    val originalText: String?,
    val translatedText: String?,
    val position: Int,
    val processingStatus: Int,
    val createdAt: Long,
    
    // ✅ JOIN результаты
    @ColumnInfo(name = "recordName") 
    val recordName: String,
    
    @ColumnInfo(name = "folderName") 
    val folderName: String
)