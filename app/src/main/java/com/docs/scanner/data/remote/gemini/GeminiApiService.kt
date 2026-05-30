package com.docs.scanner.data.remote.gemini

import com.docs.scanner.data.remote.gemini.GeminiVisionRequest
import com.docs.scanner.data.remote.gemini.GeminiVisionResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Minimal Retrofit API for Google Generative Language API.
 *
 * Note: Endpoint/model can be swapped without affecting app compilation.
 */
interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body body: GenerateContentRequest
    ): GenerateContentResponse
    
    /**
     * Generate content with vision (image) support.
     * Used for OCR of handwritten/difficult text.
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentVision(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body body: GeminiVisionRequest
    ): GeminiVisionResponse
}

data class GenerateContentRequest(
    val contents: List<Content>
) {
    data class Content(
        val parts: List<Part>
    )

    data class Part(
        val text: String
    )
}

data class GenerateContentResponse(
    val candidates: List<Candidate>?
) {
    data class Candidate(
        val content: Content?
    )

    data class Content(
        val parts: List<Part>?
    )

    data class Part(
        val text: String?
    )
}