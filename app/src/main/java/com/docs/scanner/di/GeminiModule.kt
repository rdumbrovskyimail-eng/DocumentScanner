package com.docs.scanner.di

import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.data.remote.gemini.GeminiApiService
import com.docs.scanner.data.remote.gemini.GeminiKeyManager
import com.docs.scanner.data.remote.gemini.GeminiOcrService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeminiModule {
    
    @Provides
    @Singleton
    fun provideGeminiKeyManager(
        keyStorage: EncryptedKeyStorage
    ): GeminiKeyManager {
        return GeminiKeyManager(keyStorage)
    }
    
    // GeminiOcrService уже имеет @Inject constructor, поэтому
    // Hilt создаст его автоматически. Этот provide нужен только
    // если требуется кастомная конфигурация.
}