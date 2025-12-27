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
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing network and remote service dependencies.
 * 
 * Session 4 & 7 fixes:
 * - ✅ Added GeminiTranslator (CRITICAL - was missing)
 * - ✅ Added GeminiApi wrapper
 * - ✅ Added DocumentScannerWrapper
 * - ✅ Integrated TranslationCacheManager
 * - ✅ HTTP logging DEBUG-only with API key masking
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // GSON
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
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
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // OKHTTP CLIENT
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides OkHttpClient with:
     * - Extended timeouts for Gemini API (60-120s)
     * - Retry on connection failure
     * - DEBUG-only logging with API key masking
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        
        // ✅ HTTP logging только в DEBUG режиме
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                // ✅ Маскируем API ключи в логах
                val sanitized = message
                    .replace(Regex("key=[^&\\s]+"), "key=***REDACTED***")
                    .replace(Regex("AIza[0-9A-Za-z_-]{35}"), "AIza***REDACTED***")
                
                android.util.Log.d("HTTP", sanitized)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // RETROFIT
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
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
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // GEMINI API WRAPPERS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides GeminiApi wrapper.
     * Handles rate limiting, retries, and error handling.
     * 
     * Session 4: Already exists in codebase.
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
     * 🔴 CRITICAL FIX (Session 4 & 7):
     * This was MISSING - app wouldn't compile!
     * 
     * Integrates with TranslationCacheManager to reduce API calls:
     * - Checks cache before API call
     * - Saves translation to cache after API call
     * - Supports language parameters (source/target)
     */
    @Provides
    @Singleton
    fun provideGeminiTranslator(
        geminiApi: GeminiApi,
        translationCacheManager: TranslationCacheManager,
        encryptedKeyStorage: EncryptedKeyStorage
    ): GeminiTranslator {
        return GeminiTranslator(
            geminiApi = geminiApi,
            translationCacheManager = translationCacheManager,
            encryptedKeyStorage = encryptedKeyStorage
        )
    }
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ML KIT & DOCUMENT SCANNER
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Provides ML Kit OCR scanner.
     * 
     * ✅ UPDATED (Session 4): Now accepts SettingsDataStore
     * for language selection.
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
     * ✅ NEW (Session 4): For scanning documents with camera.
     * Requires Google Play Services 23.0+
     */
    @Provides
    @Singleton
    fun provideDocumentScannerWrapper(
        @ApplicationContext context: Context
    ): DocumentScannerWrapper {
        return DocumentScannerWrapper(context)
    }
}