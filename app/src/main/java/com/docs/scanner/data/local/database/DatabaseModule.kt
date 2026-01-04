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
import com.docs.scanner.data.local.database.dao.SearchHistoryDao
import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.dao.TranslationCacheDao
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
 * - AppDatabase singleton with proper configuration
 * - All DAO instances
 * - Database migrations
 * 
 * Note: Room handles WAL mode and pragmas automatically.
 * Manual execSQL for table creation is avoided to maintain schema validation.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "document_scanner_database"

    /**
     * Migration from version 4 to 5.
     * Adds FTS, SearchHistory, and TranslationCache tables.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create FTS virtual table for full-text search
            db.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts 
                USING fts4(
                    original_text, 
                    translated_text,
                    tokenize=unicode61
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
            
            // Create index on search history timestamp
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS index_search_history_timestamp 
                ON search_history(timestamp DESC)
            """)
            
            // Create translation cache table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS translation_cache (
                    cache_key TEXT PRIMARY KEY NOT NULL,
                    source_text TEXT NOT NULL,
                    translated_text TEXT NOT NULL,
                    source_lang TEXT NOT NULL,
                    target_lang TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    hit_count INTEGER NOT NULL DEFAULT 0
                )
            """)
            
            // Create index on cache timestamp for cleanup
            db.execSQL("""
                CREATE INDEX IF NOT EXISTS index_translation_cache_timestamp 
                ON translation_cache(timestamp ASC)
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

    /**
     * Provides TranslationCacheDao for caching translations.
     * Fixes: 🟠 Серьёзная #9
     */
    @Provides
    @Singleton
    fun provideTranslationCacheDao(database: AppDatabase): TranslationCacheDao {
        return database.translationCacheDao()
    }

    /**
     * Provides SearchHistoryDao for tracking search queries.
     * Fixes: 🟠 Серьёзная #10
     */
    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }
}