package com.docs.scanner.presentation.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.FolderId
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.AllUseCases
import com.docs.scanner.presentation.screens.folders.SortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    private val _currentFolderId = MutableStateFlow(0L)
    val currentFolderId: StateFlow<Long> = _currentFolderId.asStateFlow()

    private val _uiState = MutableStateFlow<RecordsUiState>(RecordsUiState.Loading)
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()
    
    private val _sortMode = MutableStateFlow(SortMode.BY_DATE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()
    
    // Для drag & drop
    private val _isDragging = MutableStateFlow(false)
    private var _localRecords: List<Record> = emptyList()

    val allFolders: StateFlow<List<Folder>> = useCases.getFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun loadRecords(folderId: Long) {
        if (folderId == 0L) {
            _uiState.value = RecordsUiState.Error("Invalid folder ID")
            return
        }

        _currentFolderId.value = folderId
        
        val isQuickScans = folderId == FolderId.QUICK_SCANS_ID
        
        viewModelScope.launch {
            _uiState.value = RecordsUiState.Loading

            try {
                val folder = useCases.getFolderById(folderId)
                val folderName = folder?.name ?: "Records"

                combine(
                    useCases.getRecords(folderId),
                    _sortMode,
                    _isDragging
                ) { records, sortMode, isDragging ->
                    Triple(records, sortMode, isDragging)
                }
                .map { (records, sortMode, isDragging) ->
                    if (isDragging) {
                        // Во время перетаскивания возвращаем локальную копию
                        _localRecords
                    } else {
                        // Сортируем и обновляем локальную копию
                        sortRecords(records, sortMode).also { _localRecords = it }
                    }
                }
                .catch { e ->
                    _uiState.value = RecordsUiState.Error("Failed to load records: ${e.message}")
                }
                .collect { records ->
                    _uiState.value = RecordsUiState.Success(
                        folderId = folderId,
                        folderName = folderName,
                        records = records,
                        isQuickScansFolder = isQuickScans
                    )
                }
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error("Failed to load data: ${e.message}")
            }
        }
    }
    
    private fun sortRecords(records: List<Record>, sortMode: SortMode): List<Record> {
        val pinned = records.filter { it.isPinned }
        val others = records.filter { !it.isPinned }
        
        val sortedPinned = when (sortMode) {
            SortMode.BY_DATE -> pinned.sortedByDescending { it.updatedAt }
            SortMode.BY_NAME -> pinned.sortedBy { it.name.lowercase() }
            SortMode.MANUAL -> pinned // Позиция уже из БД
        }
        
        val sortedOthers = when (sortMode) {
            SortMode.BY_DATE -> others.sortedByDescending { it.updatedAt }
            SortMode.BY_NAME -> others.sortedBy { it.name.lowercase() }
            SortMode.MANUAL -> others // Позиция уже из БД
        }
        
        return sortedPinned + sortedOthers
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SORT MODE
    // ═══════════════════════════════════════════════════════════════════════════
    
    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DRAG & DROP
    // ═══════════════════════════════════════════════════════════════════════════
    
    fun startDragging() {
        _isDragging.value = true
    }
    
    fun reorderRecords(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState !is RecordsUiState.Success) return
        
        val records = currentState.records.toMutableList()
        
        if (fromIndex < 0 || fromIndex >= records.size || 
            toIndex < 0 || toIndex >= records.size) return
        
        // Перемещаем элемент
        val item = records.removeAt(fromIndex)
        records.add(toIndex, item)
        
        // Обновляем локальный список
        _localRecords = records
        _uiState.value = currentState.copy(records = records)
    }
    
    fun saveRecordOrder() {
        viewModelScope.launch {
            try {
                // Сохраняем позиции для ВСЕХ записей
                _localRecords.forEachIndexed { index, record ->
                    useCases.records.updatePosition(record.id, index)
                }
            } catch (e: Exception) {
                updateErrorMessage("Failed to save order: ${e.message}")
            } finally {
                _isDragging.value = false
            }
        }
    }
    
    fun cancelDragging() {
        _isDragging.value = false
        // Перезагрузить данные из БД
        val folderId = _currentFolderId.value
        if (folderId != 0L) {
            loadRecords(folderId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    fun createRecord(name: String, description: String?) {
        if (name.isBlank()) {
            updateErrorMessage("Name cannot be empty")
            return
        }

        val folderId = _currentFolderId.value
        
        if (folderId == 0L) {
            updateErrorMessage("No folder selected")
            return
        }
        
        if (folderId == FolderId.QUICK_SCANS_ID) {
            updateErrorMessage("Cannot create records manually in Quick Scans")
            return
        }

        viewModelScope.launch {
            when (val result = useCases.createRecord(folderId, name.trim(), description)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to create record: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch {
            when (val result = useCases.updateRecord(record)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to update record: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch {
            when (val result = useCases.deleteRecord(recordId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to delete record: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    fun moveRecord(recordId: Long, targetFolderId: Long) {
        val currentFolderId = _currentFolderId.value
        
        if (currentFolderId == targetFolderId) {
            updateErrorMessage("Record is already in this folder")
            return
        }

        viewModelScope.launch {
            when (val result = useCases.moveRecord(recordId, targetFolderId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    loadRecords(currentFolderId)
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to move record: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    fun clearError() {
        val currentState = _uiState.value
        if (currentState is RecordsUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = null)
        }
    }

    private fun updateErrorMessage(message: String) {
        val currentState = _uiState.value
        if (currentState is RecordsUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = message)
        } else {
            _uiState.value = RecordsUiState.Error(message)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// UI STATE
// ══════════════════════════════════════════════════════════════════════════════

sealed interface RecordsUiState {
    data object Loading : RecordsUiState
    
    data class Success(
        val folderId: Long,
        val folderName: String,
        val records: List<Record>,
        val isQuickScansFolder: Boolean = false,
        val errorMessage: String? = null
    ) : RecordsUiState
    
    data class Error(val message: String) : RecordsUiState
}