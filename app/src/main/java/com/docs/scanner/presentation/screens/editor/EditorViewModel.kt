/*
 * EditorViewModel.kt
 * Version: 8.0.0 - REFACTORED (2026)
 *
 * КРИТИЧЕСКИЕ ИЗМЕНЕНИЯ:
 * ✅ Разделение ответственности - используем Manager классы
 * ✅ Нет race conditions - InlineEditingManager
 * ✅ Нет lost progress - BatchOperationsManager
 * ✅ Memory efficient - ID вместо Document objects
 * ✅ Thread-safe - StateFlow + Mutex где нужно
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
// import com.docs.scanner.domain.core.Result  ← УДАЛЕНО
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
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.model.Result.*

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
    // MANAGERS - Делегируем работу специализированным классам
    // ════════════════════════════════════════════════════════════════════

    private val inlineEditingManager = InlineEditingManager(
        scope = viewModelScope,
        onSave = { docId, field, text ->
            saveInlineEditToDb(docId, field, text)
        },
        onHistoryAdd = { docId, field, prev, new ->
            addToHistory(docId, field, prev, new)
        }
    )

    private val batchOperationsManager = BatchOperationsManager(
        useCases = useCases,
        scope = viewModelScope
    )

    // ════════════════════════════════════════════════════════════════════
    // UI STATE - Только domain данные
    // ════════════════════════════════════════════════════════════════════

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // ════════════════════════════════════════════════════════════════════
    // PROCESSING STATE - Отдельно от UI
    // ════════════════════════════════════════════════════════════════════

    private val _processingState = MutableStateFlow(ProcessingState())
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    // ════════════════════════════════════════════════════════════════════
    // SELECTION STATE
    // ════════════════════════════════════════════════════════════════════

    private val _selectionState = MutableStateFlow(SelectionState())
    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

    val selectedCount: StateFlow<Int> = _selectionState.map { it.count }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // ════════════════════════════════════════════════════════════════════
    // INLINE EDITING - Делегируем менеджеру
    // ════════════════════════════════════════════════════════════════════

    val inlineEditingStates = inlineEditingManager.editingStates

    // ════════════════════════════════════════════════════════════════════
    // BATCH OPERATIONS - Делегируем менеджеру
    // ════════════════════════════════════════════════════════════════════

    val batchOperation = batchOperationsManager.currentOperation

    // ════════════════════════════════════════════════════════════════════
    // EVENTS - Используем Channel вместо SharedFlow
    // ════════════════════════════════════════════════════════════════════

    private val _shareEvent = Channel<ShareEvent>(Channel.BUFFERED)
    val shareEvent: Flow<ShareEvent> = _shareEvent.receiveAsFlow()

    private val _errorEvent = Channel<ErrorEvent>(Channel.BUFFERED)
    val errorEvent: Flow<ErrorEvent> = _errorEvent.receiveAsFlow()

    // ════════════════════════════════════════════════════════════════════
    // SETTINGS - StateIn для оптимизации
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
    // HISTORY - Для Undo
    // ════════════════════════════════════════════════════════════════════

    private val historyMutex = Mutex()
    private val _editHistory = MutableStateFlow<List<TextEditHistoryItem>>(emptyList())
    val editHistory: StateFlow<List<TextEditHistoryItem>> = _editHistory.asStateFlow()

    val canUndo: StateFlow<Boolean> = _editHistory.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // ════════════════════════════════════════════════════════════════════
    // MOVE TARGETS
    // ════════════════════════════════════════════════════════════════════

    private val _moveTargets = MutableStateFlow<List<Record>>(emptyList())
    val moveTargets: StateFlow<List<Record>> = _moveTargets.asStateFlow()

    // ════════════════════════════════════════════════════════════════════
    // CONFIDENCE TOOLTIP
    // ════════════════════════════════════════════════════════════════════

    private val _confidenceTooltip = MutableStateFlow<Pair<String, Float>?>(null)
    val confidenceTooltip: StateFlow<Pair<String, Float>?> = _confidenceTooltip.asStateFlow()

    // ════════════════════════════════════════════════════════════════════
    // DERIVED STATES
    // ════════════════════════════════════════════════════════════════════

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

                // Load move targets
                launch {
                    useCases.getRecords(record.folderId.value)
                        .catch { /* ignore */ }
                        .collect { records ->
                            _moveTargets.value = records.filter { it.id.value != recordId }
                        }
                }

                // Load documents with StateIn
                useCases.getDocuments(recordId)
                    .catch { e ->
                        _uiState.value = EditorUiState.Error("Failed to load documents: ${e.message}")
                    }
                    .collect { documents ->
                        val currentRecord = useCases.getRecordById(recordId) ?: record

                        // Обновляем только domain data
                        _uiState.value = EditorUiState.Success(
                            record = currentRecord,
                            folderName = folderName,
                            documents = documents.sortedBy { it.position }
                        )

                        // Обновляем total в selection mode если нужно
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

    /**
     * Перезагрузить только Record (без документов)
     */
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

            // Захватываем настройки В МОМЕНТ ВЫЗОВА
            val targetLang = targetLanguage.value
            val autoTranslate = autoTranslateEnabled.value

            setProcessing(
                ProcessingOperation.AddingDocument,
                message = "Adding document...",
                progress = 0
            )

            useCases.addDocument(recordId, uri)
                .collect { state ->
                    when (state) {
                        is AddDocumentState.Creating -> {
                            updateProcessing(
                                message = state.message,
                                progress = state.progress
                            )
                        }
                        is AddDocumentState.ProcessingOcr -> {
                            updateProcessing(
                                message = state.message,
                                progress = state.progress
                            )
                        }
                        is AddDocumentState.Translating -> {
                            updateProcessing(
                                message = state.message,
                                progress = state.progress
                            )
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
                    updateProcessing(
                        message = "Saving images ($done/$total)...",
                        progress = progress
                    )
                }
            )

            if (result.successful.isNotEmpty()) {
                updateProcessing(
                    message = "Processing OCR...",
                    progress = 50
                )

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
                is Result.Success -> { /* Auto-refresh from Flow */ }
                is Result.Error ->
                    sendError("Failed to delete: ${result.exception.message}")
                else -> {}
            }
        }
    }

    fun reorderDocuments(fromIndex: Int, toIndex: Int) {
        // Проверка selection mode
        if (_selectionState.value.isActive) {
            Timber.w("Cannot reorder in selection mode")
            return
        }

        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return

        val currentDocs = currentState.documents.toMutableList()
        if (fromIndex < 0 || fromIndex >= currentDocs.size ||
            toIndex < 0 || toIndex >= currentDocs.size) return

        // Optimistic update
        val item = currentDocs.removeAt(fromIndex)
        currentDocs.add(toIndex, item)

        _uiState.value = currentState.copy(documents = currentDocs)

        // Persist to DB
        viewModelScope.launch {
            val docIds = currentDocs.map { it.id }
            useCases.documents.reorder(RecordId(recordId), docIds)
                .onFailure { error ->
                    sendError("Failed to reorder: ${error.message}")
                    loadData() // Revert on failure
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

    // ════════════════════════════════════════════════════════════════════
    // RECORD OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    fun updateRecordName(name: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is EditorUiState.Success) return@launch

            val updated = currentState.record.copy(name = name.trim())
            when (val result = useCases.updateRecord(updated)) {
                is Result.Success -> { /* Auto-refresh from Flow */ }
                is Result.Error ->
                    sendError("Failed to update: ${result.exception.message}")
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
                is Result.Success -> { /* Auto-refresh */ }
                is Result.Error ->
                    sendError("Failed to update: ${result.exception.message}")
                else -> {}
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
                is Result.Success -> { /* Auto-refresh */ }
                is Result.Error ->
                    sendError("Failed to add tag: ${result.exception.message}")
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
                is Result.Success -> { /* Auto-refresh */ }
                is Result.Error ->
                    sendError("Failed to remove tag: ${result.exception.message}")
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
                is Result.Success -> { /* Auto-refresh */ }
                is Result.Error ->
                    sendError("Failed to update languages: ${result.exception.message}")
                else -> {}
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // INLINE EDITING OPERATIONS - Делегируем InlineEditingManager
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

    /**
     * Thread-safe сохранение в БД
     */
    private suspend fun saveInlineEditToDb(documentId: Long, field: TextEditField, text: String) {
        val doc = useCases.getDocumentById(documentId) ?: throw IllegalStateException("Document not found")

        val updated = when (field) {
            TextEditField.OCR_TEXT -> doc.copy(originalText = text)
            TextEditField.TRANSLATED_TEXT -> doc.copy(translatedText = text)
        }

        when (val result = useCases.updateDocument(updated)) {
            is Result.Error -> throw result.exception
            else -> {} // Success
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // TEXT EDITING (Non-inline)
    // ════════════════════════════════════════════════════════════════════

    fun updateDocumentText(documentId: Long, originalText: String?, translatedText: String?) {
        viewModelScope.launch {
            val doc = useCases.getDocumentById(documentId) ?: return@launch

            val finalOriginalText = originalText ?: doc.originalText
            val finalTranslatedText = translatedText ?: doc.translatedText

            // Add to history
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
                    sendError("Failed to update: ${result.exception.message}")
                else -> {}
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // UNDO FUNCTIONALITY
    // ════════════════════════════════════════════════════════════════════

    fun undoLastEdit() {
        viewModelScope.launch {
            historyMutex.withLock {
                val history = _editHistory.value.toMutableList()
                if (history.isEmpty()) return@launch

                val lastEdit = history.removeAt(history.lastIndex)

                // Restore previous value
                val doc = useCases.getDocumentById(lastEdit.documentId) ?: return@launch
                val updated = when (lastEdit.field) {
                    TextEditField.OCR_TEXT -> doc.copy(originalText = lastEdit.previousValue)
                    TextEditField.TRANSLATED_TEXT -> doc.copy(translatedText = lastEdit.previousValue)
                }

                when (val result = useCases.updateDocument(updated)) {
                    is Result.Success -> {
                        _editHistory.value = history
                        Timber.d("Undid edit for document ${lastEdit.documentId}")
                    }
                    is Result.Error -> {
                        sendError("Failed to undo: ${result.exception.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    private fun addToHistory(
        documentId: Long,
        field: TextEditField,
        previousValue: String?,
        newValue: String?
    ) {
        viewModelScope.launch {
            historyMutex.withLock {
                val history = _editHistory.value.toMutableList()

                history.add(
                    TextEditHistoryItem(
                        documentId = documentId,
                        field = field,
                        previousValue = previousValue,
                        newValue = newValue
                    )
                )

                // Limit history size
                while (history.size > MAX_HISTORY_SIZE) {
                    history.removeAt(0)
                }

                _editHistory.value = history
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

    fun toggleDocumentSelection(documentId: Long) {
        val current = _selectionState.value

        // Автоматически входим в selection mode если не активен
        if (!current.isActive) {
            enterSelectionMode()
        }

        _selectionState.value = current.toggle(documentId)

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
    // BATCH OPERATIONS - Делегируем BatchOperationsManager
    // ════════════════════════════════════════════════════════════════════

    fun deleteSelectedDocuments() {
        val docIds = _selectionState.value.selectedIds.toList()
        if (docIds.isEmpty()) return

        viewModelScope.launch {
            batchOperationsManager.deleteDocuments(docIds) { result ->
                result.fold(
                    onSuccess = {
                        exitSelectionMode()
                        Timber.d("Deleted ${docIds.size} documents")
                    },
                    onFailure = { error ->
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
                        exitSelectionMode()
                        Timber.d("Exported ${docIds.size} documents as ${if (asPdf) "PDF" else "ZIP"}")
                    },
                    onFailure = { error ->
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
                        exitSelectionMode()
                        Timber.d("Moved ${docIds.size} documents to record $targetRecordId")
                    },
                    onFailure = { error ->
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

    // ════════════════════════════════════════════════════════════════════
    // SINGLE DOCUMENT OPERATIONS
    // ════════════════════════════════════════════════════════════════════

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
                is DomainResult.Success -> {
                    Timber.d("Moved document $documentId to record $targetRecordId")
                }
                is DomainResult.Failure ->
                    sendError("Failed to move: ${result.error.message}")
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
                is Result.Success -> {
                    clearProcessing()
                    Timber.d("Retried OCR for document $documentId")
                }
                is Result.Error -> {
                    clearProcessing()
                    sendError("OCR failed: ${result.exception.message}")
                }
                else -> {}
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

            // Используем ТЕКУЩИЙ targetLanguage
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
                    clearProcessing()
                    Timber.d("Retried translation for document $documentId")
                }
                is DomainResult.Failure -> {
                    clearProcessing()
                    sendError("Translation failed: ${result.error.message}")
                }
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
                        Timber.d("Retried ${docIds.size} failed documents")
                    },
                    onFailure = { error ->
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
                        Timber.d("Retried OCR for all ${docIds.size} documents")
                    },
                    onFailure = { error ->
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

        // Захватываем текущий targetLanguage
        val target = targetLanguage.value
        val docIds = currentState.documents.map { it.id.value }

        Timber.d("🌐 Retrying all translations to: ${target.displayName}")

        viewModelScope.launch {
            batchOperationsManager.retryAllTranslation(docIds, target) { result ->
                result.fold(
                    onSuccess = {
                        Timber.d("Retried translation for all ${docIds.size} documents")
                    },
                    onFailure = { error ->
                        if (error !is CancellationException) {
                            sendError("Retry all translation failed: ${error.message}")
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

            setProcessing(
                ProcessingOperation.GeneratingPdf,
                message = "Generating PDF...",
                progress = 20
            )

            try {
                val docIds = currentState.documents.map { it.id }
                val outputPath = "share_${System.currentTimeMillis()}.pdf"

                when (val result = useCases.export.exportToPdf(docIds, outputPath)) {
                    is DomainResult.Success -> {
                        clearProcessing()
                        _shareEvent.send(
                            ShareEvent.File(
                                path = result.data,
                                mimeType = "application/pdf",
                                fileName = "${currentState.record.name}.pdf"
                            )
                        )
                        Timber.d("Generated PDF for record")
                    }
                    is DomainResult.Failure -> {
                        clearProcessing()
                        sendError("PDF generation failed: ${result.error.message}")
                    }
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

            setProcessing(
                ProcessingOperation.CreatingZip,
                message = "Creating ZIP...",
                progress = 20
            )

            try {
                val docIds = currentState.documents.map { it.id }

                when (val result = useCases.export.shareDocuments(docIds, asPdf = false)) {
                    is DomainResult.Success -> {
                        clearProcessing()
                        _shareEvent.send(
                            ShareEvent.File(
                                path = result.data,
                                mimeType = "application/zip",
                                fileName = "${currentState.record.name}.zip"
                            )
                        )
                        Timber.d("Created ZIP for record")
                    }
                    is DomainResult.Failure -> {
                        clearProcessing()
                        sendError("ZIP creation failed: ${result.error.message}")
                    }
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
                ShareEvent.File(
                    path = imagePath,
                    mimeType = "image/*",
                    fileName = null
                )
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AI OPERATIONS
    // ════════════════════════════════════════════════════════════════════

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
            Timber.d("Pasted text to document $documentId")
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

                Timber.d("🤖 AI Rewrite using model: $model")

                val result = geminiApi.generateText(
                    prompt = "Rewrite and improve this text, keeping the same meaning but making it clearer and more professional:\n\n$text",
                    model = model
                )

                val rewrittenText = when (result) {
                    is DomainResult.Success -> result.data
                    is DomainResult.Failure -> {
                        clearProcessing()
                        sendError("AI rewrite failed: ${result.error.message}")
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

            useCases.updateDocument(updated)
            Timber.d("Cleared formatting for document $documentId")
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
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Безопасное получение документа по ID
     */
    private suspend fun getDocumentById(documentId: Long): Document? {
        val currentState = _uiState.value
        if (currentState !is EditorUiState.Success) return null

        return currentState.documents.find { it.id.value == documentId }
            ?: useCases.getDocumentById(documentId)
    }

    /**
     * Установить processing state
     */
    private fun setProcessing(
        operation: ProcessingOperation,
        message: String = "",
        progress: Int = 0,
        canCancel: Boolean = false
    ) {
        _processingState.value = ProcessingState(
            isActive = true,
            operation = operation,
            message = message,
            progress = progress,
            canCancel = canCancel
        )
    }

    /**
     * Обновить processing progress
     */
    private fun updateProcessing(
        message: String? = null,
        progress: Int? = null
    ) {
        val current = _processingState.value
        if (!current.isActive) return

        _processingState.value = current.copy(
            message = message ?: current.message,
            progress = progress ?: current.progress
        )
    }

    /**
     * Очистить processing state
     */
    private fun clearProcessing() {
        _processingState.value = ProcessingState()
    }

    /**
     * Отправить error event
     */
    private fun sendError(
        message: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _errorEvent.send(ErrorEvent(message, actionLabel, action))
        }
        Timber.e("Error: $message")
    }

    /**
     * Refresh OCR settings (обычно не нужно, т.к. используем StateFlow)
     */
    fun refreshOcrSettings() {
        // Settings автоматически обновляются через StateFlow
        Timber.d("OCR settings are auto-updated via StateFlow")
    }
}
