package com.docs.scanner.data.local.security

/**
 * Represents a single API key entry with metadata for failover management.
 * 
 * @property key The actual API key string
 * @property label User-friendly label (e.g., "Primary", "Backup 1")
 * @property addedAt Timestamp when key was added
 * @property lastUsedAt Timestamp of last successful use
 * @property lastErrorAt Timestamp of last error
 * @property errorCount Consecutive error count (resets on success)
 * @property isActive Whether key is enabled for use
 */
data class ApiKeyEntry(
    val key: String,
    val label: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val lastErrorAt: Long? = null,
    val errorCount: Int = 0,
    val isActive: Boolean = true
) {
    /**
     * Masked version of key for UI display.
     * Shows only last 8 characters.
     */
    val maskedKey: String
        get() = if (key.length > 8) "••••••••${key.takeLast(8)}" else "••••••••"
    
    /**
     * Check if key is currently in cooldown after errors.
     */
    fun isInCooldown(cooldownMs: Long = 5 * 60 * 1000L): Boolean {
        return lastErrorAt != null && 
               (System.currentTimeMillis() - lastErrorAt) < cooldownMs
    }
    
    /**
     * Check if key should be considered "healthy" for use.
     */
    fun isHealthy(maxErrors: Int = 3, cooldownMs: Long = 5 * 60 * 1000L): Boolean {
        return isActive && (errorCount < maxErrors || !isInCooldown(cooldownMs))
    }
}

/**
 * Serialization helpers for storing in EncryptedSharedPreferences.
 */
object ApiKeyEntrySerializer {
    private const val DELIMITER = "|||"
    private const val FIELD_DELIMITER = ":::"
    
    fun serialize(entries: List<ApiKeyEntry>): String {
        return entries.joinToString(DELIMITER) { entry ->
            listOf(
                entry.key,
                entry.label,
                entry.addedAt.toString(),
                entry.lastUsedAt?.toString() ?: "",
                entry.lastErrorAt?.toString() ?: "",
                entry.errorCount.toString(),
                entry.isActive.toString()
            ).joinToString(FIELD_DELIMITER)
        }
    }
    
    fun deserialize(data: String): List<ApiKeyEntry> {
        if (data.isBlank()) return emptyList()
        
        return try {
            data.split(DELIMITER).mapNotNull { entryStr ->
                val fields = entryStr.split(FIELD_DELIMITER)
                if (fields.size >= 7 && fields[0].isNotBlank()) {
                    ApiKeyEntry(
                        key = fields[0],
                        label = fields[1],
                        addedAt = fields[2].toLongOrNull() ?: System.currentTimeMillis(),
                        lastUsedAt = fields[3].toLongOrNull(),
                        lastErrorAt = fields[4].toLongOrNull(),
                        errorCount = fields[5].toIntOrNull() ?: 0,
                        isActive = fields[6].toBooleanStrictOrNull() ?: true
                    )
                } else if (fields.isNotEmpty() && fields[0].isNotBlank()) {
                    // Legacy format: just the key
                    ApiKeyEntry(key = fields[0], label = "Migrated")
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}