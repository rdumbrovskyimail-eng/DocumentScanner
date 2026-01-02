package com.docs.scanner.domain.repository

import android.net.Uri
import com.docs.scanner.domain.model.Result
import com.google.mlkit.vision.text.Text.TextBlock

/**
 * Repository interface for OCR operations.
 * 
 * Provides text extraction from images using ML Kit.
 */
interface OcrRepository {
    
    /**
     * Recognize text from image.
     * 
     * @param imageUri URI of the image to scan
     * @return Result with extracted text
     */
    suspend fun recognizeText(imageUri: Uri): Result<String>
    
    /**
     * Recognize text with detailed block information.
     * 
     * Provides structured output with:
     * - Text blocks (paragraphs)
     * - Lines within blocks
     * - Bounding boxes
     * - Confidence scores
     * 
     * @param imageUri URI of the image to scan
     * @return Result with list of text blocks
     */
    suspend fun recognizeTextDetailed(imageUri: Uri): Result<List<TextBlock>>
    
    /**
     * Improve OCR text using AI.
     * 
     * Fixes common OCR errors:
     * - Confused characters (0/O, 1/l/I)
     * - Broken or merged words
     * - Extra symbols or noise
     * 
     * @param rawText Raw OCR output
     * @return Result with improved text
     */
    suspend fun improveOcrText(rawText: String): Result<String>
}