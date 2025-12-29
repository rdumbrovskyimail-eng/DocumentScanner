package com.docs.scanner.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SettingsRepository.
 * 
 * Responsibilities:
 * - Manage app settings via DataStore
 * - Manage API keys via EncryptedKeyStorage
 * - Track first launch state
 * 
 * Storage:
 * - API keys: EncryptedKeyStorage (encrypted with Android Keystore)
 * - Other settings: DataStore Preferences (unencrypted)
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val encryptedKeyStorage: EncryptedKeyStorage
) : SettingsRepository {
    
    companion object {
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        private val KEY_API_KEY = stringPreferencesKey("api_key_legacy") // Deprecated, kept for migration
    }
    
    override suspend fun getApiKey(): String? {
        return try {
            // Try to get from encrypted storage first
            encryptedKeyStorage.getActiveApiKey()
        } catch (e: Exception) {
            android.util.Log.e("SettingsRepository", "Failed to get API key", e)
            null
        }
    }
    
    override suspend fun setApiKey(key: String) {
        try {
            require(key.isNotBlank()) { "API key cannot be blank" }
            
            // Save to encrypted storage
            encryptedKeyStorage.setActiveApiKey(key)
            
            // Clear legacy key from DataStore if exists
            dataStore.edit { preferences ->
                preferences.remove(KEY_API_KEY)
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsRepository", "Failed to set API key", e)
            throw e
        }
    }
    
    override suspend fun isFirstLaunch(): Boolean {
        return try {
            dataStore.data.map { preferences ->
                preferences[KEY_FIRST_LAUNCH] ?: true
            }.first()
        } catch (e: Exception) {
            android.util.Log.e("SettingsRepository", "Failed to check first launch", e)
            true // Default to true on error
        }
    }
    
    override suspend fun setFirstLaunchCompleted() {
        try {
            dataStore.edit { preferences ->
                preferences[KEY_FIRST_LAUNCH] = false
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsRepository", "Failed to set first launch completed", e)
            throw e
        }
    }
}