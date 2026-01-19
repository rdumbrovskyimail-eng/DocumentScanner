/**
 * NetworkModule.kt
 * Version: 11.0.0 - SPEED OPTIMIZED (2026)
 * 
 * ✅ NEW IN 11.0.0:
 * - Fixed HttpLoggingInterceptor to skip base64 image data (prevents UI freeze!)
 * - Reduced timeouts for faster failover
 * - Changed logging level from BODY to BASIC
 * 
 * ✅ PREVIOUS FIXES:
 * - Reduced timeouts from 60/60/120 to optimized values
 * - Faster failover on invalid API keys (401/403)
 * - Better user experience (no long waits)
 * - Added connection pool configuration
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
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ✅ OPTIMIZED TIMEOUTS - Faster failover for better UX
    // ════════════════════════════════════════════════════════════════════════════════
    // 
    // OLD VALUES (caused long waits):
    // - CONNECT_TIMEOUT = 15s, READ_TIMEOUT = 30s, CALL_TIMEOUT = 45s
    //
    // NEW VALUES (faster response):
    // - Connect: 10s - enough for SSL handshake
    // - Read: 20s - enough for Gemini response
    // - Call: 25s - max total time per request
    // ════════════════════════════════════════════════════════════════════════════════
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 20L
    private const val WRITE_TIMEOUT_SECONDS = 20L
    private const val CALL_TIMEOUT_SECONDS = 25L
    
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
    // OKHTTP CLIENT - ✅ OPTIMIZED FOR SPEED
    // ════════════════════════════════════════════════════════════════════════════════
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // ✅ Optimized timeouts
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            
            // Connection pool for better performance
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = MAX_IDLE_CONNECTIONS,
                    keepAliveDuration = KEEP_ALIVE_DURATION_MINUTES,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            
            // Retry only on connection failures, not on auth errors
            .retryOnConnectionFailure(true)
        
        if (BuildConfig.DEBUG) {
            // ✅ CRITICAL FIX: Custom logging that skips base64 image data
            // Old code logged entire base64 strings (millions of characters)
            // which caused 5-10 second UI freezes!
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                // Skip base64 image data - check for common indicators
                if (message.length > 500) {
                    // Check if this looks like base64 image data
                    if (message.contains("inlineData") || 
                        message.contains("/9j/") ||      // JPEG header in base64
                        message.contains("iVBORw") ||    // PNG header in base64
                        message.contains("\"data\":\"")) {
                        Timber.tag("HTTP").d("[IMAGE DATA: ${message.length} chars - skipped for performance]")
                        return@HttpLoggingInterceptor
                    }
                }
                
                // Sanitize API keys in logs
                val sanitized = message
                    .replace(Regex("key=[^&\\s]+"), "key=***REDACTED***")
                    .replace(Regex("AIza[0-9A-Za-z_-]{35}"), "AIza***REDACTED***")
                
                Timber.tag("HTTP").d(sanitized)
            }.apply {
                // ✅ Changed from BODY to BASIC for performance
                // BODY logs entire request/response bodies including huge base64 images
                // BASIC logs only request/response lines (URL, status code)
                level = HttpLoggingInterceptor.Level.BASIC
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
        keyManager: GeminiKeyManager,
        settingsDataStore: SettingsDataStore
    ): GeminiOcrService {
        return GeminiOcrService(
            context = context,
            apiService = apiService,
            keyManager = keyManager,
            settingsDataStore = settingsDataStore
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
