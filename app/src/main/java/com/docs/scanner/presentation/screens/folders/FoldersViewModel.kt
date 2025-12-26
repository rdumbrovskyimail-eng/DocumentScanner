package com.docs.scanner.presentation.screens.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FoldersUiState {
    object Loading : FoldersUiState
    object Empty : FoldersUiState
    data class Success(val folders: List<Folder>) : FoldersUiState
    data class Error(val message: String) : FoldersUiState
}

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val getFoldersUseCase: GetFoldersUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val updateFolderUseCase: UpdateFolderUseCase,
    private val deleteFolderUseCase: DeleteFolderUseCase,
    private val quickScanUseCase: QuickScanUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<FoldersUiState>(FoldersUiState.Loading)
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    init {
        loadFolders()
    }

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = FoldersUiState.Loading
            getFoldersUseCase()
                .catch { 
                    _uiState.value = FoldersUiState.Error("Failed to load folders")
                }
                .collect { folders ->
                    _uiState.value = if (folders.isEmpty()) FoldersUiState.Empty else FoldersUiState.Success(folders)
                }
        }
    }

    fun createFolder(name: String, description: String?) {
        viewModelScope.launch {
            createFolderUseCase(name, description)
            loadFolders() // Обновляем список
        }
    }

    fun updateFolder(folder: Folder) {
        viewModelScope.launch {
            updateFolderUseCase(folder)
            loadFolders()
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            deleteFolderUseCase(id)
            loadFolders()
        }
    }

    fun quickScan(
        imageUri: Uri,
        onComplete: (Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            when (val result = quickScanUseCase(imageUri)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    onComplete(result.data)
                    loadFolders() // Важно: обновляем список папок
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    _errorMessage.value = result.exception.message ?: "Quick scan failed"
                    onError(result.exception)
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = ""
    }
}