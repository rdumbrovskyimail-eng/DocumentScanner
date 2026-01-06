package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.DocumentId
import com.docs.scanner.domain.core.FolderId
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.RecordId
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

    private val _shareEvent = MutableSharedFlow<ShareEvent>()
    val shareEvent: SharedFlow<ShareEvent> = _shareEvent.asSharedFlow()

    /**
     * Candidate records to move documents into (same folder).
     * Excludes current record.
     */
    val moveTargets: StateFlow<List<Record>> =
        uiState
            .map { it as? EditorUiState.Success }
            .distinctUntilChangedBy { it?.record?.id?.value }
            .flatMapLatest { state ->
                val record = state?.record ?: return@flatMapLatest flowOf(emptyList())
                useCases.records.observeByFolder(FolderId(record.folderId.value))
                    .map { list -> list.filter { it.id.value != record.id.value } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

                val folder = useCases.getFolderById(record.folderId.value)
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

    fun moveDocument(documentId: Long, targetRecordId: Long) {
        if (documentId <= 0 || targetRecordId <= 0) return
        viewModelScope.launch {
            when (val result = useCases.documents.move(DocumentId(documentId), RecordId(targetRecordId))) {
                is DomainResult.Success -> loadData()
                is DomainResult.Failure -> updateErrorMessage("Failed to move page: ${result.error.message}")
            }
        }
    }

    fun moveDocumentUp(documentId: Long) {
        val state = _uiState.value as? EditorUiState.Success ?: return
        val ids = state.documents.map { it.id.value }
        val index = ids.indexOf(documentId)
        if (index <= 0) return

        val swapped = ids.toMutableList().apply {
            val tmp = this[index - 1]
            this[index - 1] = this[index]
            this[index] = tmp
        }

        viewModelScope.launch {
            when (val result = useCases.documents.reorder(RecordId(recordId), swapped.map { DocumentId(it) })) {
                is DomainResult.Success -> Unit // flow will update
                is DomainResult.Failure -> updateErrorMessage("Failed to reorder: ${result.error.message}")
            }
        }
    }

    fun moveDocumentDown(documentId: Long) {
        val state = _uiState.value as? EditorUiState.Success ?: return
        val ids = state.documents.map { it.id.value }
        val index = ids.indexOf(documentId)
        if (index == -1 || index >= ids.lastIndex) return

        val swapped = ids.toMutableList().apply {
            val tmp = this[index + 1]
            this[index + 1] = this[index]
            this[index] = tmp
        }

        viewModelScope.launch {
            when (val result = useCases.documents.reorder(RecordId(recordId), swapped.map { DocumentId(it) })) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> updateErrorMessage("Failed to reorder: ${result.error.message}")
            }
        }
    }

    fun updateLanguages(source: Language, target: Language) {
        viewModelScope.launch {
            when (val result = useCases.records.updateLanguage(RecordId(recordId), source, target)) {
                is DomainResult.Success -> loadData()
                is DomainResult.Failure -> updateErrorMessage(result.error.message)
            }
        }
    }

    fun addTag(tag: String) {
        val t = tag.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            when (val result = useCases.records.addTag(RecordId(recordId), t)) {
                is DomainResult.Success -> loadData()
                is DomainResult.Failure -> updateErrorMessage("Failed to add tag: ${result.error.message}")
            }
        }
    }

    fun removeTag(tag: String) {
        val t = tag.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            when (val result = useCases.records.removeTag(RecordId(recordId), t)) {
                is DomainResult.Success -> loadData()
                is DomainResult.Failure -> updateErrorMessage("Failed to remove tag: ${result.error.message}")
            }
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

    fun shareRecordAsPdf() {
        val state = _uiState.value as? EditorUiState.Success ?: return
        viewModelScope.launch {
            val ids = state.documents.map { it.id }
            when (val r = useCases.export.shareDocuments(ids, asPdf = true)) {
                is DomainResult.Success -> _shareEvent.emit(
                    ShareEvent.File(path = r.data, mimeType = "application/pdf")
                )
                is DomainResult.Failure -> updateErrorMessage("Failed to export PDF: ${r.error.message}")
            }
        }
    }

    fun shareRecordImagesZip() {
        val state = _uiState.value as? EditorUiState.Success ?: return
        viewModelScope.launch {
            val ids = state.documents.map { it.id }
            when (val r = useCases.export.shareDocuments(ids, asPdf = false)) {
                is DomainResult.Success -> _shareEvent.emit(
                    ShareEvent.File(path = r.data, mimeType = "application/zip")
                )
                is DomainResult.Failure -> updateErrorMessage("Failed to export images: ${r.error.message}")
            }
        }
    }

    fun shareSingleImage(path: String) {
        viewModelScope.launch {
            _shareEvent.emit(ShareEvent.File(path = path, mimeType = "image/jpeg"))
        }
    }
}

sealed interface ShareEvent {
    data class File(val path: String, val mimeType: String) : ShareEvent
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