package com.docs.scanner.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docs.scanner.data.local.database.AppDatabase
import com.docs.scanner.data.local.database.dao.DocumentDao
import com.docs.scanner.data.local.database.dao.FolderDao
import com.docs.scanner.data.local.database.dao.RecordDao
import com.docs.scanner.data.local.database.dao.SearchDao
import com.docs.scanner.data.local.database.dao.TermDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database and DAO instances.
 * 
 * Provides:
 * - AppDatabase singleton
 * - All DAO instances
 * - Database migrations
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "document_scanner_database"

    /**
     * Migration from version 4 to 5.
     * Adds FTS and SearchHistory tables.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create FTS virtual table
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts 
                USING fts5(
                    original_text, 
                    translated_text, 
                    content='documents', 
                    content_rowid='id'
                )
            """)
            
            // Create search history table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS search_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    query TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    result_count INTEGER NOT NULL DEFAULT 0
                )
            """)
            
            // Create index on search history
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS index_search_history_timestamp 
                ON search_history(timestamp DESC)
            """)
            
            // Add completed_at column to terms if not exists
            try {
                db.execSQL("ALTER TABLE terms ADD COLUMN completed_at INTEGER")
            } catch (e: Exception) {
                // Column already exists, ignore
            }
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
            DATABASE_NAME
        )
            .addMigrations(MIGRATION_4_5)
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

    @Provides
    @Singleton
    fun provideSearchDao(database: AppDatabase): SearchDao {
        return database.searchDao()
    }
}