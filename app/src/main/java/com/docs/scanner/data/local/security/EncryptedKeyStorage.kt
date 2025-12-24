package com.docs.scanner.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    // ACTIVE KEY
    // ============================================
    
    fun getActiveApiKey(): String? {
        return encryptedPrefs.getString(KEY_ACTIVE_API_KEY, null)
    }
    
    fun setActiveApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_ACTIVE_API_KEY, key).apply()
    }
    
    fun removeActiveApiKey() {
        encryptedPrefs.edit().remove(KEY_ACTIVE_API_KEY).apply()
    }
    
    // ============================================
    // ALL KEYS (JSON format)
    // ============================================
    
    fun getAllKeys(): List<ApiKeyData> {
        val json = encryptedPrefs.getString(KEY_API_KEYS_JSON, null) ?: return emptyList()
        return try {
            parseApiKeysJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun saveAllKeys(keys: List<ApiKeyData>) {
        val json = serializeApiKeysJson(keys)
        encryptedPrefs.edit().putString(KEY_API_KEYS_JSON, json).apply()
    }
    
    fun addKey(key: ApiKeyData) {
        val currentKeys = getAllKeys().toMutableList()
        
        // Деактивировать все остальные ключи
        val updatedKeys = currentKeys.map { it.copy(isActive = false) }.toMutableList()
        
        // Добавить новый активный ключ
        updatedKeys.add(key.copy(isActive = true))
        
        saveAllKeys(updatedKeys)
        setActiveApiKey(key.key)
    }
    
    fun activateKey(keyId: String) {
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
    }
    
    fun deleteKey(keyId: String) {
        val keys = getAllKeys()
        val updatedKeys = keys.filter { it.id != keyId }
        
        // Если удаляем активный ключ, очищаем активный
        val deletedKey = keys.find { it.id == keyId }
        if (deletedKey?.isActive == true) {
            removeActiveApiKey()
        }
        
        saveAllKeys(updatedKeys)
    }
    
    fun clear() {
        encryptedPrefs.edit().clear().apply()
    }
    
    // ============================================
    // JSON SERIALIZATION (Simple implementation)
    // ============================================
    
    private fun parseApiKeysJson(json: String): List<ApiKeyData> {
        // Simple JSON parsing without external library
        // Format: [{"id":"uuid","key":"AIza...","label":"Work","isActive":true,"createdAt":123}]
        
        val result = mutableListOf<ApiKeyData>()
        val items = json.trim().removeSurrounding("[", "]").split("},")
        
        items.forEach { item ->
            try {
                val cleanItem = item.trim().removeSuffix("}").removePrefix("{")
                val parts = cleanItem.split(",")
                
                var id = ""
                var key = ""
                var label: String? = null
                var isActive = false
                var createdAt = 0L
                
                parts.forEach { part ->
                    val (k, v) = part.split(":").map { it.trim().removeSurrounding("\"") }
                    when (k) {
                        "id" -> id = v
                        "key" -> key = v
                        "label" -> label = v.takeIf { it != "null" }
                        "isActive" -> isActive = v.toBoolean()
                        "createdAt" -> createdAt = v.toLong()
                    }
                }
                
                if (id.isNotEmpty() && key.isNotEmpty()) {
                    result.add(ApiKeyData(id, key, label, isActive, createdAt))
                }
            } catch (e: Exception) {
                // Skip malformed entries
            }
        }
        
        return result
    }
    
    private fun serializeApiKeysJson(keys: List<ApiKeyData>): String {
        return keys.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        ) { key ->
            """{"id":"${key.id}","key":"${key.key}","label":${if (key.label != null) "\"${key.label}\"" else "null"},"isActive":${key.isActive},"createdAt":${key.createdAt}}"""
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
