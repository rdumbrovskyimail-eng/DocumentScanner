package com.docs.scanner.presentation.screens.camera

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.docs.scanner.data.remote.camera.DocumentScannerWrapper
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.usecase.AddDocumentUseCase
import com.docs.scanner.domain.usecase.CreateRecordUseCase
import com.docs.scanner.domain.usecase.GetFoldersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val documentScanner: DocumentScannerWrapper,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val createRecordUseCase: CreateRecordUseCase,
    private val getFoldersUseCase: GetFoldersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Loading)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun startScanner(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        viewModelScope.launch {
            _uiState.value = CameraUiState.Loading
            try {
                documentScanner.startScan(activity, launcher)
                _uiState.value = CameraUiState.Ready
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(e.message ?: "Scanner failed")
            }
        }
    }

    fun processScannedImages(uris: List<Uri>, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.value = CameraUiState.Processing("Creating record...")

            // Создаём запись в папке Quick Scans
            val folders = getFoldersUseCase().first()
            val quickFolderId = folders.firstOrNull { it.name == "Quick Scans" }?.id
                ?: createRecordUseCase(1L, "Quick Scan", null).let { /* fallback */ 1L } // упрощённо

            val firstUri = uris.firstOrNull() ?: run {
                _uiState.value = CameraUiState.Error("No images")
                return@launch
            }

            when (val result = addDocumentUseCase(quickFolderId, firstUri)) {
                is Result.Success -> onComplete(quickFolderId) // Переходим в редактор записи
                is Result.Error -> _uiState.value = CameraUiState.Error(result.exception.message ?: "Failed")
            }
        }
    }

    // остальные методы без изменений
}