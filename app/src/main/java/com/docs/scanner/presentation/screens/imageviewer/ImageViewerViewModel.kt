package com.docs.scanner.presentation.screens.imageviewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Image Viewer ViewModel.
 * 
 * Session 8 Fix:
 * - ✅ Moved from ImageViewerScreen.kt to separate file
 * - ✅ Removed direct DocumentRepository injection
 * - ✅ Uses GetDocumentByIdUseCase
 * - ✅ Added error state
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    private val useCases: AllUseCases,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val documentId: Long = savedStateHandle.get<Long>("documentId") ?: 0L

    private val _uiState = MutableStateFlow<ImageViewerUiState>(ImageViewerUiState.Loading)
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    // ⚠️ DEPRECATED: Keep for backward compatibility with Screen
    // Will be removed after Screen migrates to uiState
    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document.asStateFlow()

    init {
        if (documentId > 0) {
            loadDocument(documentId)
        } else {
            _uiState.value = ImageViewerUiState.Error("Invalid document ID")
        }
    }

    /**
     * Load document by ID.
     */
    fun loadDocument(documentId: Long) {
        viewModelScope.launch {
            _uiState.value = ImageViewerUiState.Loading

            try {
                val document = useCases.getDocumentById(documentId)
                
                if (document == null) {
                    _uiState.value = ImageViewerUiState.Error("Document not found")
                    _document.value = null
                } else {
                    _uiState.value = ImageViewerUiState.Success(document)
                    _document.value = document  // For backward compatibility
                }
            } catch (e: Exception) {
                _uiState.value = ImageViewerUiState.Error(
                    "Failed to load document: ${e.message}"
                )
                _document.value = null
            }
        }
    }
}

/**
 * UI State for Image Viewer.
 */
sealed interface ImageViewerUiState {
    object Loading : ImageViewerUiState
    data class Success(val document: Document) : ImageViewerUiState
    data class Error(val message: String) : ImageViewerUiState
}