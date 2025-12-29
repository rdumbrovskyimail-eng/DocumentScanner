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
 * ⚠️ TEMPORARILY DISABLED FOR DEBUGGING - Hilt annotations removed
 */
// @HiltViewModel
class RecordsViewModel(
    private val useCases: AllUseCases
) : ViewModel() {

    private val _currentFolderId = MutableStateFlow(-1L)
    val currentFolderId: StateFlow<Long> = _currentFolderId.asStateFlow()

    private val _uiState = MutableStateFlow<RecordsUiState>(RecordsUiState.Loading)
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()
}

sealed interface RecordsUiState {
    data object Loading : RecordsUiState
    
    data class Success(
        val folderId: Long,
        val folderName: String,
        val records: List<Record>,
        val errorMessage: String? = null
    ) : RecordsUiState
    
    data class Error(val message: String) : RecordsUiState
}