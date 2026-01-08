package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.FolderId
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.usecase.AllUseCases
import com.docs.scanner.domain.usecase.QuickScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow<FoldersUiState>(FoldersUiState.Loading)
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()
    
    private val _sortOrder = MutableStateFlow(SortOrder.NAME_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    private val _reorderableFolders = MutableStateFlow<List<Folder>>(emptyList())
    
    // Для показа ошибок через Snackbar независимо от состояния
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    init {
        loadFolders()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            combine(
                showArchived.flatMapLatest { includeArchived ->
                    if (includeArchived) useCases.folders.observeIncludingArchived()
                    else useCases.folders.observeAll()
                },
                _sortOrder
            ) { folders, sort ->
                sortFolders(folders, sort)
            }
            .catch { e ->
                _uiState.value = FoldersUiState.Error("Failed to load folders: ${e.message}")
            }
            .collect { sortedFolders ->
                _reorderableFolders.value = sortedFolders.filter { !it.isQuickScans && !it.isPinned }
                _uiState.value = if (sortedFolders.isEmpty()) {
                    FoldersUiState.Empty
                } else {
                    FoldersUiState.Success(sortedFolders)
                }
            }
        }
    }
    
    private fun sortFolders(folders: List<Folder>, sort: SortOrder): List<Folder> {
        val quickScans = folders.filter { it.isQuickScans }
        val pinned = folders.filter { !it.isQuickScans && it.isPinned }
            .sortedBy { it.name.lowercase() }
        val others = folders.filter { !it.isQuickScans && !it.isPinned }
            .let { list ->
                when (sort) {
                    SortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                    SortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                    SortOrder.DATE_ASC -> list.sortedBy { it.createdAt }
                    SortOrder.DATE_DESC -> list.sortedByDescending { it.createdAt }
                }
            }
        return quickScans + pinned + others
    }
    
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }
    
    fun reorderFolders(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState !is FoldersUiState.Success) return
        
        val quickScans = currentState.folders.filter { it.isQuickScans }
        val pinned = currentState.folders.filter { !it.isQuickScans && it.isPinned }
        val others = currentState.folders.filter { !it.isQuickScans && !it.isPinned }.toMutableList()
        
        if (fromIndex < 0 || fromIndex >= others.size || toIndex < 0 || toIndex >= others.size) return
        
        val item = others.removeAt(fromIndex)
        others.add(toIndex, item)
        
        _reorderableFolders.value = others
        _uiState.value = FoldersUiState.Success(quickScans + pinned + others)
    }
    
    fun saveFolderOrder() {
        viewModelScope.launch {
            _reorderableFolders.value.forEachIndexed { index, folder ->
                useCases.folders.updatePosition(folder.id, index)
            }
        }
    }

    fun setShowArchived(enabled: Boolean) {
        _showArchived.value = enabled
    }

    fun createFolder(name: String, description: String?) {
        if (name.isBlank()) {
            showError("Name cannot be empty")
            return
        }

        viewModelScope.launch {
            when (val result = useCases.folders.create(name.trim(), desc = description?.takeIf { it.isNotBlank() })) {
                is DomainResult.Success -> {
                    // Папка создана - Flow автоматически обновит UI
                }
                is DomainResult.Failure -> {
                    showError("Failed to create folder: ${result.error.message}")
                }
            }
        }
    }

    fun updateFolder(folder: Folder) {
        viewModelScope.launch {
            when (val result = useCases.folders.update(folder)) {
                is DomainResult.Success -> { /* Flow обновит */ }
                is DomainResult.Failure -> showError("Failed to update folder: ${result.error.message}")
            }
        }
    }

    fun deleteFolder(folderId: Long, deleteContents: Boolean = false) {
        viewModelScope.launch {
            when (val result = useCases.folders.delete(FolderId(folderId), deleteContents = deleteContents)) {
                is DomainResult.Success -> { /* Flow обновит */ }
                is DomainResult.Failure -> showError("Failed to delete folder: ${result.error.message}")
            }
        }
    }

    fun setPinned(folderId: Long, pinned: Boolean) {
        viewModelScope.launch {
            when (val result = useCases.folders.pin(FolderId(folderId), pinned)) {
                is DomainResult.Success -> { /* Flow обновит */ }
                is DomainResult.Failure -> showError("Failed to update pin: ${result.error.message}")
            }
        }
    }

    fun archive(folderId: Long) {
        viewModelScope.launch {
            when (val result = useCases.folders.archive(FolderId(folderId))) {
                is DomainResult.Success -> { /* Flow обновит */ }
                is DomainResult.Failure -> showError("Failed to archive folder: ${result.error.message}")
            }
        }
    }

    fun unarchive(folderId: Long) {
        viewModelScope.launch {
            when (val result = useCases.folders.unarchive(FolderId(folderId))) {
                is DomainResult.Success -> { /* Flow обновит */ }
                is DomainResult.Failure -> showError("Failed to unarchive folder: ${result.error.message}")
            }
        }
    }
    
    fun clearQuickScans() {
        viewModelScope.launch {
            try {
                val quickScansId = FolderId.QUICK_SCANS
                useCases.records.observeByFolder(quickScansId)
                    .first()
                    .forEach { record ->
                        useCases.records.delete(record.id)
                    }
            } catch (e: Exception) {
                showError("Failed to clear Quick Scans: ${e.message}")
            }
        }
    }

    fun quickScan(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = FoldersUiState.Processing(progress = 0, message = "Starting quick scan...")

            try {
                useCases.quickScan(imageUri.toString())
                    .catch { e ->
                        showError("Quick scan failed: ${e.message}")
                        loadFolders()
                    }
                    .collect { state ->
                        when (state) {
                            is QuickScanState.Preparing -> {
                                _uiState.value = FoldersUiState.Processing(5, "Preparing...")
                            }
                            is QuickScanState.CreatingRecord -> {
                                _uiState.value = FoldersUiState.Processing(20, "Creating record...")
                            }
                            is QuickScanState.SavingImage -> {
                                _uiState.value = FoldersUiState.Processing(
                                    40 + (state.progress.coerceIn(0, 100) * 20 / 100),
                                    "Saving image..."
                                )
                            }
                            is QuickScanState.Processing -> {
                                _uiState.value = FoldersUiState.Processing(70, "Processing OCR...")
                            }
                            is QuickScanState.Success -> {
                                _navigationEvent.emit(NavigationEvent.NavigateToEditor(state.recordId.value))
                                loadFolders()
                            }
                            is QuickScanState.Error -> {
                                showError("${state.stage}: ${state.error.message}")
                                loadFolders()
                            }
                        }
                    }
            } catch (e: Exception) {
                showError("Quick scan error: ${e.message}")
                loadFolders()
            }
        }
    }

    fun clearError() {
        val currentState = _uiState.value
        if (currentState is FoldersUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = null)
        }
    }

    private fun showError(message: String) {
        viewModelScope.launch {
            _errorMessage.emit(message)
        }
    }
}

sealed interface FoldersUiState {
    data object Loading : FoldersUiState
    data object Empty : FoldersUiState
    
    data class Success(
        val folders: List<Folder>,
        val errorMessage: String? = null
    ) : FoldersUiState
    
    data class Processing(
        val progress: Int,
        val message: String
    ) : FoldersUiState
    
    data class Error(val message: String) : FoldersUiState
}

sealed interface NavigationEvent {
    data class NavigateToEditor(val recordId: Long) : NavigationEvent
}

enum class SortOrder {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC
}