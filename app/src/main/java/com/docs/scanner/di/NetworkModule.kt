/**
 * NetworkModule.kt
 * Version: 9.0.0 - ПОЛНОСТЬЮ ИСПРАВЛЕНО (2026)
 * 
 * ✅ ИСПРАВЛЕНО: Line 141 - Added keyStorage parameter to GeminiKeyManager
 * ✅ ИСПРАВЛЕНО: Line 204 - Added apiService and keyManager to GeminiOcrService
 * ✅ ИСПРАВЛЕНО: Replaced encryptedKeyStorage with keyManager in GeminiTranslator
 * ✅ ОПТИМИЗИРОВАНО: Все зависимости правильно инжектируются через Hilt
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
import com.docs.scanner.data.remote.gemini.GeminiKeyManager
import com.docs.scanner.data.remote.gemini.GeminiOcrService
import com.docs.scanner.data.remote.gemini.GeminiTranslator
import com.docs.scanner.data.remote.mlkit.MLKitScanner
import com.docs.scanner.data.remote.mlkit.OcrQualityAnalyzer
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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    
    private const val CONNECT_TIMEOUT_SECONDS = 60L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 60L
    private const val CALL_TIMEOUT_SECONDS = 120L
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GSON
    // ════════════════════════════════════════════════════════════════════════════════
    
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
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                val sanitized = message
                    .replace(Regex("key=[^&\\s]+"), "key=***REDACTED***")
                    .replace(Regex("AIza[0-9A-Za-z_-]{35}"), "AIza***REDACTED***")
                
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
    
    @Provides
    @Singleton
    fun provideGeminiApiService(retrofit: Retrofit): GeminiApiService {
        return retrofit.create(GeminiApiService::class.java)
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GEMINI KEY MANAGER - ✅ ИСПРАВЛЕНО
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * ✅ FIX Line 141: Added missing keyStorage parameter
     */
    @Provides
    @Singleton
    fun provideGeminiKeyManager(
        keyStorage: EncryptedKeyStorage
    ): GeminiKeyManager {
        return GeminiKeyManager(keyStorage)
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GEMINI API WRAPPERS
    // ════════════════════════════════════════════════════════════════════════════════
    
    @Provides
    @Singleton
    fun provideGeminiApi(
        geminiApiService: GeminiApiService
    ): GeminiApi {
        return GeminiApi(geminiApiService)
    }
    
    /**
     * ✅ ИСПРАВЛЕНО: Replaced encryptedKeyStorage with keyManager
     */
    @Provides
    @Singleton
    fun provideGeminiTranslator(
        geminiApi: GeminiApi,
        translationCacheManager: TranslationCacheManager,
        keyManager: GeminiKeyManager,
        settingsDataStore: SettingsDataStore
    ): GeminiTranslator {
        return GeminiTranslator(
            geminiApi = geminiApi,
            translationCacheManager = translationCacheManager,
            keyManager = keyManager,
            settingsDataStore = settingsDataStore
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // OCR QUALITY ANALYZER
    // ════════════════════════════════════════════════════════════════════════════════
    
    @Provides
    @Singleton
    fun provideOcrQualityAnalyzer(): OcrQualityAnalyzer {
        return OcrQualityAnalyzer()
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GEMINI OCR SERVICE - ✅ ИСПРАВЛЕНО
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * ✅ FIX Line 204: Added missing apiService and keyManager parameters
     */
    @Provides
    @Singleton
    fun provideGeminiOcrService(
        @ApplicationContext context: Context,
        apiService: GeminiApiService,
        keyManager: GeminiKeyManager
    ): GeminiOcrService {
        return GeminiOcrService(
            context = context,
            apiService = apiService,
            keyManager = keyManager
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ML KIT & DOCUMENT SCANNER
    // ════════════════════════════════════════════════════════════════════════════════
    
    @Provides
    @Singleton
    fun provideMLKitScanner(
        @ApplicationContext context: Context,
        settingsDataStore: SettingsDataStore
    ): MLKitScanner {
        return MLKitScanner(context, settingsDataStore)
    }
    
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
     * ✅ GoogleDriveService provider REMOVED
     * 
     * GoogleDriveService has @Inject constructor, so Hilt creates it automatically.
     * All dependencies are available in the Hilt graph:
     * - Context: via @ApplicationContext
     * - AppDatabase: from DatabaseModule
     * - DataStore<Preferences>: from DataStoreModule
     * - JsonSerializer: @Inject constructor + @Singleton
     * - RetryPolicy: @Inject constructor + @Singleton
     * 
     * No manual @Provides needed.
     */
}