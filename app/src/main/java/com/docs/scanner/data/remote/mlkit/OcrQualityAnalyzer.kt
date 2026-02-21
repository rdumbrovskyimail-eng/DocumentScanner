package com.docs.scanner.data.remote.mlkit

import com.docs.scanner.BuildConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Analyzes OCR results quality to determine if Gemini fallback is needed.
 * 
 * ✅ ОБНОВЛЕНО 2026: Адаптивные пороги для печатного vs рукописного текста
 * 
 * Decision factors:
 * - Overall confidence score
 * - Ratio of low-confidence words
 * - Confidence variance (high variance = inconsistent recognition = likely handwritten)
 * - Text density and structure
 * - Adaptive thresholds based on content type
 * 
 * Typical scenarios for Gemini fallback:
 * - Handwritten text (high variance, medium-low confidence)
 * - Blurry/damaged documents (low confidence across the board)
 * - Mixed printed/handwritten forms
 * - Unusual fonts or stylized text
 */
@Singleton
class OcrQualityAnalyzer @Inject constructor() {
    
    companion object {
        private const val TAG = "OcrQualityAnalyzer"
        
        // Confidence thresholds
        private const val EXCELLENT_THRESHOLD = 0.85f
        private const val GOOD_THRESHOLD = 0.70f
        private const val FAIR_THRESHOLD = 0.50f
        private const val LOW_CONFIDENCE_WORD_THRESHOLD = 0.5f
        
        // Fallback triggers
        private const val MAX_LOW_CONFIDENCE_RATIO = 0.40f  // 40% low-conf words → fallback
        private const val MAX_VARIANCE_FOR_PRINTED = 0.09f  // StdDev² > 0.09 suggests handwritten
        private const val MIN_TEXT_LENGTH_FOR_ANALYSIS = 3  // Need at least 3 chars
        
        // Handwriting detection
        private const val HANDWRITING_VARIANCE_THRESHOLD = 0.12f
        private const val HANDWRITING_CONFIDENCE_CEILING = 0.75f
    }
    
    /**
     * Quality levels for OCR results.
     */
    enum class OcrQuality {
        /** Confidence >= 85%, minimal issues */
        EXCELLENT,
        
        /** Confidence >= 70%, acceptable quality */
        GOOD,
        
        /** Confidence >= 50%, may have errors */
        FAIR,
        
        /** Confidence < 50%, significant issues → recommend Gemini */
        POOR,
        
        /** No text or too short to analyze */
        FAILED
    }
    
    /**
     * Detailed quality metrics for an OCR result.
     */
    data class QualityMetrics(
        val overallConfidence: Float,
        val lowConfidenceRatio: Float,
        val confidenceVariance: Float,
        val confidenceStdDev: Float,
        val wordCount: Int,
        val averageWordLength: Float,
        val quality: OcrQuality,
        val isLikelyHandwritten: Boolean,
        val recommendGeminiFallback: Boolean,
        val fallbackReasons: List<String>
    ) {
        val qualityPercent: Int get() = (overallConfidence * 100).toInt()
        
        override fun toString(): String {
            return "QualityMetrics(quality=$quality, confidence=${qualityPercent}%, " +
                   "lowConfRatio=${(lowConfidenceRatio * 100).toInt()}%, " +
                   "handwritten=$isLikelyHandwritten, fallback=$recommendGeminiFallback)"
        }
    }
    
