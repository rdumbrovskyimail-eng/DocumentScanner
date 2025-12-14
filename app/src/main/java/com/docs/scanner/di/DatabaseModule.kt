package com.docs.scanner.di
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docs.scanner.data.local.database.AppDatabase
import com.docs.scanner.data.local.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `terms` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `dateTime` INTEGER NOT NULL,
                `reminderMinutesBefore` INTEGER,
                `isCompleted` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

@Provides
@Singleton
fun provideAppDatabase(
    @ApplicationContext context: Context
): AppDatabase {
    return Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
}

@Provides
@Singleton
fun provideFolderDao(database: AppDatabase): FolderDao {
    return database.folderDao()
}

@Provides
@Singleton
fun provideRecordDao(database: AppDatabase): RecordDao {
    return database.recordDao()
}

@Provides
@Singleton
fun provideDocumentDao(database: AppDatabase): DocumentDao {
    return database.documentDao()
}

@Provides
@Singleton
fun provideTermDao(database: AppDatabase): TermDao {
    return database.termDao()
}
}