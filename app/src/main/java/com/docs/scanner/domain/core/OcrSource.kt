package com.docs.scanner.domain.core

/**
 * Indicates which OCR engine produced the result.
 */
enum class OcrSource {
    /** Google ML Kit on-device OCR */
    ML_KIT,
    
    /** Gemini Vision API (cloud) */
    GEMINI,
    
    /** Unknown or legacy source */
    UNKNOWN
}