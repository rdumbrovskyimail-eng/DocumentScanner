package com.docs.scanner.presentation.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.FolderId
import com.docs.scanner.domain.core.RecordId
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.AllUseCases
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
    
    private val _sortOrder = MutableStateFlow(RecordSortOrder.DATE_DESC)
    val sortOrder: StateFlow<RecordSortOrder> = _sortOrder.asStateFlow()
    
    // Mutable list для drag & drop
    private val _reorderableRecords = MutableStateFlow<List<Record>>(emptyList())

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
                    _sortOrder
                ) { records, sort ->
                    sortRecords(records, sort)
                }
                .catch { e ->
                    _uiState.value = RecordsUiState.Error(
                        "Failed to load records: ${e.message}"
                    )
                }
                .collect { records ->
                    _reorderableRecords.value = records
                    _uiState.value = RecordsUiState.Success(
                        folderId = folderId,
                        folderName = folderName,
                        records = records,
                        isQuickScansFolder = isQuickScans
                    )
                }
            } catch (e: Exception) {
                _uiState.value = RecordsUiState.Error(
                    "Failed to load data: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Sort records by selected order
     */
    private fun sortRecords(records: List<Record>, sort: RecordSortOrder): List<Record> {
        return when (sort) {
            RecordSortOrder.NAME_ASC -> records.sortedBy { it.name.lowercase() }
            RecordSortOrder.NAME_DESC -> records.sortedByDescending { it.name.lowercase() }
            RecordSortOrder.DATE_ASC -> records.sortedBy { it.createdAt }
            RecordSortOrder.DATE_DESC -> records.sortedByDescending { it.createdAt }
        }
    }
    
    /**
     * Set sort order
     */
    fun setSortOrder(order: RecordSortOrder) {
        _sortOrder.value = order
    }
    
    /**
     * Reorder records during drag & drop
     */
    fun reorderRecords(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState !is RecordsUiState.Success) return
        
        val records = currentState.records.toMutableList()
        
        if (fromIndex < 0 || fromIndex >= records.size || toIndex < 0 || toIndex >= records.size) return
        
        val item = records.removeAt(fromIndex)
        records.add(toIndex, item)
        
        _reorderableRecords.value = records
        _uiState.value = currentState.copy(records = records)
    }
    
    /**
     * Save record order after drag ends
     */
    fun saveRecordOrder() {
        viewModelScope.launch {
            _reorderableRecords.value.forEachIndexed { index, record ->
                useCases.records.updatePosition(record.id, index)
            }
        }
    }

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

// Enum для сортировки записей (единственное место определения)
enum class RecordSortOrder {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC
}
