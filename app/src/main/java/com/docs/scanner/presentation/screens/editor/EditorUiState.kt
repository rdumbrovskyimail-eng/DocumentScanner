package com.docs.scanner.presentation.screens.editor

import com.docs.scanner.domain.model.Document

/**
 * ✅ Session 8 Fix: Added Processing state for better UX feedback
 */
sealed class EditorUiState {
    data object Loading : EditorUiState()
    data object Processing : EditorUiState()  // ✅ ДОБАВЛЕНО (Session 8 Problem #6)
    data object Empty : EditorUiState()
    data class Success(val documents: List<Document>) : EditorUiState()
    data class Error(val message: String) : EditorUiState()
}