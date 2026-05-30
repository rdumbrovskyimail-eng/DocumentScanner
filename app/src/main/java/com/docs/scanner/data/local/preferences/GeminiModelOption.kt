/*
 * GeminiModelOption.kt
 * Version: 1.0.0 - GEMINI MODEL SELECTION (2026)
 * 
 * Data class representing a Gemini model option for OCR.
 * Used in UI for model selection dropdown.
 * 
 * LOCATION: com.docs.scanner.data.local.preferences
 */

package com.docs.scanner.data.local.preferences

/**
 * Represents a Gemini model option for OCR.
 * 
 * @param id Model identifier (e.g., "gemini-3-flash")
 * @param displayName Human-readable name (e.g., "Gemini 3 Flash")
 * @param description Brief description of the model
 * @param isRecommended Whether this model is recommended for OCR
 */
data class GeminiModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val isRecommended: Boolean = false
)