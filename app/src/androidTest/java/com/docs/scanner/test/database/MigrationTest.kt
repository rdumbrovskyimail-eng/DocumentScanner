package com.docs.scanner.test.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.docs.scanner.data.local.database.AppDatabase
import com.docs.scanner.data.local.database.AppDatabase.Companion.MIGRATION_18_19
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )
    
    @Test
    fun migrate18To19_addsModelColumn() {
        // GIVEN: Database version 18 with existing translation cache entry
        helper.createDatabase("test_db", 18).apply {
            execSQL("""
                INSERT INTO translation_cache 
                (cache_key, original_text, translated_text, source_language, target_language, timestamp)
                VALUES 
                ('test_key_1', 'Hello', 'Привет', 'en', 'ru', 1704067200000)
            """)
            close()
        }
        
        // WHEN: Migration to version 19
        helper.runMigrationsAndValidate("test_db", 19, true, MIGRATION_18_19)
        
        // THEN: Model column exists with default value
        helper.openDatabase("test_db").use { db ->
            val cursor = db.query("SELECT * FROM translation_cache WHERE cache_key = 'test_key_1'")
            assert(cursor.moveToFirst())
            
            val modelIndex = cursor.getColumnIndex("model")
            assertNotEquals(-1, modelIndex, "Column 'model' should exist")
            
            val model = cursor.getString(modelIndex)
            assertEquals("gemini-2.5-flash-lite", model, "Default model should be gemini-2.5-flash-lite")
            
            cursor.close()
        }
    }
}