package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.FolderId
import com.docs.scanner.domain.core.RecordId
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.usecase.AllUseCases
import com.docs.scanner.domain.usecase.QuickScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Folders Screen ViewModel.
 */
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
    
    // Mutable list для drag & drop
    private val _reorderableFolders = MutableStateFlow<List<Folder>>(emptyList())

    init {
        loadFolders()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = FoldersUiState.Loading

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
                _uiState.value = FoldersUiState.Error(
                    "Failed to load folders: ${e.message}"
                )
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
    
    /**
     * Sort folders:
     * 1. Quick Scans always first
     * 2. Pinned folders next
     * 3. Other folders by selected sort order
     */
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
    
    /**
     * Set sort order
     */
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }
    
    /**
     * Reorder folders during drag & drop
     */
    fun reorderFolders(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState !is FoldersUiState.Success) return
        
        val quickScans = currentState.folders.filter { it.isQuickScans }
        val pinned = currentState.folders.filter { !it.isQuickScans && it.isPinned }
        val others = currentState.folders.filter { !it.isQuickScans && !it.isPinned }.toMutableList()
        
        if (fromIndex < 0 || fromIndex >= others.size || toIndex < 0 || toIndex >= others.size) return
        
        // Swap
        val item = others.removeAt(fromIndex)
        others.add(toIndex, item)
        
        _reorderableFolders.value = others
        _uiState.value = FoldersUiState.Success(quickScans + pinned + others)
    }
    
    /**
     * Save folder order after drag ends
     */
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
            updateErrorMessage("Name cannot be empty")
            return
        }

        viewModelScope.launch {
            when (val result = useCases.folders.create(name.trim(), desc = description?.takeIf { it.isNotBlank() })) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> updateErrorMessage("Failed to create folder: ${result.error.message}")
            }
        }
    }

    fun updateFolder(folder: Folder) {
        viewModelScope.launch {
            when (val result = useCases.folders.update(folder)) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> updateErrorMessage("Failed to update folder: ${result.error.message}")
            }
        }
    }

    fun deleteFolder(folderId: Long, deleteContents: Boolean = false) {
        viewModelScope.launch {
            when (val result = useCases.folders.delete(FolderId(folderId), deleteContents = deleteContents)) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> updateErrorMessage("Failed to delete folder: ${result.error.message}")
            }
        }
    }

    fun setPinned(folderId: Long, pinned: Boolean) {
        viewModelScope.launch {
            when (val result = useCases.folders.pin(FolderId(folderId), pinned)) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> updateErrorMessage("Failed to update pin: ${result.error.message}")
            }
        }
    }

    fun archive(folderId: Long) {
        viewModelScope.launch {
            when (val result = useCases.folders.archive(FolderId(folderId))) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> updateErrorMessage("Failed to archive folder: ${result.error.message}")
            }
        }
    }

    fun unarchive(folderId: Long) {
        viewModelScope.launch {
            when (val result = useCases.folders.unarchive(FolderId(folderId))) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> updateErrorMessage("Failed to unarchive folder: ${result.error.message}")
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
                updateErrorMessage("Failed to clear Quick Scans: ${e.message}")
            }
        }
    }
    
    /**
     * @deprecated Use reorderFolders() + saveFolderOrder() instead
     */
    fun moveFolderPosition(folderId: Long, direction: Int) {
        val currentState = _uiState.value
        if (currentState !is FoldersUiState.Success) return
        
        val reorderableFolders = currentState.folders
            .filter { !it.isQuickScans && !it.isPinned }
        
        val currentIndex = reorderableFolders.indexOfFirst { it.id.value == folderId }
        if (currentIndex == -1) return
        
        val newIndex = (currentIndex + direction).coerceIn(0, reorderableFolders.lastIndex)
        if (newIndex == currentIndex) return
        
        reorderFolders(currentIndex, newIndex)
        saveFolderOrder()
    }

    fun quickScan(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = FoldersUiState.Processing(
                progress = 0,
                message = "Starting quick scan..."
            )

            try {
                useCases.quickScan(imageUri.toString())
                    .catch { e ->
                        updateErrorMessage("Quick scan failed: ${e.message}")
                        loadFolders()
                    }
                    .collect { state ->
                        when (state) {
                            is QuickScanState.Preparing,
                            is QuickScanState.CreatingRecord,
                            is QuickScanState.SavingImage,
                            is QuickScanState.Processing -> {
                                _uiState.value = FoldersUiState.Processing(
                                    progress = when (state) {
                                        is QuickScanState.Preparing -> 5
                                        is QuickScanState.CreatingRecord -> 20
                                        is QuickScanState.SavingImage -> 40 + (state.progress.coerceIn(0, 100) * 20 / 100)
                                        is QuickScanState.Processing -> 70
                                        else -> 0
                                    },
                                    message = when (state) {
                                        is QuickScanState.Preparing -> "Preparing..."
                                        is QuickScanState.CreatingRecord -> "Creating record..."
                                        is QuickScanState.SavingImage -> "Saving image..."
                                        is QuickScanState.Processing -> "Processing OCR..."
                                        else -> ""
                                    }
                                )
                            }
                            is QuickScanState.Success -> {
                                _navigationEvent.emit(
                                    NavigationEvent.NavigateToEditor(state.recordId.value)
                                )
                                loadFolders()
                            }
                            is QuickScanState.Error -> {
                                updateErrorMessage("${state.stage}: ${state.error.message}")
                                loadFolders()
                            }
                        }
                    }
            } catch (e: Exception) {
                updateErrorMessage("Quick scan error: ${e.message}")
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

    private fun updateErrorMessage(message: String) {
        val currentState = _uiState.value
        if (currentState is FoldersUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = message)
        } else {
            _uiState.value = FoldersUiState.Error(message)
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

// Enum для сортировки
enum class SortOrder {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC
}