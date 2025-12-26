package com.docs.scanner.util

import kotlinx.coroutines.*

class Debouncer(
    private val delayMillis: Long = 500L,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    operator fun invoke(action: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(delayMillis)
            action()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun dispose() {
        cancel()
    }
}

class Throttler(
    private val intervalMillis: Long = 1000L,
    private val scope: CoroutineScope
) {
    private var lastExecution = 0L
    private var job: Job? = null

    operator fun invoke(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastExecution < intervalMillis) return

        lastExecution = now
        job?.cancel()
        job = scope.launch {
            action()
        }
    }

    fun cancel() {
        job?.cancel()
    }
}