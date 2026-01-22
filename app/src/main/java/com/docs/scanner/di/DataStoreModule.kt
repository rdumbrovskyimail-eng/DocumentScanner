package com.docs.scanner.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.remote.gemini.GeminiModelManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Extension delegate for DataStore.
 * 
 * CRITICAL: Name must match SettingsDataStore.kt
 * FIXED: 🟠 Серьёзная #8 - Unified to "app_settings"
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

/**
 * Hilt module providing DataStore instance.
 * 
 * Note: SettingsDataStore uses the same delegate extension,
 * so they share the same underlying file.
 * 
 * Fixed issues:
 * - 🟠 Серьёзная #8: Name matches SettingsDataStore ("app_settings")
 * - ✅ FIXED: Removed circular dependency (GeminiModelManager ← SettingsDataStore → GeminiModelManager)
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    
    /**
     * Provides singleton DataStore<Preferences>.
     * 
     * Used by:
     * - SettingsDataStore (settings management)
     * - Any other component needing preference storage
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.dataStore
    }
    
    /**
     * Provides singleton SettingsDataStore.
     * 
     * ✅ CRITICAL FIX: Now created BEFORE GeminiModelManager
     * This breaks the circular dependency chain.
     */
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        dataStore: DataStore<Preferences>
    ): SettingsDataStore {
        return SettingsDataStore(dataStore)
    }
    
    /**
     * Provides singleton GeminiModelManager.
     * 
     * ✅ FIXED: Now receives SettingsDataStore as dependency
     * (which is already created by Hilt above)
     * 
     * Used by:
     * - GeminiOcrService (dynamic model selection)
     * - Any component needing Gemini model management
     */
    @Provides
    @Singleton
    fun provideGeminiModelManager(
        settingsDataStore: SettingsDataStore
    ): GeminiModelManager {
        return GeminiModelManager(settingsDataStore)
    }
}