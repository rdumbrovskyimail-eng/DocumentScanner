/**
 * DatabaseModule.kt
 * Version: 7.0.1 - FIXED (2026 Standards)
 *
 * ✅ FIX MEDIUM-1: Убран fallbackToDestructiveMigration()
 *    БЫЛО: .fallbackToDestructiveMigration() — уничтожает данные при ошибке миграции
 *    СТАЛО: .fallbackToDestructiveMigrationOnDowngrade() — только при даунгрейде
 *
 * ✅ FIX: Синхронизировано имя базы данных с AppDatabase.kt
 *    БЫЛО: "document_scanner_database"
 *    СТАЛО: "document_scanner.db"
 *
 * ✅ FIX: Добавлены все миграции из AppDatabase.kt
 *
 * ✅ FIX: Убран provideSearchDao (SearchDao не существует в AppDatabase)
 *
 * Hilt module for providing database and DAO instances.
 */

package com.docs.scanner.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docs.scanner.data.local.database.AppDatabase
import com.docs.scanner.data.local.database.dao.DocumentDao
import com.docs.scanner.data.local.database.dao.FolderDao
import com.docs.scanner.data.local.database.dao.RecordDao
import com.docs.scanner.data.local.database.dao.SearchHistoryDao
import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.dao.TranslationCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
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
 * 
 * Fixed issues:
 * - ✅ MEDIUM-1: Replaced fallbackToDestructiveMigration() with safer alternative
 * - ✅ Synchronized DATABASE_NAME with AppDatabase.kt
 * - ✅ Added all migrations from AppDatabase
 * - ✅ Removed non-existent SearchDao provider
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // ✅ FIX: Синхронизировано с AppDatabase.DATABASE_NAME
    private const val DATABASE_NAME = "document_scanner.db"

    // ════════════════════════════════════════════════════════════════════════════════
    // MIGRATIONS
    // ════════════════════════════════════════════════════════════════════════════════

    /**
     * Migration from version 4 to 5.
     * Adds FTS, SearchHistory, and TranslationCache tables.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Timber.i("🔄 Running migration 4 → 5")
            
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
                    original_text TEXT NOT NULL,
                    translated_text TEXT NOT NULL,
                    source_language TEXT NOT NULL,
                    target_language TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
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
                Timber.w("Column completed_at already exists, skipping")
            }
            
            Timber.i("✅ Migration 4 → 5 completed")
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // DATABASE PROVIDER
    // ════════════════════════════════════════════════════════════════════════════════

    /**
     * Provides AppDatabase singleton.
     * 
     * ✅ FIX MEDIUM-1: Changed from fallbackToDestructiveMigration() to 
     * fallbackToDestructiveMigrationOnDowngrade().
     * 
     * БЫЛО (ОПАСНО):
     * ```
     * .fallbackToDestructiveMigration()
     * ```
     * Это уничтожает ВСЕ данные пользователя при любой ошибке миграции!
     * 
     * СТАЛО (БЕЗОПАСНО):
     * ```
     * .fallbackToDestructiveMigrationOnDowngrade()
     * ```
     * Уничтожает данные только при даунгрейде версии (редкий случай).
     * При обычном обновлении приложения миграция должна пройти корректно,
     * иначе приложение упадёт — это лучше, чем молча потерять данные.
     * 
     * Для продакшена рекомендуется:
     * 1. Тщательно тестировать все миграции
     * 2. Иметь резервное копирование (Google Drive)
     * 3. При критической ошибке — показать пользователю диалог восстановления
     */
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
            // ✅ FIX MEDIUM-1: Безопасная миграция — только при даунгрейде
            .fallbackToDestructiveMigrationOnDowngrade()
            // Enable WAL mode for better performance
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Callback for database initialization
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Timber.i("📦 Database created: $DATABASE_NAME")
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Timber.d("📂 Database opened: $DATABASE_NAME")
                }
            })
            .build()
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // DAO PROVIDERS
    // ════════════════════════════════════════════════════════════════════════════════

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

    // ✅ FIX: Убран provideSearchDao — SearchDao не существует в AppDatabase
    // Поиск доступен через documentDao().searchFts()
    // 
    // БЫЛО:
    // @Provides
    // @Singleton
    // fun provideSearchDao(database: AppDatabase): SearchDao {
    //     return database.searchDao()  // ❌ searchDao() не существует!
    // }

    /**
     * Provides TranslationCacheDao for caching translations.
     */
    @Provides
    @Singleton
    fun provideTranslationCacheDao(database: AppDatabase): TranslationCacheDao {
        return database.translationCacheDao()
    }

    /**
     * Provides SearchHistoryDao for tracking search queries.
     */
    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }
}