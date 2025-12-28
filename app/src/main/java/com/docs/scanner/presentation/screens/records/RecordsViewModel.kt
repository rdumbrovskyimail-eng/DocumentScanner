package com.docs.scanner.presentation.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Records Screen ViewModel.
 * 
 * Session 8 Fixes:
 * - ✅ Added missing RecordsUiState class (was causing compile error!)
 * - ✅ Fixed currentFolderId (StateFlow instead of mutable var)
 * - ✅ Uses MoveRecordToFolderUseCase (correct implementation)
 * - ✅ Proper error handling
 * - ✅ Uses AllUseCases for consistency
 */
@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    // ✅ FIX: StateFlow instead of mutable var
    private val _currentFolderId = MutableStateFlow(-1L)
    val currentFolderId: StateFlow<Long> = _currentFolderId.asStateFlow()

    private val _uiState = MutableStateFlow<RecordsUiState>(RecordsUiState.Loading)
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    // All folders for move dialog
    val allFolders: StateFlow<List<Folder>> = useCases.getFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Load records for folder.
     * Also loads folder name.
     */
    fun loadRecords(folderId: Long) {
        if (folderId <= 0) {
            _uiState.value = RecordsUiState.Error("Invalid folder ID")
            return
        }

        _currentFolderId.value = folderId
        
        viewModelScope.launch {
            _uiState.value = RecordsUiState.Loading

            try {
                // Get folder name
                val folder = useCases.getFolderById(folderId)
                val folderName = folder?.name ?: "Records"

                // Get records
                useCases.getRecords(folderId)
                    .catch { e ->
                        _uiState.value = RecordsUiState.Error(
                            "Failed to load records: ${e.message}"
                        )
                    }
                    .collect { records ->
                        _uiState.value = RecordsUiState.Success(
                            folderId = folderId,
                            folderName = folderName,
                            records = records
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
     * Create new record.
     */
    fun createRecord(name: String, description: String?) {
        if (name.isBlank()) {
            updateErrorMessage("Name cannot be empty")
            return
        }

        val folderId = _currentFolderId.value
        if (folderId <= 0) {
            updateErrorMessage("No folder selected")
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

    /**
     * Update record name/description.
     */
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

    /**
     * Delete record.
     */
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

    /**
     * Move record to another folder.
     * ✅ FIX: Uses MoveRecordToFolderUseCase instead of manual update.
     */
    fun moveRecord(recordId: Long, targetFolderId: Long) {
        val currentFolderId = _currentFolderId.value
        
        if (currentFolderId == targetFolderId) {
            updateErrorMessage("Record is already in this folder")
            return
        }

        viewModelScope.launch {
            when (val result = useCases.moveRecord(recordId, targetFolderId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Reload current folder (record will disappear from list)
                    loadRecords(currentFolderId)
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to move record: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        val currentState = _uiState.value
        if (currentState is RecordsUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = null)
        }
    }

    /**
     * Helper to update error message in Success state.
     */
    private fun updateErrorMessage(message: String) {
        val currentState = _uiState.value
        if (currentState is RecordsUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = message)
        } else {
            _uiState.value = RecordsUiState.Error(message)
        }
    }
}

/**
 * UI State for Records Screen.
 * 
 * Session 8: CRITICAL FIX - This class was MISSING, causing compile error!
 */
sealed interface RecordsUiState {
    object Loading : RecordsUiState
    
    data class Success(
        val folderId: Long,
        val folderName: String,
        val records: List<Record>,
        val errorMessage: String? = null
    ) : RecordsUiState
    
    data class Error(val message: String) : RecordsUiState
}