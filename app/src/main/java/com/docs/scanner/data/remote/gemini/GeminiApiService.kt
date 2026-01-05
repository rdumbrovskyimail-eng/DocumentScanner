package com.docs.scanner.data.remote.gemini

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

