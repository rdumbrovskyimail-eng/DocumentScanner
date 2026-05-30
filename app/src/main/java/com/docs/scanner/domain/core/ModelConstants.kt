package com.docs.scanner.domain.core

/**
 * ModelConstants.kt
 * Version: 1.0.0 - SINGLE SOURCE OF TRUTH FOR MODELS (2026)
 * 
 * ✅ ЕДИНЫЙ источник правды для всех Gemini моделей в проекте.
 * ✅ Используется в: SettingsDataStore, GeminiModelManager, валидации.
 * ✅ Предотвращает рассинхронизацию констант между модулями.
 */
object ModelConstants {
    /**
     * Список всех поддерживаемых моделей Gemini (май 2026).
     */
    val VALID_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-3-flash",
        "gemini-3.1-flash-lite",
        "gemini-3.1-pro-preview",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.5-pro"
    )
    
    /**
     * Дефолтная модель для OCR операций (Стабильная и дешевая 3.1 Flash-Lite)
     */
    const val DEFAULT_OCR_MODEL = "gemini-3.1-flash-lite"
    
    /**
     * Дефолтная модель для Translation операций (Стабильная 3.1 Flash-Lite)
     */
    const val DEFAULT_TRANSLATION_MODEL = "gemini-3.1-flash-lite"
    
    /**
     * Fallback модели для каждой primary модели.
     */
    fun getFallbackModels(primaryModel: String): List<String> = when (primaryModel) {
        "gemini-3.5-flash" -> listOf("gemini-3-flash", "gemini-3.1-flash-lite")
        "gemini-3-flash" -> listOf("gemini-3.1-flash-lite", "gemini-2.5-flash")
        "gemini-3.1-pro-preview" -> listOf("gemini-3.5-flash", "gemini-3.1-flash-lite")
        "gemini-2.5-pro" -> listOf("gemini-2.5-flash", "gemini-2.5-flash-lite")
        "gemini-2.5-flash" -> listOf("gemini-2.5-flash-lite", "gemini-3.1-flash-lite")
        else -> listOf("gemini-3.1-flash-lite", "gemini-2.5-flash-lite")
    }
}