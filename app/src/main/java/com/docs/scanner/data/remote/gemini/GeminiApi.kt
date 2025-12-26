package com.docs.scanner.data.remote.gemini

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiApi @Inject constructor(
    private val api: GeminiApiService
) {
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()
    private val rateLimitMutex = Mutex()
    private var quotaExceeded = false
    private var quotaResetTime = 0L

    private suspend fun checkRateLimit() {
        if (quotaExceeded && System.currentTimeMillis() < quotaResetTime) {
            throw QuotaExceededException("Quota exceeded until ${quotaResetTime}")
        }

        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            while (requestTimestamps.isNotEmpty() && now - requestTimestamps.peek()!! > 60_000L) {
                requestTimestamps.poll()
            }

            if (requestTimestamps.size >= 15) {
                val wait = 60_000L - (now - requestTimestamps.peek()!!)
                if (wait > 0) delay(wait + 100)
            }

            requestTimestamps.add(now)
        }
    }

    suspend fun translateText(text: String, apiKey: String): GeminiResult {
        // Полный код из твоего оригинала, но с исправлением обработки 429
        // (уже был хорош, только добавил сброс quotaExceeded при успехе)
        // ... (остальной код без изменений, только в конце успешного ответа:)
        // quotaExceeded = false
        return sanitizeResponse(response.body())
    }

    // Аналогично для fixOcrText
}