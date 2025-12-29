package com.docs.scanner.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    private const val DATABASE_NAME = "document_scanner.db"
    
    // ============================================
    // 🔧 HELPER: POPULATE EXISTING FTS DATA
    // ⚠️ TEMPORARILY DISABLED - FTS entity causing issues
    // ============================================
    
    private fun populateFtsData(db: SupportSQLiteDatabase) {
        // ⚠️ TEMPORARILY DISABLED
        // FTS table exists in DB but Room doesn't know about it yet
        // Uncomment when DocumentsFtsEntity is re-enabled
        /*
        db.execSQL("""
            INSERT OR IGNORE INTO documents_fts(rowid, originalText, translatedText)
            SELECT id, 
                   originalText, 
                   translatedText
            FROM documents
            WHERE originalText IS NOT NULL OR translatedText IS NOT NULL
        """)
        */
    }
    
    // ============================================
    // MIGRATION 1 → 2: Added terms table
    // ============================================
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                android.util.Log.d("Migration", "🔄 Migrating 1→2: Adding terms table")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `terms` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `dueDate` INTEGER NOT NULL,
                        `reminderMinutesBefore` INTEGER NOT NULL DEFAULT 0,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                
                android.util.Log.d("Migration", "✅ Migration 1→2 complete")
            } catch (e: Exception) {
                android.util.Log.e("Migration", "❌ Migration 1→2 failed", e)
                throw e
            }
        }
    }
    
    // ============================================
    // MIGRATION 2 → 3: Added api_keys table
    // ============================================
    
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                android.util.Log.d("Migration", "🔄 Migrating 2→3: Adding api_keys table")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `api_keys` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `key` TEXT NOT NULL,
                        `label` TEXT,
                        `isActive` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                
                android.util.Log.d("Migration", "✅ Migration 2→3 complete")
            } catch (e: Exception) {
                android.util.Log.e("Migration", "❌ Migration 2→3 failed", e)
                throw e
            }
        }
    }
    
    // ============================================
    // MIGRATION 3 → 4: FTS5 + Translation Cache + API Key Migration
    // ============================================
    
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                android.util.Log.d("Migration", "🔄 Migrating 3→4: FTS5 + translation_cache")
                
                // 1. Create translation_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `translation_cache` (
                        `textHash` TEXT PRIMARY KEY NOT NULL,
                        `originalText` TEXT NOT NULL,
                        `translatedText` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """)
                
                // 2. ⚠️ SKIP FTS population (temporarily disabled)
                // populateFtsData(db)
                
                // 3. Create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_recordId ON documents(recordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(processingStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_createdAt ON documents(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_folderId ON records(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_translation_cache_timestamp ON translation_cache(timestamp)")
                
                // 4. DROP api_keys table
                db.execSQL("DROP TABLE IF EXISTS api_keys")
                
                android.util.Log.d("Migration", "✅ Migration 3→4 complete")
                
            } catch (e: Exception) {
                android.util.Log.e("Migration", "❌ Migration 3→4 failed", e)
                throw e
            }
        }
    }
    
    // ============================================
    // MIGRATION 4 → 5: Language-aware translation cache
    // ============================================
    
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                android.util.Log.d("Migration", "🔄 Migrating 4→5: Language-aware cache")
                
                db.execSQL("""
                    CREATE TABLE translation_cache_new (
                        cacheKey TEXT PRIMARY KEY NOT NULL,
                        originalText TEXT NOT NULL,
                        translatedText TEXT NOT NULL,
                        sourceLanguage TEXT NOT NULL DEFAULT 'auto',
                        targetLanguage TEXT NOT NULL DEFAULT 'ru',
                        timestamp INTEGER NOT NULL
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO translation_cache_new 
                        (cacheKey, originalText, translatedText, sourceLanguage, targetLanguage, timestamp)
                    SELECT 
                        textHash as cacheKey,
                        originalText,
                        translatedText,
                        'auto' as sourceLanguage,
                        'ru' as targetLanguage,
                        timestamp
                    FROM translation_cache
                """)
                
                db.execSQL("DROP TABLE translation_cache")
                db.execSQL("ALTER TABLE translation_cache_new RENAME TO translation_cache")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_cache_timestamp ON translation_cache(timestamp)")
                
                android.util.Log.d("Migration", "✅ Migration 4→5 complete")
                
            } catch (e: Exception) {
                android.util.Log.e("Migration", "❌ Migration 4→5 failed", e)
                throw e
            }
        }
    }
    
    // ============================================
    // MIGRATION 5 → 6: FIX FTS5 UPDATE TRIGGER
    // ⚠️ NOTE: FTS operations temporarily commented out
    // ============================================
    
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                android.util.Log.d("Migration", "🔄 Migrating 5→6: Fixing FTS5 UPDATE trigger")
                
                // ⚠️ FTS operations temporarily disabled
                // FTS table still exists, but we skip trigger updates for now
                
                // Drop old trigger (if exists)
                db.execSQL("DROP TRIGGER IF EXISTS documents_au")
                
                // ⚠️ TEMPORARILY SKIP trigger recreation
                /*
                db.execSQL("""
                    CREATE TRIGGER documents_au AFTER UPDATE ON documents BEGIN
                        DELETE FROM documents_fts WHERE rowid = old.id;
                        INSERT INTO documents_fts(rowid, originalText, translatedText)
                        VALUES (
                            new.id,
                            COALESCE(new.originalText, ''),
                            COALESCE(new.translatedText, '')
                        );
                    END
                """)
                
                // Rebuild FTS5 index
                db.execSQL("INSERT INTO documents_fts(documents_fts) VALUES('rebuild')")
                */
                
                android.util.Log.d("Migration", "✅ Migration 5→6 complete (FTS temporarily disabled)")
                
            } catch (e: Exception) {
                android.util.Log.e("Migration", "❌ Migration 5→6 failed", e)
                throw e
            }
        }
    }
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        encryptedKeyStorage: EncryptedKeyStorage
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    android.util.Log.d("Database", "✅ Database created (v6)")
                    
                    // Enable foreign keys
                    db.execSQL("PRAGMA foreign_keys=ON")
                    
                    // ⚠️ SKIP FTS population (temporarily disabled)
                    /*
                    try {
                        populateFtsData(db)
                        android.util.Log.d("Database", "✅ FTS table populated")
                    } catch (e: Exception) {
                        android.util.Log.e("Database", "❌ Failed to populate FTS", e)
                    }
                    */
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    android.util.Log.d("Database", "✅ Database opened (v${db.version})")
                    
                    // Enable foreign keys
                    db.execSQL("PRAGMA foreign_keys=ON")
                    
                    // One-time API key migration
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        val prefs = context.getSharedPreferences("db_migration", Context.MODE_PRIVATE)
                        val migrated = prefs.getBoolean("api_keys_migrated_v4", false)
                        
                        if (!migrated && db.version >= 4) {
                            migrateApiKeysToEncrypted(db, encryptedKeyStorage, context)
                            prefs.edit().putBoolean("api_keys_migrated_v4", true).apply()
                            android.util.Log.d("Database", "✅ API key migration flag set")
                        }
                    }
                    
                    // Create backup
                    createDatabaseBackup(context)
                }
            })
            .build()
    }
    
    // ============================================
    // MIGRATE API KEYS TO ENCRYPTED STORAGE
    // ============================================
    
    private fun migrateApiKeysToEncrypted(
        db: SupportSQLiteDatabase,
        encryptedStorage: EncryptedKeyStorage,
        context: Context
    ) {
        try {
            android.util.Log.d("Database", "🔄 Starting API key migration...")
            
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='api_keys'")
            val tableExists = cursor.moveToFirst()
            cursor.close()
            
            if (!tableExists) {
                android.util.Log.d("Database", "ℹ️ api_keys table not found")
                return
            }
            
            if (encryptedStorage.getAllKeys().isNotEmpty()) {
                android.util.Log.d("Database", "ℹ️ API keys already in encrypted storage")
                return
            }
            
            val keysCursor = db.query("SELECT id, key, label, isActive, createdAt FROM api_keys")
            val keys = mutableListOf<com.docs.scanner.data.local.security.ApiKeyData>()
            
            while (keysCursor.moveToNext()) {
                val id = keysCursor.getString(0)
                val key = keysCursor.getString(1)
                val label = keysCursor.getString(2)
                val isActive = keysCursor.getInt(3) == 1
                val createdAt = keysCursor.getLong(4)
                
                keys.add(
                    com.docs.scanner.data.local.security.ApiKeyData(
                        id = id,
                        key = key,
                        label = label,
                        isActive = isActive,
                        createdAt = createdAt
                    )
                )
            }
            keysCursor.close()
            
            if (keys.isNotEmpty()) {
                encryptedStorage.saveAllKeys(keys)
                
                val savedKeys = encryptedStorage.getAllKeys()
                if (savedKeys.size != keys.size) {
                    throw IllegalStateException(
                        "Migration failed: saved ${savedKeys.size}, expected ${keys.size}"
                    )
                }
                
                keys.find { it.isActive }?.let {
                    encryptedStorage.setActiveApiKey(it.key)
                }
                
                android.util.Log.d("Database", "✅ Migrated ${keys.size} API keys")
                db.execSQL("DROP TABLE IF EXISTS api_keys")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("Database", "⚠️ API keys migration failed: ${e.message}", e)
        }
    }
    
    // ============================================
    // CREATE DATABASE BACKUP
    // ============================================
    
    private fun createDatabaseBackup(context: Context) {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                return
            }
            
            val backupFile = java.io.File(
                dbFile.parent,
                "${dbFile.name}.backup_${System.currentTimeMillis()}"
            )
            
            dbFile.copyTo(backupFile, overwrite = false)
            android.util.Log.d("Database", "✅ Backup created: ${backupFile.name}")
            
            dbFile.parentFile?.listFiles { file ->
                file.name.startsWith("${DATABASE_NAME}.backup_")
            }?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { file ->
                    if (file.delete()) {
                        android.util.Log.d("Database", "🗑️ Deleted old backup: ${file.name}")
                    }
                }
        } catch (e: Exception) {
            android.util.Log.w("Database", "⚠️ Failed to create backup: ${e.message}")
        }
    }
    
    // ============================================
    // DAO PROVIDERS
    // ============================================
    
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
    fun provideTranslationCacheDao(database: AppDatabase): TranslationCacheDao {
        return database.translationCacheDao()
    }
}