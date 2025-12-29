package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*

/**
 * Main Room database for DocumentScanner app.
 * 
 * ⚠️ DEBUGGING MODE: Minimal entities to isolate KSP issue
 */
@Database(
    entities = [
        FolderEntity::class,
        // RecordEntity::class,  // ⚠️ TEMPORARILY DISABLED
        // DocumentEntity::class,  // ⚠️ TEMPORARILY DISABLED
        // TermEntity::class,  // ⚠️ TEMPORARILY DISABLED
        // TranslationCacheEntity::class  // ⚠️ TEMPORARILY DISABLED
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun folderDao(): FolderDao
    // abstract fun recordDao(): RecordDao  // ⚠️ TEMPORARILY DISABLED
    // abstract fun documentDao(): DocumentDao  // ⚠️ TEMPORARILY DISABLED
    // abstract fun termDao(): TermDao  // ⚠️ TEMPORARILY DISABLED
    // abstract fun translationCacheDao(): TranslationCacheDao  // ⚠️ TEMPORARILY DISABLED
}