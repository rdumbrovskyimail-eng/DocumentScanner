package com.docs.scanner.presentation.screens.editor

import com.docs.scanner.domain.model.Document

sealed class EditorUiState {
    data object Loading : EditorUiState()
    data object Empty : EditorUiState()
    data class Success(val documents: List<Document>) : EditorUiState()
    data class Error(val message: String) : EditorUiState()
}
