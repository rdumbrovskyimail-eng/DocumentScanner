/*
 * GeminiTranslator.kt
 * Version: 7.2.0 - MODEL SUPPORT + CRITICAL FIXES
 * 
 * ✅ CRITICAL FIX (Session 14): Model-aware caching
 * ✅ NEW: Uses ModelConstants.getFallbackModels()
 * ✅ FIXED: Proper cache key generation with model parameter
 * ✅ FIXED: ValidationError import and usage
 */

package com.docs.scanner.data.remote.gemini

import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.preferences.GeminiModelManager
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.ModelConstants
import com.docs.scanner.domain.core.TranslationCacheStats
import com.docs.scanner.domain.core.TranslationResult
import com.docs.scanner.domain.core.ValidationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranslator @Inject constructor(
    private val geminiApi: GeminiApi,
    private val translationCacheManager: TranslationCacheManager,
    private val keyManager: GeminiKeyManager,
    private val settingsDataStore: SettingsDataStore,
    private val modelManager: GeminiModelManager
) {
    
    suspend fun translate(
        text: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        model: String? = null,
        useCacheOverride: Boolean? = null
    ): DomainResult<TranslationResult> = withContext(Dispatchers.IO) {
        
        val actualModel = model ?: try {
            modelManager.getGlobalTranslationModel()
        } catch (e: Exception) {
            Timber.w(e, "Failed to get global translation model, using default")
            ModelConstants.DEFAULT_TRANSLATION_MODEL
        }
        
        return@withContext translateWithModel(
            text = text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            model = actualModel,
            useCacheOverride = useCacheOverride
        )
    }
    
    suspend fun translateWithModel(
        text: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        model: String,
        useCacheOverride: Boolean? = null
    ): DomainResult<TranslationResult> = withContext(Dispatchers.IO) {
        
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext DomainResult.success(
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
        
        if (!modelManager.isValidModel(model)) {
            Timber.w("⚠️ Invalid model: $model, using default")
            val fallbackModel = ModelConstants.DEFAULT_TRANSLATION_MODEL
            return@withContext translateWithModel(
                text, sourceLanguage, targetLanguage, 
                fallbackModel, useCacheOverride
            )
        }
        
        val startTime = System.currentTimeMillis()
        val srcCode = sourceLanguage.code
        val tgtCode = targetLanguage.code
        
        val cacheEnabled = useCacheOverride ?: runCatching { 
            settingsDataStore.cacheEnabled.first() 
        }.getOrNull() ?: true
        
        val ttlDays = runCatching { 
            settingsDataStore.cacheTtlDays.first() 
        }.getOrNull() ?: 30
        
        if (cacheEnabled) {
            val cached = translationCacheManager.getCachedTranslation(
                text = trimmed,
                sourceLang = srcCode,
                targetLang = tgtCode,
                model = model,
                maxAgeDays = ttlDays
            )
            
            if (cached != null) {
                Timber.d("✅ Cache hit (model: $model)")
                return@withContext DomainResult.success(
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
        
        val prompt = buildTranslationPrompt(trimmed, sourceLanguage, targetLanguage)
        
        Timber.d("🌐 Calling Gemini API:")
        Timber.d("   ├─ Model: $model")
        Timber.d("   ├─ Source: ${sourceLanguage.displayName} ($srcCode)")
        Timber.d("   ├─ Target: ${targetLanguage.displayName} ($tgtCode)")
        Timber.d("   └─ Text length: ${trimmed.length} chars")
        
        when (val result = geminiApi.generateText(
            prompt = prompt,
            model = model,
            fallbackModels = ModelConstants.getFallbackModels(model)
        )) {
            is DomainResult.Success -> {
                val translated = result.data.trim()
                
                if (translated.isBlank()) {
                    return@withContext DomainResult.failure(
                        DomainError.TranslationFailed(
                            from = sourceLanguage,
                            to = targetLanguage,
                            cause = "Empty translation response"
                        )
                    )
                }
                
                if (cacheEnabled) {
                    translationCacheManager.cacheTranslation(
                        originalText = trimmed,
                        translatedText = translated,
                        sourceLang = srcCode,
                        targetLang = tgtCode,
                        model = model
                    )
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                
                Timber.d("✅ Translation successful (${processingTime}ms, model: $model)")
                
                return@withContext DomainResult.success(
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
            
            is DomainResult.Failure -> {
                val processingTime = System.currentTimeMillis() - startTime
                Timber.e("❌ Translation failed after ${processingTime}ms: ${result.error.message}")
                return@withContext DomainResult.failure(result.error)
            }
        }
    }
    
    suspend fun fixOcrText(text: String): DomainResult<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext DomainResult.success("")
        }
        
        val model = try {
            modelManager.getGlobalTranslationModel()
        } catch (e: Exception) {
            Timber.w(e, "Failed to get model for OCR fix, using default")
            ModelConstants.DEFAULT_TRANSLATION_MODEL
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
            fallbackModels = ModelConstants.getFallbackModels(model)
        )
    }
    
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
    
    private fun buildTranslationPrompt(
        text: String,
        source: Language,
        target: Language
    ): String {
        return buildString {
            if (source != Language.AUTO && source == target) {
                // Промпт для улучшения и исправления текста на том же языке
                appendLine("You are an expert editor and OCR correction specialist.")
                appendLine("The following text is already in ${target.displayName} (${target.code}).")
                appendLine("Your task is to:")
                appendLine("1. Correct any OCR mistakes, typos, spelling, and grammar errors.")
                appendLine("2. Improve the flow, styling, and punctuation of the text while preserving its exact meaning.")
                appendLine("3. Return ONLY the polished and corrected text. Do NOT add quotes, markdown, explanations, or introductory/concluding remarks.")
            } else {
                // Стандартный промпт для перевода
                appendLine("Translate the following text.")
                if (source != Language.AUTO) {
                    appendLine("Source language: ${source.displayName} (${source.code})")
                }
                appendLine("Target language: ${target.displayName} (${target.code})")
                appendLine("Return ONLY the translated text. Do not add quotes, markdown, or explanations.")
            }
            appendLine()
            append(text)
        }
    }
}