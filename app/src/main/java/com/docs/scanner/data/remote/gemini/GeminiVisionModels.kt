package com.docs.scanner.data.remote.gemini

/**
 * Request/Response models for Gemini Vision API.
 * 
 * Version: 2.0.0 - SPEED OPTIMIZED (2026)
 * 
 * ✅ NEW IN 2.0.0:
 * - OCR_FAST GenerationConfig with temperature=0, topK=20
 * - Reduced maxOutputTokens for faster processing
 * - TEXT_FIX config for text correction
 * 
 * Supports both text-only and multimodal (text + image) requests.
 * Uses simple data classes - Retrofit/Gson handles serialization automatically.
 */

// ════════════════════════════════════════════════════════════════════════════════
// REQUEST MODELS
// ════════════════════════════════════════════════════════════════════════════════

data class GeminiVisionRequest(
    val contents: List<VisionContent>,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null
)

data class VisionContent(
    val parts: List<VisionPart>,
    val role: String = "user"
)

/**
 * A part can be either text or inline image data.
 * For JSON serialization, we use nullable fields.
 */
data class VisionPart(
    val text: String? = null,
    val inlineData: InlineData? = null
) {
    companion object {
        fun text(value: String) = VisionPart(text = value)
        fun image(mimeType: String, base64Data: String) = VisionPart(
            inlineData = InlineData(mimeType, base64Data)
        )
    }
}

data class InlineData(
    val mimeType: String,
    val data: String
)

/**
 * Generation configuration for Gemini API.
 * 
 * ✅ OPTIMIZED for speed in 2.0.0:
 * - temperature = 0.0 for deterministic output (faster)
 * - topK = 20 for faster sampling (was 40)
 * - Reduced maxOutputTokens where appropriate
 */
data class GenerationConfig(
    val maxOutputTokens: Int = 4096,
    val temperature: Float = 0.1f,
    val topP: Float = 0.95f,
    val topK: Int = 40
) {
    companion object {
        /**
         * ✅ OPTIMIZED: Fast OCR config for maximum speed
         * 
         * Changes from original OCR config:
         * - maxOutputTokens: 8192 → 4096 (OCR rarely needs more)
         * - temperature: 0.1 → 0.0 (deterministic = faster)
         * - topP: 0.95 → 0.9 (slightly narrower)
         * - topK: 40 → 20 (faster sampling)
         */
        val OCR_FAST = GenerationConfig(
            maxOutputTokens = 4096,
            temperature = 0.0f,
            topP = 0.9f,
            topK = 20
        )
        
        /** Legacy OCR config for compatibility */
        val OCR = OCR_FAST

        /** Config for translation - slightly higher temperature for natural text */
        val TRANSLATION = GenerationConfig(
            maxOutputTokens = 4096,
            temperature = 0.2f,
            topP = 0.9f,
            topK = 30
        )
        
        /** Config for text correction/fixing */
        val TEXT_FIX = GenerationConfig(
            maxOutputTokens = 4096,
            temperature = 0.1f,
            topP = 0.9f,
            topK = 20
        )
    }
}

data class SafetySetting(
    val category: String,
    val threshold: String
) {
    companion object {
        /** Default safety settings - block only high probability harmful content */
        val DEFAULT = listOf(
            SafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_ONLY_HIGH"),
            SafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_ONLY_HIGH"),
            SafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_ONLY_HIGH"),
            SafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_ONLY_HIGH")
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// RESPONSE MODELS
// ════════════════════════════════════════════════════════════════════════════════

data class GeminiVisionResponse(
    val candidates: List<VisionCandidate>?,
    val promptFeedback: PromptFeedback?,
    val usageMetadata: UsageMetadata?
)

data class VisionCandidate(
    val content: VisionCandidateContent?,
    val finishReason: String?,
    val safetyRatings: List<SafetyRating>?
)

data class VisionCandidateContent(
    val parts: List<VisionResponsePart>?,
    val role: String?
)

data class VisionResponsePart(
    val text: String?
)

data class PromptFeedback(
    val blockReason: String?,
    val safetyRatings: List<SafetyRating>?
)

data class SafetyRating(
    val category: String?,
    val probability: String?
)

data class UsageMetadata(
    val promptTokenCount: Int?,
    val candidatesTokenCount: Int?,
    val totalTokenCount: Int?
)

// ════════════════════════════════════════════════════════════════════════════════
// HELPER EXTENSIONS
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Extracts text from Gemini response.
 * Joins all text parts from first candidate.
 */
fun GeminiVisionResponse.extractText(): String {
    return candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.mapNotNull { it.text }
        ?.joinToString("\n")
        ?.trim()
        .orEmpty()
}

/**
 * Checks if response was blocked by safety filters.
 */
fun GeminiVisionResponse.isBlocked(): Boolean {
    return promptFeedback?.blockReason != null
}

/**
 * Gets block reason if response was blocked.
 */
fun GeminiVisionResponse.getBlockReason(): String? {
    return promptFeedback?.blockReason
}

/**
 * Builder for creating vision requests easily.
 */
class GeminiVisionRequestBuilder {
    private val parts = mutableListOf<VisionPart>()
    private var config: GenerationConfig = GenerationConfig.OCR_FAST
    private var safety: List<SafetySetting> = SafetySetting.DEFAULT

    fun addText(text: String): GeminiVisionRequestBuilder {
        parts.add(VisionPart.text(text))
        return this
    }

    fun addImage(base64Data: String, mimeType: String = "image/jpeg"): GeminiVisionRequestBuilder {
        parts.add(VisionPart.image(mimeType, base64Data))
        return this
    }

    fun config(config: GenerationConfig): GeminiVisionRequestBuilder {
        this.config = config
        return this
    }

    fun safetySettings(settings: List<SafetySetting>): GeminiVisionRequestBuilder {
        this.safety = settings
        return this
    }

    fun build(): GeminiVisionRequest {
        require(parts.isNotEmpty()) { "Request must have at least one part" }
        return GeminiVisionRequest(
            contents = listOf(VisionContent(parts = parts)),
            generationConfig = config,
            safetySettings = safety
        )
    }
}

/** DSL-style builder function */
fun geminiVisionRequest(block: GeminiVisionRequestBuilder.() -> Unit): GeminiVisionRequest {
    return GeminiVisionRequestBuilder().apply(block).build()
}