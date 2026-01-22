/**
 * SettingsDataStore.kt
 * Version: 13.0.0 - FIXED CIRCULAR DEPENDENCY (2026)
 *
 * ✅ NEW IN 13.0.0:
 * - REMOVED GeminiModelManager from constructor (breaks circular dependency)
 * - Model validation now uses hardcoded list (synced with GeminiModelManager)
 * - getAvailableGeminiModels() returns local list (no manager dependency)
 *
 * ✅ PREVIOUS IN 12.0.0:
 * - Delegated model lists to GeminiModelManager
 * - Removed duplicate VALID_GEMINI_MODELS / VALID_TRANSLATION_MODELS
 * - Removed gemini-2.0-* models
 * - All model operations now go through GeminiModelManager
 *
 * ✅ PREVIOUS IN 11.0.0:
 * - Translation model selection (KEY_TRANSLATION_MODEL)
 * - Separate model settings for OCR and Translation
 *
 * АРХИТЕКТУРА (FIXED):
 * SettingsDataStore ← DataStore (no circular dependency)
 * GeminiModelManager ← SettingsDataStore (can now inject safely)
 */

package com.docs.scanner.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.data.remote.mlkit.OcrQualityThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
    // ❌ REMOVED: private val modelManager: GeminiModelManager
    // This was causing circular dependency:
    // GeminiModelManager needs SettingsDataStore → SettingsDataStore needs GeminiModelManager
) {
    
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

        // Image settings
        private val KEY_IMAGE_QUALITY = stringPreferencesKey("image_quality")
        
        // Gemini OCR Fallback Settings
        private val KEY_GEMINI_OCR_ENABLED = booleanPreferencesKey("gemini_ocr_enabled")
        private val KEY_GEMINI_OCR_THRESHOLD = intPreferencesKey("gemini_ocr_threshold")
        private val KEY_GEMINI_OCR_ALWAYS = booleanPreferencesKey("gemini_ocr_always")
        
        // Gemini Model Selection
        private val KEY_GEMINI_OCR_MODEL = stringPreferencesKey("gemini_ocr_model")
        private val KEY_TRANSLATION_MODEL = stringPreferencesKey("translation_model")
        
        // Legacy key for migration
        private val KEY_LEGACY_API_KEY = stringPreferencesKey("gemini_api_key")
        
        // ✅ MODEL CONSTANTS - Synced with GeminiModelManager
        // Must be kept in sync manually (unavoidable to break circular dependency)
        const val DEFAULT_OCR_MODEL = "gemini-2.5-flash-lite"
        const val DEFAULT_TRANSLATION_MODEL = "gemini-2.5-flash-lite"
        
        private val VALID_MODELS = listOf(
            "gemini-3-flash-preview",
            "gemini-3-pro-preview",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro"
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // 🔴 CRITICAL: API KEY MIGRATION
    // ════════════════════════════════════════════════════════════════════════════════
    
    suspend fun migrateApiKeyToEncrypted(encryptedStorage: EncryptedKeyStorage): Boolean {
        return try {
            val oldKey = dataStore.data.first()[KEY_LEGACY_API_KEY]
            
            if (!oldKey.isNullOrBlank()) {
                Timber.i("🔄 Found legacy API key, migrating to encrypted storage...")
                
                try {
                    encryptedStorage.setActiveApiKey(oldKey)
                    Timber.i("✅ API key migrated to encrypted storage")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to save key to encrypted storage")
                    return false
                }
                
                try {
                    dataStore.edit { prefs ->
                        prefs.remove(KEY_LEGACY_API_KEY)
                    }
                    Timber.i("✅ Removed legacy API key from DataStore")
                } catch (e: Exception) {
                    Timber.w(e, "⚠️ Failed to remove legacy key (non-critical)")
                }
                
                return true
            } else {
                Timber.d("No legacy API key found, migration not needed")
                return true
            }
        } catch (e: Exception) {
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
    // IMAGE SETTINGS
    // ════════════════════════════════════════════════════════════════════════════════
    
    val imageQuality: Flow<String> = dataStore.data
        .catch { exception ->
            Timber.e(exception, "Error reading image quality")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_IMAGE_QUALITY] ?: "HIGH" }

    suspend fun setImageQuality(quality: String) {
        require(quality in listOf("LOW", "MEDIUM", "HIGH", "ORIGINAL")) {
            "Invalid image quality: $quality"
        }
        try {
            dataStore.edit { prefs ->
                prefs[KEY_IMAGE_QUALITY] = quality
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting image quality")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GEMINI OCR FALLBACK SETTINGS
    // ════════════════════════════════════════════════════════════════════════════════
    
    val geminiOcrEnabled: Flow<Boolean> = dataStore.data
        .catch { e ->
            Timber.e(e, "Error reading geminiOcrEnabled")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_GEMINI_OCR_ENABLED] ?: true }
    
    val geminiOcrThreshold: Flow<Int> = dataStore.data
        .catch { e ->
            Timber.e(e, "Error reading geminiOcrThreshold")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_GEMINI_OCR_THRESHOLD] ?: 65 }
    
    val geminiOcrAlways: Flow<Boolean> = dataStore.data
        .catch { e ->
            Timber.e(e, "Error reading geminiOcrAlways")
            emit(emptyPreferences())
        }
        .map { prefs -> prefs[KEY_GEMINI_OCR_ALWAYS] ?: false }
    
    suspend fun setGeminiOcrEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_GEMINI_OCR_ENABLED] = enabled
        }
    }
    
    suspend fun setGeminiOcrThreshold(threshold: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_GEMINI_OCR_THRESHOLD] = threshold.coerceIn(0, 100)
        }
    }
    
    suspend fun setGeminiOcrAlways(always: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_GEMINI_OCR_ALWAYS] = always
        }
    }
    
    suspend fun getOcrQualityThresholds(): OcrQualityThresholds {
        val enabled = geminiOcrEnabled.first()
        val threshold = geminiOcrThreshold.first()
        val always = geminiOcrAlways.first()
        
        return OcrQualityThresholds(
            minConfidenceForSuccess = threshold / 100f,
            geminiOcrEnabled = enabled,
            alwaysUseGemini = always
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ✅ GEMINI MODEL SELECTION - LOCAL VALIDATION (13.0.0)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Selected Gemini model for OCR.
     * 
     * ✅ UPDATED 13.0.0: Uses local DEFAULT_OCR_MODEL constant
     */
    val geminiOcrModel: Flow<String> = dataStore.data
        .catch { e ->
            Timber.e(e, "Error reading geminiOcrModel")
            emit(emptyPreferences())
        }
        .map { prefs -> 
            prefs[KEY_GEMINI_OCR_MODEL] ?: DEFAULT_OCR_MODEL
        }
    
    /**
     * Sets the Gemini model for OCR.
     * 
     * ✅ UPDATED 13.0.0: Local validation using VALID_MODELS
     * 
     * @param model Model identifier (must be in VALID_MODELS)
     * @throws IllegalArgumentException if model is not valid
     */
    suspend fun setGeminiOcrModel(model: String) {
        require(model in VALID_MODELS) { 
            "Invalid Gemini model: $model. Valid models: $VALID_MODELS"
        }
        
        try {
            dataStore.edit { prefs ->
                prefs[KEY_GEMINI_OCR_MODEL] = model
            }
            Timber.d("✅ Gemini OCR model set to: $model")
        } catch (e: Exception) {
            Timber.e(e, "Error setting Gemini OCR model")
            throw e
        }
    }
    
    /**
     * Returns list of available Gemini models for OCR UI display.
     * 
     * ✅ UPDATED 13.0.0: Returns local hardcoded list (no manager dependency)
     * ⚠️ NOTE: Must be kept in sync with GeminiModelManager.getAvailableModels()
     */
    fun getAvailableGeminiModels(): List<GeminiModelOption> = listOf(
        GeminiModelOption(
            id = "gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash Lite 🚀",
            description = "Ultra-fast • Stable • Best for OCR & Translation",
            isRecommended = true
        ),
        GeminiModelOption(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash ⚡",
            description = "Very fast • Great balance",
            isRecommended = false
        ),
        GeminiModelOption(
            id = "gemini-3-flash-preview",
            displayName = "Gemini 3 Flash (Preview)",
            description = "Latest • May have rate limits",
            isRecommended = false
        ),
        GeminiModelOption(
            id = "gemini-3-pro-preview",
            displayName = "Gemini 3 Pro (Preview) 💰",
            description = "PAID ONLY • Highest quality • Slower",
            isRecommended = false
        ),
        GeminiModelOption(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro 🐌",
            description = "Slow (4-7s) • Complex text only",
            isRecommended = false
        )
    )
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ✅ TRANSLATION MODEL SELECTION - LOCAL VALIDATION (13.0.0)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Selected Gemini model for Translation.
     * 
     * ✅ UPDATED 13.0.0: Uses local DEFAULT_TRANSLATION_MODEL constant
     */
    val translationModel: Flow<String> = dataStore.data
        .catch { e ->
            Timber.e(e, "Error reading translationModel")
            emit(emptyPreferences())
        }
        .map { prefs -> 
            prefs[KEY_TRANSLATION_MODEL] ?: DEFAULT_TRANSLATION_MODEL
        }
    
    /**
     * Sets the Gemini model for Translation.
     * 
     * ✅ UPDATED 13.0.0: Local validation using VALID_MODELS
     * 
     * @param model Model identifier (must be in VALID_MODELS)
     * @throws IllegalArgumentException if model is not valid
     */
    suspend fun setTranslationModel(model: String) {
        require(model in VALID_MODELS) { 
            "Invalid Translation model: $model. Valid models: $VALID_MODELS"
        }
        
        try {
            dataStore.edit { prefs ->
                prefs[KEY_TRANSLATION_MODEL] = model
            }
            Timber.d("✅ Translation model set to: $model")
        } catch (e: Exception) {
            Timber.e(e, "Error setting Translation model")
            throw e
        }
    }
    
    /**
     * Returns list of available Gemini models for Translation UI display.
     * 
     * ✅ UPDATED 13.0.0: Returns local hardcoded list (no manager dependency)
     * ⚠️ NOTE: Must be kept in sync with GeminiModelManager.getAvailableModels()
     */
    fun getAvailableTranslationModels(): List<GeminiModelOption> {
        return getAvailableGeminiModels() // Same models for translation
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
