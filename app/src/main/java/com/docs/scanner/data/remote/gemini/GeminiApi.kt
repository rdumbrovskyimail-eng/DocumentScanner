package com.docs.scanner.data.remote.gemini

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * ✅ Gemini 2.0 Flash Exp (2.5 Flash Lite) API Wrapper
 * - Sliding Window Rate Limiting (15 RPM)
 * - Exponential Backoff для 429 ошибок
 * - Thread-safe реализация
 */
@Singleton
class GeminiApi @Inject constructor(
    private val api: GeminiApiService
) {
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()
    private val rateLimitMutex = Mutex()
    
    private suspend fun checkRateLimit() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            
            // Удаляем запросы старше 1 минуты
            while (requestTimestamps.isNotEmpty()) {
                val oldest = requestTimestamps.peek()
                if (now - oldest > RATE_LIMIT_WINDOW_MS) {
                    requestTimestamps.poll()
                } else {
                    break
                }
            }
            
            // Если лимит достигнут, ждём
            if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
                val oldestRequest = requestTimestamps.peek()!!
                val waitTime = RATE_LIMIT_WINDOW_MS - (now - oldestRequest)
                
                if (waitTime > 0) {
                    println("⏳ Rate limit reached. Waiting ${waitTime}ms...")
                    delay(waitTime + 100)
                    return checkRateLimit()
                }
            }
            
            requestTimestamps.add(now)
        }
    }
    
    private suspend fun exponentialBackoff(attempt: Int): Long {
        val baseDelay = 1000L
        val maxDelay = 32000L
        val delay = (baseDelay * 2.0.pow(attempt)).toLong().coerceAtMost(maxDelay)
        val jitter = (delay * 0.2 * (Math.random() - 0.5)).toLong()
        val finalDelay = delay + jitter
        
        println("⏳ Exponential backoff: waiting ${finalDelay}ms (attempt $attempt)")
        delay(finalDelay)
        
        return finalDelay
    }
    
    suspend fun fixOcrText(text: String, apiKey: String, maxRetries: Int = 3): GeminiResult {
        if (apiKey.isBlank()) return GeminiResult.Failed("API key is required")
        if (text.isBlank()) return GeminiResult.Failed("Input text is empty")
        if (!isValidApiKey(apiKey)) return GeminiResult.Failed("Invalid API key format")
        
        var lastError: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()
                
                val prompt = """
                    Исправь ошибки OCR в этом тексте. Верни только исправленный текст без пояснений.
                    Сохрани форматирование и структуру текста.
                    
                    Текст: $text
                """.trimIndent()
                
                val request = GeminiRequest(
                    contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part(prompt)))),
                    generationConfig = GeminiRequest.GenerationConfig(
                        temperature = 0.3f,
                        maxOutputTokens = MAX_TOKENS
                    ),
                    safetySettings = createSafetySettings()
                )
                
                val response = api.generateContent(apiKey, request)
                
                if (response.isSuccessful) {
                    return sanitizeResponse(response.body())
                } else {
                    when (response.code()) {
                        429 -> {
                            if (attempt < maxRetries - 1) {
                                exponentialBackoff(attempt)
                                return@repeat
                            }
                            lastError = Exception("Rate limit exceeded after $maxRetries attempts")
                        }
                        401, 403 -> return GeminiResult.Failed("Invalid API key")
                        else -> lastError = Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    exponentialBackoff(attempt)
                }
            }
        }
        
        return GeminiResult.Failed(lastError?.message ?: "Unknown error occurred")
    }
    
    suspend fun translateText(text: String, apiKey: String, maxRetries: Int = 3): GeminiResult {
        if (apiKey.isBlank()) return GeminiResult.Failed("API key is required")
        if (text.isBlank()) return GeminiResult.Failed("Input text is empty")
        if (!isValidApiKey(apiKey)) return GeminiResult.Failed("Invalid API key format")
        if (text.length > 10000) return GeminiResult.Failed("Text too long (max 10000 chars)")
        
        var lastError: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()
                
                val prompt = """
                    Переведи этот текст на русский язык. Верни только перевод без пояснений.
                    Сохрани форматирование и структуру текста.
                    Если текст уже на русском, верни его без изменений.
                    
                    Текст: $text
                """.trimIndent()
                
                val request = GeminiRequest(
                    contents = listOf(GeminiRequest.Content(parts = listOf(GeminiRequest.Part(prompt)))),
                    generationConfig = GeminiRequest.GenerationConfig(
                        temperature = TEMPERATURE,
                        maxOutputTokens = MAX_TOKENS
                    ),
                    safetySettings = createSafetySettings()
                )
                
                val response = api.generateContent(apiKey, request)
                
                if (response.isSuccessful) {
                    return sanitizeResponse(response.body())
                } else {
                    when (response.code()) {
                        429 -> {
                            if (attempt < maxRetries - 1) {
                                exponentialBackoff(attempt)
                                return@repeat
                            }
                            lastError = Exception("Rate limit exceeded after $maxRetries attempts")
                        }
                        401, 403 -> return GeminiResult.Failed("Invalid API key")
                        else -> lastError = Exception("HTTP ${response.code()}: ${response.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    exponentialBackoff(attempt)
                }
            }
        }
        
        return GeminiResult.Failed(lastError?.message ?: "Unknown error occurred")
    }
    
    private fun createSafetySettings(): List<GeminiRequest.SafetySetting> {
        return listOf(
            GeminiRequest.SafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_NONE"),
            GeminiRequest.SafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"),
            GeminiRequest.SafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"),
            GeminiRequest.SafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE")
        )
    }
    
    private fun sanitizeResponse(response: GeminiResponse?): GeminiResult {
        return try {
            response?.promptFeedback?.blockReason?.let { reason ->
                return GeminiResult.Blocked("Content blocked: $reason")
            }
            
            response?.candidates?.firstOrNull()?.let { candidate ->
                if (candidate.finishReason == "SAFETY") {
                    return GeminiResult.Blocked("Content blocked due to safety concerns")
                }
                
                val text = candidate.content.parts.joinToString(" ") { it.text }
                val cleaned = cleanText(text)
                
                if (cleaned.isBlank()) {
                    GeminiResult.Failed("Empty response from API")
                } else {
                    GeminiResult.Allowed(cleaned)
                }
            } ?: GeminiResult.Failed("No candidates in response")
        } catch (e: Exception) {
            GeminiResult.Failed("Failed to parse response: ${e.message}")
        }
    }
    
    private fun cleanText(text: String): String {
        var cleaned = text.trim()
        cleaned = cleaned.replace(Regex("`{1,3}[\\s\\S]*?`{1,3}"), "")
        cleaned = cleaned.replace(Regex("""["'«»„""]"""), "")
        
        val prefixes = listOf("Перевод:", "Translation:", "Исправленный текст:", "Corrected text:", "Here is", "Here's")
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }
        
        cleaned = cleaned.replace(Regex("\\p{C}"), "")
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        return cleaned.trim()
    }
    
    private fun isValidApiKey(key: String): Boolean {
        return key.matches(Regex("^AIza[A-Za-z0-9_-]{35}$"))
    }
    
    companion object {
        // ✅ Правильное название модели в API для Gemini 2.5 Flash Lite
        private const val GEMINI_MODEL = "gemini-2.0-flash-exp"
        private const val MAX_TOKENS = 8192
        private const val TEMPERATURE = 0.7f
        private const val MAX_REQUESTS_PER_MINUTE = 15
        private const val RATE_LIMIT_WINDOW_MS = 60_000L
    }
}
