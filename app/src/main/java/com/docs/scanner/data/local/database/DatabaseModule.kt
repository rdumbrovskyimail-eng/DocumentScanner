package com.docs.scanner.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docs.scanner.data.local.database.dao.DocumentDao
import com.docs.scanner.data.local.database.dao.FolderDao
import com.docs.scanner.data.local.database.dao.RecordDao
import com.docs.scanner.data.local.database.dao.TermDao
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
    
    // ✅ ИСПРАВЛЕННАЯ МИГРАЦИЯ 1 → 2 с правильными полями
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                // Проверяем, существует ли таблица terms
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='terms'")
                val tableExists = cursor.moveToFirst()
                cursor.close()
                
                if (!tableExists) {
                    println("✅ Creating terms table...")
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
                    println("✅ Terms table created successfully")
                } else {
                    println("ℹ️ Terms table already exists, skipping creation")
                }
                
                // Валидация структуры БД
                validateDatabaseStructure(db)
                
            } catch (e: Exception) {
                println("❌ Migration error: ${e.message}")
                throw e
            }
        }
    }
    
    private fun validateDatabaseStructure(db: SupportSQLiteDatabase) {
        val expectedTables = listOf("folders", "records", "documents", "terms")
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
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    println("✅ Database created successfully")
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    println("✅ Database opened successfully")
                    db.execSQL("PRAGMA foreign_keys=ON")
                    
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
}
