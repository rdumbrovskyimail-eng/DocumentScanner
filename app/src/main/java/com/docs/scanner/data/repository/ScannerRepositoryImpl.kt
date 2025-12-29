package com.docs.scanner.data.repository

import android.content.Context
import android.net.Uri
import com.docs.scanner.domain.repository.ScannerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ScannerRepository.
 * 
 * Responsibilities:
 * - Document scanning (delegates to ML Kit Document Scanner)
 * - OCR text recognition (delegates to OcrRepository)
 * - Translation (delegates to TranslationRepository)
 * 
 * Note: This is a facade repository that coordinates other repositories.
 * Consider refactoring to use cases if logic becomes complex.
 */
@Singleton
class ScannerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrRepository: com.docs.scanner.domain.repository.OcrRepository,
    private val translationRepository: com.docs.scanner.domain.repository.TranslationRepository
) : ScannerRepository {
    
    override suspend fun scanImage(imageUri: Uri): Result<String> {
        return try {
            // Delegate to OcrRepository
            ocrRepository.recognizeText(imageUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun translateText(text: String): Result<String> {
        return try {
            require(text.isNotBlank()) { "Text cannot be blank" }
            
            // Delegate to TranslationRepository
            translationRepository.translateText(
                text = text,
                sourceLanguage = "auto",
                targetLanguage = "ru" // TODO: Get from settings
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun fixOcrText(text: String): Result<String> {
        return try {
            require(text.isNotBlank()) { "Text cannot be blank" }
            
            // Delegate to OcrRepository
            ocrRepository.improveOcrText(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}