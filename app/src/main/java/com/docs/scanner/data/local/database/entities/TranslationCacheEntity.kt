package com.docs.scanner.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest

/**
 * Translation cache entity with language-aware caching.
 * 
 * 🔴 CRITICAL SESSION 3 FIX:
 * - ✅ Changed textHash → cacheKey (includes languages)
 * - ✅ Added sourceLanguage field
 * - ✅ Added targetLanguage field
 * - ✅ Updated generateCacheKey() to include languages
 * 
 * Database version: 4 → 5 (requires migration!)
 * 
 * Why language fields matter:
 * - "Hello" en→ru = "Привет"
 * - "Hello" en→zh = "你好"
 * - Without language fields: COLLISION! (one overwrites other)
 * - With language fields: BOTH can coexist ✅
 */
@Entity(
    tableName = "translation_cache",
    indices = [Index("timestamp")]
)
data class TranslationCacheEntity(
    @PrimaryKey
    val cacheKey: String,  // ✅ RENAMED from textHash (includes languages)
    
    val originalText: String,
    val translatedText: String,
    
    // ✅ NEW: Language metadata
    val sourceLanguage: String,
    val targetLanguage: String,
    
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Generate cache key with language awareness.
         * 
         * ✅ NEW: Includes source and target languages in hash
         * 
         * Example:
         * - generateCacheKey("Hello", "en", "ru") = "a591a6d40bf420..."
         * - generateCacheKey("Hello", "en", "zh") = "b702c8f51ca531..."
         * - Different hashes for same text but different languages!
         * 
         * @param text Text to translate
         * @param srcLang Source language code (e.g., "en", "auto")
         * @param tgtLang Target language code (e.g., "ru", "zh")
         * @return SHA-256 hash of "text|srcLang|tgtLang"
         */
        fun generateCacheKey(
            text: String,
            srcLang: String,
            tgtLang: String
        ): String {
            // Combine text + languages with separator
            val combined = "$text|$srcLang|$tgtLang"
            
            // Generate SHA-256 hash
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(combined.toByteArray())
            
            return bytes.joinToString("") { "%02x".format(it) }
        }
        
        /**
         * Check if cache entry is expired.
         * 
         * @param timestamp Entry creation timestamp
         * @param ttlDays Time-to-live in days (default: 30)
         * @return true if expired
         */
        fun isExpired(timestamp: Long, ttlDays: Int = 30): Boolean {
            val expiryTime = timestamp + (ttlDays * 24 * 60 * 60 * 1000L)
            return System.currentTimeMillis() > expiryTime
        }
        
        /**
         * Generate hash for old schema (for migration only).
         * 
         * ⚠️ DEPRECATED: Use generateCacheKey() instead
         */
        @Deprecated(
            message = "Use generateCacheKey(text, srcLang, tgtLang) instead",
            replaceWith = ReplaceWith("generateCacheKey(text, \"auto\", \"ru\")")
        )
        fun generateHash(text: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}