package com.docs.scanner.data.repository

import android.net.Uri
import android.util.Log
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
 * 
 * ⚠️ NOTE: This implementation uses ML Kit TextBlock directly
 * because the OcrRepository interface (FILE 54) requires it.
 * When OcrRepository interface is updated in Pack 6, this can
 * be refactored to use domain OcrTextBlock instead.
 */
@Singleton
class OcrRepositoryImpl @Inject constructor(
    private val mlKitScanner: MLKitScanner,
    private val geminiTranslator: GeminiTranslator,
    private val encryptedKeyStorage: EncryptedKeyStorage  // ✅ Сохранён для совместимости с Hilt
) : OcrRepository {
    
    companion object {
        private const val TAG = "OcrRepository"
    }
    
    override suspend fun recognizeText(imageUri: Uri): Result<String> {
        Log.d(TAG, "📷 Recognizing text from: $imageUri")
        return mlKitScanner.scanImage(imageUri)
    }
    
    /**
     * ⚠️ Returns ML Kit TextBlock as required by current interface.
     * Will be updated to return domain OcrTextBlock when interface is fixed.
     */
    override suspend fun recognizeTextDetailed(imageUri: Uri): Result<List<TextBlock>> {
        Log.d(TAG, "📷 Recognizing detailed text from: $imageUri")
        return when (val result = mlKitScanner.scanImageDetailed(imageUri)) {
            is Result.Success -> {
                // Convert MLKitScanner.TextBlock to ML Kit TextBlock
                // This is a workaround until interface is updated
                // For now, return empty list with error since types don't match
                Result.Error(Exception("Detailed OCR not available - interface needs update"))
            }
            is Result.Error -> Result.Error(result.exception)
            Result.Loading -> Result.Loading
        }
    }
    
    override suspend fun improveOcrText(rawText: String): Result<String> {
        if (rawText.isBlank()) {
            return Result.Error(Exception("Text cannot be empty"))
        }
        
        Log.d(TAG, "🔧 Improving OCR text (${rawText.length} chars)")
        
        // Delegate to GeminiTranslator fixOcrText
        return geminiTranslator.fixOcrText(
            text = rawText,
            useCache = false  // OCR correction usually not cached
        )
    }
}
