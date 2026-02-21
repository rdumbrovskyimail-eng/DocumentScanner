package com.docs.scanner.data.remote.mlkit

/**
 * ✅ ОБНОВЛЕНО 2026: Адаптивные пороги для разных типов контента
 * 
 * Разные пороги для печатного vs рукописного текста
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
    val alwaysUseGemini: Boolean = false,
    
    // ✅ НОВЫЕ: Адаптивные пороги для разных типов контента
    
    /** Gemini threshold for printed text (higher = more ML Kit) */
    val printedTextGeminiThreshold: Float = 0.65f,
    
    /** Gemini threshold for handwritten text (lower = more Gemini) */
    val handwrittenGeminiThreshold: Float = 0.45f,
    
    /** Variance threshold for handwriting detection */
    val handwritingVarianceThreshold: Float = 0.12f,
    
    /** Max low-confidence ratio for printed text before fallback */
    val maxLowConfidenceRatioPrinted: Float = 0.25f,
    
    /** Max low-confidence ratio for handwritten text before fallback */
    val maxLowConfidenceRatioHandwritten: Float = 0.50f
) {
    companion object {
        /** Default production thresholds (balanced) */
        val DEFAULT = OcrQualityThresholds()
        
        /** Conservative - use ML Kit more (save API calls) */
        val CONSERVATIVE = OcrQualityThresholds(
            printedTextGeminiThreshold = 0.45f,
            handwrittenGeminiThreshold = 0.30f,
            maxLowConfidenceRatioPrinted = 0.40f,
            maxLowConfidenceRatioHandwritten = 0.60f
        )
        
        /** Aggressive - use Gemini more (better quality) */
        val AGGRESSIVE = OcrQualityThresholds(
            printedTextGeminiThreshold = 0.75f,
            handwrittenGeminiThreshold = 0.55f,
            maxLowConfidenceRatioPrinted = 0.15f,
            maxLowConfidenceRatioHandwritten = 0.40f
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
     * ✅ Адаптивный порог Gemini на основе типа контента
     */
    fun getGeminiThreshold(isHandwritten: Boolean): Float {
        return if (isHandwritten) {
            handwrittenGeminiThreshold
        } else {
            printedTextGeminiThreshold
        }
    }
    
    /**
     * ✅ Адаптивный порог низкой уверенности
     */
    fun getMaxLowConfidenceRatio(isHandwritten: Boolean): Float {
        return if (isHandwritten) {
            maxLowConfidenceRatioHandwritten
        } else {
            maxLowConfidenceRatioPrinted
        }
    }
    
    /**
     * Checks if these thresholds indicate fallback should trigger.
     */
    fun shouldTriggerFallback(
        confidence: Float,
        lowConfidenceRatio: Float,
        isHandwritten: Boolean
    ): Boolean {
        if (!geminiOcrEnabled) return false
        if (alwaysUseGemini) return true
        
        val adaptiveThreshold = getGeminiThreshold(isHandwritten)
        val adaptiveMaxRatio = getMaxLowConfidenceRatio(isHandwritten)
        
        return confidence < adaptiveThreshold || 
               lowConfidenceRatio > adaptiveMaxRatio
    }
}