package com.docs.scanner.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// ============================================
// ENCRYPTED API KEY STORAGE
// ============================================

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
    }
    
    // ============================================
    // ACTIVE KEY (with error handling)
    // ============================================
    
    fun getActiveApiKey(): String? {
        return try {
            encryptedPrefs.getString(KEY_ACTIVE_API_KEY, null)
        } catch (e: Exception) {
            println("⚠️ Failed to decrypt active API key: ${e.message}")
            attemptRecovery()
            null
        }
    }
    
    fun setActiveApiKey(key: String) {
        // ✅ ДОБАВЛЕНО: Validation (Session 3 Problem #4)
        require(key.isNotBlank()) { "API key cannot be blank" }
        require(key.length >= 30) { "API key too short" }
        require(key.startsWith("AIza")) { "Invalid Gemini API key format" }
        
        try {
            encryptedPrefs.edit().putString(KEY_ACTIVE_API_KEY, key).apply()
        } catch (e: Exception) {
            println("❌ Failed to encrypt API key: ${e.message}")
            throw IllegalStateException("Cannot save API key securely", e)
        }
    }
    
    fun removeActiveApiKey() {
        try {
            encryptedPrefs.edit().remove(KEY_ACTIVE_API_KEY).apply()
        } catch (e: Exception) {
            println("⚠️ Failed to remove active API key: ${e.message}")
        }
    }
    
    // ============================================
    // ALL KEYS (JSON format with Gson)
    // ============================================
    
    fun getAllKeys(): List<ApiKeyData> {
        return try {
            val json = encryptedPrefs.getString(KEY_API_KEYS_JSON, null) ?: return emptyList()
            parseApiKeysJson(json)
        } catch (e: Exception) {
            println("⚠️ Failed to get all API keys: ${e.message}")
            emptyList()
        }
    }
    
    fun saveAllKeys(keys: List<ApiKeyData>) {
        try {
            val json = serializeApiKeysJson(keys)
            encryptedPrefs.edit().putString(KEY_API_KEYS_JSON, json).apply()
        } catch (e: Exception) {
            println("❌ Failed to save API keys: ${e.message}")
            throw IllegalStateException("Cannot save API keys securely", e)
        }
    }
    
    fun addKey(key: ApiKeyData) {
        try {
            val currentKeys = getAllKeys().toMutableList()
            
            // Деактивировать все остальные ключи
            val updatedKeys = currentKeys.map { it.copy(isActive = false) }.toMutableList()
            
            // Добавить новый активный ключ
            updatedKeys.add(key.copy(isActive = true))
            
            saveAllKeys(updatedKeys)
            setActiveApiKey(key.key)
        } catch (e: Exception) {
            println("❌ Failed to add API key: ${e.message}")
            throw e
        }
    }
    
    fun activateKey(keyId: String) {
        try {
            val keys = getAllKeys()
            val updatedKeys = keys.map { 
                if (it.id == keyId) {
                    setActiveApiKey(it.key)
                    it.copy(isActive = true)
                } else {
                    it.copy(isActive = false)
                }
            }
            saveAllKeys(updatedKeys)
        } catch (e: Exception) {
            println("❌ Failed to activate key: ${e.message}")
            throw e
        }
    }
    
    fun deleteKey(keyId: String) {
        try {
            val keys = getAllKeys()
            val updatedKeys = keys.filter { it.id != keyId }
            
            // Если удаляем активный ключ, очищаем активный
            val deletedKey = keys.find { it.id == keyId }
            if (deletedKey?.isActive == true) {
                removeActiveApiKey()
            }
            
            saveAllKeys(updatedKeys)
        } catch (e: Exception) {
            println("❌ Failed to delete key: ${e.message}")
            throw e
        }
    }
    
    fun clear() {
        try {
            encryptedPrefs.edit().clear().apply()
            println("✅ Cleared all encrypted storage")
        } catch (e: Exception) {
            println("❌ Failed to clear storage: ${e.message}")
        }
    }
    
    // ✅ ДОБАВЛЕНО: Recovery mechanism (Session 3 Problem #4)
    private fun attemptRecovery() {
        try {
            println("🔧 Attempting to recover encrypted storage...")
            encryptedPrefs.edit().clear().apply()
            println("✅ Cleared corrupted encrypted storage")
        } catch (e: Exception) {
            println("❌ Recovery failed: ${e.message}")
        }
    }
    
    // ============================================
    // JSON SERIALIZATION (Using Gson - Session 3 Problem #4)
    // ============================================
    
    // ✅ ИСПРАВЛЕНО: Используем Gson вместо custom parser (Session 3 Problem #4)
    private fun parseApiKeysJson(json: String): List<ApiKeyData> {
        return try {
            val type = object : TypeToken<List<ApiKeyData>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            println("⚠️ Failed to parse API keys JSON: ${e.message}")
            emptyList()
        }
    }
    
    private fun serializeApiKeysJson(keys: List<ApiKeyData>): String {
        return try {
            Gson().toJson(keys)
        } catch (e: Exception) {
            println("❌ Failed to serialize API keys: ${e.message}")
            "[]"
        }
    }
}

// ============================================
// DATA CLASS
// ============================================

data class ApiKeyData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val key: String,
    val label: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)