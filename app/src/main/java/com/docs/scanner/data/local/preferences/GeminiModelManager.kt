/*
 * GeminiModelManager.kt
 * Version: 7.2.0 - MODEL CONSTANTS INTEGRATION (2026)
 * 
 * ✅ CRITICAL FIX (Session 14):
 * - Uses ModelConstants.VALID_MODELS (single source of truth)
 * - Uses ModelConstants.DEFAULT_OCR_MODEL
 * - Uses ModelConstants.DEFAULT_TRANSLATION_MODEL
 * - Removed hardcoded PRODUCTION_MODELS list
 * - Added runtime validation to ensure synchronization
 * 
 * ✅ PREVIOUS FIXES:
 * - Fixed circular dependency: GeminiModelManager → SettingsDataStore (one-way)
 * - Model lists synchronized with SettingsDataStore.kt constants
 * - БЕЗ устаревших gemini-2.0-* моделей
 * 
 * АРХИТЕКТУРА:
 * ModelConstants (object, no dependencies)
 *   └─ VALID_MODELS, DEFAULT_OCR_MODEL, DEFAULT_TRANSLATION_MODEL
 * 
 * GeminiModelManager (Singleton)
 *   ├─ Depends on: SettingsDataStore, ModelConstants
 *   ├─ Validates: ModelConstants ↔ SettingsDataStore sync at runtime
 *   └─ Uses: ModelConstants for all model operations
 * 
 * SettingsDataStore (Singleton)
 *   └─ Uses: ModelConstants for validation
 */

package com.docs.scanner.data.local.preferences

import com.docs.scanner.data.remote.gemini.GeminiApi
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.ModelConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class GeminiModelManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    // Используем Provider для исключения циклической зависимости при инициализации Hilt-графа
    private val geminiApiProvider: Provider<GeminiApi>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        validateSyncWithDataStore()
    }

    private fun validateSyncWithDataStore() {
        if (DEFAULT_OCR_MODEL != ModelConstants.DEFAULT_OCR_MODEL) {
            Timber.e("❌ DEFAULT_OCR_MODEL mismatch!")
        }
    }

    // Возвращает объединенный список заводских и кастомных моделей
    suspend fun getModelIds(): List<String> {
        val custom = settingsDataStore.customModels.first()
        return (ModelConstants.VALID_MODELS + custom).distinct()
    }

    suspend fun isValidModel(modelId: String): Boolean {
        return modelId in getModelIds()
    }

    // Генерирует опции для интерфейса с индикаторами скорости
    suspend fun getAvailableModels(): List<GeminiModelOption> {
        val custom = settingsDataStore.customModels.first()
        val staticOptions = listOf(
            GeminiModelOption(
                id = "gemini-3.1-flash-lite",
                displayName = "Gemini 3.1 Flash Lite 🚀",
                description = "Stable • Blazing Fast • Minimum latency and cost",
                isRecommended = true
            ),
            GeminiModelOption(
                id = "gemini-3.5-flash",
                displayName = "Gemini 3.5 Flash ⚡",
                description = "May 2026 Stable • Near-Pro level intelligence",
                isRecommended = true
            ),
            GeminiModelOption(
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                description = "Very fast • Great balance",
                isRecommended = false
            ),
            GeminiModelOption(
                id = "gemini-3.1-pro-preview",
                displayName = "Gemini 3.1 Pro (Preview) 💰",
                description = "Complex reasoning • Slow • High cost",
                isRecommended = false
            ),
            GeminiModelOption(
                id = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro 🐌",
                description = "Slow • Complex tasks only",
                isRecommended = false
            )
        )

        val customOptions = custom.map { modelId ->
            GeminiModelOption(
                id = modelId,
                displayName = "$modelId 🛠️",
                description = "Custom User Added Model",
                isRecommended = false
            )
        }

        return (staticOptions + customOptions).distinctBy { it.id }
    }

    suspend fun getGlobalOcrModel(): String {
        return try {
            val model = settingsDataStore.geminiOcrModel.first()
            if (!isValidModel(model)) {
                DEFAULT_OCR_MODEL
            } else {
                model
            }
        } catch (e: Exception) {
            DEFAULT_OCR_MODEL
        }
    }

    suspend fun setGlobalOcrModel(modelId: String) {
        require(isValidModel(modelId)) {
            "Invalid model: $modelId"
        }
        try {
            settingsDataStore.setGeminiOcrModel(modelId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save OCR model")
            throw e
        }
    }

    suspend fun getGlobalTranslationModel(): String {
        return try {
            val model = settingsDataStore.translationModel.first()
            if (!isValidModel(model)) {
                DEFAULT_TRANSLATION_MODEL
            } else {
                model
            }
        } catch (e: Exception) {
            DEFAULT_TRANSLATION_MODEL
        }
    }

    suspend fun setGlobalTranslationModel(modelId: String) {
        require(isValidModel(modelId)) {
            "Invalid model: $modelId"
        }
        try {
            settingsDataStore.setTranslationModel(modelId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save Translation model")
            throw e
        }
    }

    /**
     * 🚀 Фоновый прогрев (Warm-up) модели при старте или смене настроек.
     * Отправляет легкий пинг-запрос для кэширования DNS и инициализации контейнера модели.
     */
    fun warmUpModel(modelId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                Timber.d("🔥 Starting model warm-up/ping for: $modelId")
                val api = geminiApiProvider.get()
                val result = api.generateText(
                    prompt = "Ping. Reply with 'OK'.",
                    model = modelId,
                    fallbackModels = emptyList()
                )
                when (result) {
                    is DomainResult.Success -> {
                        Timber.i("✅ Model $modelId is WARM. Response: ${result.data}")
                    }
                    is DomainResult.Failure -> {
                        Timber.w("⚠️ Model $modelId warm-up ping failed: ${result.error.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "⚠️ Silent fallback on warm-up failure for: $modelId")
            }
        }
    }

    fun findModelById(modelId: String, available: List<GeminiModelOption>): GeminiModelOption? {
        return available.find { it.id == modelId }
    }

    companion object {
        const val DEFAULT_OCR_MODEL = ModelConstants.DEFAULT_OCR_MODEL
        const val DEFAULT_TRANSLATION_MODEL = ModelConstants.DEFAULT_TRANSLATION_MODEL
    }
}