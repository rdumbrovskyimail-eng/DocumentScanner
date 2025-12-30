package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*

/**
 * Main Room database for DocumentScanner app.
 * 
 * ✅ ИСПРАВЛЕНО: Все entities и DAOs включены
 * 
 * Версия БД: 6
 * 
 * Entities:
 * - FolderEntity: Папки для организации записей
 * - RecordEntity: Записи (группы документов)
 * - DocumentEntity: Отдельные документы/изображения
 * - TermEntity: Термины/напоминания
 * - TranslationCacheEntity: Кэш переводов
 */
@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        TermEntity::class,
        TranslationCacheEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun translationCacheDao(): TranslationCacheDao
}
