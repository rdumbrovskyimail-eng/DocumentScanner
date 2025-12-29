package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*

/**
 * Main Room database for DocumentScanner app.
 * 
 * ⚠️ TEMPORARY: FTS5 disabled for debugging KSP issues
 * Will be re-enabled once build is stable.
 * 
 * Database schema:
 * - FolderEntity: Folder hierarchy
 * - RecordEntity: Document records within folders
 * - DocumentEntity: Scanned document pages
 * - TermEntity: Term/deadline reminders
 * - TranslationCacheEntity: Translation cache with language awareness
 * 
 * Version history:
 * v1: Initial schema (folders, records, documents)
 * v2: Added terms table
 * v3: Added api_keys table (now removed)
 * v4: Added translation_cache, migrated api_keys to encrypted
 * v5: Updated translation_cache with language fields
 * v6: FTS5 temporarily disabled
 */
@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        // DocumentsFtsEntity::class,  // ⚠️ TEMPORARILY DISABLED
        TermEntity::class,
        TranslationCacheEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun translationCacheDao(): TranslationCacheDao
}