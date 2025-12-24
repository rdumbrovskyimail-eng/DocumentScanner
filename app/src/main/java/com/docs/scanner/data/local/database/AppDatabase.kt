package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*

@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        TermEntity::class,
        TranslationCacheEntity::class // ✅ ADDED
        // ❌ REMOVED: ApiKeyEntity (moved to EncryptedSharedPreferences)
    ],
    version = 4, // ✅ UPDATED from 3 to 4
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun translationCacheDao(): TranslationCacheDao // ✅ ADDED
}
