package com.docs.scanner.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for Gemini API keys using EncryptedSharedPreferences.
 * 
 * Security:
 * - AES256_GCM encryption via MasterKey
 * - Keys never logged in plaintext
 * - Automatic recovery from corruption
 * 
 * 2026 Enhancement:
 * - Multi-key failover system with StoredApiKey model
 */
@Singleton
class EncryptedKeyStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val ioLock = Any()
    private val PREFS_NAME = "secure_api_keys"

    private fun buildPrefs(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val encryptedPrefs: SharedPreferences = runCatching { buildPrefs() }
        .getOrElse { e ->
            Timber.e(e, "Encrypted prefs corrupted — recreating")
            context.deleteSharedPreferences(PREFS_NAME)
            buildPrefs()
        }
    
    companion object {
        private const val KEY_ACTIVE_API_KEY = "active_api_key"
        private const val KEY_API_KEYS_LIST = "gemini_api_keys_list"
        
        // Validation constants
        private const val MIN_KEY_LENGTH = 35
        private const val EXPECTED_KEY_LENGTH = 39
        private const val KEY_PREFIX = "AIza"
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // LEGACY SINGLE KEY SUPPORT (for backward compatibility)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the legacy active API key.
     * @deprecated Use getAllApiKeys() instead
     */
    @Deprecated("Use getAllApiKeys() for multi-key support")
    fun getActiveApiKey(): String? {
        return try {
            val key = encryptedPrefs.getString(KEY_ACTIVE_API_KEY, null)
            if (key != null) {
                Timber.d("✅ Active API key retrieved (length: ${key.length})")
            }
            key
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to decrypt active API key")
            attemptRecovery()
            null
        }
    }
    
    /**
     * Sets the legacy active API key.
     * @deprecated Use addApiKey() instead
     */
    @Deprecated("Use addApiKey() for multi-key support")
    fun setActiveApiKey(key: String) {
        require(key.isNotBlank()) { "API key cannot be blank" }
        require(key.length >= MIN_KEY_LENGTH) { 
            "API key too short (expected $EXPECTED_KEY_LENGTH chars, got ${key.length})" 
        }
        
        if (!key.startsWith(KEY_PREFIX)) {
            Timber.w("⚠️ API key format may be invalid (expected prefix: $KEY_PREFIX)")
        }
        
        try {
            encryptedPrefs.edit().putString(KEY_ACTIVE_API_KEY, key).apply()
            Timber.d("✅ API key saved (length: ${key.length})")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to encrypt API key")
            throw IllegalStateException("Cannot save API key securely", e)
        }
    }
    
    fun removeActiveApiKey() {
        try {
            encryptedPrefs.edit().remove(KEY_ACTIVE_API_KEY).apply()
            Timber.d("✅ Active API key removed")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to remove active API key")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // MULTI-KEY MANAGEMENT (PRIMARY API)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets all stored API keys.
     * Returns PUBLIC API model (ApiKeyEntry).
     */
    fun getAllApiKeys(): List<ApiKeyEntry> {
        return try {
            val data = encryptedPrefs.getString(KEY_API_KEYS_LIST, null)
            if (data.isNullOrBlank()) {
                // Migration: check for single legacy key
                val legacyKey = getActiveApiKey()
                if (!legacyKey.isNullOrBlank()) {
                    val entry = StoredApiKey(key = legacyKey, label = "Primary", isActive = true)
                    saveAllKeysInternal(listOf(entry))
                    return listOf(entry.toApiKeyEntry())
                }
                emptyList()
            } else {
                StoredApiKeySerializer.deserialize(data).map { it.toApiKeyEntry() }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get API keys list")
            emptyList()
        }
    }
    
    /**
     * Saves all API keys (INTERNAL use only).
     * Accepts INTERNAL storage model.
     */
    private fun saveAllKeysInternal(entries: List<StoredApiKey>) {
        synchronized(ioLock) {
            try {
                val data = StoredApiKeySerializer.serialize(entries)
                encryptedPrefs.edit().putString(KEY_API_KEYS_LIST, data).commit()
                
                // Update legacy single key for backward compatibility
                if (entries.isNotEmpty()) {
                    val primary = entries.firstOrNull { it.isActive } ?: entries.first()
                    setActiveApiKey(primary.key)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save API keys list")
            }
        }
    }
    
    /**
     * Adds a new API key to the list.
     * 
     * @param key The API key string
     * @param label User-friendly label
     * @return true if added successfully
     */
    fun addApiKey(key: String, label: String = ""): Boolean {
        if (key.isBlank()) return false
        
        val existing = getAllApiKeys().map { it.toStoredApiKey() }
        
        // Check for duplicates
        if (existing.any { it.key == key }) {
            Timber.w("API key already exists")
            return false
        }
        
        // Max 5 keys
        if (existing.size >= 5) {
            Timber.w("Maximum 5 API keys allowed")
            return false
        }
        
        val newEntry = StoredApiKey(
            key = key,
            label = label.ifBlank { "Key ${existing.size + 1}" },
            isActive = existing.isEmpty() // First key is active by default
        )
        
        saveAllKeysInternal(existing + newEntry)
        Timber.i("✅ API key added: ${newEntry.toApiKeyEntry().maskedKey}")
        return true
    }
    
    /**
     * Removes an API key from the list.
     */
    fun removeApiKey(key: String): Boolean {
        val existing = getAllApiKeys().map { it.toStoredApiKey() }
        val updated = existing.filter { it.key != key }
        
        if (updated.size == existing.size) {
            return false // Key not found
        }
        
        saveAllKeysInternal(updated)
        
        // If we removed the last active key, deactivate legacy key
        if (updated.none { it.isActive }) {
            removeActiveApiKey()
        }
        
        Timber.i("🗑️ API key removed")
        return true
    }
    
    /**
     * Updates key statistics after successful use.
     */
    fun updateKeySuccess(key: String) {
        val existing = getAllApiKeys().map { it.toStoredApiKey() }
        val updated = existing.map { entry ->
            if (entry.key == key) {
                entry.copy(
                    lastUsedAt = System.currentTimeMillis(),
                    errorCount = 0 // Reset errors on success
                )
            } else entry
        }
        saveAllKeysInternal(updated)
    }
    
    /**
     * Updates key statistics after error.
     */
    fun updateKeyError(key: String) {
        synchronized(ioLock) {
            val existing = getAllApiKeys().map { it.toStoredApiKey() }
            val updated = existing.map { entry ->
                if (entry.key == key) {
                    entry.copy(
                        lastErrorAt = System.currentTimeMillis(),
                        errorCount = entry.errorCount + 1
                    )
                } else entry
            }
            saveAllKeysInternal(updated)
        }
    }
    
    /**
     * Deactivates a key (won't be used until reactivated).
     */
    fun deactivateKey(key: String) {
        val existing = getAllApiKeys().map { it.toStoredApiKey() }
        val updated = existing.map { entry ->
            if (entry.key == key) {
                entry.copy(isActive = false)
            } else entry
        }
        saveAllKeysInternal(updated)
        Timber.w("⚠️ API key deactivated: ${key.takeLast(8)}")
    }
    
    /**
     * Reactivates a previously deactivated key.
     */
    fun reactivateKey(key: String) {
        val existing = getAllApiKeys().map { it.toStoredApiKey() }
        val updated = existing.map { entry ->
            if (entry.key == key) {
                entry.copy(isActive = true, errorCount = 0)
            } else entry
        }
        saveAllKeysInternal(updated)
    }
    
    /**
     * Resets error counts for all keys.
     */
    fun resetAllKeyErrors() {
        val existing = getAllApiKeys().map { it.toStoredApiKey() }
        val updated = existing.map { entry ->
            entry.copy(errorCount = 0, lastErrorAt = null, isActive = true)
        }
        saveAllKeysInternal(updated)
        Timber.i("🔄 All API key errors reset")
    }
    
    /**
     * Sets a key as primary (moves to first position).
     */
    fun setKeyAsPrimary(key: String) {
        val existing = getAllApiKeys().map { it.toStoredApiKey() }
        val target = existing.find { it.key == key } ?: return
        val others = existing.filter { it.key != key }
        
        // Deactivate all others, activate target
        val updated = (listOf(target.copy(isActive = true)) + others.map { it.copy(isActive = false) })
        saveAllKeysInternal(updated)
        
        // Update legacy single key
        setActiveApiKey(key)
    }
    
    /**
     * Updates label for a key.
     */
    fun updateKeyLabel(key: String, newLabel: String) {
        val existing = getAllApiKeys().map { it.toStoredApiKey() }
        val updated = existing.map { entry ->
            if (entry.key == key) entry.copy(label = newLabel.ifBlank { null }) else entry
        }
        saveAllKeysInternal(updated)
    }
    
    /**
     * Clears all encrypted storage.
     */
    fun clear() {
        try {
            encryptedPrefs.edit().clear().apply()
            Timber.i("✅ Cleared all encrypted storage")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to clear storage")
        }
    }
    
    /**
     * Recovery mechanism for corrupted encrypted storage.
     */
    private fun attemptRecovery() {
        try {
            Timber.w("🔧 Attempting to recover encrypted storage...")
            encryptedPrefs.edit().clear().apply()
            Timber.i("✅ Cleared corrupted encrypted storage")
        } catch (e: Exception) {
            Timber.e(e, "❌ Recovery failed")
        }
    }
}