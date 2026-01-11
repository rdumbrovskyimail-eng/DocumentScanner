package com.docs.scanner.di

import android.content.Context
import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.alarm.AlarmScheduler
import com.docs.scanner.data.local.database.dao.TranslationCacheDao
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.domain.core.SystemTimeProvider
import com.docs.scanner.domain.core.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Main Hilt module providing application-level dependencies.
 * 
 * Session 7 fixes:
 * - ✅ Added 6 critical missing dependencies
 * - ✅ Added Coroutine Dispatchers (for testability)
 * - ✅ Removed useless Context provider
 */

// ✅ NEW: Qualifiers for Coroutine Dispatchers (testability)
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    // ❌ REMOVED: provideApplicationContext() - бесполезно, 
    // Context можно инжектить напрямую через @ApplicationContext
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // STORAGE & SECURITY
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides encrypted storage for API keys.
     * Uses Android EncryptedSharedPreferences (AES-256).
     */
    @Provides
    @Singleton
    fun provideEncryptedKeyStorage(
        @ApplicationContext context: Context
    ): EncryptedKeyStorage {
        return EncryptedKeyStorage(context)
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // TRANSLATION CACHE
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides translation cache manager.
     * Reduces API calls by caching translations in Room database.
     * 
     * Session 3 fix: Critical - без кэша каждый перевод = API вызов!
     */
    @Provides
    @Singleton
    fun provideTranslationCacheManager(
        translationCacheDao: TranslationCacheDao
    ): TranslationCacheManager {
        return TranslationCacheManager(translationCacheDao)
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ALARMS (for Term reminders)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides AlarmScheduler for scheduling term reminders.
     * Uses AlarmManager with exact alarms (requires permission on Android 12+).
     */
    @Provides
    @Singleton
    fun provideAlarmScheduler(
        @ApplicationContext context: Context
    ): AlarmScheduler {
        return AlarmScheduler(context)
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // TIME (for deterministic domain logic)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()
    
    // ⚠️ NOTE: AlarmSchedulerWrapper removed - it was duplicate wrapper.
    // ViewModels should inject AlarmScheduler directly.
    // See Session 3 Problem #3 for details.
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // COROUTINE DISPATCHERS (for testability)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides IO Dispatcher for database/network operations.
     * Can be replaced with TestDispatcher in unit tests.
     */
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    /**
     * Provides Main Dispatcher for UI operations.
     * Can be replaced with TestDispatcher in unit tests.
     */
    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    
    /**
     * Provides Default Dispatcher for CPU-intensive operations.
     * Can be replaced with TestDispatcher in unit tests.
     */
    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}