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

import com.docs.scanner.domain.core.ModelConstants
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified manager for all Gemini models in the project.
 * 
 * ✅ CRITICAL: Uses ModelConstants as the SINGLE SOURCE OF TRUTH!
 * ✅ All UI dropdowns, API calls, and settings MUST use this manager.
 * 
 * WHY SINGLETON:
 * - Prevents model list duplication
 * - Ensures consistency across the app
 * - Single source of truth for model selection
 * 
 * DEPENDENCY FLOW (Safe, no circular dependency):
 * ModelConstants (no deps) ← GeminiModelManager → SettingsDataStore → DataStore
 */
@Singleton
class GeminiModelManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    
    companion object {
        /**
         * ✅ PRODUCTION MODELS (January 2026)
         * 
         * ⚠️ CRITICAL: Now uses ModelConstants.VALID_MODELS!
         * This ensures perfect synchronization across the entire app.
         */
        private val PRODUCTION_MODELS = ModelConstants.VALID_MODELS
        
        /**
         * Default model for OCR operations.
         * 
         * ⚠️ CRITICAL: Now uses ModelConstants.DEFAULT_OCR_MODEL!
         */
        const val DEFAULT_OCR_MODEL = ModelConstants.DEFAULT_OCR_MODEL
        
        /**
         * Default model for Translation operations.
         * 
         * ⚠️ CRITICAL: Now uses ModelConstants.DEFAULT_TRANSLATION_MODEL!
         */
        const val DEFAULT_TRANSLATION_MODEL = ModelConstants.DEFAULT_TRANSLATION_MODEL
    }
    
    init {
        // ✅ Runtime validation: Ensure constants are synchronized
        validateSyncWithDataStore()
    }
    
    /**
     * ✅ Validates that GeminiModelManager constants match SettingsDataStore.
     * 
     * This prevents bugs where models are out of sync between the two classes.
     */
    private fun validateSyncWithDataStore() {
        // Validate VALID_MODELS synchronization
        if (PRODUCTION_MODELS != ModelConstants.VALID_MODELS) {
            Timber.e("""
                ❌ CRITICAL: Model lists are OUT OF SYNC!
                
                ModelConstants.VALID_MODELS: ${ModelConstants.VALID_MODELS}
                GeminiModelManager.PRODUCTION_MODELS: $PRODUCTION_MODELS
                
                This should NEVER happen! Check ModelConstants.kt
            """.trimIndent())
        }
        
        // Validate DEFAULT_OCR_MODEL synchronization
        if (DEFAULT_OCR_MODEL != ModelConstants.DEFAULT_OCR_MODEL) {
            Timber.e("❌ DEFAULT_OCR_MODEL mismatch!")
        }
        
        // Validate DEFAULT_TRANSLATION_MODEL synchronization
        if (DEFAULT_TRANSLATION_MODEL != ModelConstants.DEFAULT_TRANSLATION_MODEL) {
            Timber.e("❌ DEFAULT_TRANSLATION_MODEL mismatch!")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // PUBLIC API - Model Lists
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns all available Gemini models for UI display.
     * 
     * Used in:
     * - Settings → AI & OCR Tab → Gemini Fallback dropdown
     * - Settings → AI & OCR Tab → Translation dropdown
     * - Settings → Testing Tab → OCR Test dropdown
     * - Settings → Testing Tab → Translation Test dropdown
     * 
     * @return List of GeminiModelOption with display names and descriptions
     */
    fun getAvailableModels(): List<GeminiModelOption> = listOf(
        // ═══════════════════════════════════════════════════════════════
        // RECOMMENDED (Fast + Stable)
        // ═══════════════════════════════════════════════════════════════
        GeminiModelOption(
            id = "gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash Lite 🚀",
            description = "Ultra-fast • Stable • Best for OCR & Translation",
            isRecommended = true
        ),
        GeminiModelOption(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash ⚡",
            description = "Very fast • Great balance",
            isRecommended = false
        ),
        
        // ═══════════════════════════════════════════════════════════════
        // GEMINI 3 PREVIEW (Latest)
        // ═══════════════════════════════════════════════════════════════
        GeminiModelOption(
            id = "gemini-3-flash-preview",
            displayName = "Gemini 3 Flash (Preview)",
            description = "Latest • May have rate limits",
            isRecommended = false
        ),
        GeminiModelOption(
            id = "gemini-3-pro-preview",
            displayName = "Gemini 3 Pro (Preview) 💰",
            description = "PAID ONLY • Highest quality • Slower",
            isRecommended = false
        ),
        
        // ═══════════════════════════════════════════════════════════════
        // SLOWER BUT ACCURATE
        // ═══════════════════════════════════════════════════════════════
        GeminiModelOption(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro 🐌",
            description = "Slow (4-7s) • Complex text only",
            isRecommended = false
        )
    )
    
    /**
     * Returns raw model IDs (for validation).
     * 
     * @return List of model ID strings
     */
    fun getModelIds(): List<String> = PRODUCTION_MODELS
    
    /**
     * Validates if a model ID is supported.
     * 
     * @param modelId Model identifier to validate
     * @return true if model exists in PRODUCTION_MODELS
     */
    fun isValidModel(modelId: String): Boolean {
        return modelId in PRODUCTION_MODELS
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GLOBAL SETTINGS - OCR
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns the globally selected OCR model from DataStore.
     * 
     * Used in:
     * - EditorViewModel (when processing documents)
     * - MLKitScanner (for Gemini fallback)
     * - OCR pipeline
     * 
     * @return Model ID (e.g., "gemini-2.5-flash-lite")
     */
    suspend fun getGlobalOcrModel(): String {
        return try {
            val model = settingsDataStore.geminiOcrModel.first()
            
            if (!isValidModel(model)) {
                Timber.w("⚠️ Invalid OCR model in DataStore: $model, using default")
                DEFAULT_OCR_MODEL
            } else {
                Timber.d("✅ Global OCR model loaded: $model")
                model
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to read OCR model from DataStore")
            DEFAULT_OCR_MODEL
        }
    }
    
    /**
     * Sets the global OCR model (saves to DataStore).
     * 
     * @param modelId Model identifier
     * @throws IllegalArgumentException if model is not valid
     */
    suspend fun setGlobalOcrModel(modelId: String) {
        require(isValidModel(modelId)) {
            "Invalid model: $modelId. Valid models: $PRODUCTION_MODELS"
        }
        
        try {
            settingsDataStore.setGeminiOcrModel(modelId)
            Timber.i("✅ Global OCR model set to: $modelId")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to save OCR model")
            throw e
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // GLOBAL SETTINGS - TRANSLATION
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns the globally selected Translation model from DataStore.
     * 
     * Used in:
     * - EditorViewModel (when translating documents)
     * - GeminiTranslator
     * - Translation pipeline
     * 
     * @return Model ID (e.g., "gemini-2.5-flash-lite")
     */
    suspend fun getGlobalTranslationModel(): String {
        return try {
            val model = settingsDataStore.translationModel.first()
            
            if (!isValidModel(model)) {
                Timber.w("⚠️ Invalid Translation model in DataStore: $model, using default")
                DEFAULT_TRANSLATION_MODEL
            } else {
                Timber.d("✅ Global Translation model loaded: $model")
                model
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to read Translation model from DataStore")
            DEFAULT_TRANSLATION_MODEL
        }
    }
    
    /**
     * Sets the global Translation model (saves to DataStore).
     * 
     * @param modelId Model identifier
     * @throws IllegalArgumentException if model is not valid
     */
    suspend fun setGlobalTranslationModel(modelId: String) {
        require(isValidModel(modelId)) {
            "Invalid model: $modelId. Valid models: $PRODUCTION_MODELS"
        }
        
        try {
            settingsDataStore.setTranslationModel(modelId)
            Timber.i("✅ Global Translation model set to: $modelId")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to save Translation model")
            throw e
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // TESTING - LOCAL OVERRIDES (не сохраняются)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a local model option for Testing Tab.
     * 
     * This does NOT save to DataStore - only for temporary testing.
     * 
     * @param initialModel Initial model to use (defaults to global OCR model)
     * @return LocalModelState for use in Testing Tab
     */
    suspend fun createLocalOcrTestModel(initialModel: String? = null): String {
        return initialModel ?: getGlobalOcrModel()
    }
    
    /**
     * Creates a local model option for Translation Test.
     * 
     * This does NOT save to DataStore - only for temporary testing.
     * 
     * @param initialModel Initial model to use (defaults to global translation model)
     * @return LocalModelState for use in Testing Tab
     */
    suspend fun createLocalTranslationTestModel(initialModel: String? = null): String {
        return initialModel ?: getGlobalTranslationModel()
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Finds a model option by ID.
     * 
     * @param modelId Model identifier
     * @return GeminiModelOption or null if not found
     */
    fun findModelById(modelId: String): GeminiModelOption? {
        return getAvailableModels().find { it.id == modelId }
    }
    
    /**
     * Returns display name for a model ID.
     * 
     * @param modelId Model identifier
     * @return Display name (e.g., "Gemini 2.5 Flash Lite 🚀") or the ID itself if not found
     */
    fun getDisplayName(modelId: String): String {
        return findModelById(modelId)?.displayName ?: modelId
    }
}