package com.docs.scanner.domain.usecase

/**
 * Legacy-ish progress state used by some presentation code when adding a document.
 *
 * TODO: Replace with a single shared processing/progress model across the app.
 */
sealed interface AddDocumentState {
    data class Creating(val progress: Int, val message: String) : AddDocumentState
    data class ProcessingOcr(val progress: Int, val message: String) : AddDocumentState
    data class Translating(val progress: Int, val message: String) : AddDocumentState
    data class Success(val documentId: Long) : AddDocumentState
    data class Error(val message: String) : AddDocumentState
}

