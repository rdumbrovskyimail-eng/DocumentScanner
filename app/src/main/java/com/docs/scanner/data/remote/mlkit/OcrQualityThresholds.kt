package com.docs.scanner.data.remote.mlkit

/**
 * Configurable thresholds for OCR quality analysis.
 * Can be adjusted via Settings UI.
 */
data class OcrQualityThresholds(
    /** Minimum confidence to consider OCR successful without fallback */
    val minConfidenceForSuccess: Float = 0.50f,
    
    /** Maximum ratio of low-confidence words before triggering fallback */
    val maxLowConfidenceRatio: Float = 0.40f,
    
    /** Confidence threshold to mark individual word as "low confidence" */
    val lowConfidenceWordThreshold: Float = 0.50f,
    
    /** Whether to enable Gemini fallback at all */
    val geminiOcrEnabled: Boolean = true,
    
    /** Whether to always use Gemini (skip ML Kit entirely) */
    val alwaysUseGemini: Boolean = false
) {
    companion object {
        /** Default production thresholds */
        val DEFAULT = OcrQualityThresholds()
        
        /** Aggressive fallback - use Gemini more often */
        val AGGRESSIVE = OcrQualityThresholds(
            minConfidenceForSuccess = 0.65f,
            maxLowConfidenceRatio = 0.25f
        )
        
        /** Conservative fallback - use ML Kit more, save API calls */
        val CONSERVATIVE = OcrQualityThresholds(
            minConfidenceForSuccess = 0.35f,
            maxLowConfidenceRatio = 0.60f
        )
        
        /** Gemini only - skip ML Kit */
        val GEMINI_ONLY = OcrQualityThresholds(
            geminiOcrEnabled = true,
            alwaysUseGemini = true
        )
        
        /** ML Kit only - no fallback */
        val MLKIT_ONLY = OcrQualityThresholds(
            geminiOcrEnabled = false
        )
    }
    
    /**
     * Checks if these thresholds indicate fallback should trigger.
     */
    fun shouldTriggerFallback(confidence: Float, lowConfidenceRatio: Float): Boolean {
        if (!geminiOcrEnabled) return false
        if (alwaysUseGemini) return true
        
        return confidence < minConfidenceForSuccess || 
               lowConfidenceRatio > maxLowConfidenceRatio
    }
}