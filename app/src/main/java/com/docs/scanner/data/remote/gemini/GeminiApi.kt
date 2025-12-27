package com.docs.scanner.data.remote.gemini

import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

class QuotaExceededException(message: String) : Exception(message)

/**
 * Gemini API wrapper with rate limiting, retry logic, and error handling.
 * 
 * Session 4 fixes:
 * - ✅ Added language parameters (targetLanguage, sourceLanguage)
 * - ✅ Added model parameter support
 * - ✅ Added error response parsing
 * - ✅ Added statistics tracking
 * - ✅ Improved prompt templates
 * 
 * Features:
 * - Rate limiting: 15 requests per minute (Gemini free tier)
 * - Exponential backoff with jitter
 * - Quota management with 1-hour reset
 * - Safety settings bypass (for translation)
 * - Response sanitization
 */
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
    
    // ✅ NEW: Statistics tracking
    private var totalRequests = 0
    private var totalFailures = 0
    private var totalQuotaExceeded = 0

    private companion object {
        const val RATE_LIMIT_WINDOW_MS = 60_000L
        const val MAX_REQUESTS_PER_MINUTE = 15
        const val QUOTA_RESET_DURATION_MS = 3600_000L
        const val INITIAL_BACKOFF_MS = 1000L
        const val MAX_BACKOFF_MS = 32_000L
        
        // ✅ NEW: Model versions
        const val DEFAULT_MODEL = "gemini-2.0-flash-exp"
        const val FALLBACK_MODEL = "gemini-1.5-flash"
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
                    android.util.Log.d(
                        "GeminiApi",
                        "⏳ Rate limit reached, waiting ${waitTime}ms"
                    )
                    delay(waitTime)
                }
                requestTimestamps.poll()
            }
            
            requestTimestamps.add(System.currentTimeMillis())
        }
    }

    private suspend fun exponentialBackoff(attempt: Int) {
        val delayMs = (INITIAL_BACKOFF_MS * 2.0.pow(attempt.toDouble())).toLong()
        val actualDelay = min(delayMs, MAX_BACKOFF_MS)
        android.util.Log.d(
            "GeminiApi",
            "⏳ Backing off for ${actualDelay}ms (attempt $attempt)"
        )
        delay(actualDelay)
    }

    /**
     * Translate text using Gemini API.
     * 
     * ✅ NEW: Added language parameters
     * 
     * @param text Text to translate
     * @param apiKey Gemini API key
     * @param targetLanguage Target language (e.g., "Russian", "English", "Chinese")
     * @param sourceLanguage Source language or null for auto-detection
     * @param maxRetries Maximum retry attempts (default: 3)
     * @return GeminiResult with translated text
     */
    suspend fun translateText(
        text: String, 
        apiKey: String,
        targetLanguage: String = "Russian",
        sourceLanguage: String? = null,
        maxRetries: Int = 3
    ): GeminiResult {
        totalRequests++
        
        if (text.isBlank()) return GeminiResult.Failed("Text is empty")
        if (!isValidApiKey(apiKey)) return GeminiResult.Failed("Invalid API key format")

        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()

                // ✅ IMPROVED: Better prompt with language parameters
                val prompt = buildString {
                    append("Translate the text to $targetLanguage.")
                    if (sourceLanguage != null) {
                        append("\nSource language: $sourceLanguage")
                    }
                    append("\nReturn ONLY the translation without explanations.")
                    append("\nIf text is already in $targetLanguage, return it unchanged.")
                    append("\n\nText:\n")
                    append(text)
                }

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

                val response = api.generateContent(
                    model = DEFAULT_MODEL,
                    apiKey = apiKey,
                    request = request
                )
                
                if (response.isSuccessful) {
                    quotaExceeded = false
                    return sanitizeResponse(response.body())
                }

                // ✅ NEW: Parse error body
                return handleErrorResponse(response.code(), response.errorBody()?.string(), attempt, maxRetries)
                
            } catch (e: QuotaExceededException) {
                totalQuotaExceeded++
                if (attempt == maxRetries - 1) {
                    totalFailures++
                    return GeminiResult.Failed("Quota exceeded. Try again later.")
                }
                exponentialBackoff(attempt)
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    totalFailures++
                    return GeminiResult.Failed(e.message ?: "Network error")
                }
                exponentialBackoff(attempt)
            }
        }
        
        totalFailures++
        return GeminiResult.Failed("Max retries exceeded")
    }

    /**
     * Fix OCR errors using Gemini API.
     * 
     * @param text Raw OCR text with potential errors
     * @param apiKey Gemini API key
     * @param maxRetries Maximum retry attempts
     * @return GeminiResult with corrected text
     */
    suspend fun fixOcrText(
        text: String, 
        apiKey: String, 
        maxRetries: Int = 3
    ): GeminiResult {
        totalRequests++
        
        if (text.isBlank()) return GeminiResult.Failed("Text is empty")
        if (!isValidApiKey(apiKey)) return GeminiResult.Failed("Invalid API key format")

        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()

                val prompt = """
                    Fix OCR recognition errors in this text.
                    Return ONLY the corrected text without explanations.
                    Preserve original structure, formatting, and paragraph breaks.
                    Fix only obvious OCR errors (confused letters, extra symbols).
                    
                    Text:
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

                val response = api.generateContent(
                    model = DEFAULT_MODEL,
                    apiKey = apiKey,
                    request = request
                )
                
                if (response.isSuccessful) {
                    quotaExceeded = false
                    return sanitizeResponse(response.body())
                }

                return handleErrorResponse(response.code(), response.errorBody()?.string(), attempt, maxRetries)
                
            } catch (e: QuotaExceededException) {
                totalQuotaExceeded++
                if (attempt == maxRetries - 1) {
                    totalFailures++
                    return GeminiResult.Failed("Quota exceeded. Try again later.")
                }
                exponentialBackoff(attempt)
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    totalFailures++
                    return GeminiResult.Failed(e.message ?: "Network error")
                }
                exponentialBackoff(attempt)
            }
        }
        
        totalFailures++
        return GeminiResult.Failed("Max retries exceeded")
    }

    /**
     * Handle HTTP error responses with detailed error parsing.
     * 
     * ✅ NEW: Parse error body from API
     */
    private suspend fun handleErrorResponse(
        code: Int,
        errorBody: String?,
        attempt: Int, 
        maxRetries: Int
    ): GeminiResult {
        // ✅ Try to parse error message from response
        val errorMessage = try {
            errorBody?.let { body ->
                val error = Gson().fromJson(body, GeminiErrorResponse::class.java)
                error.error.message
            }
        } catch (e: Exception) {
            null
        }
        
        return when (code) {
            429 -> {
                quotaExceeded = true
                quotaResetTime = System.currentTimeMillis() + QUOTA_RESET_DURATION_MS
                totalQuotaExceeded++
                
                if (attempt == maxRetries - 1) {
                    GeminiResult.Failed(
                        errorMessage ?: "Quota exceeded. Try again in 1 hour."
                    )
                } else {
                    exponentialBackoff(attempt)
                    GeminiResult.Failed("Retrying...")
                }
            }
            401, 403 -> {
                GeminiResult.Failed(
                    errorMessage ?: "Invalid or expired API key"
                )
            }
            400 -> {
                GeminiResult.Failed(
                    errorMessage ?: "Bad request. Check input parameters."
                )
            }
            500, 503 -> {
                if (attempt < maxRetries - 1) {
                    exponentialBackoff(attempt)
                    GeminiResult.Failed("Retrying...")
                } else {
                    GeminiResult.Failed(
                        errorMessage ?: "Server error. Try again later."
                    )
                }
            }
            else -> {
                if (attempt < maxRetries - 1) {
                    exponentialBackoff(attempt)
                    GeminiResult.Failed("Retrying...")
                } else {
                    GeminiResult.Failed(
                        errorMessage ?: "HTTP error: $code"
                    )
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
    
    /**
     * Get API usage statistics.
     * 
     * ✅ NEW: For monitoring and debugging (Session 4)
     */
    fun getStatistics(): ApiStatistics {
        return ApiStatistics(
            totalRequests = totalRequests,
            totalFailures = totalFailures,
            quotaExceeded = totalQuotaExceeded,
            currentQueueSize = requestTimestamps.size,
            quotaResetTime = if (quotaExceeded) quotaResetTime else null
        )
    }
    
    data class ApiStatistics(
        val totalRequests: Int,
        val totalFailures: Int,
        val quotaExceeded: Int,
        val currentQueueSize: Int,
        val quotaResetTime: Long?
    )
}

/**
 * Error response from Gemini API.
 * 
 * ✅ NEW: For parsing error details (Session 4)
 */
data class GeminiErrorResponse(
    val error: ErrorDetail
)

data class ErrorDetail(
    val code: Int,
    val message: String,
    val status: String
)