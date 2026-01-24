/*
 * EditorViewModel.kt
 * Version: 7.2.0 - MODEL CONSTANTS INTEGRATION (2026)
 * 
 * ✅ CRITICAL FIX (Session 14):
 * - Uses ModelConstants via GeminiModelManager
 * - Reads targetLanguage from SettingsDataStore
 * - Reads translationModel from GeminiModelManager
 * - Removed Timber.forest().isNotEmpty() checks (redundant)
 * 
 * ✅ PREVIOUS FIXES:
 * - Settings synchronization (v3.0.0)
 * - Auto-translation uses correct language from Settings
 * - Fixed: "я поставил язык русский, а вот перевелось на АНГЛИЙСКИЙ!"
 */

package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.preferences.GeminiModelManager
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.domain.core.*
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.AddDocumentState
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val useCases: AllUseCases,
    private val settingsDataStore: SettingsDataStore,
    private val modelManager: GeminiModelManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "EditorViewModel"
    }

    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: 0L

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _shareEvent = MutableSharedFlow<ShareEvent>()
    val shareEvent: SharedFlow<ShareEvent> = _shareEvent.asSharedFlow()

    private val _moveTargets = MutableStateFlow<List<Record>>(emptyList())
    val moveTargets: StateFlow<List<Record>> = _moveTargets.asStateFlow()
    
    private val _selectedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDocIds: StateFlow<Set<Long>> = _selectedDocIds.asStateFlow()
    
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    val failedDocumentsCount: StateFlow<Int> = _uiState.map { state ->
        when (state) {
            is EditorUiState.Success -> state.documents.count { it.processingStatus.isFailed }
            else -> 0
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    val selectedCount: Int get() = _selectedDocIds.value.size

    private val targetLanguage = settingsDataStore.translationTarget
        .map { code ->
            Language.fromCode(code) ?: Language.ENGLISH.also {
                Timber.w("⚠️ Invalid target language code: $code, using English")
            }
        }
        .stateIn(
            viewModelScope, 
            SharingStarted.Lazily, 
            Language.ENGLISH
        )
    
    private val translationModel = flow {
        emit(modelManager.getGlobalTranslationModel())
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        ModelConstants.DEFAULT_TRANSLATION_MODEL
    )
    
    private val autoTranslateEnabled = settingsDataStore.autoTranslate
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            false
        )

    init {
        if (recordId != 0L) {
            loadData()
            
            viewModelScope.launch {
                val target = targetLanguage.value
                val model = translationModel.value
                val autoTranslate = autoTranslateEnabled.value
                
                Timber.d("📋 Editor Settings:")
                Timber.d("   ├─ Target Language: ${target.displayName} (${target.code})")
                Timber.d("   ├─ Translation Model: $model")
                Timber.d("   └─ Auto-translate: $autoTranslate")
            }
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
    
    fun addDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            addDocument(uris.first())
            return
        }
        
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch
            
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Adding documents...",
                processingProgress = 0
            )
            
            val result = useCases.batch.addDocuments(
                recordId = RecordId(recordId),
                imageUris = uris.map { it.toString() },
                onProgress = { done, total ->
                    val progress = (done * 50) / total
                    _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                        processingMessage = "Saving images ($done/$total)...",
                        processingProgress = progress
                    ) ?: return@addDocuments
                }
            )
            
            if (result.successful.isNotEmpty()) {
                _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                    processingMessage = "Processing OCR...",
                    processingProgress = 50
                ) ?: return@launch
                
                useCases.batch.processDocuments(
                    docIds = result.successful,
                    onProgress = { done, total ->
                        val progress = 50 + (done * 50) / total
                        _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                            processingMessage = "Processing ($done/$total)...",
                            processingProgress = progress
                        ) ?: return@processDocuments
                    }
                )
            }
            
            _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                isProcessing = false,
                errorMessage = if (result.isFullSuccess) null else "${result.failedCount} documents failed"
            ) ?: return@launch
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

    fun reorderDocuments(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return
        
        val currentDocs = currentState.documents.toMutableList()
        if (fromIndex < 0 || fromIndex >= currentDocs.size ||
            toIndex < 0 || toIndex >= currentDocs.size) return
        
        val item = currentDocs.removeAt(fromIndex)
        currentDocs.add(toIndex, item)
        
        _uiState.value = currentState.copy(documents = currentDocs)
        
        viewModelScope.launch {
            val docIds = currentDocs.map { it.id }
            useCases.documents.reorder(RecordId(recordId), docIds)
                .onFailure { error ->
                    updateErrorMessage("Failed to reorder: ${error.message}")
                    loadData()
                }
        }
    }
    
    fun moveDocumentUp(documentId: Long) {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return
        
        val docs = currentState.documents
        val index = docs.indexOfFirst { it.id.value == documentId }
        if (index > 0) {
            reorderDocuments(index, index - 1)
        }
    }
    
    fun moveDocumentDown(documentId: Long) {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return
        
        val docs = currentState.documents
        val index = docs.indexOfFirst { it.id.value == documentId }
        if (index >= 0 && index < docs.lastIndex) {
            reorderDocuments(index, index + 1)
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

    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Retrying translation...",
                processingProgress = 30
            )

            val target = targetLanguage.value
            val model = translationModel.value
            
            Timber.d("🌐 Retrying translation:")
            Timber.d("   ├─ Target: ${target.displayName} (${target.code})")
            Timber.d("   └─ Model: $model")

            when (val result = useCases.translation.translateDocument(
                docId = DocumentId(documentId),
                targetLang = target
            )) {
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

    fun enterSelectionMode() {
        _isSelectionMode.value = true
    }
    
    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedDocIds.value = emptySet()
    }
    
    fun toggleDocumentSelection(documentId: Long) {
        val current = _selectedDocIds.value.toMutableSet()
        if (current.contains(documentId)) {
            current.remove(documentId)
        } else {
            current.add(documentId)
        }
        _selectedDocIds.value = current
        
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }
    
    fun selectAll() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _selectedDocIds.value = currentState.documents.map { it.id.value }.toSet()
        }
    }
    
    fun deselectAll() {
        _selectedDocIds.value = emptySet()
    }

    fun deleteSelectedDocuments() {
        val docIds = _selectedDocIds.value.toList()
        if (docIds.isEmpty()) return
        
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch
            
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Deleting...",
                processingProgress = 0
            )
            
            val result = useCases.batch.deleteDocuments(
                docIds = docIds.map { DocumentId(it) },
                onProgress = { done, total ->
                    _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                        processingProgress = (done * 100) / total
                    ) ?: return@deleteDocuments
                }
            )
            
            _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                isProcessing = false
            ) ?: return@launch
            
            exitSelectionMode()
        }
    }
    
    fun exportSelectedDocuments(asPdf: Boolean) {
        val docIds = _selectedDocIds.value.toList()
        if (docIds.isEmpty()) return
        
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch
            
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = if (asPdf) "Generating PDF..." else "Creating ZIP...",
                processingProgress = 20
            )
            
            when (val result = useCases.export.shareDocuments(
                docIds = docIds.map { DocumentId(it) },
                asPdf = asPdf
            )) {
                is DomainResult.Success -> {
                    _uiState.value = currentState.copy(isProcessing = false)
                    _shareEvent.emit(ShareEvent.File(
                        result.data, 
                        if (asPdf) "application/pdf" else "application/zip"
                    ))
                    exitSelectionMode()
                }
                is DomainResult.Failure -> {
                    _uiState.value = currentState.copy(
                        isProcessing = false,
                        errorMessage = "Export failed: ${result.error.message}"
                    )
                }
            }
        }
    }
    
    fun moveSelectedToRecord(targetRecordId: Long) {
        val docIds = _selectedDocIds.value.toList()
        if (docIds.isEmpty()) return
        
        viewModelScope.launch {
            var successCount = 0
            docIds.forEach { docId ->
                useCases.documents.move(DocumentId(docId), RecordId(targetRecordId))
                    .onSuccess { successCount++ }
            }
            exitSelectionMode()
        }
    }

    fun retryFailedDocuments() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return
        
        val failedDocs = currentState.documents.filter { it.processingStatus.isFailed }
        if (failedDocs.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Retrying failed...",
                processingProgress = 0
            )
            
            useCases.batch.processDocuments(
                docIds = failedDocs.map { it.id },
                onProgress = { done, total ->
                    _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                        processingMessage = "Processing ($done/$total)...",
                        processingProgress = (done * 100) / total
                    ) ?: return@processDocuments
                }
            )
            
            _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                isProcessing = false
            ) ?: return@launch
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