package com.docs.scanner.data.remote.gemini

import com.google.gson.annotations.SerializedName

data class GeminiRequest(
    @SerializedName("contents")
    val contents: List<Content>,
    
    @SerializedName("generationConfig")
    val generationConfig: GenerationConfig,
    
    @SerializedName("safetySettings")
    val safetySettings: List<SafetySetting>
) {
    data class Content(
        @SerializedName("parts")
        val parts: List<Part>,
        
        @SerializedName("role")
        val role: String = "user"
    )
    
    data class Part(
        @SerializedName("text")
        val text: String
    )
    
    data class GenerationConfig(
        @SerializedName("temperature")
        val temperature: Float,
        
        @SerializedName("maxOutputTokens")
        val maxOutputTokens: Int,
        
        @SerializedName("topP")
        val topP: Float = 0.95f,
        
        @SerializedName("topK")
        val topK: Int = 40
    )
    
    data class SafetySetting(
        @SerializedName("category")
        val category: String,
        
        @SerializedName("threshold")
        val threshold: String
    )
}