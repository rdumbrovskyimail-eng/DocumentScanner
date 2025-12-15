package com.docs.scanner.data.remote.gemini

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.delay
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiRequest(
    val contents: List<Content>
) {
    data class Content(
        val parts: List<Part>
    )
    
    data class Part(
        val text: String
    )
}

data class GeminiResponse(
    val candidates: List<Candidate>?
) {
    data class Candidate(
        val content: Content,
        @SerializedName("safetyRatings")
        val safetyRatings: List<SafetyRating>
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
}

sealed class GeminiResult {
    data class Allowed(val text: String) : GeminiResult()
    data class Blocked(val reason: String) : GeminiResult()
    data class Failed(val error: String) : GeminiResult()
}

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

@Singleton
class GeminiApi @Inject constructor() {
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val api = retrofit.create(GeminiApiService::class.java)
    
    private var requestCount = 0
    private var lastRequestTime = 0L
    private val maxRequestsPerMinute = 15
    private val rateLimitWindow = 60_000L // 1 minute
    
    private suspend fun checkRateLimit() {
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
    
    suspend fun fixOcrText(text: String, apiKey: String, maxRetries: Int = 3): GeminiResult {
        if (apiKey.isBlank()) {
            return GeminiResult.Failed("API key is required")
        }
        
        if (text.isBlank()) {
            return GeminiResult.Failed("Input text is empty")
        }
        
        var lastError: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()
                
                val prompt = """
                    Исправь ошибки OCR в этом тексте. Верни только исправленный текст без пояснений.
                    Текст: $text
                """.trimIndent()
                
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiRequest.Content(
                            parts = listOf(GeminiRequest.Part(prompt))
                        )
                    )
                )
                
                val response = api.generateContent(apiKey, request)
                
                if (response.isSuccessful) {
                    return sanitizeResponse(response.body())
                } else {
                    val errorBody = response.errorBody()?.string()
                    
                    when (response.code()) {
                        429 -> {
                            // Rate limit exceeded
                            if (attempt < maxRetries - 1) {
                                delay(2000L * (attempt + 1))
                                return@repeat
                            }
                        }
                        401, 403 -> {
                            return GeminiResult.Failed("Invalid API key")
                        }
                        else -> {
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
        
        var lastError: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                checkRateLimit()
                
                val prompt = """
                    Переведи этот текст на русский язык. Верни только перевод без пояснений.
                    Текст: $text
                """.trimIndent()
                
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiRequest.Content(
                            parts = listOf(GeminiRequest.Part(prompt))
                        )
                    )
                )
                
                val response = api.generateContent(apiKey, request)
                
                if (response.isSuccessful) {
                    return sanitizeResponse(response.body())
                } else {
                    val errorBody = response.errorBody()?.string()
                    
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
    
    private fun sanitizeResponse(response: GeminiResponse?): GeminiResult {
        return try {
            response?.candidates?.firstOrNull()?.let { candidate ->
                // Check safety ratings
                val hasExplicitContent = candidate.safetyRatings.any { rating ->
                    rating.category in listOf(
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_HARASSMENT",
                        "HARM_CATEGORY_DANGEROUS_CONTENT"
                    ) && rating.probability in listOf("HIGH", "MEDIUM")
                }
                
                if (hasExplicitContent) {
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
        
        // Remove code block markers
        cleaned = cleaned.replace(Regex("`{1,3}.*?`{1,3}", RegexOption.DOT_MATCHES_ALL), "")
        
        // Remove quotes
        cleaned = cleaned.replace(Regex("[\"'«»„""]"), "")
        
        // Remove common prefixes
        cleaned = cleaned.replace(
            Regex(
                "^(Перевод|Translation|Traducción|Traduction|Übersetzung|翻訳|翻译|Исправленный текст|Corrected text)[:：]\\s*",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        
        // Remove invisible characters
        cleaned = cleaned.replace(Regex("\\p{C}"), "")
        
        // Normalize whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        return cleaned.trim()
    }
}
