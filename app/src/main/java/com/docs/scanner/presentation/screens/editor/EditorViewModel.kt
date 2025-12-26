package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.FolderRepository
import com.docs.scanner.domain.repository.RecordRepository
import com.docs.scanner.domain.usecase.*
import com.docs.scanner.util.Debouncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val getDocumentsUseCase: GetDocumentsUseCase,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val retryTranslationUseCase: RetryTranslationUseCase,
    private val fixOcrUseCase: FixOcrUseCase,
    private val documentRepository: DocumentRepository,
    private val recordRepository: RecordRepository,
    private val folderRepository: FolderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val recordId: Long = savedStateHandle.get<Long>("recordId") ?: -1L
    
    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    
    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record.asStateFlow()
    
    private val _folderName = MutableStateFlow<String?>(null)
    val folderName: StateFlow<String?> = _folderName.asStateFlow()
    
    // ✅ НОВОЕ: Error state для показа пользователю
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // ✅ НОВОЕ: Debouncer для добавления документов
    private val addDocumentDebouncer = Debouncer(1000L, viewModelScope)
    
    init {
        if (recordId > 0) {
            loadRecord(recordId)
        } else {
            _uiState.value = EditorUiState.Error("Invalid record ID")
            android.util.Log.e("EditorViewModel", "❌ Invalid recordId: $recordId")
        }
    }
    
    fun loadRecord(recordId: Long) {
        if (recordId <= 0) {
            _uiState.value = EditorUiState.Error("Invalid record ID")
            android.util.Log.e("EditorViewModel", "❌ Invalid recordId: $recordId")
            return
        }
        
        viewModelScope.launch {
            try {
                android.util.Log.d("EditorViewModel", "🔄 Loading record: $recordId")
                
                val record = recordRepository.getRecordById(recordId)
                if (record == null) {
                    android.util.Log.e("EditorViewModel", "❌ Record not found: $recordId")
                    _uiState.value = EditorUiState.Error("Record not found")
                    return@launch
                }
                
                _record.value = record
                android.util.Log.d("EditorViewModel", "✅ Record loaded: ${record.name}")
                
                // Загружаем название папки
                val folder = folderRepository.getFolderById(record.folderId)
                _folderName.value = folder?.name
                android.util.Log.d("EditorViewModel", "✅ Folder: ${folder?.name}")
                
                _uiState.value = EditorUiState.Loading
                
                getDocumentsUseCase(recordId)
                    .catch { e ->
                        android.util.Log.e("EditorViewModel", "❌ Error loading documents", e)
                        _uiState.value = EditorUiState.Error(
                            e.message ?: "Failed to load documents"
                        )
                    }
                    .collect { documents ->
                        android.util.Log.d("EditorViewModel", "✅ Documents loaded: ${documents.size}")
                        _uiState.value = if (documents.isEmpty()) {
                            EditorUiState.Empty
                        } else {
                            EditorUiState.Success(documents)
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "❌ Fatal error in loadRecord", e)
                _uiState.value = EditorUiState.Error(
                    e.message ?: "Failed to load record"
                )
            }
        }
    }
    
    fun addDocument(imageUri: Uri) {
        if (recordId <= 0) {
            android.util.Log.e("EditorViewModel", "❌ Cannot add document: invalid record ID")
            _errorMessage.value = "Cannot add document: invalid record"
            return
        }
        
        // ✅ НОВОЕ: Используем debouncer
        addDocumentDebouncer.invoke {
            viewModelScope.launch {
                try {
                    android.util.Log.d("EditorViewModel", "📄 Adding document: $imageUri")
                    
                    when (val result = addDocumentUseCase(recordId, imageUri)) {
                        is Result.Success -> {
                            android.util.Log.d("EditorViewModel", "✅ Document added: ${result.data}")
                            _errorMessage.value = null
                        }
                        is Result.Error -> {
                            val errorMsg = result.exception.message ?: "Unknown error"
                            android.util.Log.e("EditorViewModel", "❌ Error adding document: $errorMsg")
                            
                            // ✅ НОВОЕ: Обработка специфичных ошибок
                            _errorMessage.value = when {
                                errorMsg.contains("quota", ignoreCase = true) -> 
                                    "⚠️ API quota exceeded. Translation will be skipped."
                                errorMsg.contains("Invalid API key", ignoreCase = true) -> 
                                    "❌ Invalid API key. Please check settings."
                                errorMsg.contains("network", ignoreCase = true) -> 
                                    "📡 Network error. Check your connection."
                                else -> "❌ Error: $errorMsg"
                            }
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EditorViewModel", "❌ Exception adding document", e)
                    _errorMessage.value = "Error adding document: ${e.message}"
                }
            }
        }
    }
    
    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            try {
                android.util.Log.d("EditorViewModel", "🗑️ Deleting document: $documentId")
                deleteDocumentUseCase(documentId)
                android.util.Log.d("EditorViewModel", "✅ Document deleted")
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "❌ Error deleting document", e)
                _errorMessage.value = "Failed to delete document"
            }
        }
    }
    
    fun updateOriginalText(documentId: Long, newText: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("EditorViewModel", "💾 Updating text: ${newText.take(50)}...")
                documentRepository.updateOriginalText(documentId, newText)
                android.util.Log.d("EditorViewModel", "✅ Text updated")
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "❌ Error updating text", e)
                _errorMessage.value = "Failed to update text"
            }
        }
    }
    
    fun retryOcr(documentId: Long) {
        viewModelScope.launch {
            try {
                android.util.Log.d("EditorViewModel", "🔄 Retrying OCR: $documentId")
                
                when (val result = fixOcrUseCase(documentId)) {
                    is Result.Success -> {
                        android.util.Log.d("EditorViewModel", "✅ OCR retry successful")
                        _errorMessage.value = null
                    }
                    is Result.Error -> {
                        val errorMsg = result.exception.message ?: "Unknown error"
                        android.util.Log.e("EditorViewModel", "❌ OCR retry failed: $errorMsg")
                        
                        _errorMessage.value = when {
                            errorMsg.contains("quota", ignoreCase = true) -> 
                                "⚠️ API quota exceeded"
                            else -> "OCR retry failed: $errorMsg"
                        }
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "❌ Exception in retryOcr", e)
                _errorMessage.value = "OCR retry error: ${e.message}"
            }
        }
    }
    
    fun retryTranslation(documentId: Long) {
        viewModelScope.launch {
            try {
                android.util.Log.d("EditorViewModel", "🔄 Retrying translation: $documentId")
                
                when (val result = retryTranslationUseCase(documentId)) {
                    is Result.Success -> {
                        android.util.Log.d("EditorViewModel", "✅ Translation retry successful")
                        _errorMessage.value = null
                    }
                    is Result.Error -> {
                        val errorMsg = result.exception.message ?: "Unknown error"
                        android.util.Log.e("EditorViewModel", "❌ Translation retry failed: $errorMsg")
                        
                        _errorMessage.value = when {
                            errorMsg.contains("quota", ignoreCase = true) -> 
                                "⚠️ API quota exceeded. Please wait 1 hour."
                            else -> "Translation failed: $errorMsg"
                        }
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "❌ Exception in retryTranslation", e)
                _errorMessage.value = "Translation error: ${e.message}"
            }
        }
    }
    
    fun updateRecordName(newName: String) {
        if (newName.isBlank()) {
            android.util.Log.w("EditorViewModel", "⚠️ Record name cannot be empty")
            _errorMessage.value = "Record name cannot be empty"
            return
        }
        
        viewModelScope.launch {
            try {
                _record.value?.let { rec ->
                    android.util.Log.d("EditorViewModel", "💾 Updating record name: $newName")
                    val updated = rec.copy(name = newName)
                    recordRepository.updateRecord(updated)
                    _record.value = updated
                    android.util.Log.d("EditorViewModel", "✅ Record name updated")
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "❌ Error updating record name", e)
                _errorMessage.value = "Failed to update name"
            }
        }
    }
    
    fun updateRecordDescription(newDescription: String?) {
        viewModelScope.launch {
            try {
                _record.value?.let { rec ->
                    android.util.Log.d("EditorViewModel", "💾 Updating description")
                    val updated = rec.copy(description = newDescription)
                    recordRepository.updateRecord(updated)
                    _record.value = updated
                    android.util.Log.d("EditorViewModel", "✅ Description updated")
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "❌ Error updating description", e)
                _errorMessage.value = "Failed to update description"
            }
        }
    }
    
    // ✅ НОВОЕ: Очистка ошибки
    fun clearError() {
        _errorMessage.value = null
    }
}