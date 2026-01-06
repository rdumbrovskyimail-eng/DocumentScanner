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
 * 
 * Session 8+ Fixes:
 * - ✅ Removed manual loadFolders() calls (Flow auto-updates!)
 * - ✅ SharedFlow for navigation instead of callbacks
 * - ✅ Added Processing state for quick scan
 * - ✅ Uses AllUseCases for consistency
 * - ✅ Added clearQuickScans() for clearing Quick Scans folder
 * - ✅ Added moveFolderPosition() for reordering folders
 * - ✅ Sorting: Quick Scans first, then pinned, then by position/name
 */
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow<FoldersUiState>(FoldersUiState.Loading)
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    // ✅ SharedFlow for one-time navigation events
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    // Local ordering for folders (position overrides)
    // Key: folderId, Value: position (lower = higher in list)
    private val _folderPositions = MutableStateFlow<Map<Long, Int>>(emptyMap())

    init {
        loadFolders()
    }

    /**
     * Load folders with proper sorting.
     * ✅ Flow auto-updates, no need to call manually after operations!
     */
    private fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = FoldersUiState.Loading

            combine(
                showArchived.flatMapLatest { includeArchived ->
                    if (includeArchived) useCases.folders.observeIncludingArchived()
                    else useCases.folders.observeAll()
                },
                _folderPositions
            ) { folders, positions ->
                sortFolders(folders, positions)
            }
            .catch { e ->
                _uiState.value = FoldersUiState.Error(
                    "Failed to load folders: ${e.message}"
                )
            }
            .collect { sortedFolders ->
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
     * 2. Pinned folders next (sorted by name)
     * 3. Other folders by position (if set) or by name
     */
    private fun sortFolders(folders: List<Folder>, positions: Map<Long, Int>): List<Folder> {
        val quickScans = folders.filter { it.isQuickScans }
        val pinned = folders.filter { !it.isQuickScans && it.isPinned }
            .sortedBy { it.name.lowercase() }
        val others = folders.filter { !it.isQuickScans && !it.isPinned }
            .sortedWith(compareBy(
                { positions[it.id.value] ?: Int.MAX_VALUE },
                { it.name.lowercase() }
            ))
        
        return quickScans + pinned + others
    }

    fun setShowArchived(enabled: Boolean) {
        _showArchived.value = enabled
    }

    /**
     * Create new folder.
     * ✅ No manual loadFolders() - Flow updates automatically!
     */
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

    /**
     * Update folder.
     */
    fun updateFolder(folder: Folder) {
        viewModelScope.launch {
            when (val result = useCases.folders.update(folder)) {
                is DomainResult.Success -> Unit
                is DomainResult.Failure -> updateErrorMessage("Failed to update folder: ${result.error.message}")
            }
        }
    }

    /**
     * Delete folder.
     */
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
    
    /**
     * ✅ NEW: Clear all records in Quick Scans folder.
     */
    fun clearQuickScans() {
        viewModelScope.launch {
            try {
                // Get all records in Quick Scans
                val quickScansId = FolderId.QUICK_SCANS
                
                // Observe records once and delete them
                useCases.records.observeByFolder(quickScansId)
                    .first() // Get current list
                    .forEach { record ->
                        useCases.records.delete(record.id)
                    }
                    
            } catch (e: Exception) {
                updateErrorMessage("Failed to clear Quick Scans: ${e.message}")
            }
        }
    }
    
    /**
     * ✅ NEW: Move folder position up or down.
     * 
     * @param folderId The folder to move
     * @param direction -1 for up, +1 for down
     */
    fun moveFolderPosition(folderId: Long, direction: Int) {
        val currentState = _uiState.value
        if (currentState !is FoldersUiState.Success) return
        
        // Get current non-QuickScans, non-pinned folders (these are the ones that can be reordered)
        val reorderableFolders = currentState.folders
            .filter { !it.isQuickScans && !it.isPinned }
        
        val currentIndex = reorderableFolders.indexOfFirst { it.id.value == folderId }
        if (currentIndex == -1) return
        
        val newIndex = (currentIndex + direction).coerceIn(0, reorderableFolders.lastIndex)
        if (newIndex == currentIndex) return
        
        // Update positions map
        val newPositions = _folderPositions.value.toMutableMap()
        
        // Swap positions
        val targetFolder = reorderableFolders[newIndex]
        newPositions[folderId] = newIndex
        newPositions[targetFolder.id.value] = currentIndex
        
        _folderPositions.value = newPositions
    }

    /**
     * Quick scan with progress reporting.
     * ✅ Uses QuickScanState Flow + SharedFlow for navigation.
     */
    fun quickScan(imageUri: Uri) {
        viewModelScope.launch {
            // Set processing state
            _uiState.value = FoldersUiState.Processing(
                progress = 0,
                message = "Starting quick scan..."
            )

            try {
                useCases.quickScan(imageUri.toString())
                    .catch { e ->
                        updateErrorMessage("Quick scan failed: ${e.message}")
                        // Reset to folders list
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
                                // Emit navigation event
                                _navigationEvent.emit(
                                    NavigationEvent.NavigateToEditor(state.recordId.value)
                                )
                                
                                // Reset UI
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

    /**
     * Clear error message.
     */
    fun clearError() {
        val currentState = _uiState.value
        if (currentState is FoldersUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = null)
        }
    }

    /**
     * Helper to update error message.
     */
    private fun updateErrorMessage(message: String) {
        val currentState = _uiState.value
        if (currentState is FoldersUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = message)
        } else {
            _uiState.value = FoldersUiState.Error(message)
        }
    }
}

/**
 * UI State for Folders Screen.
 */
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

/**
 * Navigation events.
 */
sealed interface NavigationEvent {
    data class NavigateToEditor(val recordId: Long) : NavigationEvent
}
