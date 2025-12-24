package com.docs.scanner.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest

@Entity(tableName = "translation_cache")
data class TranslationCacheEntity(
    @PrimaryKey
    val textHash: String,
    val originalText: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // Generate hash for text
        fun generateHash(text: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
        
        // Check if cache is expired (default: 30 days)
        fun isExpired(timestamp: Long, ttlDays: Int = 30): Boolean {
            val expiryTime = timestamp + (ttlDays * 24 * 60 * 60 * 1000L)
            return System.currentTimeMillis() > expiryTime
        }
    }
}
