package com.docs.scanner.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore for app settings.
 * 
 * 🔴 CRITICAL SESSION 3 FIX:
 * - ❌ REMOVED: API key storage (was plain text - SECURITY RISK!)
 * - ✅ API keys now stored in EncryptedKeyStorage only
 * - ✅ Added migration function to move old keys to encrypted storage
 * - ✅ Added missing settings (theme, OCR language, auto-translate, cache)
 * - ✅ Added .catch{} for all Flows (error handling)
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val dataStore = context.dataStore
    
    companion object {
        // ❌ REMOVED: KEY_GEMINI_API - moved to EncryptedKeyStorage!
        
        // Onboarding & First Launch
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        
        // Google Drive Backup
        private val KEY_DRIVE_ENABLED = booleanPreferencesKey("drive_enabled")
        private val KEY_DRIVE_EMAIL = stringPreferencesKey("drive_email")
        private val KEY_LAST_BACKUP = stringPreferencesKey("last_backup_timestamp")
        
        // ✅ NEW: UI Settings
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        
        // ✅ NEW: OCR Settings
        private val KEY_OCR_LANGUAGE = stringPreferencesKey("ocr_language")
        private val KEY_TRANSLATION_TARGET = stringPreferencesKey("translation_target")
        private val KEY_AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")
        private val KEY_SAVE_ORIGINALS = booleanPreferencesKey("save_originals")
        
        // ✅ NEW: Cache Settings
        private val KEY_CACHE_ENABLED = booleanPreferencesKey("cache_enabled")
        private val KEY_CACHE_TTL = intPreferencesKey("cache_ttl_days")
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 🔴 CRITICAL: API KEY MIGRATION
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Migrate API key from plain text DataStore to EncryptedKeyStorage.
     * 
     * ⚠️ MUST BE CALLED ONCE on app startup!
     * See App.kt onCreate() or MainActivity.
     */
    suspend fun migrateApiKeyToEncrypted(encryptedStorage: EncryptedKeyStorage) {
        try {
            // Check if old key exists
            val oldKey = dataStore.data.first()[stringPreferencesKey("gemini_api_key")]
            
            if (!oldKey.isNullOrBlank()) {
                // Migrate to encrypted storage
                encryptedStorage.setActiveApiKey(oldKey)
                
                // Remove from DataStore
                dataStore.edit { prefs ->
                    prefs.remove(stringPreferencesKey("gemini_api_key"))
                }
                
                android.util.Log.d("SettingsDataStore", "✅ API key migrated to encrypted storage")
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsDataStore", "❌ Failed to migrate API key", e)
        }
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ONBOARDING & FIRST LAUNCH
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    val isFirstLaunch: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_FIRST_LAUNCH] ?: true }
    
    suspend fun getIsFirstLaunch(): Boolean {
        return dataStore.data.first()[KEY_FIRST_LAUNCH] ?: true
    }
    
    suspend fun setFirstLaunchCompleted() {
        dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = false
        }
    }
    
    val isOnboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }
    
    suspend fun setOnboardingCompleted() {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
        }
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // GOOGLE DRIVE BACKUP
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    val driveEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_DRIVE_ENABLED] ?: false }
    
    suspend fun setDriveEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DRIVE_ENABLED] = enabled
        }
    }
    
    val driveEmail: Flow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_DRIVE_EMAIL] }
    
    suspend fun setDriveEmail(email: String?) {
        dataStore.edit { prefs ->
            if (email != null) {
                prefs[KEY_DRIVE_EMAIL] = email
            } else {
                prefs.remove(KEY_DRIVE_EMAIL)
            }
        }
    }
    
    val lastBackupTimestamp: Flow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_LAST_BACKUP] }
    
    suspend fun setLastBackupTimestamp(timestamp: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP] = timestamp
        }
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ✅ NEW: UI SETTINGS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * App theme: "system", "light", "dark"
     */
    val theme: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_THEME] ?: "system" }
    
    suspend fun saveTheme(theme: String) {
        require(theme in listOf("system", "light", "dark")) {
            "Invalid theme: $theme"
        }
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = theme
        }
    }
    
    /**
     * App language: "en", "ru", etc.
     */
    val language: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_LANGUAGE] ?: "en" }
    
    suspend fun saveLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = language
        }
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ✅ NEW: OCR SETTINGS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * OCR language model: "LATIN", "CHINESE", "JAPANESE", "KOREAN", "DEVANAGARI"
     */
    val ocrLanguage: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_OCR_LANGUAGE] ?: "CHINESE" }
    
    suspend fun saveOcrLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[KEY_OCR_LANGUAGE] = language
        }
    }
    
    /**
     * Translation target language: "en", "ru", "zh", etc.
     */
    val translationTarget: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_TRANSLATION_TARGET] ?: "en" }
    
    suspend fun saveTranslationTarget(language: String) {
        dataStore.edit { prefs ->
            prefs[KEY_TRANSLATION_TARGET] = language
        }
    }
    
    /**
     * Auto-translate documents after OCR
     */
    val autoTranslate: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_AUTO_TRANSLATE] ?: false }
    
    suspend fun saveAutoTranslate(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_TRANSLATE] = enabled
        }
    }
    
    /**
     * Save original images after processing
     */
    val saveOriginals: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_SAVE_ORIGINALS] ?: true }
    
    suspend fun saveSaveOriginals(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SAVE_ORIGINALS] = enabled
        }
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ✅ NEW: CACHE SETTINGS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Enable translation caching
     */
    val cacheEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_CACHE_ENABLED] ?: true }
    
    suspend fun saveCacheEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CACHE_ENABLED] = enabled
        }
    }
    
    /**
     * Cache TTL in days (default: 30)
     */
    val cacheTtlDays: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_CACHE_TTL] ?: 30 }
    
    suspend fun saveCacheTtl(days: Int) {
        require(days in 1..365) { "Cache TTL must be 1-365 days" }
        dataStore.edit { prefs ->
            prefs[KEY_CACHE_TTL] = days
        }
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UTILITY
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Clear all settings.
     * ⚠️ Does NOT clear API keys (they're in EncryptedKeyStorage).
     */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}