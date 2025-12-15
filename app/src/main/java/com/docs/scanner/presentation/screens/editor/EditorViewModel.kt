package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.repository.DocumentRepository
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
    private val fixOcrUseCase: FixOcrUseCase,
    private val documentRepository: DocumentRepository,
    private val recordRepository: RecordRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: 0L
    
    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    
    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record.asStateFlow()
    
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
            addDocumentUseCase(recordId, imageUri)
        }
    }
    
    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            deleteDocumentUseCase(documentId)
        }
    }
    
    fun updateOriginalText(documentId: Long, newText: String) {
        viewModelScope.launch {
            documentRepository.updateOriginalText(documentId, newText)
        }
    }
    
    fun retryOcr(documentId: Long) {
        viewModelScope.launch {
            fixOcrUseCase(documentId)
        }
    }
    
    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            retryTranslationUseCase(documentId)
        }
    }
    
    fun updateRecordName(newName: String) {
        viewModelScope.launch {
            _record.value?.let { rec ->
                val updated = rec.copy(name = newName)
                recordRepository.updateRecord(updated)
                _record.value = updated
            }
        }
    }
    
    fun updateRecordDescription(newDescription: String?) {
        viewModelScope.launch {
            _record.value?.let { rec ->
                val updated = rec.copy(description = newDescription)
                recordRepository.updateRecord(updated)
                _record.value = updated
            }
        }
    }
}