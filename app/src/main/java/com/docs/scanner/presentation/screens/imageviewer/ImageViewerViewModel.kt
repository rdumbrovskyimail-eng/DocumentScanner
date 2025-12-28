package com.docs.scanner.presentation.screens.imageviewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.usecase.AllUseCases
import com.docs.scanner.presentation.navigation.Screen
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
 * - ✅ Proper parameter extraction using Screen helper
 * - ✅ Removed deprecated _document field
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    private val useCases: AllUseCases,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val documentId: Long = Screen.ImageViewer.getDocumentIdFromRoute(savedStateHandle)

    private val _uiState = MutableStateFlow<ImageViewerUiState>(ImageViewerUiState.Loading)
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    init {
        loadDocument()
    }

    /**
     * Load document by ID.
     */
    private fun loadDocument() {
        if (documentId <= 0) {
            _uiState.value = ImageViewerUiState.Error("Invalid document ID")
            return
        }

        viewModelScope.launch {
            _uiState.value = ImageViewerUiState.Loading

            try {
                val document = useCases.getDocumentById(documentId)
                
                _uiState.value = if (document != null) {
                    ImageViewerUiState.Success(document)
                } else {
                    ImageViewerUiState.Error("Document not found")
                }
            } catch (e: Exception) {
                _uiState.value = ImageViewerUiState.Error(
                    "Failed to load document: ${e.message}"
                )
            }
        }
    }

    /**
     * Retry loading the document.
     */
    fun retry() {
        loadDocument()
    }
}

/**
 * UI State for Image Viewer.
 */
sealed interface ImageViewerUiState {
    data object Loading : ImageViewerUiState
    data class Success(val document: Document) : ImageViewerUiState
    data class Error(val message: String) : ImageViewerUiState
}