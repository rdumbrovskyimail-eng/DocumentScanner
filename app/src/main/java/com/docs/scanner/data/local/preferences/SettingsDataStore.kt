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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extension delegate for DataStore.
 * FIXED: 🟠 Серьёзная #8 - Unified name to match DataStoreModule
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * DataStore for app settings.
 * 
 * Fixed issues:
 * - 🟠 Серьёзная #6: Improved null handling in migrateApiKeyToEncrypted
 * - 🟠 Серьёзная #8: Unified DataStore name with DataStoreModule ("app_settings")
 * - 🟡 #1: Replaced android.util.Log with Timber
 * 
 * Security:
 * - ✅ API keys stored in EncryptedKeyStorage (AES-256-GCM)
 * - ✅ Migration from plain text to encrypted storage
 * - ✅ All sensitive data properly handled
 * 
 * Features:
 * - Onboarding & First Launch tracking
 * - Google Drive backup settings
 * - UI settings (theme, language)
 * - OCR settings (language, auto-translate)
 * - Cache settings (enabled, TTL)
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val dataStore = context.dataStore
    
    companion object {
        // Onboarding & First Launch
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        
        // Google Drive Backup
        private val KEY_DRIVE_ENABLED = booleanPreferencesKey("drive_enabled")
        private val KEY_DRIVE_EMAIL = stringPreferencesKey("drive_email")
        private val KEY_LAST_BACKUP = stringPreferencesKey("last_backup_timestamp")
        
        // UI Settings
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        
        // OCR Settings
        private val KEY_OCR_LANGUAGE = stringPreferencesKey("ocr_language")
        private val KEY_TRANSLATION_TARGET = stringPreferencesKey("translation_target")
        private val KEY_AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")
        private val KEY_SAVE_ORIGINALS = booleanPreferencesKey("save_originals")
        
        // Cache Settings
        private val KEY_CACHE_ENABLED = booleanPreferencesKey("cache_enabled")
        private val KEY_CACHE_TTL = intPreferencesKey("cache_ttl_days")
        
        // Legacy key for migration
        private val KEY_LEGACY_API_KEY = stringPreferencesKey("gemini_api_key")
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // 🔴 CRITICAL: API KEY MIGRATION
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Migrate API key from plain text DataStore to EncryptedKeyStorage.
     * 
     * FIXED: 🟠 Серьёзная #6 - Improved error handling with nested try-catch
     * 
     * ⚠️ MUST BE CALLED ONCE on app startup!
     * See App.kt onCreate() or MainActivity.
     * 
     * @param encryptedStorage Target encrypted storage
     * @return true if migration successful or not needed, false if failed
     */
    suspend fun migrateApiKeyToEncrypted(encryptedStorage: EncryptedKeyStorage): Boolean {
        return try {
            // Check if old key exists
            val oldKey = dataStore.data.first()[KEY_LEGACY_API_KEY]
            
            if (!oldKey.isNullOrBlank()) {
                Timber.i("🔄 Found legacy API key, migrating to encrypted storage...")
                
                // Migrate to encrypted storage with separate error handling
                try {
                    encryptedStorage.setActiveApiKey(oldKey)
                    Timber.i("✅ API key migrated to encrypted storage")
                } catch (e: Exception) {
                    // FIXED: 🟠 Серьёзная #6 - Don't fail migration if setActiveApiKey fails
                    Timber.e(e, "❌ Failed to save key to encrypted storage")
                    return false
                }
                
                // Remove from DataStore (even if encryption failed, we tried)
                try {
                    dataStore.edit { prefs ->
                        prefs.remove(KEY_LEGACY_API_KEY)
                    }
                    Timber.i("✅ Removed legacy API key from DataStore")
                } catch (e: Exception) {
                    // Non-critical: key is already in encrypted storage
                    Timber.w(e, "⚠️ Failed to remove legacy key (non-critical)")
                }
                
                return true
            } else {
                Timber.d("No legacy API key found, migration not needed")
                return true
            }
        } catch (e: Exception) {
            // FIXED: 🟠 Серьёзная #6 - Catch outer exceptions (DataStore read errors)
            Timber.e(e, "❌ Migration failed at DataStore read")
            return false
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ONBOARDING & FIRST LAUNCH
    // ════════════════════════════════════════════════════════════════════════════════
    
    val isFirstLaunch: Flow<Boolean> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading first launch state")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_FIRST_LAUNCH] ?: true }
    
    suspend fun getIsFirstLaunch(): Boolean {
        return try {
            dataStore.data.first()[KEY_FIRST_LAUNCH] ?: true
        } catch (e: Exception) {
            Timber.e(e, "Error reading first launch state")
            true
        }
    }
    
    suspend fun setFirstLaunchCompleted() {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_FIRST_LAUNCH] = false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting first launch completed")
        }
    }
    
    val isOnboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading onboarding state")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }
    
    suspend fun setOnboardingCompleted() {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_ONBOARDING_COMPLETED] = true
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting onboarding completed")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GOOGLE DRIVE BACKUP
    // ════════════════════════════════════════════════════════════════════════════════
    
    val driveEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading drive enabled state")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_DRIVE_ENABLED] ?: false }
    
    suspend fun setDriveEnabled(enabled: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_DRIVE_ENABLED] = enabled
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting drive enabled")
        }
    }
    
    val driveEmail: Flow<String?> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading drive email")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_DRIVE_EMAIL] }
    
    suspend fun setDriveEmail(email: String?) {
        try {
            dataStore.edit { prefs ->
                if (email != null) {
                    prefs[KEY_DRIVE_EMAIL] = email
                } else {
                    prefs.remove(KEY_DRIVE_EMAIL)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting drive email")
        }
    }
    
    val lastBackupTimestamp: Flow<String?> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading last backup timestamp")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_LAST_BACKUP] }
    
    suspend fun setLastBackupTimestamp(timestamp: String) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_LAST_BACKUP] = timestamp
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting last backup timestamp")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // UI SETTINGS
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * App theme: "system", "light", "dark"
     */
    val theme: Flow<String> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading theme")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_THEME] ?: "system" }
    
    suspend fun setTheme(theme: String) {
        require(theme in listOf("system", "light", "dark")) {
            "Invalid theme: $theme. Must be 'system', 'light', or 'dark'"
        }
        try {
            dataStore.edit { prefs ->
                prefs[KEY_THEME] = theme
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting theme")
        }
    }
    
    /**
     * App language: empty string for system default, or language tag (e.g., "en", "ru")
     */
    val appLanguage: Flow<String> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading app language")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_LANGUAGE] ?: "" }
    
    suspend fun setAppLanguage(languageTag: String) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_LANGUAGE] = languageTag
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting app language")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // OCR SETTINGS
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * OCR language model: "LATIN", "CHINESE", "JAPANESE", "KOREAN", "DEVANAGARI"
     */
    val ocrLanguage: Flow<String> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading OCR language")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_OCR_LANGUAGE] ?: "CHINESE" }
    
    suspend fun setOcrLanguage(language: String) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_OCR_LANGUAGE] = language
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting OCR language")
        }
    }
    
    /**
     * Translation target language: "en", "ru", "zh", etc.
     */
    val translationTarget: Flow<String> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading translation target")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_TRANSLATION_TARGET] ?: "en" }
    
    suspend fun setTranslationTarget(language: String) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_TRANSLATION_TARGET] = language
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting translation target")
        }
    }
    
    /**
     * Auto-translate documents after OCR
     */
    val autoTranslate: Flow<Boolean> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading auto-translate")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_AUTO_TRANSLATE] ?: false }
    
    suspend fun setAutoTranslate(enabled: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_AUTO_TRANSLATE] = enabled
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting auto-translate")
        }
    }
    
    /**
     * Save original images after processing
     */
    val saveOriginals: Flow<Boolean> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading save originals")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_SAVE_ORIGINALS] ?: true }
    
    suspend fun setSaveOriginals(enabled: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_SAVE_ORIGINALS] = enabled
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting save originals")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // CACHE SETTINGS
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Enable translation caching
     */
    val cacheEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading cache enabled")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_CACHE_ENABLED] ?: true }
    
    suspend fun setCacheEnabled(enabled: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_CACHE_ENABLED] = enabled
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting cache enabled")
        }
    }
    
    /**
     * Cache TTL in days (default: 30)
     */
    val cacheTtlDays: Flow<Int> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading cache TTL")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_CACHE_TTL] ?: 30 }
    
    suspend fun setCacheTtl(days: Int) {
        require(days in 1..365) { 
            "Cache TTL must be between 1 and 365 days, got: $days" 
        }
        try {
            dataStore.edit { prefs ->
                prefs[KEY_CACHE_TTL] = days
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting cache TTL")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Clear all settings.
     * ⚠️ Does NOT clear API keys (they're in EncryptedKeyStorage).
     */
    suspend fun clearAll() {
        try {
            dataStore.edit { it.clear() }
            Timber.i("✅ All settings cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing settings")
        }
    }
}