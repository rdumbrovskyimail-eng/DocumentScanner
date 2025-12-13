package com.docs.scanner.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val dataStore = context.dataStore
    
    companion object {
        private val KEY_GEMINI_API = stringPreferencesKey("gemini_api_key")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        private val KEY_DRIVE_ENABLED = booleanPreferencesKey("drive_enabled")
        private val KEY_DRIVE_EMAIL = stringPreferencesKey("drive_email")
        private val KEY_LAST_BACKUP = stringPreferencesKey("last_backup_timestamp")
    }
    
    val geminiApiKey: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API]
    }
    
    suspend fun setGeminiApiKey(key: String) {
        dataStore.edit { prefs ->
            prefs[KEY_GEMINI_API] = key
        }
    }
    
    suspend fun getGeminiApiKey(): String? {
        return dataStore.data.first()[KEY_GEMINI_API]
    }
    
    val isFirstLaunch: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: true
    }
    
    suspend fun getIsFirstLaunch(): Boolean {
        return dataStore.data.first()[KEY_FIRST_LAUNCH] ?: true
    }
    
    suspend fun setFirstLaunchCompleted() {
        dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = false
        }
    }
    
    val driveEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DRIVE_ENABLED] ?: false
    }
    
    suspend fun setDriveEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DRIVE_ENABLED] = enabled
        }
    }
    
    val driveEmail: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DRIVE_EMAIL]
    }
    
    suspend fun setDriveEmail(email: String?) {
        dataStore.edit { prefs ->
            if (email != null) {
                prefs[KEY_DRIVE_EMAIL] = email
            } else {
                prefs.remove(KEY_DRIVE_EMAIL)
            }
        }
    }
    
    val lastBackupTimestamp: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_BACKUP]
    }
    
    suspend fun setLastBackupTimestamp(timestamp: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP] = timestamp
        }
    }
    
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}