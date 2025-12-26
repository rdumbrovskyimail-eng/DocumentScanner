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
        TranslationCacheEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun translationCacheDao(): TranslationCacheDao
}