    /**
     * Analyzes OcrResultWithConfidence and returns quality metrics.
     */
    fun analyze(result: OcrResultWithConfidence): QualityMetrics {
        val text = result.text
        val words = result.words
        
        // Handle empty or very short results
        if (text.isBlank() || text.length < MIN_TEXT_LENGTH_FOR_ANALYSIS) {
            return QualityMetrics(
                overallConfidence = 0f,
                lowConfidenceRatio = 1f,
                confidenceVariance = 0f,
                confidenceStdDev = 0f,
                wordCount = 0,
                averageWordLength = 0f,
                quality = OcrQuality.FAILED,
                isLikelyHandwritten = false,
                recommendGeminiFallback = true,
                fallbackReasons = listOf("No text recognized")
            )
        }
        
        // Calculate metrics
        val confidences = words.map { it.confidence }
        val overallConfidence = result.overallConfidence ?: confidences.average().toFloat()
        
        val lowConfidenceCount = words.count { it.confidence < LOW_CONFIDENCE_WORD_THRESHOLD }
        val lowConfidenceRatio = if (words.isNotEmpty()) {
            lowConfidenceCount.toFloat() / words.size
        } else 0f
        
        val variance = calculateVariance(confidences)
        val stdDev = sqrt(variance)
        
        val avgWordLength = if (words.isNotEmpty()) {
            words.map { it.text.length }.average().toFloat()
        } else 0f
        
        // Determine quality level
        val quality = classifyQuality(overallConfidence, lowConfidenceRatio)
        
        // Detect likely handwritten text
        val isLikelyHandwritten = detectHandwritten(
            overallConfidence = overallConfidence,
            variance = variance,
            lowConfidenceRatio = lowConfidenceRatio
        )
        
        // Determine if Gemini fallback is recommended
        val (shouldFallback, reasons) = evaluateFallback(
            quality = quality,
            overallConfidence = overallConfidence,
            lowConfidenceRatio = lowConfidenceRatio,
            variance = variance,
            isLikelyHandwritten = isLikelyHandwritten,
            wordCount = words.size
        )
        
        val metrics = QualityMetrics(
            overallConfidence = overallConfidence,
            lowConfidenceRatio = lowConfidenceRatio,
            confidenceVariance = variance,
            confidenceStdDev = stdDev,
            wordCount = words.size,
            averageWordLength = avgWordLength,
            quality = quality,
            isLikelyHandwritten = isLikelyHandwritten,
            recommendGeminiFallback = shouldFallback,
            fallbackReasons = reasons
        )
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: $metrics")
        }
        
