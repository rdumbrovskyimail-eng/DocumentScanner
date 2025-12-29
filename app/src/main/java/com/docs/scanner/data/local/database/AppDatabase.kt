package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*

/**
 * Main Room database for DocumentScanner app.
 * 
 * ✅ FTS5 Entity Added:
 * - DocumentsFtsEntity: Virtual table for full-text search
 * - Room will auto-create triggers for sync
 * 
 * Database schema:
 * - FolderEntity: Folder hierarchy
 * - RecordEntity: Document records within folders
 * - DocumentEntity: Scanned document pages
 * - DocumentsFtsEntity: FTS virtual table (linked to DocumentEntity)
 * - TermEntity: Term/deadline reminders
 * - TranslationCacheEntity: Translation cache with language awareness
 * 
 * Version history:
 * v1: Initial schema (folders, records, documents)
 * v2: Added terms table
 * v3: Added api_keys table (now removed)
 * v4: Added translation_cache + FTS5, migrated api_keys to encrypted
 * v5: Updated translation_cache with language fields
 * v6: Fixed FTS5 UPDATE trigger (DELETE+INSERT pattern) + TypeConverters added
 */
@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        DocumentsFtsEntity::class,  // ✅ ADDED FTS ENTITY
        TermEntity::class,
        TranslationCacheEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)  // ✅ ДОБАВЛЕНО - критически важно для преобразования типов!
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun translationCacheDao(): TranslationCacheDao
}