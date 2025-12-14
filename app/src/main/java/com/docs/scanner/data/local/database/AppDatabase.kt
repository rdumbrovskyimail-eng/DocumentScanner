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
TermEntity::class
],
version = 2,
exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
abstract fun folderDao(): FolderDao
abstract fun recordDao(): RecordDao
abstract fun documentDao(): DocumentDao
abstract fun termDao(): TermDao
companion object {
    const val DATABASE_NAME = "document_scanner.db"
}
}