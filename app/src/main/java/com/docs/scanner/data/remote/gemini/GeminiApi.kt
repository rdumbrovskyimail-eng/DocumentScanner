package com.docs.scanner.data.remote.gemini

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

// ✅ Добавлен класс QuotaExceededException
class QuotaExceededException(message: String) : Exception(message)

@Singleton
class GeminiApi @Inject constructor(
    private val api: GeminiApiService
) {
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()
    private val rateLimitMutex = Mutex()
    private var quotaExceeded = false
    private var quotaResetTime = 0L

    private suspend fun checkRateLimit() {
        if (quotaExceeded && System.currentTimeMillis() < quotaResetTime) {
            throw QuotaExceededException("Quota exceeded. Reset at $quotaResetTime")
        }

        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            while (requestTimestamps.isNotEmpty() && now - requestTimestamps.peek()!! > 60_000L) {
                requestTimestamps.poll()
            }

            if (requestTimestamps.size >= 15) {
                val wait = 61_000L - (now - requestTimestamps.peek()!!)
                delay(wait)
            }
            requestTimestamps.add(now)
        }
    }

    private suspend fun exponentialBackoff(attempt: Int) {
        val delayMs = (2000.0 * 2.0.pow(attempt.toDouble())).toLong().coerceAtMost(64000L)
        delay(delayMs)
    }

    suspend fun translateText(text: String, apiKey: String, maxRetries: Int = 4): GeminiResult {
        if (text.isBlank() || !isValidApiKey(apiKey)) return GeminiResult.Failed("Invalid input or key")

        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()

                val prompt = """
                    Переведи на русский язык. Верни только перевод, без пояснений.
                    Если текст уже на русском — верни его без изменений.
                    
                    Текст: $text
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part(prompt)))),
                    generationConfig = GeminiRequest.GenerationConfig(temperature = 0.3f, maxOutputTokens = 8192),
                    safetySettings = createSafetySettings()
                )

                val response = api.generateContent(apiKey, request)
                if (response.isSuccessful) {
                    quotaExceeded = false
                    return sanitizeResponse(response.body())
                }

                when (response.code()) {
                    429 -> {
                        quotaExceeded = true
                        quotaResetTime = System.currentTimeMillis() + 3600_000L // 1 hour
                        if (attempt == maxRetries - 1) return GeminiResult.Failed("Quota exceeded. Try again in 1 hour.")
                        exponentialBackoff(attempt)
                    }
                    401, 403 -> return GeminiResult.Failed("Invalid API key")
                    else -> {
                        if (attempt == maxRetries - 1) {
                            return GeminiResult.Failed("HTTP ${response.code()}")
                        }
                        exponentialBackoff(attempt)
                    }
                }
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) return GeminiResult.Failed(e.message ?: "Network error")
                exponentialBackoff(attempt)
            }
        }
        return GeminiResult.Failed("Max retries exceeded")
    }

    suspend fun fixOcrText(text: String, apiKey: String, maxRetries: Int = 4): GeminiResult {
        if (text.isBlank() || !isValidApiKey(apiKey)) return GeminiResult.Failed("Invalid input or key")

        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()

                val prompt = """
                    Исправь ошибки распознавания текста (OCR). Верни только исправленный текст, без пояснений.
                    Сохрани оригинальную структуру и форматирование.
                    
                    Текст: $text
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part(prompt)))),
                    generationConfig = GeminiRequest.GenerationConfig(temperature = 0.2f, maxOutputTokens = 8192),
                    safetySettings = createSafetySettings()
                )

                val response = api.generateContent(apiKey, request)
                if (response.isSuccessful) {
                    quotaExceeded = false
                    return sanitizeResponse(response.body())
                }

                when (response.code()) {
                    429 -> {
                        quotaExceeded = true
                        quotaResetTime = System.currentTimeMillis() + 3600_000L
                        if (attempt == maxRetries - 1) return GeminiResult.Failed("Quota exceeded. Try again in 1 hour.")
                        exponentialBackoff(attempt)
                    }
                    401, 403 -> return GeminiResult.Failed("Invalid API key")
                    else -> {
                        if (attempt == maxRetries - 1) {
                            return GeminiResult.Failed("HTTP ${response.code()}")
                        }
                        exponentialBackoff(attempt)
                    }
                }
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) return GeminiResult.Failed(e.message ?: "Network error")
                exponentialBackoff(attempt)
            }
        }
        return GeminiResult.Failed("Max retries exceeded")
    }

    private fun createSafetySettings() = listOf(
        GeminiRequest.SafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_NONE"),
        GeminiRequest.SafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"),
        GeminiRequest.SafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"),
        GeminiRequest.SafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE")
    )

    private fun sanitizeResponse(response: GeminiResponse?): GeminiResult {
        if (response == null) return GeminiResult.Failed("Empty response")
        
        val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: return GeminiResult.Failed("No text in response")
        
        val cleaned = text.trim()
        
        if (cleaned.isBlank()) return GeminiResult.Failed("Empty result")
        
        return GeminiResult.Allowed(cleaned)
    }

    private fun isValidApiKey(key: String) = key.matches(Regex("^AIza[A-Za-z0-9_-]{35}$"))
}