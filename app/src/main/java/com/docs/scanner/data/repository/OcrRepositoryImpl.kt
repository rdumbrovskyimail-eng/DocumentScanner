package com.docs.scanner.data.repository

import android.net.Uri
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.data.remote.gemini.GeminiTranslator
import com.docs.scanner.data.remote.mlkit.MLKitScanner
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.OcrRepository
import com.google.mlkit.vision.text.Text.TextBlock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of OcrRepository using ML Kit and Gemini.
 * 
 * Session 5 addition:
 * - Separates OCR from ScannerRepository
 * - Combines ML Kit + Gemini for best quality
 */
@Singleton
class OcrRepositoryImpl @Inject constructor(
    private val mlKitScanner: MLKitScanner,
    private val geminiTranslator: GeminiTranslator,
    private val encryptedKeyStorage: EncryptedKeyStorage
) : OcrRepository {
    
    override suspend fun recognizeText(imageUri: Uri): Result<String> {
        return mlKitScanner.scanImage(imageUri)
    }
    
    override suspend fun recognizeTextDetailed(imageUri: Uri): Result<List<TextBlock>> {
        return mlKitScanner.scanImageDetailed(imageUri)
    }
    
    override suspend fun improveOcrText(rawText: String): Result<String> {
        if (rawText.isBlank()) {
            return Result.Error(Exception("Text cannot be empty"))
        }
        
        // Delegate to GeminiTranslator fixOcrText
        return geminiTranslator.fixOcrText(
            text = rawText,
            useCache = false  // OCR correction usually not cached
        )
    }
}