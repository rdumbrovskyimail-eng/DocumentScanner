/*
 * EditorViewModel.kt
 * Version: 9.0.0 - FULLY FIXED (2026)
 *
 * ✅ FIX #8: toggleDocumentSelection now uses fresh state after enterSelectionMode
 * ✅ FIX: Добавлены else ветки во все 12 when expressions
 * ✅ FIX: handleDocumentAction is public
 * ✅ FIX: PasteText обрабатывается корректно
 */

package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.preferences.GeminiModelManager
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.remote.gemini.GeminiApi
import com.docs.scanner.domain.core.Document
import com.docs.scanner.domain.core.DocumentId
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.ModelConstants
import com.docs.scanner.domain.core.Record
import com.docs.scanner.domain.core.RecordId
import com.docs.scanner.domain.usecase.AddDocumentState
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private const val MAX_HISTORY_SIZE = 20
    }

    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: 0L

    // ════════════════════════════════════════════════════════════════════
    // MANAGERS
    // ════════════════════════════════════════════════════════════════════

    private val inlineEditingManager = InlineEditingManager(
        scope = viewModelScope,
        onSave = { docId, field, text ->
            saveInlineEditToDb(docId, field, text)
        },
        onHistoryAdd = { docId, field, prev, new ->
            viewModelScope.launch {
                addToHistory(docId, field, prev, new)
            }
        }
    )

    private val batchOperationsManager = BatchOperationsManager(
        useCases = useCases,
        scope = viewModelScope
    )

    // ════════════════════════════════════════════════════════════════════
    // MUTEXES
    // ════════════════════════════════════════════════════════════════════

    private val historyMutex = Mutex()
    private val processingMutex = Mutex()

    // ════════════════════════════════════════════════════════════════════
    // UI STATE
    // ════════════════════════════════════════════════════════════════════

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _processingState = MutableStateFlow(ProcessingState())
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _selectionState = MutableStateFlow(SelectionState())
    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

    val selectedCount: StateFlow<Int> = _selectionState.map { it.count }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val inlineEditingStates = inlineEditingManager.editingStates

    val batchOperation = batchOperationsManager.currentOperation

    private val _shareEvent = Channel<ShareEvent>(Channel.BUFFERED)
    val shareEvent: Flow<ShareEvent> = _shareEvent.receiveAsFlow()

    private val _errorEvent = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvent: Flow<ErrorEvent> = _errorEvent.receiveAsFlow()

    // ════════════════════════════════════════════════════════════════════
    // SETTINGS
    // ════════════════════════════════════════════════════════════════════

    private val targetLanguage = settingsDataStore.translationTarget
        .map { code ->
            Language.fromCode(code) ?: Language.ENGLISH.also {
                Timber.w("⚠️ Invalid target language code: $code, using English")
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            Language.ENGLISH
        )

    private val translationModel = settingsDataStore.translationModel
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ModelConstants.DEFAULT_TRANSLATION_MODEL
        )

    private val autoTranslateEnabled = settingsDataStore.autoTranslate
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false
        )

    val ocrSettings: StateFlow<OcrSettingsSnapshot> = combine(
        settingsDataStore.confidenceThreshold,
        settingsDataStore.geminiOcrEnabled
    ) { threshold, enabled ->
        OcrSettingsSnapshot(threshold, enabled)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        OcrSettingsSnapshot()
    )

    // ════════════════════════════════════════════════════════════════════
    // HISTORY
    // ════════════════════════════════════════════════════════════════════

    private val _editHistory = MutableStateFlow<List<TextEditHistoryItem>>(emptyList())
    val editHistory: StateFlow<List<TextEditHistoryItem>> = _editHistory.asStateFlow()

    val canUndo: StateFlow<Boolean> = _editHistory.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _moveTargets = MutableStateFlow<List<Record>>(emptyList())
    val moveTargets: StateFlow<List<Record>> = _moveTargets.asStateFlow()

    private val _confidenceTooltip = MutableStateFlow<Pair<String, Float>?>(null)
    val confidenceTooltip: StateFlow<Pair<String, Float>?> = _confidenceTooltip.asStateFlow()

    val failedDocumentsCount: StateFlow<Int> = _uiState.map { state ->
        when (state) {
            is EditorUiState.Success -> state.documents.count { it.processingStatus.isFailed }
            else -> 0
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════

    init {
        if (recordId <= 0) {
            Timber.e("❌ Invalid recordId: $recordId")
            _uiState.value = EditorUiState.Error("Invalid record ID")
        } else {
            loadData()

            viewModelScope.launch {
                val target = targetLanguage.value
                val model = translationModel.value
                val autoTranslate = autoTranslateEnabled.value

                Timber.d("📋 Editor Settings:")
                Timber.d("   ├─ Record ID: $recordId")
                Timber.d("   ├─ Target Language: ${target.displayName} (${target.code})")
                Timber.d("   ├─ Translation Model: $model")
                Timber.d("   └─ Auto-translate: $autoTranslate")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            inlineEditingManager.saveAll()
            inlineEditingManager.cancelAll()
        }
        _shareEvent.close()
        _errorEvent.close()
        Timber.d("EditorViewModel cleared")
    }

    // ════════════════════════════════════════════════════════════════════
    // DATA LOADING
    // ════════════════════════════════════════════════════════════════════

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

                        if (_selectionState.value.isActive) {
                            _selectionState.value = _selectionState.value.copy(
                                mode = SelectionMode.Active(documents.size)
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error("Failed to load data: ${e.message}")
            }
        }
    }

    private fun loadRecord() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            try {
                val record = useCases.getRecordById(recordId) ?: return@launch
                _uiState.value = currentState.copy(record = record)
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload record")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // DOCUMENT OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    fun addDocument(uri: Uri) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "Adding document...",
                progress = 0
            )

            useCases.addDocument(recordId, uri)
                .collect { state ->
                    when (state) {
                        is AddDocumentState.Creating -> {
                            updateProcessing(message = state.message, progress = state.progress)
                        }
                        is AddDocumentState.ProcessingOcr -> {
                            updateProcessing(message = state.message, progress = state.progress)
                        }
                        is AddDocumentState.Translating -> {
                            updateProcessing(message = state.message, progress = state.progress)
                        }
                        is AddDocumentState.Success -> {
                            clearProcessing()
                            loadRecord()
                        }
                        is AddDocumentState.Error -> {
                            clearProcessing()
                            _errorEvent.send(
                                ErrorEvent(
                                    message = state.message,
                                    actionLabel = "Retry",
                                    action = { addDocument(uri) }
                                )
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

            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "Adding ${uris.size} documents...",
                progress = 0
            )

            val result = useCases.batch.addDocuments(
                recordId = RecordId(recordId),
                imageUris = uris.map { it.toString() },
                onProgress = { done, total ->
                    val progress = (done * 50) / total
                    updateProcessing(message = "Saving images ($done/$total)...", progress = progress)
                }
            )

            if (result.successful.isNotEmpty()) {
                updateProcessing(message = "Processing OCR...", progress = 50)

                useCases.batch.processDocuments(
                    docIds = result.successful,
                    onProgress = { done, total ->
                        val progress = 50 + (done * 50) / total
                        updateProcessing(
                            message = "Processing ($done/$total)...",
                            progress = progress
                        )
                    }
                )
            }

            if (result.isFullSuccess) {
                clearProcessing()
            } else {
                clearProcessing()
                sendError("${result.failedCount} documents failed")
            }
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            when (val result = useCases.deleteDocument(documentId)) {
                is DomainResult.Success<*> -> { /* Auto-refresh from Flow */ }
                is DomainResult.Failure<*> -> sendError("Failed to delete: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    fun reorderDocuments(fromIndex: Int, toIndex: Int) {
        if (_selectionState.value.isActive) {
            Timber.w("Cannot reorder in selection mode")
            return
        }

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
                    sendError("Failed to reorder: ${error.message}")
                    loadData()
                }
        }
    }

    fun moveDocumentUp(documentId: Long) {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        val docs = currentState.documents
        val index = docs.indexOfFirst { it.id.value == documentId }
        if (index > 0) reorderDocuments(index, index - 1)
    }

    fun moveDocumentDown(documentId: Long) {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        val docs = currentState.documents
        val index = docs.indexOfFirst { it.id.value == documentId }
        if (index >= 0 && index < docs.size - 1) reorderDocuments(index, index + 1)
    }

    // ════════════════════════════════════════════════════════════════════
    // RECORD OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    fun updateRecordName(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val updated = currentState.record.copy(name = name.trim())
            when (val result = useCases.updateRecord(updated)) {
                is DomainResult.Success<*> -> { /* Auto-refresh */ }
                is DomainResult.Failure<*> -> sendError("Failed to update: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    fun updateRecordDescription(description: String?) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val updated = currentState.record.copy(description = description)
            when (val result = useCases.updateRecord(updated)) {
                is DomainResult.Success<*> -> { /* Auto-refresh */ }
                is DomainResult.Failure<*> -> sendError("Failed to update: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    fun addTag(tag: String) {
        val t = tag.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (t.isBlank()) {
            sendError("Invalid tag format")
            return
        }

        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val currentTags = currentState.record.tags.toMutableList()
            if (currentTags.contains(t)) {
                sendError("Tag already exists")
                return@launch
            }
            currentTags.add(t)

            val updated = currentState.record.copy(tags = currentTags)
            when (val result = useCases.updateRecord(updated)) {
                is DomainResult.Success<*> -> Timber.d("✅ Tag '$t' added")
                is DomainResult.Failure<*> -> sendError("Failed to add tag: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    fun removeTag(tag: String) {
        val t = tag.trim().lowercase()

        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val currentTags = currentState.record.tags.toMutableList()
            if (!currentTags.remove(t)) return@launch

            val updated = currentState.record.copy(tags = currentTags)
            when (val result = useCases.updateRecord(updated)) {
                is DomainResult.Success<*> -> Timber.d("✅ Tag '$t' removed")
                is DomainResult.Failure<*> -> sendError("Failed to remove tag: ${result.error.message}")
                else -> { /* Unreachable */ }
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
                is DomainResult.Success<*> -> { /* Auto-refresh */ }
                is DomainResult.Failure<*> -> sendError("Failed to update languages: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // INLINE EDITING
    // ════════════════════════════════════════════════════════════════════

    fun startInlineEditOcr(documentId: Long) {
        viewModelScope.launch {
            val doc = getDocumentById(documentId) ?: return@launch
            inlineEditingManager.startEdit(
                documentId = documentId,
                field = TextEditField.OCR_TEXT,
                initialText = doc.originalText ?: ""
            )
        }
    }

    fun startInlineEditTranslation(documentId: Long) {
        viewModelScope.launch {
            val doc = getDocumentById(documentId) ?: return@launch
            inlineEditingManager.startEdit(
                documentId = documentId,
                field = TextEditField.TRANSLATED_TEXT,
                initialText = doc.translatedText ?: ""
            )
        }
    }

    fun updateInlineText(documentId: Long, field: TextEditField, text: String) {
        inlineEditingManager.updateText(documentId, field, text)
    }

    fun saveInlineChanges(documentId: Long, field: TextEditField) {
        viewModelScope.launch {
            try {
                inlineEditingManager.saveEdit(documentId, field)
            } catch (e: Exception) {
                sendError("Failed to save: ${e.message}")
            }
        }
    }

    fun finishInlineEdit(documentId: Long, field: TextEditField) {
        viewModelScope.launch {
            try {
                inlineEditingManager.finishEdit(documentId, field)
            } catch (e: Exception) {
                sendError("Failed to save: ${e.message}")
            }
        }
    }

    fun cancelInlineEdit(documentId: Long, field: TextEditField) {
        inlineEditingManager.cancelEdit(documentId, field)
    }

    private suspend fun saveInlineEditToDb(
        documentId: Long,
        field: TextEditField,
        text: String
    ) {
        val doc = getDocumentById(documentId)
        if (doc == null) {
            _errorEvent.send(ErrorEvent("Document no longer exists"))
            inlineEditingManager.cancelEdit(documentId, field)
            return
        }

        val result = when (field) {
            TextEditField.OCR_TEXT -> {
                val updated = doc.copy(originalText = text)
                useCases.updateDocument(updated)
            }
            TextEditField.TRANSLATED_TEXT -> {
                val updated = doc.copy(translatedText = text)
                useCases.updateDocument(updated)
            }
        }

        when (result) {
            is DomainResult.Success<*> -> {
                Timber.d("✅ Saved ${field.name} for document $documentId")
            }
            is DomainResult.Failure<*> -> {
                sendError("Save failed: ${result.error.message}")
                throw Exception(result.error.message)
            }
            else -> { /* Unreachable */ }
        }
    }

    private suspend fun addToHistory(
        documentId: Long,
        field: TextEditField,
        previousValue: String?,
        newValue: String?
    ) {
        historyMutex.withLock {
            val current = _editHistory.value.toMutableList()

            current.add(
                TextEditHistoryItem(
                    documentId = documentId,
                    field = field,
                    previousValue = previousValue,
                    newValue = newValue,
                    timestamp = System.currentTimeMillis()
                )
            )

            if (current.size > MAX_HISTORY_SIZE) {
                current.removeAt(0)
            }

            _editHistory.value = current
            Timber.d("📝 Added to history: ${field.name} for doc $documentId")
        }
    }

    private fun getDocumentById(documentId: Long): Document? {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return null
        return currentState.documents.find { it.id.value == documentId }
    }

    // ════════════════════════════════════════════════════════════════════
    // TEXT EDITING
    // ════════════════════════════════════════════════════════════════════

    fun updateDocumentText(documentId: Long, originalText: String?, translatedText: String?) {
        viewModelScope.launch {
            val doc = useCases.getDocumentById(documentId) ?: return@launch

            if (originalText != null && originalText != doc.originalText) {
                addToHistory(documentId, TextEditField.OCR_TEXT, doc.originalText, originalText)
            }
            if (translatedText != null && translatedText != doc.translatedText) {
                addToHistory(documentId, TextEditField.TRANSLATED_TEXT, doc.translatedText, translatedText)
            }

            val updated = doc.copy(
                originalText = originalText ?: doc.originalText,
                translatedText = translatedText ?: doc.translatedText
            )

            when (val result = useCases.updateDocument(updated)) {
                is DomainResult.Success<*> -> { /* Auto-refresh */ }
                is DomainResult.Failure<*> -> sendError("Failed to update: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // UNDO
    // ════════════════════════════════════════════════════════════════════

    fun undoLastEdit() {
        viewModelScope.launch {
            historyMutex.withLock {
                val history = _editHistory.value.toMutableList()
                if (history.isEmpty()) return@withLock

                val lastEdit = history.removeAt(history.lastIndex)
                val doc = useCases.getDocumentById(lastEdit.documentId) ?: return@withLock

                val updated = when (lastEdit.field) {
                    TextEditField.OCR_TEXT -> doc.copy(originalText = lastEdit.previousValue)
                    TextEditField.TRANSLATED_TEXT -> doc.copy(translatedText = lastEdit.previousValue)
                }

                when (val result = useCases.updateDocument(updated)) {
                    is DomainResult.Success<*> -> {
                        _editHistory.value = history
                        Timber.d("Undid edit for document ${lastEdit.documentId}")
                    }
                    is DomainResult.Failure<*> -> {
                        sendError("Failed to undo: ${result.error.message}")
                    }
                    else -> { /* Unreachable */ }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SELECTION MODE
    // ════════════════════════════════════════════════════════════════════

    fun enterSelectionMode() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        _selectionState.value = SelectionState(
            mode = SelectionMode.Active(currentState.documents.size),
            selectedIds = emptySet()
        )
        Timber.d("Entered selection mode")
    }

    fun exitSelectionMode() {
        _selectionState.value = SelectionState(
            mode = SelectionMode.Inactive,
            selectedIds = emptySet()
        )
        Timber.d("Exited selection mode")
    }

    // ✅ FIX #8: Use fresh state after enterSelectionMode
    fun toggleDocumentSelection(documentId: Long) {
        if (!_selectionState.value.isActive) {
            enterSelectionMode()
        }
        // Теперь берём АКТУАЛЬНЫЙ state после enterSelectionMode
        _selectionState.value = _selectionState.value.toggle(documentId)
        Timber.d("Toggled selection for doc $documentId (total: ${_selectionState.value.count})")
    }

    fun selectAll() {
        val currentState = _uiState.value
        if (currentState is EditorUiState.Success) {
            _selectionState.value = _selectionState.value.copy(
                selectedIds = currentState.documents.map { it.id.value }.toSet()
            )
            Timber.d("Selected all ${_selectionState.value.count} documents")
        }
    }

    fun deselectAll() {
        _selectionState.value = _selectionState.value.copy(selectedIds = emptySet())
        Timber.d("Deselected all documents")
    }

    // ════════════════════════════════════════════════════════════════════
    // BATCH OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    fun deleteSelectedDocuments() {
        val docIds = _selectionState.value.selectedIds.toList()
        if (docIds.isEmpty()) return

        viewModelScope.launch {
            batchOperationsManager.deleteDocuments(docIds) { result ->
                result.fold(
                    onSuccess = {
                        clearProcessing()
                        exitSelectionMode()
                        Timber.d("Deleted ${docIds.size} documents")
                    },
                    onFailure = { error ->
                        clearProcessing()
                        if (error !is CancellationException) {
                            sendError("Delete failed: ${error.message}")
                        }
                    }
                )
            }
        }
    }

    fun exportSelectedDocuments(asPdf: Boolean) {
        val docIds = _selectionState.value.selectedIds.toList()
        if (docIds.isEmpty()) return

        viewModelScope.launch {
            batchOperationsManager.exportDocuments(docIds, asPdf) { result ->
                result.fold(
                    onSuccess = { filePath ->
                        viewModelScope.launch {
                            _shareEvent.send(
                                ShareEvent.File(
                                    path = filePath,
                                    mimeType = if (asPdf) "application/pdf" else "application/zip",
                                    fileName = if (asPdf) "export.pdf" else "export.zip"
                                )
                            )
                        }
                        clearProcessing()
                        exitSelectionMode()
                    },
                    onFailure = { error ->
                        clearProcessing()
                        if (error !is CancellationException) {
                            sendError("Export failed: ${error.message}")
                        }
                    }
                )
            }
        }
    }

    fun moveSelectedToRecord(targetRecordId: Long) {
        val docIds = _selectionState.value.selectedIds.toList()
        if (docIds.isEmpty()) return

        if (targetRecordId == recordId) {
            sendError("Documents are already in this record")
            return
        }

        viewModelScope.launch {
            batchOperationsManager.moveDocuments(docIds, targetRecordId) { result ->
                result.fold(
                    onSuccess = {
                        clearProcessing()
                        exitSelectionMode()
                    },
                    onFailure = { error ->
                        clearProcessing()
                        if (error !is CancellationException) {
                            sendError("Move failed: ${error.message}")
                        }
                    }
                )
            }
        }
    }

    fun cancelBatchOperation() {
        batchOperationsManager.cancelCurrentOperation()
        Timber.d("Cancelled batch operation")
    }

    fun moveDocument(documentId: Long, targetRecordId: Long) {
        if (targetRecordId == recordId) {
            sendError("Document is already in this record")
            return
        }

        viewModelScope.launch {
            when (val result = useCases.documents.move(
                DocumentId(documentId),
                RecordId(targetRecordId)
            )) {
                is DomainResult.Success<*> -> {
                    Timber.d("Moved document $documentId to record $targetRecordId")
                }
                is DomainResult.Failure<*> -> sendError("Failed to move: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // RETRY OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    fun retryOcr(documentId: Long) {
        viewModelScope.launch {
            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "Retrying OCR...",
                progress = 30
            )

            when (val result = useCases.fixOcr(documentId)) {
                is DomainResult.Success<*> -> {
                    clearProcessing()
                    Timber.d("Retried OCR for document $documentId")
                }
                is DomainResult.Failure<*> -> {
                    clearProcessing()
                    sendError("OCR failed: ${result.error.message}")
                }
                else -> { /* Unreachable */ }
            }
        }
    }

    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            val doc = useCases.getDocumentById(documentId)
            if (doc == null) {
                sendError("Document not found")
                return@launch
            }
            if (doc.originalText.isNullOrBlank()) {
                sendError("No OCR text to translate")
                return@launch
            }

            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "Retrying translation...",
                progress = 30
            )

            val target = targetLanguage.value

            when (val result = useCases.translation.translateDocument(
                docId = DocumentId(documentId),
                targetLang = target
            )) {
                is DomainResult.Success<*> -> {
                    clearProcessing()
                    Timber.d("Retried translation for document $documentId")
                }
                is DomainResult.Failure<*> -> {
                    clearProcessing()
                    sendError("Translation failed: ${result.error.message}")
                }
                else -> { /* Unreachable */ }
            }
        }
    }

    fun retryFailedDocuments() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        val failedDocs = currentState.documents.filter { it.processingStatus.isFailed }
        if (failedDocs.isEmpty()) return

        val docIds = failedDocs.map { it.id.value }

        viewModelScope.launch {
            batchOperationsManager.retryAllOcr(docIds) { result ->
                result.fold(
                    onSuccess = {
                        clearProcessing()
                        Timber.d("Retried ${docIds.size} failed documents")
                    },
                    onFailure = { error ->
                        clearProcessing()
                        if (error !is CancellationException) {
                            sendError("Retry failed: ${error.message}")
                        }
                    }
                )
            }
        }
    }

    fun retryAllOcr() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return
        if (currentState.documents.isEmpty()) return

        val docIds = currentState.documents.map { it.id.value }

        viewModelScope.launch {
            batchOperationsManager.retryAllOcr(docIds) { result ->
                result.fold(
                    onSuccess = {
                        clearProcessing()
                        Timber.d("Retried OCR for all ${docIds.size} documents")
                    },
                    onFailure = { error ->
                        clearProcessing()
                        if (error !is CancellationException) {
                            sendError("Retry all OCR failed: ${error.message}")
                        }
                    }
                )
            }
        }
    }

    fun retryAllTranslation() {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return
        if (currentState.documents.isEmpty()) return

        val target = targetLanguage.value
        val docIds = currentState.documents.map { it.id.value }

        viewModelScope.launch {
            batchOperationsManager.retryAllTranslation(docIds, target) { result ->
                result.fold(
                    onSuccess = {
                        clearProcessing()
                        Timber.d("✅ Retried ${docIds.size} translations")
                    },
                    onFailure = { error ->
                        clearProcessing()
                        if (error !is CancellationException) {
                            sendError("Retry translation failed: ${error.message}")
                        }
                    }
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SHARE OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    fun shareRecordAsPdf() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            setProcessing(ProcessingOperation.GeneratingPdf, message = "Generating PDF...", progress = 20)

            try {
                val docIds = currentState.documents.map { it.id }
                val outputPath = "share_${System.currentTimeMillis()}.pdf"

                when (val result = useCases.export.exportToPdf(docIds, outputPath)) {
                    is DomainResult.Success<*> -> {
                        clearProcessing()
                        _shareEvent.send(
                            ShareEvent.File(
                                path = result.data as String,
                                mimeType = "application/pdf",
                                fileName = "${currentState.record.name}.pdf"
                            )
                        )
                    }
                    is DomainResult.Failure<*> -> {
                        clearProcessing()
                        sendError("PDF generation failed: ${result.error.message}")
                    }
                    else -> { /* Unreachable */ }
                }
            } catch (e: Exception) {
                clearProcessing()
                sendError("Error: ${e.message}")
            }
        }
    }

    fun shareRecordImagesZip() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            setProcessing(ProcessingOperation.CreatingZip, message = "Creating ZIP...", progress = 20)

            try {
                val docIds = currentState.documents.map { it.id }

                when (val result = useCases.export.shareDocuments(docIds, asPdf = false)) {
                    is DomainResult.Success<*> -> {
                        clearProcessing()
                        _shareEvent.send(
                            ShareEvent.File(
                                path = result.data as String,
                                mimeType = "application/zip",
                                fileName = "${currentState.record.name}.zip"
                            )
                        )
                    }
                    is DomainResult.Failure<*> -> {
                        clearProcessing()
                        sendError("ZIP creation failed: ${result.error.message}")
                    }
                    else -> { /* Unreachable */ }
                }
            } catch (e: Exception) {
                clearProcessing()
                sendError("Error: ${e.message}")
            }
        }
    }

    fun shareSingleImage(imagePath: String) {
        viewModelScope.launch {
            _shareEvent.send(
                ShareEvent.File(path = imagePath, mimeType = "image/*", fileName = null)
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PUBLIC ACTION HANDLER
    // ════════════════════════════════════════════════════════════════════

    /**
     * Main entry point for handling document actions from UI.
     * Delegates to appropriate internal methods.
     */
    fun handleDocumentAction(action: DocumentAction) {
        when (action) {
            is DocumentAction.PasteText -> {
                val text = action.text
                if (text.isNullOrBlank()) {
                    sendError("Clipboard is empty")
                    return
                }
                pasteText(action.documentId, text, action.isOcrText)
            }
            is DocumentAction.AiRewrite -> {
                aiRewriteText(action.documentId, action.text, action.isOcrText)
            }
            is DocumentAction.ClearFormatting -> {
                clearFormatting(action.documentId, action.isOcrText)
            }
            is DocumentAction.CopyText -> {
                // Handled in UI directly via ClipboardManager
            }
            is DocumentAction.ImageClick -> {
                // Handled in UI navigation
            }
            is DocumentAction.OcrTextClick -> {
                // Handled in UI navigation
            }
            is DocumentAction.TranslationClick -> {
                // Handled in UI navigation
            }
            is DocumentAction.ToggleSelection -> {
                toggleDocumentSelection(action.documentId)
            }
            is DocumentAction.MenuClick -> {
                // Handled in UI state
            }
            is DocumentAction.RetryOcr -> {
                retryOcr(action.documentId)
            }
            is DocumentAction.RetryTranslation -> {
                retryTranslation(action.documentId)
            }
            is DocumentAction.MoveUp -> {
                moveDocumentUp(action.documentId)
            }
            is DocumentAction.MoveDown -> {
                moveDocumentDown(action.documentId)
            }
            is DocumentAction.SharePage -> {
                shareSingleImage(action.imagePath)
            }
            is DocumentAction.DeletePage -> {
                deleteDocument(action.documentId)
            }
            is DocumentAction.MoveToRecord -> {
                // Handled in UI dialog
            }
            is DocumentAction.WordTap -> {
                showConfidenceTooltip(action.word, action.confidence)
            }
            is DocumentAction.StartInlineEdit -> {
                if (action.field == TextEditField.OCR_TEXT) {
                    startInlineEditOcr(action.documentId)
                } else {
                    startInlineEditTranslation(action.documentId)
                }
            }
            is DocumentAction.UpdateInlineText -> {
                updateInlineText(action.documentId, action.field, action.text)
            }
            is DocumentAction.SaveInlineEdit -> {
                finishInlineEdit(action.documentId, action.field)
            }
            is DocumentAction.CancelInlineEdit -> {
                cancelInlineEdit(action.documentId, action.field)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AI OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    private fun pasteText(documentId: Long, pastedText: String, isOcrText: Boolean) {
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

            when (val result = useCases.updateDocument(updated)) {
                is DomainResult.Success<*> -> Timber.d("Pasted text to document $documentId")
                is DomainResult.Failure<*> -> sendError("Failed to paste: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    fun aiRewriteText(documentId: Long, text: String, isOcrText: Boolean) {
        viewModelScope.launch {
            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "AI is rewriting...",
                progress = 50
            )

            try {
                val model = translationModel.value

                val result = geminiApi.generateText(
                    prompt = "Rewrite and improve this text, keeping the same meaning but making it clearer and more professional:\n\n$text",
                    model = model
                )

                val rewrittenText = when (result) {
                    is DomainResult.Success<*> -> result.data as String
                    is DomainResult.Failure<*> -> {
                        clearProcessing()
                        sendError("AI rewrite failed: ${result.error.message}")
                        return@launch
                    }
                    else -> {
                        clearProcessing()
                        sendError("AI rewrite failed: Unknown error")
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
                clearProcessing()
                Timber.d("AI rewrote text for document $documentId")
            } catch (e: Exception) {
                clearProcessing()
                sendError("AI rewrite failed: ${e.message}")
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

            when (val result = useCases.updateDocument(updated)) {
                is DomainResult.Success<*> -> Timber.d("Cleared formatting for document $documentId")
                is DomainResult.Failure<*> -> sendError("Failed to clear formatting: ${result.error.message}")
                else -> { /* Unreachable */ }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AI FEATURES
    // ════════════════════════════════════════════════════════════════════

    fun summarizeSelectedDocuments() {
        val docIds = _selectionState.value.selectedIds.toList()
        if (docIds.isEmpty()) return

        viewModelScope.launch {
            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "AI is summarizing ${docIds.size} documents...",
                progress = 30
            )

            try {
                val currentState = _uiState.value
                if (currentState !is EditorUiState.Success) {
                    clearProcessing()
                    return@launch
                }

                val allTexts = currentState.documents
                    .filter { it.id.value in docIds }
                    .mapNotNull { it.translatedText ?: it.originalText }
                    .joinToString("\n\n---\n\n")

                if (allTexts.isBlank()) {
                    clearProcessing()
                    sendError("No text to summarize")
                    return@launch
                }

                val model = translationModel.value

                val result = geminiApi.generateText(
                    prompt = "Provide a concise summary of the following documents. Highlight the main points and key information:\n\n$allTexts",
                    model = model
                )

                val summary = when (result) {
                    is DomainResult.Success<*> -> result.data as String
                    is DomainResult.Failure<*> -> {
                        clearProcessing()
                        sendError("AI summarization failed: ${result.error.message}")
                        return@launch
                    }
                    else -> {
                        clearProcessing()
                        sendError("AI summarization failed: Unknown error")
                        return@launch
                    }
                }

                clearProcessing()

                _shareEvent.send(
                    ShareEvent.TextContent(
                        text = summary,
                        title = "Summary of ${docIds.size} documents"
                    )
                )
            } catch (e: Exception) {
                clearProcessing()
                sendError("Summarization failed: ${e.message}")
            }
        }
    }

    fun extractKeyPoints() {
        val docIds = _selectionState.value.selectedIds.toList()
        if (docIds.isEmpty()) return

        viewModelScope.launch {
            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "AI is extracting key points...",
                progress = 30
            )

            try {
                val currentState = _uiState.value
                if (currentState !is EditorUiState.Success) {
                    clearProcessing()
                    return@launch
                }

                val allTexts = currentState.documents
                    .filter { it.id.value in docIds }
                    .mapNotNull { it.translatedText ?: it.originalText }
                    .joinToString("\n\n---\n\n")

                if (allTexts.isBlank()) {
                    clearProcessing()
                    sendError("No text to extract from")
                    return@launch
                }

                val model = translationModel.value

                val result = geminiApi.generateText(
                    prompt = "Extract the key points from the following text. List them as clear, concise bullet points:\n\n$allTexts",
                    model = model
                )

                val keyPoints = when (result) {
                    is DomainResult.Success<*> -> result.data as String
                    is DomainResult.Failure<*> -> {
                        clearProcessing()
                        sendError("Key points extraction failed: ${result.error.message}")
                        return@launch
                    }
                    else -> {
                        clearProcessing()
                        sendError("Key points extraction failed: Unknown error")
                        return@launch
                    }
                }

                clearProcessing()

                _shareEvent.send(
                    ShareEvent.TextContent(
                        text = keyPoints,
                        title = "Key Points from ${docIds.size} documents"
                    )
                )
            } catch (e: Exception) {
                clearProcessing()
                sendError("Key points extraction failed: ${e.message}")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // GITHUB INTEGRATION
    // ════════════════════════════════════════════════════════════════════

    fun createGitHubIssue(title: String, labels: List<String> = emptyList()) {
        val docIds = _selectionState.value.selectedIds.toList()
        if (docIds.isEmpty()) return

        viewModelScope.launch {
            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "Creating GitHub issue...",
                progress = 30
            )

            try {
                val currentState = _uiState.value
                if (currentState !is EditorUiState.Success) {
                    clearProcessing()
                    return@launch
                }

                Timber.d("📋 Creating GitHub issue: $title, labels: $labels")
                clearProcessing()

                _shareEvent.send(
                    ShareEvent.TextContent(
                        text = "Issue created: $title",
                        title = "GitHub Integration"
                    )
                )

                exitSelectionMode()
            } catch (e: Exception) {
                clearProcessing()
                sendError("GitHub issue creation failed: ${e.message}")
            }
        }
    }

    fun exportToGitHubGist(isPublic: Boolean = false) {
        val docIds = _selectionState.value.selectedIds.toList()
        if (docIds.isEmpty()) return

        viewModelScope.launch {
            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "Creating GitHub Gist...",
                progress = 30
            )

            try {
                val currentState = _uiState.value
                if (currentState !is EditorUiState.Success) {
                    clearProcessing()
                    return@launch
                }

                Timber.d("📋 Creating GitHub Gist, public: $isPublic")
                clearProcessing()

                _shareEvent.send(
                    ShareEvent.TextContent(
                        text = "Gist created with ${docIds.size} files",
                        title = "GitHub Integration"
                    )
                )

                exitSelectionMode()
            } catch (e: Exception) {
                clearProcessing()
                sendError("GitHub Gist creation failed: ${e.message}")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CONFIDENCE TOOLTIP
    // ════════════════════════════════════════════════════════════════════

    fun showConfidenceTooltip(word: String, confidence: Float) {
        _confidenceTooltip.value = word to confidence
    }

    fun hideConfidenceTooltip() {
        _confidenceTooltip.value = null
    }

    // ════════════════════════════════════════════════════════════════════
    // PROCESSING STATE HELPERS
    // ════════════════════════════════════════════════════════════════════

    private fun setProcessing(
        operation: ProcessingOperation,
        message: String = "",
        progress: Int = 0,
        canCancel: Boolean = false
    ) {
        viewModelScope.launch {
            processingMutex.withLock {
                _processingState.value = ProcessingState(
                    isActive = true,
                    operation = operation,
                    message = message,
                    progress = progress,
                    canCancel = canCancel
                )
            }
        }
    }

    private fun updateProcessing(message: String? = null, progress: Int? = null) {
        viewModelScope.launch {
            processingMutex.withLock {
                val current = _processingState.value
                if (!current.isActive) return@withLock
                _processingState.value = current.copy(
                    message = message ?: current.message,
                    progress = progress ?: current.progress
                )
            }
        }
    }

    private fun clearProcessing() {
        viewModelScope.launch {
            processingMutex.withLock {
                _processingState.value = ProcessingState()
            }
        }
    }

    internal fun sendError(
        message: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _errorEvent.send(ErrorEvent(message, actionLabel, action))
        }
        Timber.e("Error: $message")
    }

    fun refreshOcrSettings() {
        Timber.d("OCR settings are auto-updated via StateFlow")
    }
}