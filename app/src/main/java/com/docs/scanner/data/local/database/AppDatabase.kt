package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*

/**
 * Main Room database for DocumentScanner app.
 * 
 * Session 2, 3 & 4 fixes:
 * - ✅ Updated version 5 → 6
 * - ✅ Fixed FTS5 UPDATE trigger (DELETE+INSERT pattern)
 * - ✅ Changed exportSchema false → true (for migration tracking)
 * - ✅ Removed ApiKeyEntity (migrated to EncryptedStorage in v4)
 * - ✅ Updated TranslationCacheEntity (now with language fields)
 * 
 * Database schema:
 * - FolderEntity: Folder hierarchy
 * - RecordEntity: Document records within folders
 * - DocumentEntity: Scanned document pages
 * - TermEntity: Term/deadline reminders
 * - TranslationCacheEntity: Translation cache with language awareness
 * - documents_fts: FTS5 virtual table (created in migration, fixed in v6)
 * 
 * Version history:
 * v1: Initial schema (folders, records, documents)
 * v2: Added terms table
 * v3: Added api_keys table (now removed)
 * v4: Added translation_cache + FTS5, migrated api_keys to encrypted
 * v5: Updated translation_cache with language fields
 * v6: Fixed FTS5 UPDATE trigger (DELETE+INSERT pattern) ← CURRENT
 * 
 * Migration chain: MIGRATION_1_2 → MIGRATION_2_3 → MIGRATION_3_4 → MIGRATION_4_5 → MIGRATION_5_6
 * 
 * Schema location: app/schemas/ (for version control)
 */
@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        TermEntity::class,
        TranslationCacheEntity::class
    ],
    version = 6,  // ✅ UPDATED from 5
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun translationCacheDao(): TranslationCacheDao
}