package com.docs.scanner.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.docs.scanner.domain.model.Folder

/**
 * Room entity for Folder.
 * 
 * ## Relationships
 * - Has many [RecordEntity] via foreign key
 * 
 * ## Indices
 * - `name`: For search and uniqueness checks
 * - `createdAt`: For sorting
 */
@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["name"]),
        Index(value = ["createdAt"])
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
    
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert to domain model.
     * Note: recordCount must be provided externally (via JOIN query).
     */
    fun toDomain(recordCount: Int = 0): Folder = Folder(
        id = id,
        name = name,
        description = description,
        recordCount = recordCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        /**
         * Create entity from domain model.
         */
        fun fromDomain(folder: Folder): FolderEntity = FolderEntity(
            id = folder.id,
            name = folder.name,
            description = folder.description,
            createdAt = folder.createdAt,
            updatedAt = folder.updatedAt
        )
    }
}