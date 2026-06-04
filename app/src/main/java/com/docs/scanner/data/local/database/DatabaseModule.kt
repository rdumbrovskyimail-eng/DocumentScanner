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
import com.docs.scanner.data.local.database.dao.AnalyticsNoteDao
import com.docs.scanner.data.local.database.dao.AnalyticsTranslationDao
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



    // ════════════════════════════════════════════════════════════════════════════════
    // DATABASE PROVIDER
    // ════════════════════════════════════════════════════════════════════════════════

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        // ✅ ИСПРАВЛЕНО: Используем правильный инстанс со всеми 19 миграциями
        return AppDatabase.getInstance(context)
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

    // ─── Analytics Center DAOs ──────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAnalyticsTranslationDao(database: AppDatabase): AnalyticsTranslationDao {
        return database.analyticsTranslationDao()
    }

    @Provides
    @Singleton
    fun provideAnalyticsNoteDao(database: AppDatabase): AnalyticsNoteDao {
        return database.analyticsNoteDao()
    }
}