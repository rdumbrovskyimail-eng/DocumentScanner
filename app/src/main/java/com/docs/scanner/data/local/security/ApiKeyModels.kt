package com.docs.scanner.data.local.security

/**
 * PUBLIC API MODEL
 * 
 * Represents a single API key entry with metadata for failover management.
 * Used by GeminiKeyManager and UI layers.
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
    
    /**
     * Convert to internal storage model.
     */
    internal fun toStoredApiKey(): StoredApiKey {
        return StoredApiKey(
            id = java.util.UUID.randomUUID().toString(),
            key = key,
            label = label.ifBlank { null },
            isActive = isActive,
            createdAt = addedAt,
            lastUsedAt = lastUsedAt,
            lastErrorAt = lastErrorAt,
            errorCount = errorCount
        )
    }
}

/**
 * INTERNAL STORAGE MODEL
 * 
 * Data class for encrypted storage persistence.
 * Maps 1:1 with encrypted SharedPreferences data.
 * 
 * @property id Unique identifier (auto-generated UUID)
 * @property key The actual API key (encrypted at rest)
 * @property label Optional user-friendly label
 * @property isActive Whether this key is currently active
 * @property createdAt Timestamp of creation
 * @property lastUsedAt Timestamp of last successful use
 * @property lastErrorAt Timestamp of last error
 * @property errorCount Consecutive error count
 */
internal data class StoredApiKey(
    val id: String = java.util.UUID.randomUUID().toString(),
    val key: String,
    val label: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val lastErrorAt: Long? = null,
    val errorCount: Int = 0
) {
    /**
     * Convert to public API model.
     */
    fun toApiKeyEntry(): ApiKeyEntry {
        return ApiKeyEntry(
            key = key,
            label = label ?: "Unlabeled Key",
            addedAt = createdAt,
            lastUsedAt = lastUsedAt,
            lastErrorAt = lastErrorAt,
            errorCount = errorCount,
            isActive = isActive
        )
    }
}

/**
 * Serialization helpers for storing in EncryptedSharedPreferences.
 */
internal object StoredApiKeySerializer {
    private const val DELIMITER = "|||"
    private const val FIELD_DELIMITER = ":::"
    
    fun serialize(entries: List<StoredApiKey>): String {
        return entries.joinToString(DELIMITER) { entry ->
            listOf(
                entry.id,
                entry.key,
                entry.label ?: "",
                entry.isActive.toString(),
                entry.createdAt.toString(),
                entry.lastUsedAt?.toString() ?: "",
                entry.lastErrorAt?.toString() ?: "",
                entry.errorCount.toString()
            ).joinToString(FIELD_DELIMITER)
        }
    }
    
    fun deserialize(data: String): List<StoredApiKey> {
        if (data.isBlank()) return emptyList()
        
        return try {
            data.split(DELIMITER).mapNotNull { entryStr ->
                val fields = entryStr.split(FIELD_DELIMITER)
                
                when {
                    // Full format (8 fields)
                    fields.size >= 8 && fields[1].isNotBlank() -> {
                        StoredApiKey(
                            id = fields[0],
                            key = fields[1],
                            label = fields[2].ifBlank { null },
                            isActive = fields[3].toBooleanStrictOrNull() ?: false,
                            createdAt = fields[4].toLongOrNull() ?: System.currentTimeMillis(),
                            lastUsedAt = fields[5].toLongOrNull(),
                            lastErrorAt = fields[6].toLongOrNull(),
                            errorCount = fields[7].toIntOrNull() ?: 0
                        )
                    }
                    // Legacy format (3-5 fields): id, key, label?, isActive?, createdAt?
                    fields.size >= 2 && fields[1].isNotBlank() -> {
                        StoredApiKey(
                            id = fields[0],
                            key = fields[1],
                            label = fields.getOrNull(2)?.ifBlank { null },
                            isActive = fields.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
                            createdAt = fields.getOrNull(4)?.toLongOrNull() ?: System.currentTimeMillis()
                        )
                    }
                    // Very old format: just the key
                    fields.size == 1 && fields[0].isNotBlank() -> {
                        StoredApiKey(
                            key = fields[0],
                            label = "Migrated Key"
                        )
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}