/*
 * DocumentScanner - App Database
 * Version: 7.2.1 (Build 721) - PRODUCTION READY 2026
 *
 * ✅ CRITICAL FIXES (Session 15):
 * - Added MIGRATION_18_19 for model column in translation_cache
 * - Schema version updated to 19
 * - ModelConstants integration
 */

package com.docs.scanner.data.local.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entity.*
import com.docs.scanner.domain.core.ModelConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import timber.log.Timber

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
        SearchHistoryEntity::class,
        // ─── Analytics Center (v21) ───
        AnalyticsTranslationEntity::class,
        AnalyticsTranslationFtsEntity::class,
        AnalyticsNoteEntity::class,
        AnalyticsNoteFtsEntity::class
    ],
    version = 21, // ✅ Bumped from 20 — Analytics Center tables added
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

    // ─── Analytics Center ───
    abstract fun analyticsTranslationDao(): AnalyticsTranslationDao
    abstract fun analyticsNoteDao(): AnalyticsNoteDao

    companion object {
        const val DATABASE_NAME = "document_scanner.db"
        const val DATABASE_VERSION = 21

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
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21 // ✅ Analytics Center
                )
                .addCallback(DatabaseCallback(context))
                .fallbackToDestructiveMigrationOnDowngrade()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }

        // ✅ ДОБАВЛЕНО: Миграция 19→20 (добавление колонки word_confidences)
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN word_confidences TEXT")
            }
        }

        /**
         * Migration 20 → 21: Analytics Center.
         *
         * Creates two autonomous storage areas:
         *   - analytics_translations  — mirror of every translation event
         *   - analytics_notes         — free-form notes ("Information Analysis")
         *
         * Each gets an FTS4 virtual table and four Room-style content-sync
         * triggers (matching the convention Room uses for documents_fts).
         *
         * No foreign keys: Analytics Center entries survive deletion of the
         * source documents/records by design.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Timber.d("🔄 Running migration 20 → 21: Analytics Center")

                // ─── 1. analytics_translations ────────────────────────────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `analytics_translations` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `translated_text` TEXT NOT NULL,
                        `original_text` TEXT,
                        `source_language` TEXT NOT NULL DEFAULT 'auto',
                        `target_language` TEXT NOT NULL DEFAULT 'ru',
                        `source_document_id` INTEGER,
                        `source_record_id` INTEGER,
                        `source_record_name` TEXT,
                        `source_folder_name` TEXT,
                        `user_modified` INTEGER NOT NULL DEFAULT 0,
                        `word_count` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_analytics_translations_created_at` ON `analytics_translations` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_analytics_translations_updated_at` ON `analytics_translations` (`updated_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_analytics_translations_target_language` ON `analytics_translations` (`target_language`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_analytics_translations_source_record_id` ON `analytics_translations` (`source_record_id`)")

                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `analytics_translations_fts`
                    USING FTS4(`translated_text` TEXT, `original_text` TEXT, content=`analytics_translations`)
                """.trimIndent())

                // Room-style FTS content sync triggers (same naming pattern as documents_fts).
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_analytics_translations_fts_BEFORE_UPDATE`
                    BEFORE UPDATE ON `analytics_translations`
                    BEGIN DELETE FROM `analytics_translations_fts` WHERE `docid`=OLD.`rowid`; END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_analytics_translations_fts_BEFORE_DELETE`
                    BEFORE DELETE ON `analytics_translations`
                    BEGIN DELETE FROM `analytics_translations_fts` WHERE `docid`=OLD.`rowid`; END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_analytics_translations_fts_AFTER_UPDATE`
                    AFTER UPDATE ON `analytics_translations`
                    BEGIN INSERT INTO `analytics_translations_fts`(`docid`, `translated_text`, `original_text`)
                        VALUES (NEW.`rowid`, NEW.`translated_text`, NEW.`original_text`); END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_analytics_translations_fts_AFTER_INSERT`
                    AFTER INSERT ON `analytics_translations`
                    BEGIN INSERT INTO `analytics_translations_fts`(`docid`, `translated_text`, `original_text`)
                        VALUES (NEW.`rowid`, NEW.`translated_text`, NEW.`original_text`); END
                """.trimIndent())

                Timber.d("  ↳ analytics_translations + FTS created")

                // ─── 2. analytics_notes ───────────────────────────────────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `analytics_notes` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `title` TEXT NOT NULL DEFAULT '',
                        `content` TEXT NOT NULL DEFAULT '',
                        `tags` TEXT NOT NULL DEFAULT '[]',
                        `color` TEXT,
                        `is_pinned` INTEGER NOT NULL DEFAULT 0,
                        `is_archived` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_analytics_notes_created_at` ON `analytics_notes` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_analytics_notes_updated_at` ON `analytics_notes` (`updated_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_analytics_notes_is_pinned` ON `analytics_notes` (`is_pinned`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_analytics_notes_is_archived` ON `analytics_notes` (`is_archived`)")

                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `analytics_notes_fts`
                    USING FTS4(`title` TEXT, `content` TEXT, content=`analytics_notes`)
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_analytics_notes_fts_BEFORE_UPDATE`
                    BEFORE UPDATE ON `analytics_notes`
                    BEGIN DELETE FROM `analytics_notes_fts` WHERE `docid`=OLD.`rowid`; END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_analytics_notes_fts_BEFORE_DELETE`
                    BEFORE DELETE ON `analytics_notes`
                    BEGIN DELETE FROM `analytics_notes_fts` WHERE `docid`=OLD.`rowid`; END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_analytics_notes_fts_AFTER_UPDATE`
                    AFTER UPDATE ON `analytics_notes`
                    BEGIN INSERT INTO `analytics_notes_fts`(`docid`, `title`, `content`)
                        VALUES (NEW.`rowid`, NEW.`title`, NEW.`content`); END
                """.trimIndent())

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_analytics_notes_fts_AFTER_INSERT`
                    AFTER INSERT ON `analytics_notes`
                    BEGIN INSERT INTO `analytics_notes_fts`(`docid`, `title`, `content`)
                        VALUES (NEW.`rowid`, NEW.`title`, NEW.`content`); END
                """.trimIndent())

                Timber.d("  ↳ analytics_notes + FTS created")
                Timber.d("✅ Migration 20 → 21 completed")
            }
        }

        // ✅ ДОБАВЛЕНО: Миграция 18→19 (добавление колонки model)
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    Timber.d("🔄 Running migration 18 → 19: Adding model to translation_cache")
                    
                    // Попытка добавить колонку (может уже существовать)
                    try {
                        db.execSQL("ALTER TABLE translation_cache ADD COLUMN model TEXT")
                        Timber.d("✅ Added model column")
                    } catch (e: Exception) {
                        Timber.w(e, "⚠️ translation_cache.model might already exist")
                    }
                    
                    // Заполнить существующие записи дефолтным значением
                    db.execSQL("""
                        UPDATE translation_cache 
                        SET model = 'google_mlkit' 
                        WHERE model IS NULL
                    """)
                    Timber.d("✅ Updated existing rows with default model")
                    
                    // Пересоздать таблицу с NOT NULL constraint
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS translation_cache_new (
                            cache_key TEXT PRIMARY KEY NOT NULL,
                            original_text TEXT NOT NULL,
                            translated_text TEXT NOT NULL,
                            source_language TEXT NOT NULL,
                            target_language TEXT NOT NULL,
                            model TEXT NOT NULL DEFAULT 'google_mlkit',
                            timestamp INTEGER NOT NULL
                        )
                    """)
                    
                    db.execSQL("""
                        INSERT INTO translation_cache_new (
                            cache_key, original_text, translated_text,
                            source_language, target_language, model, timestamp
                        )
                        SELECT cache_key, original_text, translated_text,
                               source_language, target_language,
                               COALESCE(model, 'google_mlkit'),
                               timestamp
                        FROM translation_cache
                    """)
                    
                    db.execSQL("DROP TABLE translation_cache")
                    db.execSQL("ALTER TABLE translation_cache_new RENAME TO translation_cache")
                    
                    Timber.d("✅ Recreated table with NOT NULL constraint")
                    
                    // Восстановить индексы
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS index_translation_cache_timestamp 
                        ON translation_cache(timestamp ASC)
                    """)
                    
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS index_translation_cache_languages 
                        ON translation_cache(source_language, target_language)
                    """)
                    
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS index_translation_cache_model 
                        ON translation_cache(model)
                    """)
                    
                    Timber.d("✅ Created indices")
                    
                    Timber.d("✅ Migration 18→19 completed successfully")
                    
                } catch (e: Exception) {
                    Timber.e(e, "❌ Migration 18→19 FAILED")
                    throw e
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TYPE CONVERTERS
// ══════════════════════════════════════════════════════════════════════════════

class Converters {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { 
            json.encodeToString(
                ListSerializer(String.serializer()), 
                it
            ) 
        }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            try {
                json.decodeFromString(
                    ListSerializer(String.serializer()), 
                    it
                )
            } catch (e: Exception) {
                Timber.w(e, "⚠️ Failed to decode string list")
                null
            }
        }
    }

    @TypeConverter
    fun fromLongList(list: List<Long>?): String? {
        return list?.let { 
            json.encodeToString(
                ListSerializer(Long.serializer()), 
                it
            ) 
        }
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        return value?.let {
            try {
                json.decodeFromString(
                    ListSerializer(Long.serializer()), 
                    it
                )
            } catch (e: Exception) {
                Timber.w(e, "⚠️ Failed to decode long list")
                null
            }
        }
    }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String? {
        return map?.let { 
            json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()), 
                it
            ) 
        }
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return value?.let {
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), String.serializer()), 
                    it
                )
            } catch (e: Exception) {
                Timber.w(e, "⚠️ Failed to decode string map")
                null
            }
        }
    }

    @TypeConverter
    fun fromStringFloatMap(map: Map<String, Float>?): String? {
        return map?.let {
            json.encodeToString(
                MapSerializer(String.serializer(), Float.serializer()),
                it
            )
        }
    }

    @TypeConverter
    fun toStringFloatMap(value: String?): Map<String, Float>? {
        return value?.let {
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), Float.serializer()),
                    it
                )
            } catch (e: Exception) {
                Timber.w(e, "⚠️ Failed to decode string float map")
                null
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DATABASE CALLBACK
// ══════════════════════════════════════════════════════════════════════════════

class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {

    companion object {
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)

        // ✅ Fixed mmap_size
        try {
            db.execSQL("PRAGMA mmap_size=268435456")
            Timber.d("✅ Set fixed mmap_size: 256MB")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to set mmap_size")
        }

        try { db.execSQL("PRAGMA foreign_keys=ON") } catch (_: Exception) {}

        // Performance PRAGMAs
        try {
            db.execSQL("PRAGMA synchronous=NORMAL")
            db.execSQL("PRAGMA temp_store=MEMORY")
            db.execSQL("PRAGMA cache_size=-16384") // 16MB cache
            Timber.d("✅ Database PRAGMA settings applied")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to apply PRAGMA settings")
        }

        // Integrity check (Debug only)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val cursor = db.query("PRAGMA integrity_check")
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    if (result == "ok") {
                        Timber.d("✅ Database integrity check passed")
                    } else {
                        Timber.e("❌ Database integrity check failed: $result")
                    }
                }
                cursor.close()
            } catch (e: Exception) {
                Timber.w(e, "⚠️ Could not perform integrity check")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// MIGRATIONS
// ══════════════════════════════════════════════════════════════════════════════

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
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
        """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_due_date ON terms(due_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_is_completed ON terms(is_completed)")
        Timber.d("✅ Migration 1→2: terms table created")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        Timber.d("✅ Migration 2→3: position field added to documents")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN processing_status INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE documents ADD COLUMN ocr_confidence REAL")
        db.execSQL("ALTER TABLE documents ADD COLUMN file_size INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE documents ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE documents ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_processing_status ON documents(processing_status)")
        Timber.d("✅ Migration 3→4: processing fields added")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts 
            USING fts4(content="documents", original_text, translated_text)
            """
        )
        
        db.execSQL(
            """
            INSERT INTO documents_fts(rowid, original_text, translated_text)
            SELECT id, original_text, translated_text FROM documents
            """
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS translation_cache (
                cache_key TEXT PRIMARY KEY NOT NULL,
                original_text TEXT NOT NULL,
                translated_text TEXT NOT NULL,
                source_language TEXT NOT NULL,
                target_language TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_cache_timestamp ON translation_cache(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_translation_cache_languages ON translation_cache(source_language, target_language)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                query TEXT NOT NULL,
                result_count INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL
            )
            """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_timestamp ON search_history(timestamp)")

        db.execSQL("ALTER TABLE records ADD COLUMN source_language TEXT NOT NULL DEFAULT 'auto'")
        db.execSQL("ALTER TABLE records ADD COLUMN target_language TEXT NOT NULL DEFAULT 'en'")

        db.execSQL("ALTER TABLE documents ADD COLUMN detected_language TEXT")
        db.execSQL("ALTER TABLE documents ADD COLUMN source_language TEXT NOT NULL DEFAULT 'auto'")
        db.execSQL("ALTER TABLE documents ADD COLUMN target_language TEXT NOT NULL DEFAULT 'en'")

        db.execSQL("ALTER TABLE folders ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN color INTEGER")
        db.execSQL("ALTER TABLE folders ADD COLUMN icon TEXT")

        db.execSQL("ALTER TABLE records ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE records ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE records ADD COLUMN tags TEXT")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_is_pinned ON folders(is_pinned)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_is_archived ON folders(is_archived)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_is_pinned ON records(is_pinned)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_is_archived ON records(is_archived)")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_is_cancelled ON terms(is_cancelled)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_document_id ON terms(document_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_folder_id ON terms(folder_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_terms_is_completed_due_date ON terms(is_completed, due_date)")

        Timber.d("✅ Migration 4→5: FTS, caching, and language support added")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_created_at ON folders(created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_created_at ON records(created_at)")
        Timber.d("✅ Migration 5→6: Performance indices added")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 6→7: No schema changes")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 7→8: No schema changes")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 8→9: No schema changes")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 9→10: No schema changes")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 10→11: No schema changes")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 11→12: No schema changes")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 12→13: No schema changes")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 13→14: No schema changes")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 14→15: No schema changes")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 15→16: No schema changes")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("✅ Migration 16→17: Production ready v7.0.0")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            // ADD POSITION TO FOLDERS
            try {
                db.execSQL("ALTER TABLE folders ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                Timber.d("✅ Added position column to folders")
            } catch (e: Exception) {
                Timber.w(e, "⚠️ folders.position might already exist")
            }

            db.execSQL("""
                UPDATE folders 
                SET position = (
                    SELECT COUNT(*) 
                    FROM folders f2 
                    WHERE (f2.is_pinned > folders.is_pinned) 
                       OR (f2.is_pinned = folders.is_pinned AND f2.updated_at > folders.updated_at)
                )
            """)

            db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_position ON folders(position)")

            // ADD POSITION TO RECORDS
            try {
                db.execSQL("ALTER TABLE records ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                Timber.d("✅ Added position column to records")
            } catch (e: Exception) {
                Timber.w(e, "⚠️ records.position might already exist")
            }

            db.execSQL("""
                UPDATE records 
                SET position = (
                    SELECT COUNT(*) 
                    FROM records r2 
                    WHERE r2.folder_id = records.folder_id
                      AND ((r2.is_pinned > records.is_pinned) 
                           OR (r2.is_pinned = records.is_pinned AND r2.updated_at > records.updated_at))
                )
            """)

            db.execSQL("CREATE INDEX IF NOT EXISTS index_records_position ON records(position)")

            Timber.d("✅ Migration 17→18: Added position field to folders and records")

        } catch (e: Exception) {
            Timber.e(e, "❌ Migration 17→18 FAILED")
            throw e
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DATABASE EXTENSIONS
// ══════════════════════════════════════════════════════════════════════════════

suspend fun AppDatabase.clearAllData() = withContext(Dispatchers.IO) {
    try {
        clearAllTables()
        Timber.d("✅ All database tables cleared")
    } catch (e: Exception) {
        Timber.e(e, "❌ Failed to clear database")
    }
}

fun AppDatabase.getDatabaseSize(context: Context): Long {
    val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
    return if (dbFile.exists()) dbFile.length() else 0L
}

fun AppDatabase.getWalSize(context: Context): Long {
    val walFile = context.getDatabasePath("${AppDatabase.DATABASE_NAME}-wal")
    return if (walFile.exists()) walFile.length() else 0L
}

fun AppDatabase.getTotalDatabaseSize(context: Context): Long {
    val dbSize = getDatabaseSize(context)
    val walSize = getWalSize(context)
    val shmFile = context.getDatabasePath("${AppDatabase.DATABASE_NAME}-shm")
    val shmSize = if (shmFile.exists()) shmFile.length() else 0L
    return dbSize + walSize + shmSize
}

suspend fun AppDatabase.vacuum() = withContext(Dispatchers.IO) {
    try {
        openHelper.writableDatabase.execSQL("VACUUM")
        Timber.d("✅ Database vacuumed")
    } catch (e: Exception) {
        Timber.e(e, "❌ VACUUM failed")
    }
}

suspend fun AppDatabase.analyze() = withContext(Dispatchers.IO) {
    try {
        openHelper.writableDatabase.execSQL("ANALYZE")
        Timber.d("✅ Database analyzed")
    } catch (e: Exception) {
        Timber.e(e, "❌ ANALYZE failed")
    }
}

suspend fun AppDatabase.validateIntegrity(): Boolean = withContext(Dispatchers.IO) {
    try {
        val cursor = openHelper.readableDatabase.query("PRAGMA integrity_check")
        val result = if (cursor.moveToFirst()) {
            cursor.getString(0) == "ok"
        } else {
            false
        }
        cursor.close()

        if (result) {
            Timber.d("✅ Database integrity validated")
        } else {
            Timber.e("❌ Database integrity check failed")
        }

        result
    } catch (e: Exception) {
        Timber.e(e, "❌ Integrity validation failed")
        false
    }
}

data class DatabaseStats(
    val totalSize: Long,
    val walSize: Long,
    val folderCount: Int,
    val recordCount: Int,
    val documentCount: Int,
    val termCount: Int,
    val cacheEntries: Int
)

suspend fun AppDatabase.getStats(context: Context): DatabaseStats = withContext(Dispatchers.IO) {
    DatabaseStats(
        totalSize = getTotalDatabaseSize(context),
        walSize = getWalSize(context),
        folderCount = folderDao().getCount(),
        recordCount = recordDao().getCount(),
        documentCount = documentDao().getCount(),
        termCount = termDao().getActiveCount(),
        cacheEntries = translationCacheDao().getCount()
    )
}