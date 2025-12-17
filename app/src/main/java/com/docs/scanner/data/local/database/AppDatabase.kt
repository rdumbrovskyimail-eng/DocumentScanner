package com.docs.scanner.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.docs.scanner.data.local.database.dao.DocumentDao
import com.docs.scanner.data.local.database.dao.FolderDao
import com.docs.scanner.data.local.database.dao.RecordDao
import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.dao.ApiKeyDao // Не забудьте импортировать новый DAO
import com.docs.scanner.data.local.database.entities.DocumentEntity
import com.docs.scanner.data.local.database.entities.FolderEntity
import com.docs.scanner.data.local.database.entities.RecordEntity
import com.docs.scanner.data.local.database.entities.TermEntity
import com.docs.scanner.data.local.database.entities.ApiKeyEntity // Импорт новой сущности

@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        TermEntity::class,
        ApiKeyEntity::class // ✅ Добавлена новая сущность
    ],
    version = 3, // ✅ Обновлено до версии 3
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun apiKeyDao(): ApiKeyDao // ✅ Добавлен доступ к новому DAO
}
