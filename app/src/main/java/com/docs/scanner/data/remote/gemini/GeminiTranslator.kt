/*
 * GeminiTranslator.kt
 * Version: 3.1.0 - SETTINGS INTEGRATION (2026)
 * 
 * ✅ NEW IN 3.1.0:
 * - Full integration with SettingsDataStore for model selection
 * - Model parameter uses settings if not specified
 * - Cache key includes model for proper isolation
 * 
 * LOCATION: com.docs.scanner.data.remote.gemini
 */

package com.docs.scanner.data.remote.gemini

import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.preferences.GeminiModelManager
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.TranslationCacheStats
import com.docs.scanner.domain.core.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles translation using Gemini API.
 * 
 * ✅ UPDATED: Full settings integration
 * - Uses model from GeminiModelManager
 * - Respects cache settings from SettingsDataStore
 * - Cache keys include model for isolation
 */
@Singleton
class GeminiTranslator @Inject constructor(
    private val geminiApi: GeminiApi,
    private val translationCacheManager: TranslationCacheManager,
    private val keyManager: GeminiKeyManager,
    private val settingsDataStore: SettingsDataStore,
    private val modelManager: GeminiModelManager
) {
    
    companion object {
        private const val TAG = "GeminiTranslator"
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // PRIMARY TRANSLATION METHOD
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Translates text using model from settings.
     * 
     * ✅ UPDATED: Uses model from GeminiModelManager if not specified
     * 
     * @param text Text to translate
     * @param sourceLanguage Source language (use Language.AUTO for auto-detection)
     * @param targetLanguage Target language
     * @param useCacheOverride Optional cache override (null = use settings)
     * @return Translation result
     */
    suspend fun translate(
        text: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        useCacheOverride: Boolean? = null
    ): DomainResult<TranslationResult> = withContext(Dispatchers.IO) {
        
        // ✅ STEP 1: Get model from settings
        val model = try {
            modelManager.getGlobalTranslationModel()
        } catch (e: Exception) {
            Timber.w(e, "Failed to get global translation model, using default")
            GeminiModelManager.DEFAULT_TRANSLATION_MODEL
        }
        
        // ✅ STEP 2: Call translateWithModel with settings model
        return@withContext translateWithModel(
            text = text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            model = model,
            useCacheOverride = useCacheOverride
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // MODEL-SPECIFIC TRANSLATION (for testing different models)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Translates text using specified model.
     * 
     * ✅ NEW: Allows model override for testing different models in Settings UI.
     * ✅ Cache key includes model for proper isolation
     * 
     * @param text Text to translate
     * @param sourceLanguage Source language
     * @param targetLanguage Target language
     * @param model Gemini model ID to use (e.g., "gemini-2.5-flash-lite")
     * @param useCacheOverride Optional cache override (null = use settings)
     * @return TranslationResult or error
     */
    suspend fun translateWithModel(
        text: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        model: String,
        useCacheOverride: Boolean? = null
    ): DomainResult<TranslationResult> = withContext(Dispatchers.IO) {
        
        // ✅ STEP 1: Validate input
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext DomainResult.Success(
                TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    fromCache = true,
                    processingTimeMs = 0
                )
            )
        }
        
        if (sourceLanguage != Language.AUTO && sourceLanguage == targetLanguage) {
            return@withContext DomainResult.Failure(
                DomainError.ValidationError.InvalidInput(
                    "Source and target languages must be different"
                )
            )
        }
        
        // ✅ STEP 2: Validate model
        if (!modelManager.isValidModel(model)) {
            Timber.w("⚠️ Invalid model: $model, using default")
            val fallbackModel = GeminiModelManager.DEFAULT_TRANSLATION_MODEL
            return@withContext translateWithModel(
                text, sourceLanguage, targetLanguage, 
                fallbackModel, useCacheOverride
            )
        }
        
        val startTime = System.currentTimeMillis()
        val srcCode = sourceLanguage.code
        val tgtCode = targetLanguage.code
        
        // ✅ STEP 3: Get cache settings
        val cacheEnabled = useCacheOverride ?: runCatching { 
            settingsDataStore.cacheEnabled.first() 
        }.getOrNull() ?: true
        
        val ttlDays = runCatching { 
            settingsDataStore.cacheTtlDays.first() 
        }.getOrNull() ?: 30
        
        // ✅ STEP 4: Check cache (key includes model!)
        if (cacheEnabled) {
            val cacheKey = buildCacheKey(trimmed, srcCode, tgtCode, model)
            val cached = translationCacheManager.getCachedTranslation(
                text = cacheKey,
                sourceLang = srcCode,
                targetLang = tgtCode,
                maxAgeDays = ttlDays
            )
            
            if (cached != null) {
                if (Timber.forest().isNotEmpty()) {
                    Timber.d("✅ Cache hit (model: $model)")
                }
                return@withContext DomainResult.Success(
                    TranslationResult(
                        originalText = trimmed,
                        translatedText = cached,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage,
                        fromCache = true,
                        processingTimeMs = 0
                    )
                )
            }
        }
        
        // ✅ STEP 5: Build prompt
        val prompt = buildTranslationPrompt(trimmed, sourceLanguage, targetLanguage)
        
        if (Timber.forest().isNotEmpty()) {
            Timber.d("🌐 Calling Gemini API:")
            Timber.d("   ├─ Model: $model")
            Timber.d("   ├─ Source: ${sourceLanguage.displayName} ($srcCode)")
            Timber.d("   ├─ Target: ${targetLanguage.displayName} ($tgtCode)")
            Timber.d("   └─ Text length: ${trimmed.length} chars")
        }
        
        // ✅ STEP 6: Call API with fallback chain
        return@withContext when (val result = geminiApi.generateText(
            prompt = prompt,
            model = model,
            fallbackModels = getFallbackModels(model)
        )) {
            is DomainResult.Success -> {
                val translated = result.data.trim()
                
                if (translated.isBlank()) {
                    DomainResult.Failure(
                        DomainError.TranslationFailed(
                            from = sourceLanguage,
                            to = targetLanguage,
                            cause = "Empty translation response"
                        )
                    )
                } else {
                    // ✅ STEP 7: Cache result (with model in key)
                    if (cacheEnabled) {
                        val cacheKey = buildCacheKey(trimmed, srcCode, tgtCode, model)
                        translationCacheManager.cacheTranslation(
                            originalText = cacheKey,
                            translatedText = translated,
                            sourceLang = srcCode,
                            targetLang = tgtCode
                        )
                    }
                    
                    val processingTime = System.currentTimeMillis() - startTime
                    
                    if (Timber.forest().isNotEmpty()) {
                        Timber.d("✅ Translation successful (${processingTime}ms, model: $model)")
                    }
                    
                    DomainResult.Success(
                        TranslationResult(
                            originalText = trimmed,
                            translatedText = translated,
                            sourceLanguage = sourceLanguage,
                            targetLanguage = targetLanguage,
                            fromCache = false,
                            processingTimeMs = processingTime
                        )
                    )
                }
            }
            
            is DomainResult.Failure -> {
                val processingTime = System.currentTimeMillis() - startTime
                Timber.e("❌ Translation failed after ${processingTime}ms: ${result.error.message}")
                result
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // OCR TEXT CORRECTION
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Fixes OCR errors in text using Gemini.
     * 
     * @param text OCR text to fix
     * @return Fixed text or error
     */
    suspend fun fixOcrText(text: String): DomainResult<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext DomainResult.Success("")
        }
        
        // ✅ Use model from settings
        val model = try {
            modelManager.getGlobalTranslationModel()
        } catch (e: Exception) {
            Timber.w(e, "Failed to get model for OCR fix, using default")
            GeminiModelManager.DEFAULT_TRANSLATION_MODEL
        }
        
        val prompt = buildString {
            appendLine("You are given OCR text. Fix obvious OCR errors, spacing, and punctuation.")
            appendLine("Do NOT translate. Keep original language. Return ONLY corrected text.")
            appendLine()
            append(trimmed)
        }
        
        return@withContext geminiApi.generateText(
            prompt = prompt,
            model = model,
            fallbackModels = getFallbackModels(model)
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // CACHE MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════════════
    
    suspend fun clearCache() {
        translationCacheManager.clearAllCache()
        Timber.d("🗑️ Translation cache cleared")
    }
    
    suspend fun clearOldCache(ttlDays: Int): Int {
        return try {
            val cleared = translationCacheManager.cleanupExpiredCache(ttlDays)
            Timber.d("🗑️ Cleared $cleared expired cache entries (older than $ttlDays days)")
            cleared
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear old cache")
            0
        }
    }
    
    suspend fun getCacheStats(): TranslationCacheStats {
        val stats = translationCacheManager.getCacheStats()
        return TranslationCacheStats(
            totalEntries = stats.totalEntries,
            hitRate = 0f,
            totalSizeBytes = stats.totalSizeBytes,
            oldestEntryTimestamp = stats.oldestEntry.takeIf { it > 0 },
            newestEntryTimestamp = stats.newestEntry.takeIf { it > 0 }
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Builds cache key that includes model for proper isolation.
     * 
     * ✅ CRITICAL: Different models may produce different translations,
     * so cache must be model-specific!
     */
    private fun buildCacheKey(
        text: String,
        sourceCode: String,
        targetCode: String,
        model: String
    ): String {
        val textHash = text.hashCode().toString(16)
        // Format: src_tgt_model_hash
        return "${sourceCode}_${targetCode}_${model}_$textHash"
    }
    
    /**
     * Builds translation prompt for Gemini.
     */
    private fun buildTranslationPrompt(
        text: String,
        source: Language,
        target: Language
    ): String {
        return buildString {
            appendLine("Translate the following text.")
            
            if (source != Language.AUTO) {
                appendLine("Source language: ${source.displayName} (${source.code})")
            }
            
            appendLine("Target language: ${target.displayName} (${target.code})")
            appendLine("Return ONLY the translated text. Do not add quotes, markdown, or explanations.")
            appendLine()
            append(text)
        }
    }
    
    /**
     * Returns fallback models for the given primary model.
     * 
     * ✅ Strategy:
     * - 3.x models → fallback to 2.5-flash-lite
     * - 2.5-pro → fallback to 2.5-flash → 2.5-flash-lite
     * - 2.5-flash → fallback to 2.5-flash-lite
     * - 2.5-flash-lite → fallback to 2.5-flash
     */
    private fun getFallbackModels(primaryModel: String): List<String> {
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
