package com.docs.scanner.presentation.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val getRecordsUseCase: GetRecordsUseCase,
    private val createRecordUseCase: CreateRecordUseCase,
    private val updateRecordUseCase: UpdateRecordUseCase,
    private val deleteRecordUseCase: DeleteRecordUseCase,
    private val getFoldersUseCase: GetFoldersUseCase
) : ViewModel() {

    private var currentFolderId: Long = -1L

    private val _uiState = MutableStateFlow<RecordsUiState>(RecordsUiState.Loading)
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    private val _folderName = MutableStateFlow("Records")
    val folderName: StateFlow<String> = _folderName.asStateFlow()

    val allFolders: StateFlow<List<Folder>> = getFoldersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadRecords(folderId: Long) {
        currentFolderId = folderId
        viewModelScope.launch {
            // Можно загрузить имя папки, если нужно
            getRecordsUseCase(folderId)
                .catch { _uiState.value = RecordsUiState.Error("Load error") }
                .collect { records ->
                    _uiState.value = if (records.isEmpty()) RecordsUiState.Empty else RecordsUiState.Success(records)
                }
        }
    }

    fun createRecord(name: String, description: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            createRecordUseCase(currentFolderId, name, description)
            loadRecords(currentFolderId)
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch {
            updateRecordUseCase(record)
            loadRecords(currentFolderId)
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            deleteRecordUseCase(id)
            loadRecords(currentFolderId)
        }
    }

    fun moveRecord(recordId: Long, newFolderId: Long) {
        viewModelScope.launch {
            // Получаем запись и обновляем folderId
            val records = (_uiState.value as? RecordsUiState.Success)?.records ?: return@launch
            val record = records.find { it.id == recordId } ?: return@launch
            updateRecordUseCase(record.copy(folderId = newFolderId))
            loadRecords(currentFolderId) // Обновляем текущий список
        }
    }
}