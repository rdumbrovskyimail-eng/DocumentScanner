package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.docs.scanner.data.local.database.dao.DocumentDao
import com.docs.scanner.data.local.database.dao.FolderDao
import com.docs.scanner.data.local.database.dao.RecordDao
import com.docs.scanner.data.local.database.entities.DocumentEntity
import com.docs.scanner.data.local.database.entities.FolderEntity
import com.docs.scanner.data.local.database.entities.RecordEntity

@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    
    companion object {
        const val DATABASE_NAME = "document_scanner.db"
    }
}