package com.docs.scanner.data.remote.gemini

import com.docs.scanner.domain.core.DomainError
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.DomainResult.Companion.failure
import com.docs.scanner.domain.core.Language
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API Facade with dual-mode operation.
 * 
 * Version: 2.0.0 - FIXED (2026)
 * 
 * ✅ FIXES:
 * - Split into two methods: generateText() with failover and generateTextWithKey() for testing
 * - Fixed conflict between manual apiKey parameter and keyManager.executeWithFailover
 * - Added proper error handling for empty responses
 * 
 * Features:
 * - Automatic API key failover (production)
 * - Single key testing (settings UI)
 * - Fallback model support
 * - Comprehensive error handling
 */
@Singleton
class GeminiApi @Inject constructor(
    private val service: GeminiApiService,
    private val keyManager: GeminiKeyManager
) {
    
    companion object {
        private const val TAG = "GeminiApi"
        private const val DEFAULT_MODEL = "gemini-2.0-flash-lite"
        private const val FALLBACK_MODEL = "gemini-1.5-flash"
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // PRODUCTION API - WITH AUTOMATIC FAILOVER
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Generates text using automatic API key failover.
     * 
     * This is the PRIMARY method for production use.
     * Uses GeminiKeyManager to automatically try multiple keys if one fails.
     * 
     * Flow:
     * 1. GeminiKeyManager selects first healthy key
     * 2. Makes API call
     * 3. If fails (401/403/429/5xx) → automatically tries next key
     * 4. Returns result or error after all keys exhausted
     * 
     * @param prompt User prompt
     * @param model Model to use (default: gemini-2.0-flash-lite)
     * @param fallbackModels Models to try if primary fails
     * @return Generated text or error
     */
    suspend fun generateText(
        prompt: String,
        model: String = DEFAULT_MODEL,
        fallbackModels: List<String> = listOf(FALLBACK_MODEL)
    ): DomainResult<String> {
        if (prompt.isBlank()) {
            return failure(
                DomainError.TranslationFailed(
                    from = Language.AUTO,
                    to = Language.AUTO,
                    cause = "Prompt cannot be blank"
                )
            )
        }
        
        return try {
            val request = createRequest(prompt)
            
            // ✅ Use failover - GeminiKeyManager will try multiple keys
            val response = keyManager.executeWithFailover { apiKey ->
                service.generateContent(
                    model = model,
                    apiKey = apiKey,
                    body = request
                )
            }
            
            extractTextFromResponse(response)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Text generation failed (with failover)")
            DomainResult.failure(DomainError.NetworkFailed(e))
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // TESTING API - WITH SPECIFIC KEY (NO FAILOVER)
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Generates text using a SPECIFIC API key (no failover).
     * 
     * This method is for TESTING ONLY (Settings UI).
     * Does NOT use GeminiKeyManager - tests the exact key provided.
     * 
     * Use cases:
     * - User testing new API key in Settings
     * - Verifying key validity
     * - Debugging specific key issues
     * 
     * @param apiKey Specific API key to test
     * @param prompt Test prompt (default: "Reply with: OK")
     * @param model Model to use
     * @param fallbackModels Models to try if primary fails (for this specific key)
     * @return Generated text or error
     */
    suspend fun generateTextWithKey(
        apiKey: String,
        prompt: String = "Reply with: OK",
        model: String = DEFAULT_MODEL,
        fallbackModels: List<String> = listOf(FALLBACK_MODEL)
    ): DomainResult<String> {
        if (apiKey.isBlank()) {
            return failure(DomainError.MissingApiKey)
        }
        
        if (prompt.isBlank()) {
            return failure(
                DomainResult.Failure(
                    DomainError.TranslationFailed(
                        from = Language.AUTO,
                        to = Language.AUTO,
                        cause = "Prompt cannot be blank"
                    )
                )
            )
        }
        
        return try {
            val request = createRequest(prompt)
            
            // ✅ Direct call - NO failover, test this specific key
            var lastError: Exception? = null
            val modelsToTry = listOf(model) + fallbackModels
            
            for (modelToTry in modelsToTry) {
                try {
                    Timber.d("$TAG: Testing key with model: $modelToTry")
                    
                    val response = service.generateContent(
                        model = modelToTry,
                        apiKey = apiKey,
                        body = request
                    )
                    
                    // If successful, extract and return
                    return extractTextFromResponse(response)
                    
                } catch (e: Exception) {
                    lastError = e
                    Timber.w(e, "$TAG: Model $modelToTry failed, trying next")
                    continue
                }
            }
            
            // All models failed
            Timber.e(lastError, "$TAG: All models failed for test key")
            DomainResult.failure(DomainError.NetworkFailed(lastError!!))
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Key test failed")
            DomainResult.failure(DomainError.NetworkFailed(e))
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a standard request for text generation.
     */
    private fun createRequest(prompt: String): GenerateContentRequest {
        return GenerateContentRequest(
            contents = listOf(
                GenerateContentRequest.Content(
                    parts = listOf(
                        GenerateContentRequest.Part(text = prompt)
                    )
                )
            )
        )
    }
    
    /**
     * Extracts text from Gemini API response.
     * Handles empty responses and null candidates.
     */
    private fun extractTextFromResponse(
        response: GenerateContentResponse
    ): DomainResult<String> {
        val text = response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.joinToString(separator = "") { it.text.orEmpty() }
            ?.trim()
            .orEmpty()
        
        return if (text.isBlank()) {
            Timber.w("$TAG: Empty response from Gemini API")
            DomainResult.failure(
                DomainError.TranslationFailed(
                    from = Language.AUTO,
                    to = Language.AUTO,
                    cause = "Empty response from Gemini"
                )
            )
        } else {
            Timber.d("$TAG: ✅ Generated ${text.length} characters")
            DomainResult.Success(text)
        }
    }
}