package com.docs.scanner.data.repository

import android.content.Context
import android.net.Uri
import com.docs.scanner.data.remote.GeminiApiService
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.OcrRepository
import com.docs.scanner.domain.repository.ScannerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ScannerRepository.
 * 
 * Combines OCR (ML Kit) and AI (Gemini) capabilities for:
 * - Text extraction from images
 * - Translation to target language
 * - OCR error correction
 */
@Singleton
class ScannerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrRepository: OcrRepository,
    private val geminiApiService: GeminiApiService
) : ScannerRepository {

    companion object {
        private const val TAG = "ScannerRepository"
        
        // Supported language codes
        private val LANGUAGE_NAMES = mapOf(
            "ru" to "Russian",
            "en" to "English",
            "zh" to "Chinese (Simplified)",
            "zh-TW" to "Chinese (Traditional)",
            "ja" to "Japanese",
            "ko" to "Korean",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "ar" to "Arabic",
            "hi" to "Hindi",
            "uk" to "Ukrainian",
            "pl" to "Polish",
            "nl" to "Dutch",
            "tr" to "Turkish",
            "vi" to "Vietnamese",
            "th" to "Thai"
        )
    }

    /**
     * Scan image and extract text using OCR.
     */
    override suspend fun scanImage(imagePath: String): Result<String> {
        return try {
            if (imagePath.isBlank()) {
                return Result.Error(IllegalArgumentException("Image path cannot be empty"))
            }
            
            Timber.tag(TAG).d("Starting OCR for: $imagePath")
            
            val uri = Uri.parse(imagePath)
            val ocrResult = ocrRepository.recognizeText(uri)
            
            when (ocrResult) {
                is Result.Success -> {
                    Timber.tag(TAG).d("OCR completed: ${ocrResult.data.length} chars")
                    Result.Success(ocrResult.data)
                }
                is Result.Error -> {
                    Timber.tag(TAG).e(ocrResult.exception, "OCR failed")
                    Result.Error(ocrResult.exception, "OCR recognition failed")
                }
                is Result.Loading -> Result.Loading
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Unexpected error during OCR")
            Result.Error(e, "Unexpected OCR error: ${e.message}")
        }
    }

    /**
     * Translate text to target language using Gemini AI.
     * 
     * FIX C4: Added targetLanguage parameter
     */
    override suspend fun translateText(
        text: String,
        targetLanguage: String
    ): Result<String> {
        return try {
            // Validation
            if (text.isBlank()) {
                return Result.Error(
                    IllegalArgumentException("Text cannot be empty"),
                    "Text to translate is empty"
                )
            }
            
            if (text.length > 30000) {
                return Result.Error(
                    IllegalArgumentException("Text too long"),
                    "Text exceeds maximum length of 30,000 characters"
                )
            }
            
            val languageName = getLanguageName(targetLanguage)
            Timber.tag(TAG).d("Starting translation to $languageName (${text.length} chars)")
            
            val prompt = buildTranslationPrompt(text, languageName)
            val result = geminiApiService.generateContent(prompt)
            
            when (result) {
                is Result.Success -> {
                    val translatedText = validateTranslationResult(result.data)
                    Timber.tag(TAG).d("Translation completed: ${translatedText.length} chars")
                    Result.Success(translatedText)
                }
                is Result.Error -> {
                    Timber.tag(TAG).e(result.exception, "Translation failed")
                    Result.Error(result.exception, "Translation failed: ${result.message}")
                }
                is Result.Loading -> Result.Loading
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Unexpected error during translation")
            Result.Error(e, "Unexpected translation error: ${e.message}")
        }
    }

    /**
     * Fix OCR errors using Gemini AI.
     */
    override suspend fun fixOcrText(text: String): Result<String> {
        return try {
            if (text.isBlank()) {
                return Result.Error(
                    IllegalArgumentException("Text cannot be empty"),
                    "Text to fix is empty"
                )
            }
            
            Timber.tag(TAG).d("Starting OCR fix (${text.length} chars)")
            
            val prompt = buildOcrFixPrompt(text)
            val result = geminiApiService.generateContent(prompt)
            
            when (result) {
                is Result.Success -> {
                    val fixedText = result.data.trim()
                    Timber.tag(TAG).d("OCR fix completed")
                    Result.Success(fixedText)
                }
                is Result.Error -> {
                    Timber.tag(TAG).e(result.exception, "OCR fix failed")
                    Result.Error(result.exception, "OCR correction failed")
                }
                is Result.Loading -> Result.Loading
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Unexpected error during OCR fix")
            Result.Error(e, "Unexpected OCR fix error: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════

    private fun getLanguageName(code: String): String {
        return LANGUAGE_NAMES[code.lowercase()] ?: code
    }

    private fun buildTranslationPrompt(text: String, targetLanguage: String): String {
        return """
            |Translate the following text to $targetLanguage.
            |
            |Instructions:
            |1. Preserve the original formatting and structure
            |2. Maintain paragraph breaks and line spacing
            |3. Keep any special characters or symbols
            |4. Only output the translation, no explanations or notes
            |5. If the text is already in $targetLanguage, return it unchanged
            |
            |Text to translate:
            |$text
        """.trimMargin()
    }

    private fun buildOcrFixPrompt(text: String): String {
        return """
            |Fix any OCR (Optical Character Recognition) errors in the following text.
            |
            |Instructions:
            |1. Correct spelling mistakes caused by OCR misreading
            |2. Fix broken or merged words
            |3. Correct character substitutions (e.g., 0 vs O, 1 vs l)
            |4. Improve readability while preserving original meaning
            |5. Maintain the original structure and formatting
            |6. Only output the corrected text, no explanations
            |
            |Text to fix:
            |$text
        """.trimMargin()
    }

    private fun validateTranslationResult(text: String): String {
        // Remove any potential AI commentary
        val cleaned = text.trim()
        
        // Check for common AI response patterns to remove
        val patterns = listOf(
            "Here is the translation:",
            "Here's the translation:",
            "Translation:",
            "Translated text:",
            "The translation is:"
        )
        
        var result = cleaned
        for (pattern in patterns) {
            if (result.startsWith(pattern, ignoreCase = true)) {
                result = result.substring(pattern.length).trim()
            }
        }
        
        return result
    }
}