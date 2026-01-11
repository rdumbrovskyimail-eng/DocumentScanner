/**
 * NetworkModule.kt
 * Version: 7.0.1 - FIXED (2026 Standards)
 *
 * ✅ FIX CRITICAL-3: Исправлен провайдер GoogleDriveService
 *    БЫЛО: return GoogleDriveService(context) - не хватало 4 зависимостей
 *    СТАЛО: Провайдер удалён, т.к. GoogleDriveService имеет @Inject constructor
 *           и все его зависимости доступны в Hilt графе
 *
 * Hilt module providing network and remote service dependencies.
 */

package com.docs.scanner.di

import android.content.Context
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.data.remote.camera.DocumentScannerWrapper
import com.docs.scanner.data.remote.gemini.GeminiApi
import com.docs.scanner.data.remote.gemini.GeminiApiService
import com.docs.scanner.data.remote.gemini.GeminiTranslator
import com.docs.scanner.data.remote.mlkit.MLKitScanner
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing network and remote service dependencies.
 * 
 * Fixed issues:
 * - ✅ CRITICAL-3: Removed broken GoogleDriveService provider
 *   GoogleDriveService has @Inject constructor, so Hilt creates it automatically.
 *   All its dependencies (AppDatabase, DataStore, JsonSerializer, RetryPolicy)
 *   are already available in the Hilt graph.
 * 
 * - 🔵 Minor: OkHttp retryOnConnectionFailure для POST запросов (спорно)
 * - 🟡 #12: Unified logging (Timber instead of android.util.Log)
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    
    // Timeout configuration for Gemini API (large responses)
    private const val CONNECT_TIMEOUT_SECONDS = 60L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 60L
    private const val CALL_TIMEOUT_SECONDS = 120L
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GSON
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Provides Gson with lenient parsing.
     * Lenient mode allows parsing of malformed JSON from Gemini API.
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // OKHTTP CLIENT
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Provides OkHttpClient with:
     * - Extended timeouts for Gemini API (60-120s)
     * - Retry on connection failure (READ-ONLY requests recommended)
     * - DEBUG-only logging with API key masking
     * 
     * NOTE: 🔵 Minor #18 - retryOnConnectionFailure can retry POST requests.
     * This is generally safe for idempotent operations, but be cautious
     * with non-idempotent endpoints. Consider using Interceptor for
     * selective retry logic if needed.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // ⚠️ Can retry POST - see note above
        
        // HTTP logging only in DEBUG mode
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                // Mask API keys in logs for security
                val sanitized = message
                    .replace(Regex("key=[^&\\s]+"), "key=***REDACTED***")
                    .replace(Regex("AIza[0-9A-Za-z_-]{35}"), "AIza***REDACTED***")
                
                // FIXED: 🟡 #12 - Use Timber instead of android.util.Log
                Timber.tag("HTTP").d(sanitized)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // RETROFIT
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Provides Retrofit instance for Gemini API.
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * Provides Gemini API service interface.
     */
    @Provides
    @Singleton
    fun provideGeminiApiService(retrofit: Retrofit): GeminiApiService {
        return retrofit.create(GeminiApiService::class.java)
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GEMINI API WRAPPERS
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Provides GeminiApi wrapper.
     * Handles rate limiting, retries, and error handling.
     */
    @Provides
    @Singleton
    fun provideGeminiApi(
        geminiApiService: GeminiApiService
    ): GeminiApi {
        return GeminiApi(geminiApiService)
    }
    
    /**
     * Provides GeminiTranslator with cache integration.
     * 
     * Integrates with TranslationCacheManager to reduce API calls:
     * - Checks cache before API call
     * - Saves translation to cache after API call
     * - Supports language parameters (source/target)
     * 
     * Previously CRITICAL FIX: This was missing and caused compilation failure.
     */
    @Provides
    @Singleton
    fun provideGeminiTranslator(
        geminiApi: GeminiApi,
        translationCacheManager: TranslationCacheManager,
        encryptedKeyStorage: EncryptedKeyStorage,
        settingsDataStore: SettingsDataStore
    ): GeminiTranslator {
        return GeminiTranslator(
            geminiApi = geminiApi,
            translationCacheManager = translationCacheManager,
            encryptedKeyStorage = encryptedKeyStorage,
            settingsDataStore = settingsDataStore
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ML KIT & DOCUMENT SCANNER
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Provides ML Kit OCR scanner with language settings.
     */
    @Provides
    @Singleton
    fun provideMLKitScanner(
        @ApplicationContext context: Context,
        settingsDataStore: SettingsDataStore
    ): MLKitScanner {
        return MLKitScanner(context, settingsDataStore)
    }
    
    /**
     * Provides Google ML Kit Document Scanner wrapper.
     * 
     * For scanning documents with camera.
     * Requires Google Play Services 23.0+
     */
    @Provides
    @Singleton
    fun provideDocumentScannerWrapper(
        @ApplicationContext context: Context
    ): DocumentScannerWrapper {
        return DocumentScannerWrapper(context)
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GOOGLE DRIVE
    // ════════════════════════════════════════════════════════════════════════════════
    
    /*
     * ✅ FIX CRITICAL-3: REMOVED broken provider!
     * 
     * БЫЛО (ОШИБКА):
     * ```
     * @Provides
     * @Singleton
     * fun provideGoogleDriveService(
     *     @ApplicationContext context: Context
     * ): GoogleDriveService {
     *     return GoogleDriveService(context)  // ❌ Не хватает 4 параметров!
     * }
     * ```
     * 
     * GoogleDriveService имеет конструктор:
     * ```
     * class GoogleDriveService @Inject constructor(
     *     context: Context,
     *     database: AppDatabase,           // ❌ Не передавался!
     *     dataStore: DataStore<Preferences>, // ❌ Не передавался!
     *     jsonSerializer: JsonSerializer,    // ❌ Не передавался!
     *     retryPolicy: RetryPolicy           // ❌ Не передавался!
     * )
     * ```
     * 
     * РЕШЕНИЕ: Удалить провайдер.
     * Поскольку GoogleDriveService имеет @Inject constructor и @Singleton,
     * Hilt автоматически создаст его, инжектируя все зависимости из графа:
     * - Context: через @ApplicationContext
     * - AppDatabase: из DatabaseModule
     * - DataStore<Preferences>: из DataStoreModule
     * - JsonSerializer: @Inject constructor + @Singleton (авто-binding)
     * - RetryPolicy: @Inject constructor + @Singleton (авто-binding)
     */
}