package com.docs.scanner.data.remote.gemini

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

private const val GEMINI_MODEL = "gemini-2.0-flash-exp"
private const val MAX_TOKENS = 8192
private const val TEMPERATURE = 0.7f

data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null
) {
    data class Content(
        val parts: List<Part>
    )
    
    data class Part(
        val text: String
    )
    
    data class GenerationConfig(
        val temperature: Float = TEMPERATURE,
        val maxOutputTokens: Int = MAX_TOKENS,
        val topP: Float = 0.95f,
        val topK: Int = 40
    )
    
    data class SafetySetting(
        val category: String,
        val threshold: String
    )
}

data class GeminiResponse(
    val candidates: List<Candidate>?,
    val promptFeedback: PromptFeedback?
) {
    data class Candidate(
        val content: Content,
        @SerializedName("safetyRatings")
        val safetyRatings: List<SafetyRating>,
        val finishReason: String?
    )
    
    data class Content(
        val parts: List<Part>
    )
    
    data class Part(
        val text: String
    )
    
    data class SafetyRating(
        val category: String,
        val probability: String
    )
    
    data class PromptFeedback(
        val blockReason: String?
    )
}

sealed class GeminiResult {
    data class Allowed(val text: String) : GeminiResult()
    data class Blocked(val reason: String) : GeminiResult()
    data class Failed(val error: String) : GeminiResult()
}

interface GeminiApiService {
    @POST("v1beta/models/$GEMINI_MODEL:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

@Singleton
class GeminiApi @Inject constructor(
    private val api: GeminiApiService
) {
    private val requestLock = Mutex()
    private var requestCount = 0
    private var lastRequestTime = 0L
    private val maxRequestsPerMinute = 15
    private val rateLimitWindow = 60_000L
    
    private suspend fun checkRateLimit() {
        requestLock.withLock {
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastRequestTime > rateLimitWindow) {
                requestCount = 0
                lastRequestTime = currentTime
            }
            
            if (requestCount >= maxRequestsPerMinute) {
                val waitTime = rateLimitWindow - (currentTime - lastRequestTime)
                if (waitTime > 0) {
                    delay(waitTime)
                    requestCount = 0
                    lastRequestTime = System.currentTimeMillis()
                }
            }
            
            requestCount++
        }
    }
    
    suspend fun fixOcrText(text: String, apiKey: String, maxRetries: Int = 3): GeminiResult {
        if (apiKey.isBlank()) {
            return GeminiResult.Failed("API key is required")
        }
        
        if (text.isBlank()) {
            return GeminiResult.Failed("Input text is empty")
        }
        
        if (!isValidApiKey(apiKey)) {
            return GeminiResult.Failed("Invalid API key format")
        }
        
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
                    contents = listOf(
                        GeminiRequest.Content(
                            parts = listOf(GeminiRequest.Part(prompt))
                        )
                    ),
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
                                delay(2000L * (attempt + 1))
                                return@repeat
                            }
                        }
                        401, 403 -> {
                            return GeminiResult.Failed("Invalid API key")
                        }
                        else -> {
                            val errorBody = response.errorBody()?.string()
                            lastError = Exception("HTTP ${response.code()}: $errorBody")
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1))
                }
            }
        }
        
        return GeminiResult.Failed(lastError?.message ?: "Unknown error occurred")
    }
    
    suspend fun translateText(text: String, apiKey: String, maxRetries: Int = 3): GeminiResult {
        if (apiKey.isBlank()) {
            return GeminiResult.Failed("API key is required")
        }
        
        if (text.isBlank()) {
            return GeminiResult.Failed("Input text is empty")
        }
        
        if (!isValidApiKey(apiKey)) {
            return GeminiResult.Failed("Invalid API key format")
        }
        
        if (text.length > 10000) {
            return GeminiResult.Failed("Text too long (max 10000 chars)")
        }
        
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
                    contents = listOf(
                        GeminiRequest.Content(
                            parts = listOf(GeminiRequest.Part(prompt))
                        )
                    ),
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
                                delay(2000L * (attempt + 1))
                                return@repeat
                            }
                        }
                        401, 403 -> {
                            return GeminiResult.Failed("Invalid API key")
                        }
                        else -> {
                            val errorBody = response.errorBody()?.string()
                            lastError = Exception("HTTP ${response.code()}: $errorBody")
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1))
                }
            }
        }
        
        return GeminiResult.Failed(lastError?.message ?: "Unknown error occurred")
    }
    
    private fun createSafetySettings(): List<GeminiRequest.SafetySetting> {
        return listOf(
            GeminiRequest.SafetySetting(
                category = "HARM_CATEGORY_HARASSMENT",
                threshold = "BLOCK_NONE"
            ),
            GeminiRequest.SafetySetting(
                category = "HARM_CATEGORY_HATE_SPEECH",
                threshold = "BLOCK_NONE"
            ),
            GeminiRequest.SafetySetting(
                category = "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                threshold = "BLOCK_NONE"
            ),
            GeminiRequest.SafetySetting(
                category = "HARM_CATEGORY_DANGEROUS_CONTENT",
                threshold = "BLOCK_NONE"
            )
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
        
        val prefixes = listOf(
            "Перевод:",
            "Translation:",
            "Исправленный текст:",
            "Corrected text:",
            "Here is",
            "Here's"
        )
        
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
}
