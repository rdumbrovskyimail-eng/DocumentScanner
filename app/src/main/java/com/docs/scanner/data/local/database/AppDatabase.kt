package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*

/**
 * Main Room database for DocumentScanner app.
 * 
 * ⚠️ TEMPORARILY SIMPLIFIED FOR DEBUGGING
 */
@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        // ⚠️ TEMPORARILY COMMENTED
        // DocumentEntity::class,
        // DocumentsFtsEntity::class,
        // TermEntity::class,
        // TranslationCacheEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    
    // ⚠️ TEMPORARILY COMMENTED
    // abstract fun documentDao(): DocumentDao
    // abstract fun termDao(): TermDao
    // abstract fun translationCacheDao(): TranslationCacheDao
}