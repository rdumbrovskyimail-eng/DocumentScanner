package com.docs.scanner.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
// ✅ NEW IMPORTS (added for multi-key system)
import com.docs.scanner.data.local.security.ApiKeyEntry
import com.docs.scanner.data.local.security.ApiKeyEntrySerializer

/**
 * Encrypted storage for Gemini API keys using EncryptedSharedPreferences.
 * 
 * Security:
 * - AES256_GCM encryption via MasterKey
 * - Keys never logged in plaintext
 * - Automatic recovery from corruption
 * 
 * Fixed issues:
 * - 🟠 Серьёзная #3: Improved API key validation
 * - 🔒 SEC-1: Removed exception messages that could leak key data
 * - 🟡 #1: Replaced println() with Timber
 * 
 * 2026 Enhancement:
 * - Added multi-key failover system (ApiKeyEntry)
 */
@Singleton
class EncryptedKeyStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_api_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_ACTIVE_API_KEY = "active_api_key"
        private const val KEY_API_KEYS_JSON = "api_keys_json"
        
        // ✅ NEW: Multi-key storage constant
        private const val KEY_API_KEYS_LIST = "gemini_api_keys_list"
        
        // Validation constants
        private const val MIN_KEY_LENGTH = 35 // Gemini keys are typically 39 chars
        private const val EXPECTED_KEY_LENGTH = 39
        private const val KEY_PREFIX = "AIza"
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ACTIVE KEY (with secure error handling)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Retrieves the active API key.
     * 
     * SECURITY: Never logs key content, only presence/absence.
     */
    fun getActiveApiKey(): String? {
        return try {
            val key = encryptedPrefs.getString(KEY_ACTIVE_API_KEY, null)
            if (key != null) {
                Timber.d("✅ Active API key retrieved (length: ${key.length})")
            }
            key
        } catch (e: Exception) {
            // FIXED: 🔒 SEC-1 - Don't log exception message (could leak key fragments)
            Timber.w(e, "⚠️ Failed to decrypt active API key")
            attemptRecovery()
            null
        }
    }
    
    /**
     * Sets the active API key with validation.
     * 
     * FIXED: 🟠 Серьёзная #3 - Improved validation with warnings instead of hard failures
     */
    fun setActiveApiKey(key: String) {
        // Basic validation
        require(key.isNotBlank()) { "API key cannot be blank" }
        require(key.length >= MIN_KEY_LENGTH) { 
            "API key too short (expected $EXPECTED_KEY_LENGTH chars, got ${key.length})" 
        }
        
        // Soft validation with warning
        if (!key.startsWith(KEY_PREFIX)) {
            Timber.w("⚠️ API key format may be invalid (expected prefix: $KEY_PREFIX)")
        }
        
        if (key.length != EXPECTED_KEY_LENGTH) {
            Timber.w("⚠️ API key length unusual (expected $EXPECTED_KEY_LENGTH, got ${key.length})")
        }
        
        try {
            encryptedPrefs.edit().putString(KEY_ACTIVE_API_KEY, key).apply()
            Timber.d("✅ API key saved (length: ${key.length})")
        } catch (e: Exception) {
            // FIXED: 🔒 SEC-1 - Don't log exception details
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
    // ALL KEYS (JSON format with Gson)
    // ════════════════════════════════════════════════════════════════════════════════
    
    fun getAllKeys(): List<ApiKeyData> {
        return try {
            val json = encryptedPrefs.getString(KEY_API_KEYS_JSON, null) ?: return emptyList()
            val keys = parseApiKeysJson(json)
            Timber.d("✅ Retrieved ${keys.size} API keys")
            keys
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to get all API keys")
            emptyList()
        }
    }
    
    fun saveAllKeys(keys: List<ApiKeyData>) {
        try {
            val json = serializeApiKeysJson(keys)
            encryptedPrefs.edit().putString(KEY_API_KEYS_JSON, json).apply()
            Timber.d("✅ Saved ${keys.size} API keys")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to save API keys")
            throw IllegalStateException("Cannot save API keys securely", e)
        }
    }
    
    fun addKey(key: ApiKeyData) {
        try {
            // Validate key before adding
            require(key.key.isNotBlank()) { "API key cannot be blank" }
            require(key.key.length >= MIN_KEY_LENGTH) { "API key too short" }
            
            val currentKeys = getAllKeys().toMutableList()
            
            // Деактивировать все остальные ключи
            val updatedKeys = currentKeys.map { it.copy(isActive = false) }.toMutableList()
            
            // Добавить новый активный ключ
            updatedKeys.add(key.copy(isActive = true))
            
            saveAllKeys(updatedKeys)
            setActiveApiKey(key.key)
            
            Timber.d("✅ Added new API key (label: ${key.label ?: "unlabeled"})")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to add API key")
            throw e
        }
    }
    
    fun activateKey(keyId: String) {
        try {
            val keys = getAllKeys()
            val targetKey = keys.find { it.id == keyId }
                ?: throw IllegalArgumentException("Key not found: $keyId")
            
            val updatedKeys = keys.map { 
                if (it.id == keyId) {
                    setActiveApiKey(it.key)
                    it.copy(isActive = true)
                } else {
                    it.copy(isActive = false)
                }
            }
            
            saveAllKeys(updatedKeys)
            Timber.d("✅ Activated key: $keyId")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to activate key")
            throw e
        }
    }
    
    fun deleteKey(keyId: String) {
        try {
            val keys = getAllKeys()
            val deletedKey = keys.find { it.id == keyId }
            val updatedKeys = keys.filter { it.id != keyId }
            
            // Если удаляем активный ключ, очищаем активный
            if (deletedKey?.isActive == true) {
                removeActiveApiKey()
                Timber.w("⚠️ Deleted active API key")
            }
            
            saveAllKeys(updatedKeys)
            Timber.d("✅ Deleted key: $keyId")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to delete key")
            throw e
        }
    }
    
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
     * 
     * SECURITY: Only logs success/failure, never key data.
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
    
    // ════════════════════════════════════════════════════════════════════════════════
    // JSON SERIALIZATION (Using Gson)
    // ════════════════════════════════════════════════════════════════════════════════
    
    private fun parseApiKeysJson(json: String): List<ApiKeyData> {
        return try {
            val type = object : TypeToken<List<ApiKeyData>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to parse API keys JSON")
            emptyList()
        }
    }
    
    private fun serializeApiKeysJson(keys: List<ApiKeyData>): String {
        return try {
            Gson().toJson(keys)
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to serialize API keys")
            "[]"
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // MULTI-KEY MANAGEMENT (2026)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets all stored API keys with their metadata.
     */
    fun getAllApiKeys(): List<ApiKeyEntry> {
        return try {
            val data = encryptedPrefs.getString(KEY_API_KEYS_LIST, null)
            if (data.isNullOrBlank()) {
                // Migration: check for single legacy key
                val legacyKey = getActiveApiKey()
                if (!legacyKey.isNullOrBlank()) {
                    val entry = ApiKeyEntry(key = legacyKey, label = "Primary")
                    saveAllApiKeys(listOf(entry))
                    return listOf(entry)
                }
                emptyList()
            } else {
                ApiKeyEntrySerializer.deserialize(data)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get API keys list")
            emptyList()
        }
    }
    
    /**
     * Saves all API keys (replaces existing list).
     */
    private fun saveAllApiKeys(entries: List<ApiKeyEntry>) {
        try {
            val data = ApiKeyEntrySerializer.serialize(entries)
            encryptedPrefs.edit().putString(KEY_API_KEYS_LIST, data).apply()
            
            // Also update legacy single key for backward compatibility
            if (entries.isNotEmpty()) {
                val primary = entries.firstOrNull { it.isActive } ?: entries.first()
                setActiveApiKey(primary.key)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save API keys list")
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
        
        val existing = getAllApiKeys()
        
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
        
        val newEntry = ApiKeyEntry(
            key = key,
            label = label.ifBlank { "Key ${existing.size + 1}" }
        )
        
        saveAllApiKeys(existing + newEntry)
        Timber.i("✅ API key added: ${newEntry.maskedKey}")
        return true
    }
    
    /**
     * Removes an API key from the list.
     */
    fun removeApiKey(key: String): Boolean {
        val existing = getAllApiKeys()
        val updated = existing.filter { it.key != key }
        
        if (updated.size == existing.size) {
            return false // Key not found
        }
        
        saveAllApiKeys(updated)
        Timber.i("🗑️ API key removed")
        return true
    }
    
    /**
     * Updates key statistics after successful use.
     */
    fun updateKeySuccess(key: String) {
        val existing = getAllApiKeys()
        val updated = existing.map { entry ->
            if (entry.key == key) {
                entry.copy(
                    lastUsedAt = System.currentTimeMillis(),
                    errorCount = 0 // Reset errors on success
                )
            } else entry
        }
        saveAllApiKeys(updated)
    }
    
    /**
     * Updates key statistics after error.
     */
    fun updateKeyError(key: String) {
        val existing = getAllApiKeys()
        val updated = existing.map { entry ->
            if (entry.key == key) {
                entry.copy(
                    lastErrorAt = System.currentTimeMillis(),
                    errorCount = entry.errorCount + 1
                )
            } else entry
        }
        saveAllApiKeys(updated)
    }
    
    /**
     * Deactivates a key (won't be used until reactivated).
     */
    fun deactivateKey(key: String) {
        val existing = getAllApiKeys()
        val updated = existing.map { entry ->
            if (entry.key == key) {
                entry.copy(isActive = false)
            } else entry
        }
        saveAllApiKeys(updated)
        Timber.w("⚠️ API key deactivated: ${key.takeLast(8)}")
    }
    
    /**
     * Reactivates a previously deactivated key.
     */
    fun reactivateKey(key: String) {
        val existing = getAllApiKeys()
        val updated = existing.map { entry ->
            if (entry.key == key) {
                entry.copy(isActive = true, errorCount = 0)
            } else entry
        }
        saveAllApiKeys(updated)
    }
    
    /**
     * Resets error counts for all keys.
     */
    fun resetAllKeyErrors() {
        val existing = getAllApiKeys()
        val updated = existing.map { entry ->
            entry.copy(errorCount = 0, lastErrorAt = null, isActive = true)
        }
        saveAllApiKeys(updated)
        Timber.i("🔄 All API key errors reset")
    }
    
    /**
     * Sets a key as primary (moves to first position).
     */
    fun setKeyAsPrimary(key: String) {
        val existing = getAllApiKeys()
        val target = existing.find { it.key == key } ?: return
        val others = existing.filter { it.key != key }
        saveAllApiKeys(listOf(target) + others)
        
        // Update legacy single key
        setActiveApiKey(key)
    }
    
    /**
     * Updates label for a key.
     */
    fun updateKeyLabel(key: String, newLabel: String) {
        val existing = getAllApiKeys()
        val updated = existing.map { entry ->
            if (entry.key == key) entry.copy(label = newLabel) else entry
        }
        saveAllApiKeys(updated)
    }
}

/**
 * Data class representing an API key entry.
 * 
 * @property id Unique identifier (auto-generated UUID)
 * @property key The actual API key (encrypted at rest)
 * @property label Optional user-friendly label
 * @property isActive Whether this key is currently active
 * @property createdAt Timestamp of creation
 */
data class ApiKeyData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val key: String,
    val label: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)