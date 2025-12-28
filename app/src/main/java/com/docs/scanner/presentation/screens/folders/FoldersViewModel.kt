package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Session 8 Fixes:
 * - ✅ Removed manual loadFolders() calls (Flow auto-updates!)
 * - ✅ SharedFlow for navigation instead of callbacks
 * - ✅ Added Processing state for quick scan
 * - ✅ Uses AllUseCases for consistency
 */
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow<FoldersUiState>(FoldersUiState.Loading)
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()

    // ✅ FIX: SharedFlow for one-time navigation events
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        loadFolders()
    }

    /**
     * Load folders.
     * ✅ FIX: Flow auto-updates, no need to call manually after operations!
     */
    private fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = FoldersUiState.Loading

            useCases.getFolders()
                .catch { e ->
                    _uiState.value = FoldersUiState.Error(
                        "Failed to load folders: ${e.message}"
                    )
                }
                .collect { folders ->
                    _uiState.value = if (folders.isEmpty()) {
                        FoldersUiState.Empty
                    } else {
                        FoldersUiState.Success(folders)
                    }
                }
        }
    }

    /**
     * Create new folder.
     * ✅ FIX: No manual loadFolders() - Flow updates automatically!
     */
    fun createFolder(name: String, description: String?) {
        if (name.isBlank()) {
            updateErrorMessage("Name cannot be empty")
            return
        }

        viewModelScope.launch {
            when (val result = useCases.createFolder(name.trim(), description)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow, no manual call needed!
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to create folder: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Update folder.
     */
    fun updateFolder(folder: Folder) {
        viewModelScope.launch {
            when (val result = useCases.updateFolder(folder)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to update folder: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Delete folder.
     */
    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            when (val result = useCases.deleteFolder(folderId)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to delete folder: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Quick scan with progress reporting.
     * ✅ FIX: Uses QuickScanState Flow + SharedFlow for navigation.
     */
    fun quickScan(imageUri: Uri) {
        viewModelScope.launch {
            // Set processing state
            _uiState.value = FoldersUiState.Processing(
                progress = 0,
                message = "Starting quick scan..."
            )

            try {
                useCases.quickScan(imageUri)
                    .catch { e ->
                        updateErrorMessage("Quick scan failed: ${e.message}")
                        // Reset to folders list
                        loadFolders()
                    }
                    .collect { state ->
                        when (state) {
                            is QuickScanState.CreatingStructure,
                            is QuickScanState.CreatingFolder,
                            is QuickScanState.CreatingRecord,
                            is QuickScanState.ScanningImage,
                            is QuickScanState.ProcessingOcr,
                            is QuickScanState.Translating -> {
                                _uiState.value = FoldersUiState.Processing(
                                    progress = when (state) {
                                        is QuickScanState.CreatingStructure -> state.progress
                                        is QuickScanState.CreatingFolder -> state.progress
                                        is QuickScanState.CreatingRecord -> state.progress
                                        is QuickScanState.ScanningImage -> state.progress
                                        is QuickScanState.ProcessingOcr -> state.progress
                                        is QuickScanState.Translating -> state.progress
                                        else -> 0
                                    },
                                    message = when (state) {
                                        is QuickScanState.CreatingStructure -> state.message
                                        is QuickScanState.CreatingFolder -> state.message
                                        is QuickScanState.CreatingRecord -> state.message
                                        is QuickScanState.ScanningImage -> state.message
                                        is QuickScanState.ProcessingOcr -> state.message
                                        is QuickScanState.Translating -> state.message
                                        else -> ""
                                    }
                                )
                            }
                            is QuickScanState.Success -> {
                                // Emit navigation event
                                _navigationEvent.emit(
                                    NavigationEvent.NavigateToEditor(state.recordId)
                                )
                                
                                // Reset UI
                                loadFolders()
                            }
                            is QuickScanState.Error -> {
                                updateErrorMessage(state.message)
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
    object Loading : FoldersUiState
    object Empty : FoldersUiState
    
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