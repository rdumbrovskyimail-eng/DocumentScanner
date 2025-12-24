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
        val parts: List<Part>
    )
    
    data class Part(
        @SerializedName("text")
        val text: String
    )
    
    data class GenerationConfig(
        @SerializedName("temperature")
        val temperature: Float,
        
        @SerializedName("maxOutputTokens")
        val maxOutputTokens: Int
    )
    
    data class SafetySetting(
        @SerializedName("category")
        val category: String,
        
        @SerializedName("threshold")
        val threshold: String
    )
}
