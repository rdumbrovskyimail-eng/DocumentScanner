package com.docs.scanner.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.docs.scanner.domain.model.Record

/**
 * Room entity for Record.
 * 
 * ## Relationships
 * - Belongs to [FolderEntity] via `folderId`
 * - Has many [DocumentEntity] via foreign key
 * - Cascade delete: when folder deleted, all records deleted
 * 
 * ## Indices
 * - `folderId`: For efficient lookup by folder
 * - `name`: For search
 * - `createdAt`: For sorting
 */
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["name"]),
        Index(value = ["createdAt"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "folderId")
    val folderId: Long,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert to domain model.
     * Note: documentCount must be provided externally (via query or separate count).
     */
    fun toDomain(documentCount: Int = 0): Record = Record(
        id = id,
        folderId = folderId,
        name = name,
        description = description,
        documentCount = documentCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        /**
         * Create entity from domain model.
         */
        fun fromDomain(record: Record): RecordEntity = RecordEntity(
            id = record.id,
            folderId = record.folderId,
            name = record.name,
            description = record.description,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt
        )
    }
}