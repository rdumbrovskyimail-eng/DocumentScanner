package com.docs.scanner.data.remote.gemini

import com.google.gson.annotations.SerializedName

data class GeminiResponse(
    @SerializedName("candidates")
    val candidates: List<Candidate>?,
    
    @SerializedName("promptFeedback")
    val promptFeedback: PromptFeedback?
) {
    data class Candidate(
        @SerializedName("content")
        val content: Content,
        
        @SerializedName("finishReason")
        val finishReason: String?,
        
        @SerializedName("safetyRatings")
        val safetyRatings: List<SafetyRating>?
    )
    
    data class Content(
        @SerializedName("parts")
        val parts: List<Part>
    )
    
    data class Part(
        @SerializedName("text")
        val text: String
    )
    
    data class SafetyRating(
        @SerializedName("category")
        val category: String,
        
        @SerializedName("probability")
        val probability: String
    )
    
    data class PromptFeedback(
        @SerializedName("blockReason")
        val blockReason: String?,
        
        @SerializedName("safetyRatings")
        val safetyRatings: List<SafetyRating>?
    )
}
