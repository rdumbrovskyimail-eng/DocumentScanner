package com.docs.scanner.data.remote.gemini

import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.TranslationCacheStats
import com.docs.scanner.domain.core.TranslationResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * GeminiTranslator.kt
 * Version: 2.0.0 - MODEL SELECTION SUPPORT (2026)
 * 
 * ✅ NEW in 2.0.0:
 * - translateWithModel() method for explicit model selection
 * - Used in Settings → Translation Test
 * 
 * ✅ UPDATED:
 * - preferredModel: gemini-2.5-flash-lite (fastest)
 * - fallbackModels: 2.5-flash-lite → 2.5-flash → 3-flash → 2.5-pro → 3-pro
 */
@Singleton
class GeminiTranslator @Inject constructor(
    private val geminiApi: GeminiApi,
    private val translationCacheManager: TranslationCacheManager,
    private val keyManager: GeminiKeyManager,
    private val settingsDataStore: SettingsDataStore
) {
    /**
     * 2026 default: fast/cheap model.
     *
     * ✅ UPDATED: Complete fallback chain from fastest to slowest
     * 2.5-flash-lite → 2.5-flash → 3-flash-preview → 2.5-pro → 3-pro-preview
     */
    private val preferredModel: String = "gemini-2.5-flash-lite"
    private val fallbackModels: List<String> = listOf(
        "gemini-2.5-flash-lite",      // ✅ Ultra-fast, cheapest (same as preferred for consistency)
        "gemini-2.5-flash",           // ✅ Fast, stable
        "gemini-3-flash-preview",     // ✅ Latest fast (may have rate limits)
        "gemini-2.5-pro",             // ✅ Slow but accurate
        "gemini-3-pro-preview"        // ✅ Best quality (PAID only)
    )

    suspend fun translate(
        text: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        useCacheOverride: Boolean? = null
    ): DomainResult<TranslationResult> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return DomainResult.Success(
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

        val start = System.currentTimeMillis()
        val srcCode = sourceLanguage.code
        val tgtCode = targetLanguage.code

        val cacheEnabled = useCacheOverride ?: (runCatching { settingsDataStore.cacheEnabled.first() }.getOrNull() ?: true)
        val ttlDays = runCatching { settingsDataStore.cacheTtlDays.first() }.getOrNull() ?: 30

        if (cacheEnabled) {
            val cached = translationCacheManager.getCachedTranslation(
                text = trimmed,
                sourceLang = srcCode,
                targetLang = tgtCode,
                maxAgeDays = ttlDays
            )
            if (cached != null) {
                return DomainResult.Success(
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

        val prompt = buildString {
            appendLine("Translate the following text.")
            appendLine("Source language: ${sourceLanguage.displayName} (${sourceLanguage.code})")
            appendLine("Target language: ${targetLanguage.displayName} (${targetLanguage.code})")
            appendLine("Return ONLY the translated text. Do not add quotes, markdown, or explanations.")
            appendLine()
            append(trimmed)
        }

        return when (val api = geminiApi.generateText(
            prompt = prompt,
            model = preferredModel,
            fallbackModels = fallbackModels
        )) {
            is DomainResult.Success -> {
                val translated = api.data.trim()
                if (translated.isBlank()) {
                    DomainResult.failure(
                        DomainError.TranslationFailed(
                            from = sourceLanguage,
                            to = targetLanguage,
                            cause = "Empty translation"
                        )
                    )
                } else {
                    if (cacheEnabled) {
                        translationCacheManager.cacheTranslation(
                            originalText = trimmed,
                            translatedText = translated,
                            sourceLang = srcCode,
                            targetLang = tgtCode
                        )
                    }

                    DomainResult.Success(
                        TranslationResult(
                            originalText = trimmed,
                            translatedText = translated,
                            sourceLanguage = sourceLanguage,
                            targetLanguage = targetLanguage,
                            fromCache = false,
                            processingTimeMs = System.currentTimeMillis() - start
                        )
                    )
                }
            }

            is DomainResult.Failure -> DomainResult.failure(api.error)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ NEW in 2.0.0: TRANSLATE WITH SPECIFIC MODEL
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Translates text using specified model.
     * 
     * ✅ NEW: Allows model override for testing different models in Settings UI.
     * 
     * Similar to translate() but allows model override.
     * Used for testing different models in Settings UI.
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
    ): DomainResult<TranslationResult> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return DomainResult.Success(
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

        val start = System.currentTimeMillis()
        val srcCode = sourceLanguage.code
        val tgtCode = targetLanguage.code

        val cacheEnabled = useCacheOverride ?: (runCatching { settingsDataStore.cacheEnabled.first() }.getOrNull() ?: true)
        val ttlDays = runCatching { settingsDataStore.cacheTtlDays.first() }.getOrNull() ?: 30

        // Check cache first
        if (cacheEnabled) {
            val cached = translationCacheManager.getCachedTranslation(
                text = trimmed,
                sourceLang = srcCode,
                targetLang = tgtCode,
                maxAgeDays = ttlDays
            )
            if (cached != null) {
                Timber.d("📦 Translation cache hit")
                return DomainResult.Success(
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

        val prompt = buildString {
            appendLine("Translate the following text.")
            appendLine("Source language: ${sourceLanguage.displayName} (${sourceLanguage.code})")
            appendLine("Target language: ${targetLanguage.displayName} (${targetLanguage.code})")
            appendLine("Return ONLY the translated text. Do not add quotes, markdown, or explanations.")
            appendLine()
            append(trimmed)
        }

        // ✅ Use specified model with proper fallback chain
        return when (val api = geminiApi.generateText(
            prompt = prompt,
            model = model,  // ✅ Используем переданную модель
            fallbackModels = fallbackModels  // ✅ Fallback: flash-lite → flash → 3-flash → 2.5-pro → 3-pro
        )) {
            is DomainResult.Success -> {
                val translated = api.data.trim()
                if (translated.isBlank()) {
                    DomainResult.failure(
                        DomainError.TranslationFailed(
                            from = sourceLanguage,
                            to = targetLanguage,
                            cause = "Empty translation"
                        )
                    )
                } else {
                    // Cache the result
                    if (cacheEnabled) {
                        translationCacheManager.cacheTranslation(
                            originalText = trimmed,
                            translatedText = translated,
                            sourceLang = srcCode,
                            targetLang = tgtCode
                        )
                    }

                    val elapsed = System.currentTimeMillis() - start
                    Timber.d("✅ Translated with $model in ${elapsed}ms")
                    
                    DomainResult.Success(
                        TranslationResult(
                            originalText = trimmed,
                            translatedText = translated,
                            sourceLanguage = sourceLanguage,
                            targetLanguage = targetLanguage,
                            fromCache = false,
                            processingTimeMs = elapsed
                        )
                    )
                }
            }

            is DomainResult.Failure -> DomainResult.failure(api.error)
        }
    }

    suspend fun fixOcrText(text: String): DomainResult<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return DomainResult.Success("")

        val prompt = buildString {
            appendLine("You are given OCR text. Fix obvious OCR errors, spacing, and punctuation.")
            appendLine("Do NOT translate. Keep original language. Return ONLY corrected text.")
            appendLine()
            append(trimmed)
        }

        return geminiApi.generateText(
            prompt = prompt,
            model = preferredModel,
            fallbackModels = fallbackModels
        )
    }

    suspend fun clearCache() {
        translationCacheManager.clearAllCache()
    }

    suspend fun clearOldCache(ttlDays: Int): Int {
        return try {
            translationCacheManager.cleanupExpiredCache(ttlDays)
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
}