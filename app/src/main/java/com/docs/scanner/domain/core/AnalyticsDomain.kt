package com.docs.scanner.domain.core

interface AnalyticsDomain {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
}