/*
 * EditorViewModel.kt
 * Version: 7.0.0 - PRODUCTION READY (2026) - 100% FIXED
 */

package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.preferences.GeminiModelManager
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.remote.gemini.GeminiApi
import com.docs.scanner.domain.core.*
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.usecase.AddDocumentState
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val useCases: AllUseCases,
    private val settingsDataStore: SettingsDataStore,
    private val modelManager: GeminiModelManager,
    private val geminiApi: GeminiApi,
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
    
    private val translationModel = settingsDataStore.translationModel
        .stateIn(
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
    
    private val _selectedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDocIds: StateFlow<Set<Long>> = _selectedDocIds.asStateFlow()
    
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    data class OcrSettingsSnapshot(
        val confidenceThreshold: Float = 0.7f,
        val geminiEnabled: Boolean = true
    )
    
    private val _ocrSettings = MutableStateFlow(OcrSettingsSnapshot())
    val ocrSettings: StateFlow<OcrSettingsSnapshot> = _ocrSettings.asStateFlow()
    
    data class TextEditHistoryItem(
        val documentId: Long,
        val field: TextEditField,
        val previousValue: String?,
        val newValue: String?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    enum class TextEditField { OCR_TEXT, TRANSLATED_TEXT }
    
    private val _editHistory = MutableStateFlow<List<TextEditHistoryItem>>(emptyList())
    val editHistory: StateFlow<List<TextEditHistoryItem>> = _editHistory.asStateFlow()
    
    private val maxHistorySize = 20
    
    private val _inlineEditingDocId = MutableStateFlow<Long?>(null)
    val inlineEditingDocId: StateFlow<Long?> = _inlineEditingDocId.asStateFlow()
    
    private val _inlineEditingField = MutableStateFlow<TextEditField?>(null)
    val inlineEditingField: StateFlow<TextEditField?> = _inlineEditingField.asStateFlow()
    
    private var pendingInlineChanges: Pair<Long, String>? = null
    private var autoSaveJob: Job? = null
    
    private val _confidenceTooltip = MutableStateFlow<Pair<String, Float>?>(null)
    val confidenceTooltip: StateFlow<Pair<String, Float>?> = _confidenceTooltip.asStateFlow()
    
    val failedDocumentsCount: StateFlow<Int> = _uiState.map { state ->
        when (state) {
            is EditorUiState.Success -> state.documents.count { it.processingStatus.isFailed }
            else -> 0
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    val selectedCount: StateFlow<Int> = _selectedDocIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    val canUndo: StateFlow<Boolean> = _editHistory.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    init {
        if (recordId != 0L) {
            loadData()
            loadOcrSettings()
            
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

    private fun loadOcrSettings() {
        viewModelScope.launch {
            settingsDataStore.confidenceThreshold.collect { threshold ->
                _ocrSettings.value = _ocrSettings.value.copy(confidenceThreshold = threshold)
            }
        }
        viewModelScope.launch {
            settingsDataStore.geminiOcrEnabled.collect { enabled ->
                _ocrSettings.value = _ocrSettings.value.copy(geminiEnabled = enabled)
            }
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
                is Result.Success -> { /* Auto-refresh */ }
                is Result.Error -> 
                    updateErrorMessage("Failed to delete: ${result.exception.message}")
                else -> {}
            }
        }
    }

    fun reorderDocuments(fromIndex: Int, toIndex: Int) {
        if (_isSelectionMode.value) return
        
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
        if (index >= 0 && index < docs.size - 1) {
            reorderDocuments(index, index + 1)
        }
    }

    fun updateRecordName(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val updated = currentState.record.copy(name = name.trim())
            when (val result = useCases.updateRecord(updated)) {
                is Result.Success -> loadData()
                is Result.Error -> 
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
                is Result.Success -> loadData()
                is Result.Error -> 
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
                is Result.Success -> loadData()
                is Result.Error -> 
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
                is Result.Success -> loadData()
                is Result.Error -> 
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
                is Result.Success -> loadData()
                is Result.Error -> 
                    updateErrorMessage("Failed to update languages: ${result.exception.message}")
                else -> {}
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

    fun updateDocumentText(documentId: Long, originalText: String?, translatedText: String?) {
        viewModelScope.launch {
            val doc = useCases.getDocumentById(documentId) ?: return@launch
            
            val finalOriginalText = originalText ?: doc.originalText
            val finalTranslatedText = translatedText ?: doc.translatedText
            
            if (originalText != null && originalText != doc.originalText) {
                addToHistory(documentId, TextEditField.OCR_TEXT, doc.originalText, originalText)
            }
            if (translatedText != null && translatedText != doc.translatedText) {
                addToHistory(documentId, TextEditField.TRANSLATED_TEXT, doc.translatedText, translatedText)
            }
            
            val updated = doc.copy(
                originalText = finalOriginalText,
                translatedText = finalTranslatedText
            )
            
            when (val result = useCases.updateDocument(updated)) {
                is Result.Success -> { /* Auto-refresh */ }
                is Result.Error -> 
                    updateErrorMessage("Failed to update text: ${result.exception.message}")
                else -> {}
            }
        }
    }

    fun startInlineEditOcr(documentId: Long) {
        _inlineEditingDocId.value = documentId
        _inlineEditingField.value = TextEditField.OCR_TEXT
    }

    fun startInlineEditTranslation(documentId: Long) {
        _inlineEditingDocId.value = documentId
        _inlineEditingField.value = TextEditField.TRANSLATED_TEXT
    }

    fun updateInlineText(text: String) {
        val docId = _inlineEditingDocId.value ?: return
        pendingInlineChanges = docId to text
        
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1500)
            saveInlineChanges()
        }
    }

    fun saveInlineChanges() {
        val (docId, text) = pendingInlineChanges ?: return
        val field = _inlineEditingField.value ?: return
        
        viewModelScope.launch {
            val doc = useCases.getDocumentById(docId) ?: return@launch
            
            val previousValue = when (field) {
                TextEditField.OCR_TEXT -> doc.originalText
                TextEditField.TRANSLATED_TEXT -> doc.translatedText
            }
            addToHistory(docId, field, previousValue, text)
            
            val updated = when (field) {
                TextEditField.OCR_TEXT -> doc.copy(originalText = text)
                TextEditField.TRANSLATED_TEXT -> doc.copy(translatedText = text)
            }
            
            useCases.updateDocument(updated)
        }
        
        pendingInlineChanges = null
        _inlineEditingDocId.value = null
        _inlineEditingField.value = null
    }

    fun undoLastEdit() {
        val history = _editHistory.value.toMutableList()
        if (history.isEmpty()) return
        
        val lastEdit = history.removeAt(history.size - 1)
        _editHistory.value = history
        
        viewModelScope.launch {
            val doc = useCases.getDocumentById(lastEdit.documentId) ?: return@launch
            
            val restored = when (lastEdit.field) {
                TextEditField.OCR_TEXT -> doc.copy(originalText = lastEdit.previousValue)
                TextEditField.TRANSLATED_TEXT -> doc.copy(translatedText = lastEdit.previousValue)
            }
            
            useCases.updateDocument(restored)
        }
    }

    private fun addToHistory(
        documentId: Long,
        field: TextEditField,
        previousValue: String?,
        newValue: String?
    ) {
        val history = _editHistory.value.toMutableList()
        history.add(TextEditHistoryItem(documentId, field, previousValue, newValue))
        while (history.size > maxHistorySize) {
            history.removeAt(0)
        }
        _editHistory.value = history
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
                is Result.Success -> {
                    _uiState.value = currentState.copy(isProcessing = false)
                }
                is Result.Error -> {
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

            val doc = useCases.getDocumentById(documentId)
            if (doc == null) {
                updateErrorMessage("Document not found")
                return@launch
            }
            if (doc.originalText.isNullOrBlank()) {
                updateErrorMessage("No OCR text to translate")
                return@launch
            }

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

    fun retryAllOcr() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return
        if (currentState.documents.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Retrying all OCR...",
                processingProgress = 0
            )
            
            val total = currentState.documents.size
            currentState.documents.forEachIndexed { index, doc ->
                useCases.fixOcr(doc.id.value)
                _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                    processingProgress = ((index + 1) * 100) / total
                ) ?: return@launch
            }
            
            _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                isProcessing = false
            ) ?: return@launch
        }
    }

    fun retryAllTranslation() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return
        if (currentState.documents.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Retrying all translations...",
                processingProgress = 0
            )
            
            val target = targetLanguage.value
            val total = currentState.documents.size
            var failedCount = 0
            
            Timber.d("🌐 Retrying all translations to: ${target.displayName}")
            
            currentState.documents.forEachIndexed { index, doc ->
                when (useCases.translation.translateDocument(
                    docId = doc.id,
                    targetLang = target
                )) {
                    is DomainResult.Failure -> failedCount++
                    else -> {}
                }
                
                _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                    processingProgress = ((index + 1) * 100) / total
                ) ?: return@launch
            }
            
            _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                isProcessing = false,
                errorMessage = if (failedCount > 0) "$failedCount translations failed" else null
            ) ?: return@launch
        }
    }

    fun moveDocument(documentId: Long, targetRecordId: Long) {
        if (targetRecordId == recordId) {
            updateErrorMessage("Document is already in this record")
            return
        }
        
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
                processingMessage = "Deleting ${docIds.size} pages...",
                processingProgress = 0
            )
            
            useCases.batch.deleteDocuments(
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
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch
            
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "Moving ${docIds.size} pages...",
                processingProgress = 0
            )
            
            var done = 0
            docIds.forEach { docId ->
                useCases.documents.move(DocumentId(docId), RecordId(targetRecordId))
                    .onSuccess { done++ }
                
                _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                    processingProgress = (done * 100) / docIds.size
                ) ?: return@launch
            }
            
            _uiState.value = (_uiState.value as? EditorUiState.Success)?.copy(
                isProcessing = false
            ) ?: return@launch
            
            exitSelectionMode()
        }
    }

    fun pasteText(documentId: Long, pastedText: String, isOcrText: Boolean) {
        viewModelScope.launch {
            val doc = useCases.getDocumentById(documentId) ?: return@launch
            
            val field = if (isOcrText) TextEditField.OCR_TEXT else TextEditField.TRANSLATED_TEXT
            val previousValue = if (isOcrText) doc.originalText else doc.translatedText
            addToHistory(documentId, field, previousValue, pastedText)
            
            val updated = if (isOcrText) {
                doc.copy(originalText = pastedText)
            } else {
                doc.copy(translatedText = pastedText)
            }
            
            useCases.updateDocument(updated)
        }
    }

    fun aiRewriteText(documentId: Long, text: String, isOcrText: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch
            
            _uiState.value = currentState.copy(
                isProcessing = true,
                processingMessage = "AI is rewriting...",
                processingProgress = 50
            )
            
            try {
                val model = translationModel.value

                Timber.d("🤖 AI Rewrite using model: $model")

                val result = geminiApi.generateText(
                    prompt = "Rewrite and improve this text, keeping the same meaning but making it clearer and more professional:\n\n$text",
                    model = model
                )

                val rewrittenText = when (result) {
                    is DomainResult.Success -> result.data
                    is DomainResult.Failure -> {
                        _uiState.value = currentState.copy(
                            isProcessing = false,
                            errorMessage = "AI rewrite failed: ${result.error.message}"
                        )
                        return@launch
                    }
                }
                
                val doc = useCases.getDocumentById(documentId) ?: return@launch
                
                val field = if (isOcrText) TextEditField.OCR_TEXT else TextEditField.TRANSLATED_TEXT
                val previousValue = if (isOcrText) doc.originalText else doc.translatedText
                addToHistory(documentId, field, previousValue, rewrittenText)
                
                val updated = if (isOcrText) {
                    doc.copy(originalText = rewrittenText)
                } else {
                    doc.copy(translatedText = rewrittenText)
                }
                
                useCases.updateDocument(updated)
                
                _uiState.value = currentState.copy(isProcessing = false)
                
            } catch (e: Exception) {
                _uiState.value = currentState.copy(
                    isProcessing = false,
                    errorMessage = "AI rewrite failed: ${e.message}"
                )
            }
        }
    }

    fun clearFormatting(documentId: Long, isOcrText: Boolean) {
        viewModelScope.launch {
            val doc = useCases.getDocumentById(documentId) ?: return@launch
            
            val originalValue = if (isOcrText) doc.originalText else doc.translatedText
            if (originalValue == null) return@launch
            
            val cleanedText = originalValue
                .replace(Regex("\\s+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .replace(Regex("^\\s+|\\s+$"), "")
                .replace(Regex("\\t"), " ")
            
            val field = if (isOcrText) TextEditField.OCR_TEXT else TextEditField.TRANSLATED_TEXT
            addToHistory(documentId, field, originalValue, cleanedText)
            
            val updated = if (isOcrText) {
                doc.copy(originalText = cleanedText)
            } else {
                doc.copy(translatedText = cleanedText)
            }
            
            useCases.updateDocument(updated)
        }
    }

    fun showConfidenceTooltip(word: String, confidence: Float) {
        _confidenceTooltip.value = word to confidence
    }

    fun hideConfidenceTooltip() {
        _confidenceTooltip.value = null
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

    fun refreshOcrSettings() {
        loadOcrSettings()
    }
}

// ============================================
// UI STATE
// ============================================

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

// ============================================
// SHARE EVENT
// ============================================

sealed interface ShareEvent {
    data class File(val path: String, val mimeType: String) : ShareEvent
}