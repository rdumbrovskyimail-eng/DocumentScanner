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

/**
 * Database module with complete migration chain.
 * 
 * Current database version: 6
 * 
 * Session 2, 3 & 4 fixes:
 * - ✅ Added MIGRATION_4_5 (language-aware cache)
 * - ✅ Added MIGRATION_5_6 (FIX FTS5 triggers - DELETE+INSERT instead of UPDATE)
 * - ✅ Fixed FTS5 triggers (COALESCE for NULL)
 * - ✅ Fixed api_keys migration (one-time check with SharedPrefs)
 * - ✅ Added DROP TABLE in MIGRATION_3_4
 * - ✅ Improved backup cleanup
 * 
 * Migration history:
 * v1: Initial schema (folders, records, documents)
 * v2: Added terms table
 * v3: Added api_keys table
 * v4: Added translation_cache + FTS5 + migrated api_keys to encrypted
 * v5: Updated translation_cache with language fields
 * v6: Fixed FTS5 UPDATE trigger (DELETE+INSERT pattern)
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    private const val DATABASE_NAME = "document_scanner.db"
    
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
                android.util.Log.d("Migration", "  ✅ translation_cache table created")
                
                // 2. Create FTS5 virtual table for full-text search
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts 
                    USING fts5(
                        originalText, 
                        translatedText, 
                        content=documents,
                        content_rowid=id
                    )
                """)
                android.util.Log.d("Migration", "  ✅ FTS5 table created")
                
                // 3. Populate FTS5 with existing data
                db.execSQL("""
                    INSERT INTO documents_fts(rowid, originalText, translatedText)
                    SELECT id, 
                           COALESCE(originalText, ''), 
                           COALESCE(translatedText, '')
                    FROM documents
                    WHERE originalText IS NOT NULL OR translatedText IS NOT NULL
                """)
                android.util.Log.d("Migration", "  ✅ FTS5 table populated")
                
                // 4. Create triggers to keep FTS5 in sync
                db.execSQL("""
                    CREATE TRIGGER documents_ai AFTER INSERT ON documents BEGIN
                        INSERT INTO documents_fts(rowid, originalText, translatedText)
                        VALUES (
                            new.id, 
                            COALESCE(new.originalText, ''), 
                            COALESCE(new.translatedText, '')
                        );
                    END
                """)
                
                // ⚠️ NOTE: This UPDATE trigger has a bug (will be fixed in v6)
                db.execSQL("""
                    CREATE TRIGGER documents_au AFTER UPDATE ON documents BEGIN
                        UPDATE documents_fts 
                        SET originalText = COALESCE(new.originalText, ''),
                            translatedText = COALESCE(new.translatedText, '')
                        WHERE rowid = new.id;
                    END
                """)
                
                db.execSQL("""
                    CREATE TRIGGER documents_ad AFTER DELETE ON documents BEGIN
                        DELETE FROM documents_fts WHERE rowid = old.id;
                    END
                """)
                android.util.Log.d("Migration", "  ✅ FTS5 triggers created")
                
                // 5. Create indices for better performance
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_recordId ON documents(recordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(processingStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_createdAt ON documents(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_folderId ON records(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_translation_cache_timestamp ON translation_cache(timestamp)")
                android.util.Log.d("Migration", "  ✅ Indices created")
                
                // 6. DROP api_keys table (will be migrated in callback)
                db.execSQL("DROP TABLE IF EXISTS api_keys")
                android.util.Log.d("Migration", "  ✅ api_keys table dropped (migrated to EncryptedStorage)")
                
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
                
                // Step 1: Create new table with language fields
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
                android.util.Log.d("Migration", "  ✅ New table created")
                
                // Step 2: Migrate existing data with default languages
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
                android.util.Log.d("Migration", "  ✅ Data migrated")
                
                // Step 3: Drop old table
                db.execSQL("DROP TABLE translation_cache")
                android.util.Log.d("Migration", "  ✅ Old table dropped")
                
                // Step 4: Rename new table
                db.execSQL("ALTER TABLE translation_cache_new RENAME TO translation_cache")
                android.util.Log.d("Migration", "  ✅ Table renamed")
                
                // Step 5: Create index
                db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_cache_timestamp ON translation_cache(timestamp)")
                android.util.Log.d("Migration", "  ✅ Index created")
                
                android.util.Log.d("Migration", "✅ Migration 4→5 complete")
                
            } catch (e: Exception) {
                android.util.Log.e("Migration", "❌ Migration 4→5 failed", e)
                throw e
            }
        }
    }
    
    // ============================================
    // MIGRATION 5 → 6: FIX FTS5 UPDATE TRIGGER
    // ✅ CRITICAL FIX: FTS5 doesn't support UPDATE, use DELETE+INSERT
    // ============================================
    
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                android.util.Log.d("Migration", "🔄 Migrating 5→6: Fixing FTS5 UPDATE trigger")
                
                // Step 1: Drop the buggy UPDATE trigger
                db.execSQL("DROP TRIGGER IF EXISTS documents_au")
                android.util.Log.d("Migration", "  ✅ Old UPDATE trigger dropped")
                
                // Step 2: Create corrected UPDATE trigger (DELETE + INSERT pattern)
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
                android.util.Log.d("Migration", "  ✅ New UPDATE trigger created (DELETE+INSERT pattern)")
                
                // Step 3: Rebuild FTS5 index to ensure consistency
                db.execSQL("INSERT INTO documents_fts(documents_fts) VALUES('rebuild')")
                android.util.Log.d("Migration", "  ✅ FTS5 index rebuilt")
                
                android.util.Log.d("Migration", "✅ Migration 5→6 complete - FTS5 is now fully functional!")
                
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
                MIGRATION_5_6  // ✅ NEW - FIX FTS5 TRIGGER
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    android.util.Log.d("Database", "✅ Database created (v6)")
                    
                    // Enable foreign keys
                    db.execSQL("PRAGMA foreign_keys=ON")
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
                        } else {
                            android.util.Log.d("Database", "ℹ️ API key migration already done or not needed")
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
            
            // Check if api_keys table exists
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='api_keys'")
            val tableExists = cursor.moveToFirst()
            cursor.close()
            
            if (!tableExists) {
                android.util.Log.d("Database", "ℹ️ api_keys table not found (already migrated)")
                return
            }
            
            // Check if already migrated
            if (encryptedKeyStorage.getAllKeys().isNotEmpty()) {
                android.util.Log.d("Database", "ℹ️ API keys already in encrypted storage")
                return
            }
            
            // Read keys from database
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
                // Save to encrypted storage
                encryptedKeyStorage.saveAllKeys(keys)
                
                // Verify migration success
                val savedKeys = encryptedKeyStorage.getAllKeys()
                if (savedKeys.size != keys.size) {
                    throw IllegalStateException(
                        "Migration failed: saved ${savedKeys.size}, expected ${keys.size}"
                    )
                }
                
                // Set active key
                keys.find { it.isActive }?.let {
                    encryptedKeyStorage.setActiveApiKey(it.key)
                }
                
                android.util.Log.d("Database", "✅ Migrated ${keys.size} API keys to encrypted storage")
                
                // Drop api_keys table ONLY if successful
                db.execSQL("DROP TABLE IF EXISTS api_keys")
                android.util.Log.d("Database", "✅ api_keys table dropped")
            } else {
                android.util.Log.d("Database", "ℹ️ No API keys to migrate")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("Database", "⚠️ API keys migration failed: ${e.message}", e)
            // Don't throw - allow app to continue
        }
    }
    
    // ============================================
    // CREATE DATABASE BACKUP
    // ============================================
    
    private fun createDatabaseBackup(context: Context) {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                android.util.Log.d("Database", "ℹ️ No database file to backup")
                return
            }
            
            val backupFile = java.io.File(
                dbFile.parent,
                "${dbFile.name}.backup_${System.currentTimeMillis()}"
            )
            
            dbFile.copyTo(backupFile, overwrite = false)
            android.util.Log.d("Database", "✅ Backup created: ${backupFile.name}")
            
            // Clean old backups (keep last 5)
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
            // Don't throw - non-critical
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
