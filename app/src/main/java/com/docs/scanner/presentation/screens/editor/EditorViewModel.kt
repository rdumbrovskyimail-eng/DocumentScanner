package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.repository.RecordRepository
import com.docs.scanner.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val getDocumentsUseCase: GetDocumentsUseCase,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val retryTranslationUseCase: RetryTranslationUseCase,
    private val recordRepository: RecordRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: 0L
    
    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    
    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record.asStateFlow()
    
    private var clipboardContent: String? = null
    
    fun loadRecord(recordId: Long) {
        viewModelScope.launch {
            _record.value = recordRepository.getRecordById(recordId)
            
            _uiState.value = EditorUiState.Loading
            
            getDocumentsUseCase(recordId)
                .catch { e ->
                    _uiState.value = EditorUiState.Error(
                        e.message ?: "Failed to load documents"
                    )
                }
                .collect { documents ->
                    _uiState.value = if (documents.isEmpty()) {
                        EditorUiState.Empty
                    } else {
                        EditorUiState.Success(documents)
                    }
                }
        }
    }
    
    fun addDocument(imageUri: Uri) {
        viewModelScope.launch {
            when (val result = addDocumentUseCase(recordId, imageUri)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    _uiState.value = EditorUiState.Error(
                        result.message ?: "Failed to add document"
                    )
                }
                else -> {}
            }
        }
    }
    
    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            deleteDocumentUseCase(documentId)
        }
    }
    
    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            when (val result = retryTranslationUseCase(documentId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    _uiState.value = EditorUiState.Error(
                        result.message ?: "Failed to retry translation"
                    )
                }
                else -> {}
            }
        }
    }
    
    fun copyToClipboard(text: String?) {
        if (text != null) {
            clipboardContent = text
        }
    }
}