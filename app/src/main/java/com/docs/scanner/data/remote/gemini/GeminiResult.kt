package com.docs.scanner.data.remote.gemini

sealed class GeminiResult {
    data class Allowed(val text: String) : GeminiResult()
    data class Blocked(val reason: String) : GeminiResult()
    data class Failed(val error: String) : GeminiResult()
}
