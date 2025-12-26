package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.model.Result
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
    private val recordRepository: RecordRepository,
    private val folderRepository: FolderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: -1L

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record.asStateFlow()

    private val _folderName = MutableStateFlow<String?>("Documents")
    val folderName: StateFlow<String?> = _folderName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        if (recordId > 0) loadRecord(recordId)
    }

    fun loadRecord(recordId: Long) {
        viewModelScope.launch {
            val record = recordRepository.getRecordById(recordId) ?: run {
                _uiState.value = EditorUiState.Error("Record not found")
                return@launch
            }

            _record.value = record

            val folder = folderRepository.getFolderById(record.folderId)
            _folderName.value = folder?.name ?: "Documents"

            getDocumentsUseCase(recordId)
                .catch { e ->
                    _errorMessage.value = "Failed to load documents: ${e.message}"
                    _uiState.value = EditorUiState.Error(e.message ?: "Load error")
                }
                .collect { documents ->
                    _uiState.value = if (documents.isEmpty()) EditorUiState.Empty else EditorUiState.Success(documents)
                }
        }
    }

    fun addDocument(imageUri: Uri) {
        viewModelScope.launch {
            when (val result = addDocumentUseCase(recordId, imageUri)) {
                is Result.Success -> {
                    // Успех — обновляем список
                    loadRecord(recordId)
                }
                is Result.Error -> {
                    val msg = when {
                        result.exception.message?.contains("quota", ignoreCase = true) == true ->
                            "API quota exceeded. Document saved without translation."
                        result.exception.message?.contains("Invalid API key") == true ->
                            "Invalid API key. Check settings."
                        else -> "Failed to add document: ${result.exception.message}"
                    }
                    _errorMessage.value = msg
                }
            }
        }
    }

    fun retryOcr(documentId: Long) {
        viewModelScope.launch {
            when (val result = fixOcrUseCase(documentId)) {
                is Result.Success -> loadRecord(recordId)
                is Result.Error -> _errorMessage.value = "OCR retry failed: ${result.exception.message}"
            }
        }
    }

    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            when (val result = retryTranslationUseCase(documentId)) {
                is Result.Success -> loadRecord(recordId)
                is Result.Error -> {
                    val msg = if (result.exception.message?.contains("quota") == true) {
                        "API quota exceeded. Try later."
                    } else {
                        "Translation failed: ${result.exception.message}"
                    }
                    _errorMessage.value = msg
                }
            }
        }
    }

    fun updateRecordName(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            _record.value?.let {
                val updated = it.copy(name = newName.trim())
                recordRepository.updateRecord(updated)
                _record.value = updated
            }
        }
    }

    fun updateRecordDescription(newDescription: String?) {
        viewModelScope.launch {
            _record.value?.let {
                val updated = it.copy(description = newDescription?.takeIf { it.isNotBlank() })
                recordRepository.updateRecord(updated)
                _record.value = updated
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}