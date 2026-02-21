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
     * Список всех поддерживаемых моделей Gemini (январь 2026).
     * 
     * ⚠️ КРИТИЧНО: При добавлении/удалении модели обновить ТОЛЬКО здесь!
     */
    val VALID_MODELS = listOf(
        "gemini-3-flash-preview",
        "gemini-3-pro-preview",
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-pro"
    )
    
    /**
     * Дефолтная модель для OCR операций.
     */
    const val DEFAULT_OCR_MODEL = "gemini-2.5-flash-lite"
    
    /**
     * Дефолтная модель для Translation операций.
     */
    const val DEFAULT_TRANSLATION_MODEL = "gemini-2.5-flash-lite"
    
    /**
     * Fallback модели для каждой primary модели.
     */
    fun getFallbackModels(primaryModel: String): List<String> {
        return when (primaryModel) {
            "gemini-3-flash-preview" -> listOf(
                "gemini-2.5-flash-lite",
                "gemini-2.5-flash"
            )
            "gemini-3-pro-preview" -> listOf(
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite"
            )
            "gemini-2.5-pro" -> listOf(
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite"
            )
            "gemini-2.5-flash" -> listOf(
                "gemini-2.5-flash-lite"
            )
            "gemini-2.5-flash-lite" -> listOf(
                "gemini-2.5-flash"
            )
            else -> listOf(
                "gemini-2.5-flash-lite",
                "gemini-2.5-flash"
            )
        }
    }
}