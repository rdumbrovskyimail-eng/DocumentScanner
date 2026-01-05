package com.docs.scanner.data.remote.gemini

import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.DomainResult.Companion.failure
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiApi @Inject constructor(
    private val service: GeminiApiService
) {
    suspend fun generateText(
        apiKey: String,
        prompt: String,
        model: String,
        fallbackModels: List<String> = emptyList()
    ): DomainResult<String> {
        if (apiKey.isBlank()) return failure(DomainError.MissingApiKey)

        val modelsToTry = listOf(model) + fallbackModels
        val request = GenerateContentRequest(
            contents = listOf(
                GenerateContentRequest.Content(
                    parts = listOf(GenerateContentRequest.Part(text = prompt))
                )
            )
        )

        for ((index, m) in modelsToTry.withIndex()) {
            try {
                val response = service.generateContent(
                    model = m,
                    apiKey = apiKey,
                    body = request
                )

                val text = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.joinToString(separator = "") { it.text.orEmpty() }
                    ?.trim()
                    .orEmpty()

                if (text.isBlank()) {
                    return failure(
                        DomainError.TranslationFailed(
                            from = com.docs.scanner.domain.core.Language.AUTO,
                            to = com.docs.scanner.domain.core.Language.AUTO,
                            cause = "Empty response from Gemini ($m)"
                        )
                    )
                }
                return DomainResult.Success(text)
            } catch (e: HttpException) {
                // If model is not available in this project/region, try fallback models.
                val isLast = index == modelsToTry.lastIndex
                Timber.w(e, "Gemini API failed for model=%s (code=%s)", m, e.code())
                if (isLast || (e.code() != 400 && e.code() != 404)) {
                    return failure(DomainError.NetworkFailed(e))
                }
            } catch (e: Exception) {
                Timber.e(e, "Gemini API call failed (model=$m)")
                return failure(DomainError.NetworkFailed(e))
            }
        }

        return failure(DomainError.NetworkFailed(IllegalStateException("No Gemini model succeeded")))
    }
}

