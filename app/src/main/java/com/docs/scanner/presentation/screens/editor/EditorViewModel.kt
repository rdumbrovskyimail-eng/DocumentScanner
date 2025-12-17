package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.FolderRepository
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
    private val folderRepository: FolderRepository,  // ✅ ДОБАВЛЕНО
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: -1L
    
    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    
    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record.asStateFlow()
    
    // ✅ ДОБАВЛЕНО: Название папки
    private val _folderName = MutableStateFlow<String?>(null)
    val folderName: StateFlow<String?> = _folderName.asStateFlow()
    
    init {
        if (recordId > 0) {
            loadRecord(recordId)
        } else {
            _uiState.value = EditorUiState.Error("Invalid record ID")
        }
    }
    
    fun loadRecord(recordId: Long) {
        if (recordId <= 0) {
            _uiState.value = EditorUiState.Error("Invalid record ID")
            return
        }
        
        viewModelScope.launch {
            try {
                val record = recordRepository.getRecordById(recordId)
                _record.value = record
                
                // ✅ Загружаем название папки
                record?.let {
                    val folder = folderRepository.getFolderById(it.folderId)
                    _folderName.value = folder?.name
                }
                
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
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(
                    e.message ?: "Failed to load record"
                )
            }
        }
    }
    
    fun addDocument(imageUri: Uri) {
        if (recordId <= 0) {
            println("Cannot add document: invalid record ID")
            return
        }
        
        viewModelScope.launch {
            try {
                addDocumentUseCase(recordId, imageUri)
            } catch (e: Exception) {
                println("Error adding document: ${e.message}")
            }
        }
    }
    
    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            try {
                deleteDocumentUseCase(documentId)
            } catch (e: Exception) {
                println("Error deleting document: ${e.message}")
            }
        }
    }
    
    fun updateOriginalText(documentId: Long, newText: String) {
        viewModelScope.launch {
            try {
                documentRepository.updateOriginalText(documentId, newText)
            } catch (e: Exception) {
                println("Error updating text: ${e.message}")
            }
        }
    }
    
    fun retryOcr(documentId: Long) {
        viewModelScope.launch {
            try {
                fixOcrUseCase(documentId)
            } catch (e: Exception) {
                println("Error retrying OCR: ${e.message}")
            }
        }
    }
    
    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            try {
                retryTranslationUseCase(documentId)
            } catch (e: Exception) {
                println("Error retrying translation: ${e.message}")
            }
        }
    }
    
    fun updateRecordName(newName: String) {
        if (newName.isBlank()) {
            println("Record name cannot be empty")
            return
        }
        
        viewModelScope.launch {
            try {
                _record.value?.let { rec ->
                    val updated = rec.copy(name = newName)
                    recordRepository.updateRecord(updated)
                    _record.value = updated
                }
            } catch (e: Exception) {
                println("Error updating record name: ${e.message}")
            }
        }
    }
    
    fun updateRecordDescription(newDescription: String?) {
        viewModelScope.launch {
            try {
                _record.value?.let { rec ->
                    val updated = rec.copy(description = newDescription)
                    recordRepository.updateRecord(updated)
                    _record.value = updated
                }
            } catch (e: Exception) {
                println("Error updating record description: ${e.message}")
            }
        }
    }
}
