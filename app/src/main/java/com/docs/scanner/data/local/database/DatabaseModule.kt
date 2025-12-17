package com.docs.scanner.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    
    private const val DATABASE_NAME = "document_scanner.db"
    
    // Migration 1 → 2: Добавление таблицы terms
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='terms'")
                val tableExists = cursor.moveToFirst()
                cursor.close()
                
                if (!tableExists) {
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
                }
                
                validateDatabaseStructure(db)
            } catch (e: Exception) {
                println("❌ Migration 1→2 error: ${e.message}")
                throw e
            }
        }
    }
    
    // ✅ НОВАЯ Migration 2 → 3: Добавление таблицы api_keys
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='api_keys'")
                val tableExists = cursor.moveToFirst()
                cursor.close()
                
                if (!tableExists) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `api_keys` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `key` TEXT NOT NULL,
                            `label` TEXT,
                            `isActive` INTEGER NOT NULL DEFAULT 0,
                            `createdAt` INTEGER NOT NULL
                        )
                    """)
                    println("✅ API keys table created")
                }
                
                validateDatabaseStructure(db)
            } catch (e: Exception) {
                println("❌ Migration 2→3 error: ${e.message}")
                throw e
            }
        }
    }
    
    private fun validateDatabaseStructure(db: SupportSQLiteDatabase) {
        val expectedTables = listOf("folders", "records", "documents", "terms", "api_keys")
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
        
        val existingTables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            existingTables.add(cursor.getString(0))
        }
        cursor.close()
        
        expectedTables.forEach { table ->
            if (table !in existingTables) {
                println("⚠️ WARNING: Expected table '$table' not found in database")
            }
        }
        
        println("✅ Database validation complete. Found tables: $existingTables")
    }
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // ✅ Добавлена новая миграция
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    println("✅ Database created successfully")
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    println("✅ Database opened successfully")
                    db.execSQL("PRAGMA foreign_keys=ON")
                    
                    // ✅ Создаём backup перед любой операцией
                    try {
                        val dbFile = context.getDatabasePath(DATABASE_NAME)
                        val backupFile = java.io.File(
                            dbFile.parent, 
                            "${dbFile.name}.backup_${System.currentTimeMillis()}"
                        )
                        if (dbFile.exists()) {
                            dbFile.copyTo(backupFile, overwrite = false)
                            println("✅ Database backup created: ${backupFile.name}")
                        }
                    } catch (e: Exception) {
                        println("⚠️ Failed to create backup: ${e.message}")
                    }
                    
                    try {
                        validateDatabaseStructure(db)
                    } catch (e: Exception) {
                        println("❌ Database validation error: ${e.message}")
                    }
                }
            })
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
    
    // ✅ НОВОЕ: Provider для ApiKeyDao
    @Provides
    @Singleton
    fun provideApiKeyDao(database: AppDatabase): ApiKeyDao {
        return database.apiKeyDao()
    }
}
