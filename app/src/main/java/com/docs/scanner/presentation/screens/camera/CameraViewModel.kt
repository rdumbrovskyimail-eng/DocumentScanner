package com.docs.scanner.presentation.screens.camera

import android.app.Activity
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.remote.camera.DocumentScannerWrapper
import com.docs.scanner.domain.usecase.QuickScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val documentScanner: DocumentScannerWrapper,
    private val quickScanUseCase: QuickScanUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Loading)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    
    private var isScannerActive = false
    
    fun startScanner(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (isScannerActive) return
        
        viewModelScope.launch {
            isScannerActive = true
            _uiState.value = CameraUiState.Loading
            
            try {
                documentScanner.startScan(activity, launcher)
                _uiState.value = CameraUiState.Ready
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(
                    e.message ?: "Failed to initialize scanner"
                )
                isScannerActive = false
            }
        }
    }
    
    fun processScannedImages(uris: List<Uri>, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.value = CameraUiState.Processing("Creating record...")
            
            try {
                val firstUri = uris.firstOrNull() ?: run {
                    _uiState.value = CameraUiState.Error("No images captured")
                    return@launch
                }
                
                _uiState.value = CameraUiState.Processing("Processing document...")
                
                when (val result = quickScanUseCase(firstUri)) {
                    is com.docs.scanner.domain.model.Result.Success -> {
                        onComplete(result.data)
                    }
                    is com.docs.scanner.domain.model.Result.Error -> {
                        _uiState.value = CameraUiState.Error(
                            result.exception.message ?: "Processing failed"
                        )
                    }
                    else -> {
                        _uiState.value = CameraUiState.Error("Unknown error")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(
                    e.message ?: "Failed to process images"
                )
            }
        }
    }
    
    fun onScanCancelled() {
        isScannerActive = false
        _uiState.value = CameraUiState.Loading
    }
    
    fun onError(message: String) {
        isScannerActive = false
        _uiState.value = CameraUiState.Error(message)
    }
}
