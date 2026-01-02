package com.docs.scanner.data.repository

import android.net.Uri
import com.docs.scanner.data.remote.gemini.GeminiTranslator
import com.docs.scanner.data.remote.mlkit.MLKitScanner
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.OcrRepository
import com.google.mlkit.vision.text.Text.TextBlock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of OcrRepository.
 * 
 * Combines:
 * - ML Kit for OCR (text extraction)
 * - Gemini for OCR improvement (error correction)
 */
@Singleton
class OcrRepositoryImpl @Inject constructor(
    private val mlKitScanner: MLKitScanner,
    private val geminiTranslator: GeminiTranslator
) : OcrRepository {
    
    /**
     * Recognize text from image using ML Kit.
     */
    override suspend fun recognizeText(imageUri: Uri): Result<String> {
        return try {
            Timber.d("Starting OCR for: $imageUri")
            mlKitScanner.scanImage(imageUri)
        } catch (e: Exception) {
            Timber.e(e, "OCR failed")
            Result.Error(e)
        }
    }
    
    /**
     * Recognize text with detailed block information.
     * Useful for maintaining document structure.
     */
    override suspend fun recognizeTextDetailed(imageUri: Uri): Result<List<TextBlock>> {
        return try {
            Timber.d("Starting detailed OCR for: $imageUri")
            mlKitScanner.scanImageDetailed(imageUri)
        } catch (e: Exception) {
            Timber.e(e, "Detailed OCR failed")
            Result.Error(e)
        }
    }
    
    /**
     * Improve OCR text using Gemini AI.
     * Fixes common OCR errors like:
     * - Confused characters (0/O, 1/l/I)
     * - Broken words
     * - Extra symbols
     */
    override suspend fun improveOcrText(rawText: String): Result<String> {
        if (rawText.isBlank()) {
            return Result.Error(IllegalArgumentException("Text cannot be empty"))
        }
        
        return try {
            Timber.d("Improving OCR text (${rawText.length} chars)")
            geminiTranslator.fixOcrText(
                text = rawText,
                useCache = false  // OCR correction usually not cached
            )
        } catch (e: Exception) {
            Timber.e(e, "OCR improvement failed")
            Result.Error(e)
        }
    }
}