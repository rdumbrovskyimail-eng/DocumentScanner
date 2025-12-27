package com.docs.scanner.domain.repository

import android.net.Uri
import com.docs.scanner.domain.model.Result
import com.google.mlkit.vision.text.Text.TextBlock

/**
 * Domain repository interface for OCR operations.
 * 
 * Session 5 addition:
 * - Separates OCR logic from ScannerRepository (SRP)
 * - Handles ML Kit text recognition
 * - OCR text improvement via Gemini
 * 
 * Responsibilities:
 * - Text recognition from images (ML Kit)
 * - Detailed text block extraction
 * - OCR error correction (Gemini)
 */
interface OcrRepository {
    
    /**
     * Recognize text from image using ML Kit.
     * 
     * @param imageUri Image URI to process
     * @return Result with recognized text
     */
    suspend fun recognizeText(imageUri: Uri): Result<String>
    
    /**
     * Recognize text with detailed block information.
     * 
     * Returns structured text blocks with:
     * - Text content
     * - Bounding boxes
     * - Confidence scores
     * - Line/paragraph structure
     * 
     * @param imageUri Image URI to process
     * @return Result with list of text blocks
     */
    suspend fun recognizeTextDetailed(imageUri: Uri): Result<List<TextBlock>>
    
    /**
     * Improve OCR text quality using Gemini AI.
     * 
     * Fixes common OCR errors:
     * - Confused characters (0/O, 1/l, 5/S)
     * - Extra spaces
     * - Missing punctuation
     * - Broken words
     * 
     * @param rawText Raw OCR text with potential errors
     * @return Result with corrected text
     */
    suspend fun improveOcrText(rawText: String): Result<String>
}