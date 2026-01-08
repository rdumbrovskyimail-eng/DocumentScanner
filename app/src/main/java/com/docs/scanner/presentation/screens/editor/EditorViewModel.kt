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
                useCases.getRecords(record.folderId.value)
                    .catch { /* ignore */ }
                    .collect { records ->
                        _moveTargets.value = records.filter { it.id != record.id }
                    }

                useCases.documents.observeByRecord(RecordId(recordId))
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

    fun addDocument(uri: Uri) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Adding document...",
                processingProgress = 10
            )

            try {
                when (val result = useCases.documents.addFromUri(RecordId(recordId), uri.toString())) {
                    is DomainResult.Success -> {
                        _uiState.value = currentState.copy(
                            isProcessing = true,
                            processingMessage = "Processing OCR...",
                            processingProgress = 50
                        )
                        // Documents will auto-refresh via Flow
                    }
                    is DomainResult.Failure -> {
                        _uiState.value = currentState.copy(
                            isProcessing = false,
                            errorMessage = "Failed to add document: ${result.error.message}"
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

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            when (val result = useCases.documents.delete(DocumentId(documentId))) {
                is DomainResult.Success -> { /* Auto-refresh */ }
                is DomainResult.Failure -> updateErrorMessage("Failed to delete: ${result.error.message}")
            }
        }
    }

    fun moveDocumentUp(documentId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val docs = currentState.documents.toMutableList()
            val index = docs.indexOfFirst { it.id.value == documentId }
            if (index <= 0) return@launch

            val doc = docs.removeAt(index)
            docs.add(index - 1, doc)

            // Update positions
            docs.forEachIndexed { i, d ->
                useCases.documents.updatePosition(d.id, i)
            }
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

            // Update positions
            docs.forEachIndexed { i, d ->
                useCases.documents.updatePosition(d.id, i)
            }
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

    /**
     * Add tag to record via useCases.records.update
     */
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
            when (val result = useCases.records.update(updated)) {
                is DomainResult.Success -> loadData()
                is DomainResult.Failure -> updateErrorMessage("Failed to add tag: ${result.error.message}")
            }
        }
    }

    /**
     * Remove tag from record via useCases.records.update
     */
    fun removeTag(tag: String) {
        val t = tag.trim().lowercase()
        
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch
            
            val currentTags = currentState.record.tags.toMutableList()
            currentTags.remove(t)
            
            val updated = currentState.record.copy(tags = currentTags)
            when (val result = useCases.records.update(updated)) {
                is DomainResult.Success -> loadData()
                is DomainResult.Failure -> updateErrorMessage("Failed to remove tag: ${result.error.message}")
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

    fun updateDocumentText(documentId: Long, originalText: String?, translatedText: String?) {
        viewModelScope.launch {
            when (val result = useCases.documents.updateText(
                DocumentId(documentId), 
                originalText, 
                translatedText
            )) {
                is DomainResult.Success -> { /* Auto-refresh */ }
                is DomainResult.Failure -> updateErrorMessage("Failed to update text: ${result.error.message}")
            }
        }
    }

    fun retryOcr(documentId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Retrying OCR...",
                processingProgress = 30
            )

            when (val result = useCases.documents.retryOcr(DocumentId(documentId))) {
                is DomainResult.Success -> {
                    _uiState.value = currentState.copy(isProcessing = false)
                }
                is DomainResult.Failure -> {
                    _uiState.value = currentState.copy(
                        isProcessing = false,
                        errorMessage = "OCR failed: ${result.error.message}"
                    )
                }
            }
        }
    }

    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Retrying translation...",
                processingProgress = 30
            )

            when (val result = useCases.documents.retryTranslation(DocumentId(documentId))) {
                is DomainResult.Success -> {
                    _uiState.value = currentState.copy(isProcessing = false)
                }
                is DomainResult.Failure -> {
                    _uiState.value = currentState.copy(
                        isProcessing = false,
                        errorMessage = "Translation failed: ${result.error.message}"
                    )
                }
            }
        }
    }

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
                        _shareEvent.emit(ShareEvent.File(result.value, "application/pdf"))
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
                        _shareEvent.emit(ShareEvent.File(result.value, "application/zip"))
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
