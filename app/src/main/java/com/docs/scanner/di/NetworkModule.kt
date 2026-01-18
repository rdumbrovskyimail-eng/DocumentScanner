/**
 * NetworkModule.kt
 * Version: 10.0.0 - OPTIMIZED TIMEOUTS (2026)
 * 
 * ✅ FIXES in 10.0.0:
 * - Reduced timeouts from 60/60/120 to 15/30/45 seconds
 * - Faster failover on invalid API keys (401/403)
 * - Better user experience (no 2-minute waits)
 * - Added connection pool configuration
 * - Improved retry logic
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
import okhttp3.ConnectionPool
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
    
    // ✅ OPTIMIZED TIMEOUTS - Faster failover
    // ════════════════════════════════════════════════════════════════════════════════
    // OLD VALUES (caused 2-minute waits):
    // - CONNECT_TIMEOUT_SECONDS = 60L
    // - READ_TIMEOUT_SECONDS = 60L  
    // - CALL_TIMEOUT_SECONDS = 120L
    //
    // NEW VALUES (faster failover):
    // - Connect: 15s - enough for SSL handshake
    // - Read: 30s - enough for API response
    // - Call: 45s - max total time per request
    // ════════════════════════════════════════════════════════════════════════════════
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 45L
    
    // Connection pool settings
    private const val MAX_IDLE_CONNECTIONS = 5
    private const val KEEP_ALIVE_DURATION_MINUTES = 5L
    
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
    // OKHTTP CLIENT - ✅ OPTIMIZED
    // ════════════════════════════════════════════════════════════════════════════════
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // ✅ NEW: Optimized timeouts
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            
            // ✅ NEW: Connection pool for better performance
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = MAX_IDLE_CONNECTIONS,
                    keepAliveDuration = KEEP_ALIVE_DURATION_MINUTES,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            
            // ✅ IMPROVED: Retry only on connection failures, not on auth errors
            .retryOnConnectionFailure(true)
        
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                // ✅ Sanitize API keys in logs
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
    // GEMINI KEY MANAGER
    // ════════════════════════════════════════════════════════════════════════════════
    
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
        geminiApiService: GeminiApiService,
        keyManager: GeminiKeyManager
    ): GeminiApi {
        return GeminiApi(geminiApiService, keyManager)
    }
    
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
    // GEMINI OCR SERVICE
    // ════════════════════════════════════════════════════════════════════════════════
    
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
        settingsDataStore: SettingsDataStore,
        geminiOcrService: GeminiOcrService,
        qualityAnalyzer: OcrQualityAnalyzer
    ): MLKitScanner {
        return MLKitScanner(context, settingsDataStore, geminiOcrService, qualityAnalyzer)
    }
    
    @Provides
    @Singleton
    fun provideDocumentScannerWrapper(
        @ApplicationContext context: Context
    ): DocumentScannerWrapper {
        return DocumentScannerWrapper(context)
    }
}