package com.docs.scanner.data.remote.gemini

import com.docs.scanner.domain.model.Result
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranslator @Inject constructor(
    private val geminiApi: GeminiApi
) {
    
    suspend fun translate(text: String, apiKey: String): Result<String> {
        return when (val result = geminiApi.translateText(text, apiKey)) {
            is GeminiResult.Allowed -> Result.Success(result.text)
            is GeminiResult.Blocked -> Result.Error(Exception(result.reason))
            is GeminiResult.Failed -> Result.Error(Exception(result.error))
        }
    }
    
    suspend fun fixOcrText(text: String, apiKey: String): Result<String> {
        return when (val result = geminiApi.fixOcrText(text, apiKey)) {
            is GeminiResult.Allowed -> Result.Success(result.text)
            is GeminiResult.Blocked -> Result.Error(Exception(result.reason))
            is GeminiResult.Failed -> Result.Error(Exception(result.error))
        }
    }
}
