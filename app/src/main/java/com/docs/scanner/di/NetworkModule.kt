/**
 * NetworkModule.kt
 * Version: 14.0.0 - GEMINI MODEL MANAGER INTEGRATION (2026)
 * 
 * ✅ NEW IN 14.0.0:
 * - Added GeminiModelManager injection to GeminiTranslator
 * - Centralized model selection through ModelManager
 * 
 * ✅ PREVIOUS IN 13.1.0:
 * - BuildConfig.BUILD_TYPE вместо BuildConfig.DEBUG для гарантированного отключения в release
 * - Асинхронное логирование больших сообщений (устраняет 5-10 сек UI freeze)
 * - Ультра-агрессивные тайм-ауты для мгновенного failover
 * - Smart logging с фильтрацией base64 по размеру и паттернам
 * - HEADERS-only logging вместо BASIC (не парсит тело запроса)
 * - Connection pooling с оптимальными параметрами
 */

package com.docs.scanner.di

import android.content.Context
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.preferences.GeminiModelManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    // ✅ ULTRA-AGGRESSIVE TIMEOUTS - Мгновенный failover
    // ════════════════════════════════════════════════════════════════════════════════
    private const val CONNECT_TIMEOUT_SECONDS = 5L
    private const val READ_TIMEOUT_SECONDS = 12L
    private const val WRITE_TIMEOUT_SECONDS = 12L
    private const val CALL_TIMEOUT_SECONDS = 15L
    
    // ✅ Connection pool для переиспользования TCP-соединений
    private const val MAX_IDLE_CONNECTIONS = 5
    private const val KEEP_ALIVE_DURATION_MINUTES = 5L
    
    // ✅ Async logging scope для неблокирующего логирования
    private val loggingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
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
    // ✅ OKHTTP CLIENT - ULTRA OPTIMIZED
    // ════════════════════════════════════════════════════════════════════════════════
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = MAX_IDLE_CONNECTIONS,
                    keepAliveDuration = KEEP_ALIVE_DURATION_MINUTES,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .retryOnConnectionFailure(true)
        
        if (BuildConfig.BUILD_TYPE == "debug") {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                if (message.length <= 500) {
                    val sanitized = sanitizeApiKeys(message)
                    Timber.tag("HTTP").d(sanitized)
                    return@HttpLoggingInterceptor
                }
                
                if (isBase64ImageData(message)) {
                    loggingScope.launch {
                        Timber.tag("HTTP").d(
                            "[IMAGE DATA: ${message.length} chars - logged async for performance]"
                        )
                    }
                    return@HttpLoggingInterceptor
                }
                
                if (message.length > 5_000) {
                    val sanitized = sanitizeApiKeys(message)
                    Timber.tag("HTTP").d(
                        sanitized.take(1000) + "... [truncated ${message.length - 1000} chars]"
                    )
                    return@HttpLoggingInterceptor
                }
                
                val sanitized = sanitizeApiKeys(message)
                Timber.tag("HTTP").d(sanitized)
                
            }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ✅ HELPER FUNCTIONS
    // ════════════════════════════════════════════════════════════════════════════════
    
    private fun isBase64ImageData(message: String): Boolean {
        return message.contains("inlineData") ||
               message.contains("/9j/") ||
               message.contains("iVBORw") ||
               message.contains("\"data\":\"")
    }
    
    private fun sanitizeApiKeys(message: String): String {
        return message
            .replace(Regex("key=[^&\\s]+"), "key=***REDACTED***")
            .replace(Regex("AIza[0-9A-Za-z_-]{35}"), "AIza***REDACTED***")
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
    
    // ✅ ИСПРАВЛЕНО: Добавлен modelManager
    @Provides
    @Singleton
    fun provideGeminiTranslator(
        geminiApi: GeminiApi,
        translationCacheManager: TranslationCacheManager,
        keyManager: GeminiKeyManager,
        settingsDataStore: SettingsDataStore,
        modelManager: GeminiModelManager  // ✅ ДОБАВЛЕНО!
    ): GeminiTranslator {
        return GeminiTranslator(
            geminiApi = geminiApi,
            translationCacheManager = translationCacheManager,
            keyManager = keyManager,
            settingsDataStore = settingsDataStore,
            modelManager = modelManager  // ✅ ДОБАВЛЕНО!
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