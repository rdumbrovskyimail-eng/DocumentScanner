/*
 * DocumentScanner - App Database
 * Version: 5.0.0 - Production Ready 2026 (SYNCHRONIZED)
 * 
 * ✅ Synchronized with Domain v4.1.0
 * ✅ Room Database with 7 entities
 * ✅ Optimized migrations
 * ✅ FTS4 full-text search
 */

package com.docs.scanner.data.local.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entity.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ══════════════════════════════════════════════════════════════════════════════
// DATABASE
// ══════════════════════════════════════════════════════════════════════════════

@Database(
    entities = [
        FolderEntity::class,
        RecordEntity::class,
        DocumentEntity::class,
        DocumentFtsEntity::class,
        TermEntity::class,
        TranslationCacheEntity::class,
        SearchHistoryEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    // ─────────────────────────────────────────────────────────────────────────
    // DAOs
    // ─────────────────────────────────────────────────────────────────────────
    
    abstract fun folderDao(): FolderDao
    abstract fun recordDao(): RecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun termDao(): TermDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    
    companion object {
        const val DATABASE_NAME = "document_scanner.db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
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
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TYPE CONVERTERS
// ══════════════════════════════════════════════════════════════════════════════

class Converters {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // ─────────────────────────────────────────────────────────────────────────
    // List<String> <-> JSON
    // ─────────────────────────────────────────────────────────────────────────
    
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { 
            try {
                json.decodeFromString<List<String>>(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // List<Long> <-> JSON
    // ─────────────────────────────────────────────────────────────────────────
    
    @TypeConverter
    fun fromLongList(list: List<Long>?): String? {
        return list?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        return value?.let {
            try {
                json.decodeFromString<List<Long>>(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Map<String, String> <-> JSON
    // ─────────────────────────────────────────────────────────────────────────
    
    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? {
        return map?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return value?.let {
            try {
                json.decodeFromString<Map<String, String>>(it)
            } catch (e: Exception) {
                null
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DATABASE CALLBACK
// ══════════════════════════════════════════════════════════════════════════════

class DatabaseCallback : RoomDatabase.Callback() {
    
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        
        // FTS triggers для автообновления индекса
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS documents_fts_insert 
            AFTER INSERT ON documents 
            BEGIN
                INSERT INTO documents_fts(rowid, original_text, translated_text)
                VALUES (NEW.id, NEW.original_text, NEW.translated_text);
            END
        """)
        
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS documents_fts_update 
            AFTER UPDATE ON documents 
            BEGIN
                UPDATE documents_fts 
                SET original_text = NEW.original_text, translated_text = NEW.translated_text
                WHERE rowid = NEW.id;
            END
        """)
        
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS documents_fts_delete 
            BEFORE DELETE ON documents 
            BEGIN
                DELETE FROM documents_fts WHERE rowid = OLD.id;
            END
        """)
    }
    
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        
        // ✅ NEW: Enable WAL mode for better concurrency (2026 best practice)
        db.execSQL("PRAGMA journal_mode=WAL")
        
        // ✅ NEW: Optimize for performance
        db.execSQL("PRAGMA synchronous=NORMAL")
        db.execSQL("PRAGMA temp_store=MEMORY")
        db.execSQL("PRAGMA mmap_size=30000000000") // 30GB memory-mapped I/O
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// MIGRATIONS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Migration 1→2: Добавлена таблица terms
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS terms (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                due_date INTEGER NOT NULL,
                reminder_minutes_before INTEGER NOT NULL DEFAULT 60,
                priority INTEGER NOT NULL DEFAULT 1,
                is_completed INTEGER NOT NULL DEFAULT 0,
                is_cancelled INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER,
                document_id INTEGER,
                folder_id INTEGER,
                color INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_due_date ON terms(due_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_is_completed ON terms(is_completed)")
    }
}

/**
 * Migration 2→3: Добавлено поле position в documents
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migration 3→4: Добавлены поля processing_status, ocr_confidence, file_size, width, height
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN processing_status INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE documents ADD COLUMN ocr_confidence REAL")
        db.execSQL("ALTER TABLE documents ADD COLUMN file_size INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE documents ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE documents ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_processing_status ON documents(processing_status)")
    }
}

/**
 * Migration 4→5: Добавлены FTS, translation_cache, search_history, языковые поля
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // FTS таблица
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts 
            USING fts4(content="documents", original_text, translated_text)
        """)
        
        // Заполнение FTS существующими данными
        db.execSQL("""
            INSERT INTO documents_fts(rowid, original_text, translated_text)
            SELECT id, original_text, translated_text FROM documents
        """)
        
        // Translation cache с языками
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
        db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_cache_timestamp ON translation_cache(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_cache_languages ON translation_cache(source_language, target_language)")
        
        // Search history
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                query TEXT NOT NULL,
                result_count INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_timestamp ON search_history(timestamp)")
        
        // Языковые поля в records
        db.execSQL("ALTER TABLE records ADD COLUMN source_language TEXT NOT NULL DEFAULT 'auto'")
        db.execSQL("ALTER TABLE records ADD COLUMN target_language TEXT NOT NULL DEFAULT 'en'")
        
        // Языковые поля в documents
        db.execSQL("ALTER TABLE documents ADD COLUMN detected_language TEXT")
        db.execSQL("ALTER TABLE documents ADD COLUMN source_language TEXT NOT NULL DEFAULT 'auto'")
        db.execSQL("ALTER TABLE documents ADD COLUMN target_language TEXT NOT NULL DEFAULT 'en'")
        
        // Поля архивации и закрепления
        db.execSQL("ALTER TABLE folders ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN color INTEGER")
        db.execSQL("ALTER TABLE folders ADD COLUMN icon TEXT")
        
        db.execSQL("ALTER TABLE records ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE records ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE records ADD COLUMN tags TEXT")
        
        // Индексы для новых полей
        db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_is_pinned ON folders(is_pinned)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_is_archived ON folders(is_archived)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_is_pinned ON records(is_pinned)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_is_archived ON records(is_archived)")
        
        // Поля для terms
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_is_cancelled ON terms(is_cancelled)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_document_id ON terms(document_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_folder_id ON terms(folder_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_is_completed_due_date ON terms(is_completed, due_date)")
    }
}

/**
 * ✅ NEW: Migration 5→6: ProcessingStatus refactoring compatibility
 * Технически ничего не меняется в схеме (processing_status уже Int),
 * но добавляем migration для явного указания версии
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Схема не меняется, но обновляем версию для совместимости
        // с Domain v4.1.0 (ProcessingStatus sealed interface)
        
        // ✅ Опционально: можно добавить индексы для производительности
        db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_created_at ON folders(created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_created_at ON records(created_at)")
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DATABASE EXTENSIONS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Очистка всех данных (для logout/reset)
 */
suspend fun AppDatabase.clearAllData() {
    clearAllTables()
}

/**
 * Получение размера базы данных
 */
fun AppDatabase.getDatabaseSize(context: Context): Long {
    val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
    return if (dbFile.exists()) dbFile.length() else 0L
}

/**
 * ✅ NEW: Get WAL file size (for complete storage calculation)
 */
fun AppDatabase.getWalSize(context: Context): Long {
    val walFile = context.getDatabasePath("${AppDatabase.DATABASE_NAME}-wal")
    return if (walFile.exists()) walFile.length() else 0L
}

/**
 * ✅ NEW: Get total database storage (DB + WAL + SHM)
 */
fun AppDatabase.getTotalDatabaseSize(context: Context): Long {
    val dbSize = getDatabaseSize(context)
    val walSize = getWalSize(context)
    val shmFile = context.getDatabasePath("${AppDatabase.DATABASE_NAME}-shm")
    val shmSize = if (shmFile.exists()) shmFile.length() else 0L
    return dbSize + walSize + shmSize
}

/**
 * ✅ NEW: Vacuum database (reclaim unused space)
 */
suspend fun AppDatabase.vacuum() {
    openHelper.writableDatabase.execSQL("VACUUM")
}

/**
 * ✅ NEW: Analyze database (update statistics for query optimizer)
 */
suspend fun AppDatabase.analyze() {
    openHelper.writableDatabase.execSQL("ANALYZE")
}