package com.docs.scanner.data.remote.gemini

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApi {
    
    @POST("v1beta/models/gemini-2.0-flash-exp:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig = GenerationConfig(),
    val safetySettings: List<SafetySetting> = DEFAULT_SAFETY_SETTINGS
) {
    data class Content(
        val parts: List<Part>,
        val role: String = "user"
    )
    
    data class Part(
        val text: String
    )
    
    data class GenerationConfig(
        val temperature: Double = 0.1,
        val topK: Int = 1,
        val topP: Double = 0.95,
        val maxOutputTokens: Int = 8192,
        val candidateCount: Int = 1
    )
    
    data class SafetySetting(
        val category: String,
        val threshold: String
    )
    
    companion object {
        val DEFAULT_SAFETY_SETTINGS = listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        ).map { SafetySetting(it, "BLOCK_NONE") }
    }
}

data class GeminiResponse(
    val candidates: List<Candidate>?,
    val promptFeedback: PromptFeedback? = null
) {
    data class Candidate(
        val content: Content,
        val finishReason: String?,
        val safetyRatings: List<SafetyRating>? = null
    )
    
    data class Content(
        val parts: List<Part>,
        val role: String = "model"
    )
    
    data class Part(
        val text: String
    )
    
    data class PromptFeedback(
        val blockReason: String? = null,
        val safetyRatings: List<SafetyRating>? = null
    )
    
    data class SafetyRating(
        val category: String,
        val probability: String
    )
}

class GeminiTranslator(
    private val api: GeminiApi
) {
    
    suspend fun translate(
        text: String,
        apiKey: String
    ): com.docs.scanner.domain.model.Result<String> {
        return try {
            val prompt = buildTranslationPrompt(text)
            val request = GeminiRequest(
                contents = listOf(
                    GeminiRequest.Content(
                        parts = listOf(GeminiRequest.Part(prompt))
                    )
                )
            )
            
            val response = api.generateContent(apiKey, request)
            
            if (response.promptFeedback?.blockReason != null) {
                return com.docs.scanner.domain.model.Result.Error(
                    Exception("Content blocked: ${response.promptFeedback.blockReason}")
                )
            }
            
            val translatedText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim()
                ?: throw Exception("Empty response from Gemini API")
            
            val cleanedText = cleanTranslation(translatedText)
            
            com.docs.scanner.domain.model.Result.Success(cleanedText)
            
        } catch (e: Exception) {
            com.docs.scanner.domain.model.Result.Error(e)
        }
    }
    
    private fun buildTranslationPrompt(text: String): String {
        return """
Переведи следующий текст на русский язык.

ВАЖНЫЕ ПРАВИЛА:
1. Автоматически определи исходный язык текста (немецкий, английский, польский, украинский и т.д.)
2. Выполни точный и дословный перевод на русский язык
3. Сохрани все нюансы, смысл и эмоциональную окраску оригинала
4. Сохрани структуру текста (абзацы, списки, форматирование)
5. Для технических терминов используй устоявшиеся русские эквиваленты
6. НЕ добавляй никаких пояснений, комментариев или примечаний
7. Выведи ТОЛЬКО перевод, без дополнительного текста

Текст для перевода:
$text
        """.trimIndent()
    }
    
    private fun cleanTranslation(text: String): String {
        var cleaned = text
        
        cleaned = cleaned.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        cleaned = cleaned.replace(Regex("\\*(.+?)\\*"), "$1")
        cleaned = cleaned.replace(Regex("^#+\\s+", RegexOption.MULTILINE), "")
        cleaned = cleaned.replace(Regex("`(.+?)`"), "$1")
        
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'")) ||
            (cleaned.startsWith("«") && cleaned.endsWith("»"))) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        
        cleaned = cleaned.replace(
            Regex(
                "^(Перевод|Translation|Traducción|Traduction|Übersetzung|翻訳|翻译)\\s*[:：]\\s*",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        
        return cleaned.trim()
    }
    
    suspend fun fixOcrText(
        text: String,
        apiKey: String
    ): com.docs.scanner.domain.model.Result<String> {
        return try {
            val prompt = """
Исправь ошибки распознавания текста (OCR errors).

ЗАДАЧА:
1. Исходный текст содержит ошибки OCR (неправильно распознанные буквы)
2. Определи язык текста
3. Исправь все ошибки распознавания
4. Сохрани смысл и структуру
5. Выведи ТОЛЬКО исправленный текст без пояснений

Текст с ошибками:
$text
            """.trimIndent()
            
            val request = GeminiRequest(
                contents = listOf(
                    GeminiRequest.Content(
                        parts = listOf(GeminiRequest.Part(prompt))
                    )
                )
            )
            
            val response = api.generateContent(apiKey, request)
            val fixedText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim()
                ?: throw Exception("Empty response")
            
            com.docs.scanner.domain.model.Result.Success(cleanTranslation(fixedText))
            
        } catch (e: Exception) {
            com.docs.scanner.domain.model.Result.Error(e)
        }
    }
}