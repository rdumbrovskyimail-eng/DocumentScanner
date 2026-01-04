package com.docs.scanner.data.remote.gemini

import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.DomainResult.Companion.failure
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiApi @Inject constructor(
    private val service: GeminiApiService
) {
    suspend fun generateText(apiKey: String, prompt: String): DomainResult<String> {
        if (apiKey.isBlank()) return failure(DomainError.MissingApiKey)

        return try {
            val response = service.generateContent(
                apiKey = apiKey,
                body = GenerateContentRequest(
                    contents = listOf(
                        GenerateContentRequest.Content(
                            parts = listOf(GenerateContentRequest.Part(text = prompt))
                        )
                    )
                )
            )

            val text = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.joinToString(separator = "") { it.text.orEmpty() }
                ?.trim()
                .orEmpty()

            if (text.isBlank()) failure(DomainError.TranslationFailed(from = com.docs.scanner.domain.core.Language.AUTO, to = com.docs.scanner.domain.core.Language.AUTO, cause = "Empty response from Gemini"))
            else DomainResult.Success(text)
        } catch (e: Exception) {
            Timber.e(e, "Gemini API call failed")
            failure(DomainError.NetworkFailed(e))
        }
    }
}

