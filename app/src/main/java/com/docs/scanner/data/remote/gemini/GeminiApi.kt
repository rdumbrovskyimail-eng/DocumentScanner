package com.docs.scanner.data.remote.gemini

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

class QuotaExceededException(message: String) : Exception(message)

@Singleton
class GeminiApi @Inject constructor(
    private val api: GeminiApiService
) {
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()
    private val rateLimitMutex = Mutex()
    
    @Volatile
    private var quotaExceeded = false
    
    @Volatile
    private var quotaResetTime = 0L

    private companion object {
        const val RATE_LIMIT_WINDOW_MS = 60_000L
        const val MAX_REQUESTS_PER_MINUTE = 15
        const val QUOTA_RESET_DURATION_MS = 3600_000L
        const val INITIAL_BACKOFF_MS = 1000L
        const val MAX_BACKOFF_MS = 32_000L
    }

    private suspend fun checkRateLimit() {
        if (quotaExceeded && System.currentTimeMillis() < quotaResetTime) {
            throw QuotaExceededException("Quota exceeded. Reset at $quotaResetTime")
        }

        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            
            // Удаляем устаревшие метки времени
            while (requestTimestamps.isNotEmpty() && 
                   now - requestTimestamps.peek()!! > RATE_LIMIT_WINDOW_MS) {
                requestTimestamps.poll()
            }

            // Ждем, если превышен лимит
            if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
                val oldestRequest = requestTimestamps.peek()!!
                val waitTime = RATE_LIMIT_WINDOW_MS - (now - oldestRequest) + 100L // +100ms буфер
                if (waitTime > 0) {
                    delay(waitTime)
                }
                requestTimestamps.poll()
            }
            
            requestTimestamps.add(System.currentTimeMillis())
        }
    }

    private suspend fun exponentialBackoff(attempt: Int) {
        val delayMs = (INITIAL_BACKOFF_MS * 2.0.pow(attempt.toDouble())).toLong()
        delay(min(delayMs, MAX_BACKOFF_MS))
    }

    suspend fun translateText(text: String, apiKey: String, maxRetries: Int = 3): GeminiResult {
        if (text.isBlank()) return GeminiResult.Failed("Text is empty")
        if (!isValidApiKey(apiKey)) return GeminiResult.Failed("Invalid API key format")

        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()

                val prompt = """
                    Переведи текст на русский язык.
                    Верни ТОЛЬКО перевод без пояснений, комментариев или дополнительного текста.
                    Если текст уже на русском языке — верни его без изменений.
                    
                    Текст:
                    $text
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiRequest.Content(
                            parts = listOf(GeminiRequest.Part(prompt))
                        )
                    ),
                    generationConfig = GeminiRequest.GenerationConfig(
                        temperature = 0.3f,
                        maxOutputTokens = 8192,
                        topP = 0.95f,
                        topK = 40
                    ),
                    safetySettings = createSafetySettings()
                )

                val response = api.generateContent(apiKey, request)
                
                if (response.isSuccessful) {
                    quotaExceeded = false
                    return sanitizeResponse(response.body())
                }

                return handleErrorResponse(response.code(), attempt, maxRetries)
                
            } catch (e: QuotaExceededException) {
                if (attempt == maxRetries - 1) {
                    return GeminiResult.Failed("Quota exceeded. Try again later.")
                }
                exponentialBackoff(attempt)
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    return GeminiResult.Failed(e.message ?: "Network error")
                }
                exponentialBackoff(attempt)
            }
        }
        
        return GeminiResult.Failed("Max retries exceeded")
    }

    suspend fun fixOcrText(text: String, apiKey: String, maxRetries: Int = 3): GeminiResult {
        if (text.isBlank()) return GeminiResult.Failed("Text is empty")
        if (!isValidApiKey(apiKey)) return GeminiResult.Failed("Invalid API key format")

        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()

                val prompt = """
                    Исправь ошибки распознавания текста (OCR).
                    Верни ТОЛЬКО исправленный текст без пояснений.
                    Сохрани оригинальную структуру, форматирование и разбиение на абзацы.
                    Исправь только очевидные ошибки OCR (перепутанные буквы, лишние символы).
                    
                    Текст:
                    $text
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiRequest.Content(
                            parts = listOf(GeminiRequest.Part(prompt))
                        )
                    ),
                    generationConfig = GeminiRequest.GenerationConfig(
                        temperature = 0.2f,
                        maxOutputTokens = 8192,
                        topP = 0.95f,
                        topK = 40
                    ),
                    safetySettings = createSafetySettings()
                )

                val response = api.generateContent(apiKey, request)
                
                if (response.isSuccessful) {
                    quotaExceeded = false
                    return sanitizeResponse(response.body())
                }

                return handleErrorResponse(response.code(), attempt, maxRetries)
                
            } catch (e: QuotaExceededException) {
                if (attempt == maxRetries - 1) {
                    return GeminiResult.Failed("Quota exceeded. Try again later.")
                }
                exponentialBackoff(attempt)
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    return GeminiResult.Failed(e.message ?: "Network error")
                }
                exponentialBackoff(attempt)
            }
        }
        
        return GeminiResult.Failed("Max retries exceeded")
    }

    private suspend fun handleErrorResponse(
        code: Int, 
        attempt: Int, 
        maxRetries: Int
    ): GeminiResult {
        return when (code) {
            429 -> {
                quotaExceeded = true
                quotaResetTime = System.currentTimeMillis() + QUOTA_RESET_DURATION_MS
                if (attempt == maxRetries - 1) {
                    GeminiResult.Failed("Quota exceeded. Try again in 1 hour.")
                } else {
                    exponentialBackoff(attempt)
                    GeminiResult.Failed("Retrying...")
                }
            }
            401, 403 -> GeminiResult.Failed("Invalid or expired API key")
            400 -> GeminiResult.Failed("Bad request. Check input parameters.")
            500, 503 -> {
                if (attempt < maxRetries - 1) {
                    exponentialBackoff(attempt)
                    GeminiResult.Failed("Retrying...")
                } else {
                    GeminiResult.Failed("Server error. Try again later.")
                }
            }
            else -> {
                if (attempt < maxRetries - 1) {
                    exponentialBackoff(attempt)
                    GeminiResult.Failed("Retrying...")
                } else {
                    GeminiResult.Failed("HTTP error: $code")
                }
            }
        }
    }

    private fun createSafetySettings() = listOf(
        GeminiRequest.SafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_NONE"),
        GeminiRequest.SafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"),
        GeminiRequest.SafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"),
        GeminiRequest.SafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE")
    )

    private fun sanitizeResponse(response: GeminiResponse?): GeminiResult {
        if (response == null) {
            return GeminiResult.Failed("Empty response from server")
        }

        // Проверка на блокировку контента
        response.promptFeedback?.blockReason?.let { reason ->
            return GeminiResult.Blocked("Content blocked: $reason")
        }

        val candidate = response.candidates?.firstOrNull()
            ?: return GeminiResult.Failed("No candidates in response")

        // Проверка причины завершения
        when (candidate.finishReason) {
            "STOP" -> {} // Нормальное завершение
            "MAX_TOKENS" -> return GeminiResult.Failed("Response too long")
            "SAFETY" -> return GeminiResult.Blocked("Content blocked by safety filters")
            "RECITATION" -> return GeminiResult.Blocked("Content blocked due to recitation")
            else -> {} // Продолжаем
        }

        val text = candidate.content.parts.firstOrNull()?.text
            ?: return GeminiResult.Failed("No text in response")

        val cleaned = text.trim()
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (cleaned.isBlank()) {
            return GeminiResult.Failed("Empty result after cleaning")
        }

        return GeminiResult.Allowed(cleaned)
    }

    private fun isValidApiKey(key: String): Boolean {
        return key.matches(Regex("^AIza[A-Za-z0-9_-]{35}$"))
    }
}