package com.docs.scanner.data.remote.gemini

import com.docs.scanner.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranslator @Inject constructor(
    private val geminiApi: GeminiApi
) {
    
    suspend fun translate(text: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        when (val result = geminiApi.translateText(text, apiKey)) {
            is GeminiResult.Allowed -> Result.Success(result.text)
            is GeminiResult.Blocked -> Result.Error(Exception("Translation blocked: ${result.reason}"))
            is GeminiResult.Failed -> Result.Error(Exception("Translation failed: ${result.error}"))
        }
    }
    
    suspend fun fixOcrText(text: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        when (val result = geminiApi.fixOcrText(text, apiKey)) {
            is GeminiResult.Allowed -> Result.Success(result.text)
            is GeminiResult.Blocked -> Result.Error(Exception("OCR fix blocked: ${result.reason}"))
            is GeminiResult.Failed -> Result.Error(Exception("OCR fix failed: ${result.error}"))
        }
    }
}