        return metrics
    }
    
    /**
     * Simplified analysis from basic OcrResult (without word-level confidence).
     * Uses overall confidence only.
     */
    fun analyzeSimple(confidence: Float?, textLength: Int): QualityMetrics {
        val conf = confidence ?: 0f
        
        if (textLength < MIN_TEXT_LENGTH_FOR_ANALYSIS) {
            return QualityMetrics(
                overallConfidence = conf,
                lowConfidenceRatio = 1f,
                confidenceVariance = 0f,
                confidenceStdDev = 0f,
                wordCount = 0,
                averageWordLength = 0f,
                quality = OcrQuality.FAILED,
                isLikelyHandwritten = false,
                recommendGeminiFallback = true,
                fallbackReasons = listOf("No text recognized")
            )
        }
        
        val quality = when {
            conf >= EXCELLENT_THRESHOLD -> OcrQuality.EXCELLENT
            conf >= GOOD_THRESHOLD -> OcrQuality.GOOD
            conf >= FAIR_THRESHOLD -> OcrQuality.FAIR
            else -> OcrQuality.POOR
        }
        
        val shouldFallback = quality == OcrQuality.POOR || quality == OcrQuality.FAILED
        val reasons = if (shouldFallback) {
            listOf("Low overall confidence: ${(conf * 100).toInt()}%")
        } else emptyList()
        
        return QualityMetrics(
            overallConfidence = conf,
            lowConfidenceRatio = if (conf < FAIR_THRESHOLD) 1f else 0f,
            confidenceVariance = 0f,
            confidenceStdDev = 0f,
            wordCount = textLength / 5, // Rough estimate
            averageWordLength = 5f,
            quality = quality,
            isLikelyHandwritten = conf < HANDWRITING_CONFIDENCE_CEILING && conf > 0.3f,
            recommendGeminiFallback = shouldFallback,
            fallbackReasons = reasons
        )
    }
    
    /**
     * Quick check if fallback is needed based on OcrResultWithConfidence.
     */
    fun shouldFallbackToGemini(result: OcrResultWithConfidence): Boolean {
        return analyze(result).recommendGeminiFallback
    }
    
    /**
     * Quick check if fallback is needed based on simple metrics.
     */
    fun shouldFallbackToGemini(confidence: Float?, textLength: Int): Boolean {
        if (textLength < MIN_TEXT_LENGTH_FOR_ANALYSIS) return true
        val conf = confidence ?: return true
        return conf < FAIR_THRESHOLD
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════════════════
    
    private fun classifyQuality(confidence: Float, lowConfidenceRatio: Float): OcrQuality {
        return when {
            confidence >= EXCELLENT_THRESHOLD && lowConfidenceRatio < 0.1f -> OcrQuality.EXCELLENT
            confidence >= GOOD_THRESHOLD && lowConfidenceRatio < 0.25f -> OcrQuality.GOOD
            confidence >= FAIR_THRESHOLD && lowConfidenceRatio < 0.5f -> OcrQuality.FAIR
            confidence > 0f -> OcrQuality.POOR
            else -> OcrQuality.FAILED
        }
    }
    
    private fun detectHandwritten(
        overallConfidence: Float,
        variance: Float,
        lowConfidenceRatio: Float
    ): Boolean {
        // Handwritten text typically has:
        // 1. High variance in confidence (some words recognized well, others poorly)
        // 2. Medium-low overall confidence
        // 3. Significant portion of low-confidence words
        
        val hasHighVariance = variance > HANDWRITING_VARIANCE_THRESHOLD
        val hasMediumConfidence = overallConfidence in 0.3f..HANDWRITING_CONFIDENCE_CEILING
        val hasSignificantLowConf = lowConfidenceRatio > 0.2f
        
        // Need at least 2 of 3 indicators
        val indicators = listOf(hasHighVariance, hasMediumConfidence, hasSignificantLowConf)
        return indicators.count { it } >= 2
    }
    
    /**
     * ✅ ОБНОВЛЕНО: Адаптивная оценка fallback с учетом типа контента
     */
    private fun evaluateFallback(
        quality: OcrQuality,
        overallConfidence: Float,
        lowConfidenceRatio: Float,
        variance: Float,
        isLikelyHandwritten: Boolean,
        wordCount: Int
    ): Pair<Boolean, List<String>> {
        val reasons = mutableListOf<String>()
        
        // Automatic fallback conditions
        if (quality == OcrQuality.FAILED) {
            reasons.add("No text recognized")
            return true to reasons
        }
        
        if (quality == OcrQuality.POOR) {
            reasons.add("Very low confidence: ${(overallConfidence * 100).toInt()}%")
        }
        
        // ✅ Адаптивные пороги на основе типа контента
        val adaptiveGeminiThreshold = if (isLikelyHandwritten) {
            0.45f  // 45% для рукописного
        } else {
            0.65f  // 65% для печатного
        }
        
        val adaptiveMaxLowConfRatio = if (isLikelyHandwritten) {
            0.50f  // 50% для рукописного
        } else {
            0.25f  // 25% для печатного
        }
        
        if (overallConfidence < adaptiveGeminiThreshold) {
            reasons.add("Confidence ${(overallConfidence * 100).toInt()}% < threshold ${(adaptiveGeminiThreshold * 100).toInt()}%")
        }
        
        if (lowConfidenceRatio > adaptiveMaxLowConfRatio) {
            reasons.add("${(lowConfidenceRatio * 100).toInt()}% words with low confidence")
        }
        
        if (isLikelyHandwritten && overallConfidence < GOOD_THRESHOLD) {
            reasons.add("Likely handwritten text detected")
        }
        
        if (variance > MAX_VARIANCE_FOR_PRINTED && overallConfidence < GOOD_THRESHOLD) {
            reasons.add("Inconsistent recognition (possible mixed content)")
        }
        
        // Small amount of text with low confidence
        if (wordCount < 10 && overallConfidence < FAIR_THRESHOLD) {
            reasons.add("Short text with low confidence")
        }
        
        return reasons.isNotEmpty() to reasons
    }
    
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }
}