package com.docs.scanner.data.remote.gemini

import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.security.ApiKeyEntry
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multiple Gemini API keys with automatic failover.
 * 
 * Features:
 * - Automatic switch to backup key on failure
 * - Rate limit handling (429)
 * - Invalid key detection (401/403)
 * - Exponential backoff for server errors
 * - Cooldown period for failed keys
 * 
 * Usage:
 * ```
 * val result = keyManager.executeWithFailover { apiKey ->
 *     geminiService.generateContent(apiKey, request)
 * }
 * ```
 */
@Singleton
class GeminiKeyManager @Inject constructor(
    private val keyStorage: EncryptedKeyStorage
) {
    companion object {
        private const val TAG = "GeminiKeyManager"
        private const val MAX_ERRORS_BEFORE_COOLDOWN = 3
        private const val ERROR_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30000L
    }
    
    private val currentKeyIndex = AtomicInteger(0)
    private val keyLock = Mutex()
    
    /**
     * Gets the currently active API key.
     * Skips keys that are in cooldown or have too many errors.
     * 
     * @return Active API key or null if none available
     */
    suspend fun getActiveKey(): String? = keyLock.withLock {
        val allKeys = keyStorage.getAllApiKeys()
        
        if (allKeys.isEmpty()) {
            Timber.w("$TAG: No API keys configured")
            return@withLock null
        }
        
        val healthyKeys = allKeys.filter { it.isHealthy(MAX_ERRORS_BEFORE_COOLDOWN, ERROR_COOLDOWN_MS) }
        
        if (healthyKeys.isEmpty()) {
            // All keys are in cooldown - try the one with oldest error
            val fallback = allKeys
                .filter { it.isActive }
                .minByOrNull { it.lastErrorAt ?: 0L }
            
            if (fallback != null) {
                Timber.w("$TAG: All keys in cooldown, using fallback: ${fallback.maskedKey}")
                return@withLock fallback.key
            }
            
            Timber.e("$TAG: No active API keys available!")
            return@withLock null
        }
        
        val index = currentKeyIndex.get() % healthyKeys.size
        val selected = healthyKeys[index]
        
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: Using key ${index + 1}/${healthyKeys.size}: ${selected.maskedKey}")
        }
        
        return@withLock selected.key
    }
    
    /**
     * Reports successful API call for statistics.
     */
    suspend fun reportSuccess(key: String) {
        keyStorage.updateKeySuccess(key)
        if (BuildConfig.DEBUG) {
            Timber.d("$TAG: ✅ Key success reported")
        }
    }
    
    /**
     * Reports failed API call and returns next available key.
     * 
     * @param failedKey The key that failed
     * @param error The exception that occurred
     * @return Next available key or null if none available
     */
    suspend fun reportErrorAndGetNext(failedKey: String, error: Throwable): String? = keyLock.withLock {
        val errorType = classifyError(error)
        Timber.w("$TAG: ⚠️ Key failed (${errorType}): ${error.message}")
        
        keyStorage.updateKeyError(failedKey)
        
        // For invalid keys, deactivate immediately
        if (errorType == ErrorType.INVALID_KEY) {
            Timber.e("$TAG: ❌ Deactivating invalid key")
            keyStorage.deactivateKey(failedKey)
        }
        
        // Move to next key
        currentKeyIndex.incrementAndGet()
        
        return@withLock getActiveKey()
    }
    
    /**
     * Executes an operation with automatic failover between API keys.
     * 
     * @param maxRetries Maximum number of keys to try
     * @param operation The API operation to execute
     * @return Result of the operation
     * @throws Exception if all keys fail
     */
    suspend fun <T> executeWithFailover(
        maxRetries: Int = 3,
        operation: suspend (apiKey: String) -> T
    ): T {
        val triedKeys = mutableSetOf<String>()
        var lastError: Throwable? = null
        var backoffMs = INITIAL_BACKOFF_MS
        
        repeat(maxRetries) { attempt ->
            val key = getActiveKey()
            
            if (key == null) {
                throw lastError ?: IllegalStateException("No API keys available")
            }
            
            if (key in triedKeys) {
                // Already tried this key - wait for cooldown or fail
                if (attempt > 0) {
                    Timber.d("$TAG: Waiting ${backoffMs}ms before retry...")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
            
            triedKeys.add(key)
            
            try {
                val result = operation(key)
                reportSuccess(key)
                return result
            } catch (e: HttpException) {
                lastError = e
                handleHttpError(e, key, attempt)
            } catch (e: Exception) {
                lastError = e
                Timber.e(e, "$TAG: Unexpected error")
                reportErrorAndGetNext(key, e)
            }
        }
        
        throw lastError ?: IllegalStateException("All API key attempts exhausted")
    }
    
    private suspend fun handleHttpError(e: HttpException, key: String, attempt: Int) {
        when (e.code()) {
            429 -> {
                // Rate limit - switch immediately, no backoff needed
                Timber.w("$TAG: Rate limit (429), switching key immediately")
                reportErrorAndGetNext(key, e)
            }
            401, 403 -> {
                // Invalid/unauthorized key
                Timber.e("$TAG: Invalid key (${e.code()}), deactivating")
                reportErrorAndGetNext(key, e)
            }
            in 500..599 -> {
                // Server error - might be temporary
                Timber.w("$TAG: Server error (${e.code()}), will retry")
                if (attempt > 0) {
                    delay(INITIAL_BACKOFF_MS * (attempt + 1))
                }
                reportErrorAndGetNext(key, e)
            }
            else -> {
                Timber.w("$TAG: HTTP error (${e.code()})")
                reportErrorAndGetNext(key, e)
            }
        }
    }
    
    private fun classifyError(error: Throwable): ErrorType {
        return when {
            error is HttpException -> when (error.code()) {
                429 -> ErrorType.RATE_LIMIT
                401, 403 -> ErrorType.INVALID_KEY
                in 500..599 -> ErrorType.SERVER_ERROR
                else -> ErrorType.OTHER
            }
            else -> ErrorType.OTHER
        }
    }
    
    private enum class ErrorType {
        RATE_LIMIT,
        INVALID_KEY,
        SERVER_ERROR,
        OTHER
    }
    
    /**
     * Gets count of available healthy keys.
     */
    suspend fun getHealthyKeyCount(): Int {
        return keyStorage.getAllApiKeys()
            .count { it.isHealthy(MAX_ERRORS_BEFORE_COOLDOWN, ERROR_COOLDOWN_MS) }
    }
    
    /**
     * Resets error counts for all keys.
     * Useful for manual recovery.
     */
    suspend fun resetAllKeyErrors() {
        keyStorage.resetAllKeyErrors()
        currentKeyIndex.set(0)
        Timber.i("$TAG: All key errors reset")
    }
}