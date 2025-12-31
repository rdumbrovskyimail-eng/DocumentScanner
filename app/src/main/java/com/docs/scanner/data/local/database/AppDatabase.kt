package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.docs.scanner.data.local.database.dao.DocumentDao
import com.docs.scanner.data.local.database.dao.FolderDao
import com.docs.scanner.data.local.database.dao.RecordDao
import com.docs.scanner.data.local.database.dao.SearchDao
import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.entities.DocumentEntity
import com.docs.scanner.data.local.database.entities.DocumentFtsEntity
import com.docs.scanner.data.local.database.entities.FolderEntity
import com.docs.scanner.data.local.database.entities.RecordEntity
import com.docs.scanner.data.local.database.entities.SearchHistoryEntity
import com.docs.scanner.data.local.database.entities.TermEntity

/**
 * Main Room database for the DocumentScanner app.
 * 
 * ## Entities
 * - [FolderEntity]: Folders containing records
 * - [RecordEntity]: Records containing documents  
 * - [DocumentEntity]: Scanned document images with OCR/translation
 * - [TermEntity]: Deadline reminders
 * - [DocumentFtsEntity]: Full-text search index for documents
 * - [SearchHistoryEntity]: User search history
 * 
 * ## Version History
 * - v1: Initial schema (Folders, Records, Documents)
 * - v2: Added TermEntity
 * - v3: Added position field to DocumentEntity
 * - v4: Added processing status fields
 * - v5: Added FTS support and SearchHistory
 * 
 * ## DAOs
 * - [folderDao]: CRUD for folders
 * - [recordDao]: CRUD for records
 * - [documentDao]: CRUD for documents + FTS triggers
 * - [termDao]: CRUD for terms/deadlines
 * - [searchDao]: Search history and FTS queries
 */
@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        TermEntity::class,
        DocumentFtsEntity::class,
        SearchHistoryEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * DAO for folder operations.
     */
    abstract fun folderDao(): FolderDao
    
    /**
     * DAO for record operations.
     */
    abstract fun recordDao(): RecordDao
    
    /**
     * DAO for document operations including FTS.
     */
    abstract fun documentDao(): DocumentDao
    
    /**
     * DAO for term/deadline operations.
     */
    abstract fun termDao(): TermDao
    
    /**
     * DAO for search operations.
     */
    abstract fun searchDao(): SearchDao
}