package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.AddDocumentState
import com.docs.scanner.domain.usecase.AllUseCases
import com.docs.scanner.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor Screen ViewModel.
 * 
 * Session 8 Fixes:
 * - ✅ Uses Screen helper for parameter extraction
 * - ✅ All other fixes already applied
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val useCases: AllUseCases,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ✅ FIX: Use Screen helper
    private val recordId: Long = Screen.Editor.getRecordIdFromRoute(savedStateHandle)

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        if (recordId > 0) {
            loadData()
        } else {
            _uiState.value = EditorUiState.Error("Invalid record ID")
        }
    }

    // ... rest of the code remains the same ...
    
    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading

            try {
                val record = useCases.getRecordById(recordId)
                if (record == null) {
                    _uiState.value = EditorUiState.Error("Record not found")
                    return@launch
                }

                val folder = useCases.getFolderById(record.folderId)
                val folderName = folder?.name ?: "Documents"

                useCases.getDocuments(recordId)
                    .catch { e ->
                        _uiState.value = EditorUiState.Error(
                            "Failed to load documents: ${e.message}"
                        )
                    }
                    .collect { documents ->
                        _uiState.value = EditorUiState.Success(
                            record = record,
                            folderName = folderName,
                            documents = documents
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(
                    "Failed to load data: ${e.message}"
                )
            }
        }
    }

    fun addDocument(imageUri: Uri) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is EditorUiState.Success) {
                _uiState.value = currentState.copy(isProcessing = true)
            }

            useCases.addDocument(recordId, imageUri)
                .catch { e ->
                    _uiState.value = when (val state = _uiState.value) {
                        is EditorUiState.Success -> state.copy(
                            isProcessing = false,
                            errorMessage = "Failed to add document: ${e.message}"
                        )
                        else -> EditorUiState.Error("Failed to add document: ${e.message}")
                    }
                }
                .collect { state ->
                    when (state) {
                        is AddDocumentState.Creating -> {
                            updateProcessingState(state.progress, state.message)
                        }
                        is AddDocumentState.ProcessingOcr -> {
                            updateProcessingState(state.progress, state.message)
                        }
                        is AddDocumentState.Translating -> {
                            updateProcessingState(state.progress, state.message)
                        }
                        is AddDocumentState.Success -> {
                            loadData()
                        }
                        is AddDocumentState.Error -> {
                            val errorMsg = when {
                                state.message.contains("quota", ignoreCase = true) ->
                                    "API quota exceeded. Document saved without translation."
                                state.message.contains("Invalid API key") ->
                                    "Invalid API key. Check settings."
                                else -> state.message
                            }
                            
                            _uiState.value = when (val current = _uiState.value) {
                                is EditorUiState.Success -> current.copy(
                                    isProcessing = false,
                                    errorMessage = errorMsg
                                )
                                else -> EditorUiState.Error(errorMsg)
                            }
                        }
                    }
                }
        }
    }

    private fun updateProcessingState(progress: Int, message: String) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingProgress = progress,
                processingMessage = message
            )
        }
    }

    fun retryOcr(documentId: Long) {
        viewModelScope.launch {
            when (val result = useCases.fixOcr(documentId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    loadData()
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("OCR retry failed: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            when (val result = useCases.retryTranslation(documentId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    loadData()
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    val msg = if (result.exception.message?.contains("quota") == true) {
                        "API quota exceeded. Try later."
                    } else {
                        "Translation failed: ${result.exception.message}"
                    }
                    updateErrorMessage(msg)
                }
                else -> {}
            }
        }
    }

    fun updateRecordName(newName: String) {
        if (newName.isBlank()) return
        
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is EditorUiState.Success) {
                val updated = currentState.record.copy(name = newName.trim())
                
                when (useCases.updateRecord(updated)) {
                    is com.docs.scanner.domain.model.Result.Success -> {
                        _uiState.value = currentState.copy(record = updated)
                    }
                    is com.docs.scanner.domain.model.Result.Error -> {
                        updateErrorMessage("Failed to update name")
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateRecordDescription(newDescription: String?) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is EditorUiState.Success) {
                val updated = currentState.record.copy(
                    description = newDescription?.takeIf { it.isNotBlank() }
                )
                
                when (useCases.updateRecord(updated)) {
                    is com.docs.scanner.domain.model.Result.Success -> {
                        _uiState.value = currentState.copy(record = updated)
                    }
                    is com.docs.scanner.domain.model.Result.Error -> {
                        updateErrorMessage("Failed to update description")
                    }
                    else -> {}
                }
            }
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            when (useCases.deleteDocument(documentId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    loadData()
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to delete document")
                }
                else -> {}
            }
        }
    }

    fun updateDocumentText(documentId: Long, originalText: String?, translatedText: String?) {
        viewModelScope.launch {
            val document = useCases.getDocumentById(documentId)
            if (document == null) {
                updateErrorMessage("Document not found")
                return@launch
            }

            val updated = document.copy(
                originalText = originalText,
                translatedText = translatedText
            )

            when (useCases.updateDocument(updated)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    loadData()
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to update text")
                }
                else -> {}
            }
        }
    }

    fun clearError() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = null)
        }
    }

    private fun updateErrorMessage(message: String) {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = message)
        }
    }
}

sealed interface EditorUiState {
    data object Loading : EditorUiState
    
    data class Success(
        val record: Record,
        val folderName: String,
        val documents: List<Document>,
        val isProcessing: Boolean = false,
        val processingProgress: Int = 0,
        val processingMessage: String = "",
        val errorMessage: String? = null
    ) : EditorUiState
    
    data class Error(val message: String) : EditorUiState
}