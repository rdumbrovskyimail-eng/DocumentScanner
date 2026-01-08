package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.DocumentId
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.RecordId
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.AddDocumentState
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val useCases: AllUseCases,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: 0L

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _shareEvent = MutableSharedFlow<ShareEvent>()
    val shareEvent: SharedFlow<ShareEvent> = _shareEvent.asSharedFlow()

    private val _moveTargets = MutableStateFlow<List<Record>>(emptyList())
    val moveTargets: StateFlow<List<Record>> = _moveTargets.asStateFlow()

    init {
        if (recordId != 0L) {
            loadData()
        } else {
            _uiState.value = EditorUiState.Error("Invalid record ID")
        }
    }

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
                val folderName = folder?.name ?: ""

                // Load move targets (other records in same folder)
                launch {
                    useCases.getRecords(record.folderId.value)
                        .catch { /* ignore */ }
                        .collect { records ->
                            _moveTargets.value = records.filter { it.id.value != recordId }
                        }
                }

                useCases.getDocuments(recordId)
                    .catch { e ->
                        _uiState.value = EditorUiState.Error("Failed to load documents: ${e.message}")
                    }
                    .collect { documents ->
                        val currentRecord = useCases.getRecordById(recordId) ?: record
                        _uiState.value = EditorUiState.Success(
                            record = currentRecord,
                            folderName = folderName,
                            documents = documents.sortedBy { it.position }
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error("Failed to load data: ${e.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADD DOCUMENT - использует useCases.addDocument(recordId, uri)
    // ══════════════════════════════════════════════════════════════════════════
    
    fun addDocument(uri: Uri) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            useCases.addDocument(recordId, uri)
                .collect { state ->
                    when (state) {
                        is AddDocumentState.Creating -> {
                            _uiState.value = currentState.copy(
                                isProcessing = true,
                                processingMessage = state.message,
                                processingProgress = state.progress
                            )
                        }
                        is AddDocumentState.ProcessingOcr -> {
                            _uiState.value = currentState.copy(
                                isProcessing = true,
                                processingMessage = state.message,
                                processingProgress = state.progress
                            )
                        }
                        is AddDocumentState.Translating -> {
                            _uiState.value = currentState.copy(
                                isProcessing = true,
                                processingMessage = state.message,
                                processingProgress = state.progress
                            )
                        }
                        is AddDocumentState.Success -> {
                            _uiState.value = currentState.copy(isProcessing = false)
                            // Documents auto-refresh via Flow
                        }
                        is AddDocumentState.Error -> {
                            _uiState.value = currentState.copy(
                                isProcessing = false,
                                errorMessage = state.message
                            )
                        }
                    }
                }
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            when (val result = useCases.deleteDocument(documentId)) {
                is com.docs.scanner.domain.model.Result.Success -> { /* Auto-refresh */ }
                is com.docs.scanner.domain.model.Result.Error -> 
                    updateErrorMessage("Failed to delete: ${result.exception.message}")
                else -> {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REORDER - использует useCases.documents.reorder(recordId, docIds)
    // ══════════════════════════════════════════════════════════════════════════

    fun moveDocumentUp(documentId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val docs = currentState.documents.toMutableList()
            val index = docs.indexOfFirst { it.id.value == documentId }
            if (index <= 0) return@launch

            val doc = docs.removeAt(index)
            docs.add(index - 1, doc)

            // Reorder via use case
            val docIds = docs.map { it.id }
            useCases.documents.reorder(RecordId(recordId), docIds)
        }
    }

    fun moveDocumentDown(documentId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val docs = currentState.documents.toMutableList()
            val index = docs.indexOfFirst { it.id.value == documentId }
            if (index < 0 || index >= docs.lastIndex) return@launch

            val doc = docs.removeAt(index)
            docs.add(index + 1, doc)

            // Reorder via use case
            val docIds = docs.map { it.id }
            useCases.documents.reorder(RecordId(recordId), docIds)
        }
    }

    fun updateRecordName(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val updated = currentState.record.copy(name = name.trim())
            when (val result = useCases.updateRecord(updated)) {
                is com.docs.scanner.domain.model.Result.Success -> loadData()
                is com.docs.scanner.domain.model.Result.Error -> 
                    updateErrorMessage("Failed to update: ${result.exception.message}")
                else -> {}
            }
        }
    }

    fun updateRecordDescription(description: String?) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val updated = currentState.record.copy(description = description)
            when (val result = useCases.updateRecord(updated)) {
                is com.docs.scanner.domain.model.Result.Success -> loadData()
                is com.docs.scanner.domain.model.Result.Error -> 
                    updateErrorMessage("Failed to update: ${result.exception.message}")
                else -> {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAGS - через useCases.updateRecord с измененным списком тегов
    // ══════════════════════════════════════════════════════════════════════════

    fun addTag(tag: String) {
        val t = tag.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (t.isBlank()) {
            updateErrorMessage("Invalid tag format")
            return
        }

        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch
            
            val currentTags = currentState.record.tags.toMutableList()
            if (currentTags.contains(t)) {
                updateErrorMessage("Tag already exists")
                return@launch
            }
            currentTags.add(t)
            
            val updated = currentState.record.copy(tags = currentTags)
            when (val result = useCases.updateRecord(updated)) {
                is com.docs.scanner.domain.model.Result.Success -> loadData()
                is com.docs.scanner.domain.model.Result.Error -> 
                    updateErrorMessage("Failed to add tag: ${result.exception.message}")
                else -> {}
            }
        }
    }

    fun removeTag(tag: String) {
        val t = tag.trim().lowercase()
        
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch
            
            val currentTags = currentState.record.tags.toMutableList()
            currentTags.remove(t)
            
            val updated = currentState.record.copy(tags = currentTags)
            when (val result = useCases.updateRecord(updated)) {
                is com.docs.scanner.domain.model.Result.Success -> loadData()
                is com.docs.scanner.domain.model.Result.Error -> 
                    updateErrorMessage("Failed to remove tag: ${result.exception.message}")
                else -> {}
            }
        }
    }

    fun updateLanguages(source: Language, target: Language) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val updated = currentState.record.copy(
                sourceLanguage = source,
                targetLanguage = target
            )
            when (val result = useCases.updateRecord(updated)) {
                is com.docs.scanner.domain.model.Result.Success -> loadData()
                is com.docs.scanner.domain.model.Result.Error -> 
                    updateErrorMessage("Failed to update languages: ${result.exception.message}")
                else -> {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UPDATE DOCUMENT TEXT - через useCases.updateDocument(doc)
    // ══════════════════════════════════════════════════════════════════════════

    fun updateDocumentText(documentId: Long, originalText: String?, translatedText: String?) {
        viewModelScope.launch {
            val doc = useCases.getDocumentById(documentId) ?: return@launch
            
            val updated = doc.copy(
                originalText = originalText,
                translatedText = translatedText
            )
            
            when (val result = useCases.updateDocument(updated)) {
                is com.docs.scanner.domain.model.Result.Success -> { /* Auto-refresh */ }
                is com.docs.scanner.domain.model.Result.Error -> 
                    updateErrorMessage("Failed to update text: ${result.exception.message}")
                else -> {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RETRY OCR - через useCases.fixOcr(documentId)
    // ══════════════════════════════════════════════════════════════════════════

    fun retryOcr(documentId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Retrying OCR...",
                processingProgress = 30
            )

            when (val result = useCases.fixOcr(documentId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    _uiState.value = currentState.copy(isProcessing = false)
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    _uiState.value = currentState.copy(
                        isProcessing = false,
                        errorMessage = "OCR failed: ${result.exception.message}"
                    )
                }
                else -> {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RETRY TRANSLATION - через useCases.retryTranslation(documentId)
    // ══════════════════════════════════════════════════════════════════════════

    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Retrying translation...",
                processingProgress = 30
            )

            when (val result = useCases.retryTranslation(documentId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    _uiState.value = currentState.copy(isProcessing = false)
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    _uiState.value = currentState.copy(
                        isProcessing = false,
                        errorMessage = "Translation failed: ${result.exception.message}"
                    )
                }
                else -> {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MOVE DOCUMENT - через useCases.documents.move(id, toRecord)
    // ══════════════════════════════════════════════════════════════════════════

    fun moveDocument(documentId: Long, targetRecordId: Long) {
        viewModelScope.launch {
            when (val result = useCases.documents.move(
                DocumentId(documentId),
                RecordId(targetRecordId)
            )) {
                is DomainResult.Success -> { /* Auto-refresh */ }
                is DomainResult.Failure -> updateErrorMessage("Failed to move: ${result.error.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SHARE - через useCases.export
    // ══════════════════════════════════════════════════════════════════════════

    fun shareRecordAsPdf() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Generating PDF...",
                processingProgress = 20
            )

            try {
                val docIds = currentState.documents.map { it.id }
                val outputPath = "share_${System.currentTimeMillis()}.pdf"
                
                when (val result = useCases.export.exportToPdf(docIds, outputPath)) {
                    is DomainResult.Success -> {
                        _uiState.value = currentState.copy(isProcessing = false)
                        _shareEvent.emit(ShareEvent.File(result.data, "application/pdf"))
                    }
                    is DomainResult.Failure -> {
                        _uiState.value = currentState.copy(
                            isProcessing = false,
                            errorMessage = "PDF generation failed: ${result.error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isProcessing = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun shareRecordImagesZip() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Creating ZIP...",
                processingProgress = 20
            )

            try {
                val docIds = currentState.documents.map { it.id }
                
                when (val result = useCases.export.shareDocuments(docIds, asPdf = false)) {
                    is DomainResult.Success -> {
                        _uiState.value = currentState.copy(isProcessing = false)
                        _shareEvent.emit(ShareEvent.File(result.data, "application/zip"))
                    }
                    is DomainResult.Failure -> {
                        _uiState.value = currentState.copy(
                            isProcessing = false,
                            errorMessage = "ZIP creation failed: ${result.error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isProcessing = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun shareSingleImage(imagePath: String) {
        viewModelScope.launch {
            _shareEvent.emit(ShareEvent.File(imagePath, "image/*"))
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
        val processingMessage: String = "",
        val processingProgress: Int = 0,
        val errorMessage: String? = null
    ) : EditorUiState

    data class Error(val message: String) : EditorUiState
}

sealed interface ShareEvent {
    data class File(val path: String, val mimeType: String) : ShareEvent
}