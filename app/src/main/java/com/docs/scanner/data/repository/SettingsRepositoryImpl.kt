package com.docs.scanner.data.repository

import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SettingsRepository.
 * 
 * Combines:
 * - SettingsDataStore for general preferences
 * - EncryptedKeyStorage for API keys
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val encryptedKeyStorage: EncryptedKeyStorage
) : SettingsRepository {

    // ══════════════════════════════════════════════════════════════
    // API KEY (Encrypted Storage)
    // ══════════════════════════════════════════════════════════════

    override suspend fun getApiKey(): String? {
        return try {
            encryptedKeyStorage.getActiveApiKey()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get API key")
            null
        }
    }

    override suspend fun setApiKey(key: String) {
        try {
            encryptedKeyStorage.setActiveApiKey(key)
            Timber.d("API key saved")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save API key")
            throw e
        }
    }

    override suspend fun clearApiKey() {
        try {
            encryptedKeyStorage.removeActiveApiKey()
            Timber.d("API key cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear API key")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TRANSLATION SETTINGS
    // ══════════════════════════════════════════════════════════════

    override suspend fun getTargetLanguage(): String {
        return try {
            settingsDataStore.translationTarget.first()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get target language")
            "ru" // Default
        }
    }

    override suspend fun setTargetLanguage(language: String) {
        try {
            settingsDataStore.saveTranslationTarget(language)
            Timber.d("Target language set to: $language")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set target language")
        }
    }

    override suspend fun isAutoTranslateEnabled(): Boolean {
        return try {
            settingsDataStore.autoTranslate.first()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get auto-translate setting")
            false
        }
    }

    override suspend fun setAutoTranslateEnabled(enabled: Boolean) {
        try {
            settingsDataStore.saveAutoTranslate(enabled)
            Timber.d("Auto-translate set to: $enabled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set auto-translate")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ONBOARDING
    // ══════════════════════════════════════════════════════════════

    override suspend fun isOnboardingCompleted(): Boolean {
        return try {
            settingsDataStore.isOnboardingCompleted.first()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get onboarding status")
            false
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        try {
            if (completed) {
                settingsDataStore.setOnboardingCompleted()
            }
            Timber.d("Onboarding completed: $completed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set onboarding status")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // THEME
    // ══════════════════════════════════════════════════════════════

    override suspend fun getThemeMode(): Int {
        return try {
            when (settingsDataStore.theme.first()) {
                "light" -> THEME_LIGHT
                "dark" -> THEME_DARK
                else -> THEME_SYSTEM
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get theme mode")
            THEME_SYSTEM
        }
    }

    override suspend fun setThemeMode(mode: Int) {
        try {
            val themeName = when (mode) {
                THEME_LIGHT -> "light"
                THEME_DARK -> "dark"
                else -> "system"
            }
            settingsDataStore.saveTheme(themeName)
            Timber.d("Theme set to: $themeName")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set theme mode")
        }
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}