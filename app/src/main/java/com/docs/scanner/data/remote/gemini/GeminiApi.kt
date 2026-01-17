package com.docs.scanner.data.remote.gemini

import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.DomainResult.Companion.failure
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiApi @Inject constructor(
    private val service: GeminiApiService,
    private val keyManager: GeminiKeyManager
) {
    suspend fun generateText(
        apiKey: String,
        prompt: String,
        model: String,
        fallbackModels: List<String> = emptyList()
    ): DomainResult<String> {
        if (apiKey.isBlank()) return failure(DomainError.MissingApiKey)

        return try {
            val request = GenerateContentRequest(
                contents = listOf(
                    GenerateContentRequest.Content(
                        parts = listOf(GenerateContentRequest.Part(text = prompt))
                    )
                )
            )
            
            val response = keyManager.executeWithFailover { key ->
                service.generateContent(
                    model = model,
                    apiKey = key,
                    body = request
                )
            }
            
            val text = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.joinToString(separator = "") { it.text.orEmpty() }
                ?.trim()
                .orEmpty()
            
            if (text.isBlank()) {
                DomainResult.failure(
                    DomainError.TranslationFailed(
                        from = com.docs.scanner.domain.core.Language.AUTO,
                        to = com.docs.scanner.domain.core.Language.AUTO,
                        cause = "Empty response from Gemini"
                    )
                )
            } else {
                DomainResult.Success(text)
            }
        } catch (e: Exception) {
            Timber.e(e, "Gemini API call failed")
            DomainResult.failure(DomainError.NetworkFailed(e))
        }
    }
}