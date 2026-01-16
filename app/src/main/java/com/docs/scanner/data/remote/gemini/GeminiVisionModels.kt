package com.docs.scanner.data.remote.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request/Response models for Gemini Vision API.
 * Supports both text-only and multimodal (text + image) requests.
 */

// ════════════════════════════════════════════════════════════════════════════════
// REQUEST MODELS
// ════════════════════════════════════════════════════════════════════════════════

@JsonClass(generateAdapter = true)
data class GeminiVisionRequest(
    @Json(name = "contents") val contents: List<VisionContent>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "safetySettings") val safetySettings: List<SafetySetting>? = null
)

@JsonClass(generateAdapter = true)
data class VisionContent(
    @Json(name = "parts") val parts: List<VisionPart>,
    @Json(name = "role") val role: String = "user"
)

/**
 * A part can be either text or inline image data.
 * For JSON serialization, we use a unified class with nullable fields.
 */
@JsonClass(generateAdapter = true)
data class VisionPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
) {
    companion object {
        fun text(value: String) = VisionPart(text = value)
        fun image(mimeType: String, base64Data: String) = VisionPart(
            inlineData = InlineData(mimeType, base64Data)
        )
    }
}

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int = 4096,
    @Json(name = "temperature") val temperature: Float = 0.1f,
    @Json(name = "topP") val topP: Float = 0.95f,
    @Json(name = "topK") val topK: Int = 40
) {
    companion object {
        /** Optimized config for OCR - low temperature for accuracy */
        val OCR = GenerationConfig(
            maxOutputTokens = 8192,
            temperature = 0.1f,
            topP = 0.95f,
            topK = 40
        )
        
        /** Config for translation - slightly higher temperature */
        val TRANSLATION = GenerationConfig(
            maxOutputTokens = 4096,
            temperature = 0.3f,
            topP = 0.95f,
            topK = 40
        )
    }
}

@JsonClass(generateAdapter = true)
data class SafetySetting(
    @Json(name = "category") val category: String,
    @Json(name = "threshold") val threshold: String
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

@JsonClass(generateAdapter = true)
data class GeminiVisionResponse(
    @Json(name = "candidates") val candidates: List<VisionCandidate>?,
    @Json(name = "promptFeedback") val promptFeedback: PromptFeedback?,
    @Json(name = "usageMetadata") val usageMetadata: UsageMetadata?
)

@JsonClass(generateAdapter = true)
data class VisionCandidate(
    @Json(name = "content") val content: VisionCandidateContent?,
    @Json(name = "finishReason") val finishReason: String?,
    @Json(name = "safetyRatings") val safetyRatings: List<SafetyRating>?
)

@JsonClass(generateAdapter = true)
data class VisionCandidateContent(
    @Json(name = "parts") val parts: List<VisionResponsePart>?,
    @Json(name = "role") val role: String?
)

@JsonClass(generateAdapter = true)
data class VisionResponsePart(
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class PromptFeedback(
    @Json(name = "blockReason") val blockReason: String?,
    @Json(name = "safetyRatings") val safetyRatings: List<SafetyRating>?
)

@JsonClass(generateAdapter = true)
data class SafetyRating(
    @Json(name = "category") val category: String?,
    @Json(name = "probability") val probability: String?
)

@JsonClass(generateAdapter = true)
data class UsageMetadata(
    @Json(name = "promptTokenCount") val promptTokenCount: Int?,
    @Json(name = "candidatesTokenCount") val candidatesTokenCount: Int?,
    @Json(name = "totalTokenCount") val totalTokenCount: Int?
)

// ════════════════════════════════════════════════════════════════════════════════
// HELPER EXTENSIONS
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Extracts text from Gemini response.
 * Joins all text parts from all candidates.
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
    private var config: GenerationConfig = GenerationConfig.OCR
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