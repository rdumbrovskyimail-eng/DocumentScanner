package com.docs.scanner.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ✅ Debounce для предотвращения множественных кликов
 * 
 * Использование:
 * ```kotlin
 * val debouncer = Debouncer(500L, viewModelScope)
 * Button(onClick = { debouncer.invoke { /* action */ } })
 * ```
 */
class Debouncer(
    private val delayMillis: Long = 500L,
    private val scope: CoroutineScope
) {
    private var debounceJob: Job? = null
    private var lastClickTime = 0L
    
    operator fun invoke(action: () -> Unit) {
        val now = System.currentTimeMillis()
        
        // Игнорируем слишком быстрые клики
        if (now - lastClickTime < delayMillis) {
            android.util.Log.d("Debouncer", "⚠️ Ignoring rapid click")
            return
        }
        
        lastClickTime = now
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMillis)
            action()
        }
    }
    
    fun cancel() {
        debounceJob?.cancel()
    }
}

/**
 * ✅ Throttle для ограничения частоты вызовов
 * 
 * Использование:
 * ```kotlin
 * val throttler = Throttler(1000L, viewModelScope)
 * Button(onClick = { throttler.invoke { /* action */ } })
 * ```
 */
class Throttler(
    private val intervalMillis: Long = 1000L,
    private val scope: CoroutineScope
) {
    private var lastExecutionTime = 0L
    private var throttleJob: Job? = null
    
    operator fun invoke(action: () -> Unit) {
        val now = System.currentTimeMillis()
        
        if (now - lastExecutionTime < intervalMillis) {
            android.util.Log.d("Throttler", "⚠️ Too frequent call, throttling")
            return
        }
        
        lastExecutionTime = now
        throttleJob?.cancel()
        throttleJob = scope.launch {
            action()
        }
    }
    
    fun cancel() {
        throttleJob?.cancel()
    }
}
