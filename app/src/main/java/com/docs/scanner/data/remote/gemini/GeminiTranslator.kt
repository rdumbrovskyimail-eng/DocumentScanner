package com.docs.scanner.data.remote.gemini

import com.docs.scanner.data.cache.TranslationCacheManager
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.TranslationCacheStats
import com.docs.scanner.domain.core.TranslationResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranslator @Inject constructor(
    private val geminiApi: GeminiApi,
    private val translationCacheManager: TranslationCacheManager,
    private val encryptedKeyStorage: EncryptedKeyStorage
) {
    /**
     * 2026 default: fast/cheap model.
     *
     * We keep a fallback to a widely-available model to avoid hard failures if the
     * preferred model name is not enabled for the project/region.
     */
    private val preferredModel: String = "gemini-2.5-flash-lite"
    private val fallbackModels: List<String> = listOf("gemini-1.5-flash")

    suspend fun translate(
        text: String,
        sourceLanguage: Language,
        targetLanguage: Language
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

        val cached = translationCacheManager.getCachedTranslation(
            text = trimmed,
            sourceLang = srcCode,
            targetLang = tgtCode
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

        val apiKey = encryptedKeyStorage.getActiveApiKey().orEmpty()
        if (apiKey.isBlank()) return DomainResult.failure(DomainError.MissingApiKey)

        val prompt = buildString {
            appendLine("Translate the following text.")
            appendLine("Source language: ${sourceLanguage.displayName} (${sourceLanguage.code})")
            appendLine("Target language: ${targetLanguage.displayName} (${targetLanguage.code})")
            appendLine("Return ONLY the translated text. Do not add quotes, markdown, or explanations.")
            appendLine()
            append(trimmed)
        }

        return when (val api = geminiApi.generateText(apiKey, prompt, model = preferredModel, fallbackModels = fallbackModels)) {
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
                    translationCacheManager.cacheTranslation(
                        originalText = trimmed,
                        translatedText = translated,
                        sourceLang = srcCode,
                        targetLang = tgtCode
                    )

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

    suspend fun fixOcrText(text: String): DomainResult<String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return DomainResult.Success("")

        val apiKey = encryptedKeyStorage.getActiveApiKey().orEmpty()
        if (apiKey.isBlank()) return DomainResult.failure(DomainError.MissingApiKey)

        val prompt = buildString {
            appendLine("You are given OCR text. Fix obvious OCR errors, spacing, and punctuation.")
            appendLine("Do NOT translate. Keep original language. Return ONLY corrected text.")
            appendLine()
            append(trimmed)
        }

        return geminiApi.generateText(apiKey, prompt, model = preferredModel, fallbackModels = fallbackModels)
    }

    suspend fun clearCache() {
        translationCacheManager.clearAllCache()
    }

    suspend fun clearOldCache(ttlDays: Int): Int {
        return try {
            translationCacheManager.cleanupExpiredCache(ttlDays)
            0
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

