package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*

/**
 * Main Room database for DocumentScanner app.
 * 
 * ⚠️ TEMPORARY: FTS5 Entity disabled for debugging KSP issues
 * The FTS table still exists in the database (created by migrations),
 * but Room temporarily doesn't know about it until we fix the entity definition.
 * 
 * Database schema:
 * - FolderEntity: Folder hierarchy
 * - RecordEntity: Document records within folders
 * - DocumentEntity: Scanned document pages
 * - DocumentsFtsEntity: FTS virtual table (TEMPORARILY DISABLED)
 * - TermEntity: Term/deadline reminders
 * - TranslationCacheEntity: Translation cache with language awareness
 * 
 * Version history:
 * v1: Initial schema (folders, records, documents)
 * v2: Added terms table
 * v3: Added api_keys table (now removed)
 * v4: Added translation_cache + FTS5, migrated api_keys to encrypted
 * v5: Updated translation_cache with language fields
 * v6: Fixed FTS5 UPDATE trigger + TypeConverters added
 */
@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        // DocumentsFtsEntity::class,  // ⚠️ TEMPORARILY DISABLED - causing KSP generation issues
        TermEntity::class,
        TranslationCacheEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)  // ✅ Type converters for Date and List<String>
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun translationCacheDao(): TranslationCacheDao
}