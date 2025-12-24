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
    // MIGRATION 1 → 2: Added terms table
    // ============================================
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
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
                println("✅ Migration 1→2: terms table created")
            } catch (e: Exception) {
                println("❌ Migration 1→2 error: ${e.message}")
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
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `api_keys` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `key` TEXT NOT NULL,
                        `label` TEXT,
                        `isActive` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                println("✅ Migration 2→3: api_keys table created")
            } catch (e: Exception) {
                println("❌ Migration 2→3 error: ${e.message}")
                throw e
            }
        }
    }
    
    // ============================================
    // MIGRATION 3 → 4: FTS5 + Translation Cache
    // SECURITY: Migrate api_keys to EncryptedSharedPreferences
    // ============================================
    
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                // 1. Create translation_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `translation_cache` (
                        `textHash` TEXT PRIMARY KEY NOT NULL,
                        `originalText` TEXT NOT NULL,
                        `translatedText` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """)
                println("✅ Translation cache table created")
                
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
                println("✅ FTS5 table created")
                
                // 3. Populate FTS5 with existing data
                db.execSQL("""
                    INSERT INTO documents_fts(rowid, originalText, translatedText)
                    SELECT id, originalText, translatedText FROM documents
                    WHERE originalText IS NOT NULL OR translatedText IS NOT NULL
                """)
                println("✅ FTS5 table populated")
                
                // 4. Create triggers to keep FTS5 in sync
                db.execSQL("""
                    CREATE TRIGGER documents_ai AFTER INSERT ON documents BEGIN
                        INSERT INTO documents_fts(rowid, originalText, translatedText)
                        VALUES (new.id, new.originalText, new.translatedText);
                    END
                """)
                
                db.execSQL("""
                    CREATE TRIGGER documents_au AFTER UPDATE ON documents BEGIN
                        UPDATE documents_fts 
                        SET originalText = new.originalText, translatedText = new.translatedText
                        WHERE rowid = new.id;
                    END
                """)
                
                db.execSQL("""
                    CREATE TRIGGER documents_ad AFTER DELETE ON documents BEGIN
                        DELETE FROM documents_fts WHERE rowid = old.id;
                    END
                """)
                println("✅ FTS5 triggers created")
                
                // 5. Create indices for better performance
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_recordId ON documents(recordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(processingStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_folderId ON records(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_translation_cache_timestamp ON translation_cache(timestamp)")
                println("✅ Indices created")
                
                // 6. Note: api_keys migration will be handled in callback
                println("✅ Migration 3→4 completed successfully")
                
            } catch (e: Exception) {
                println("❌ Migration 3→4 error: ${e.message}")
                e.printStackTrace()
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    println("✅ Database created successfully")
                    
                    // Enable foreign keys
                    db.execSQL("PRAGMA foreign_keys=ON")
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    println("✅ Database opened successfully")
                    
                    // Enable foreign keys
                    db.execSQL("PRAGMA foreign_keys=ON")
                    
                    // Migrate API keys from Room to EncryptedSharedPreferences
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        migrateApiKeysToEncrypted(db, encryptedKeyStorage, context)
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
            // Check if api_keys table exists
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='api_keys'")
            val tableExists = cursor.moveToFirst()
            cursor.close()
            
            if (!tableExists) {
                println("ℹ️ No api_keys table found, skipping migration")
                return
            }
            
            // Check if already migrated
            if (encryptedStorage.getAllKeys().isNotEmpty()) {
                println("ℹ️ API keys already migrated, skipping")
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
                encryptedStorage.saveAllKeys(keys)
                
                // Set active key
                keys.find { it.isActive }?.let {
                    encryptedStorage.setActiveApiKey(it.key)
                }
                
                println("✅ Migrated ${keys.size} API keys to encrypted storage")
                
                // Drop api_keys table
                db.execSQL("DROP TABLE IF EXISTS api_keys")
                println("✅ Dropped api_keys table")
            }
            
        } catch (e: Exception) {
            println("⚠️ API keys migration failed: ${e.message}")
            // Don't throw - allow app to continue
        }
    }
    
    // ============================================
    // CREATE DATABASE BACKUP
    // ============================================
    
    private fun createDatabaseBackup(context: Context) {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                val backupFile = java.io.File(
                    dbFile.parent,
                    "${dbFile.name}.backup_${System.currentTimeMillis()}"
                )
                dbFile.copyTo(backupFile, overwrite = false)
                println("✅ Database backup created: ${backupFile.name}")
                
                // Clean old backups (keep last 5)
                dbFile.parentFile?.listFiles { file ->
                    file.name.startsWith("${DATABASE_NAME}.backup_")
                }?.sortedByDescending { it.lastModified() }
                    ?.drop(5)
                    ?.forEach { 
                        it.delete()
                        println("🗑️ Deleted old backup: ${it.name}")
                    }
            }
        } catch (e: Exception) {
            println("⚠️ Failed to create backup: ${e.message}")
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
