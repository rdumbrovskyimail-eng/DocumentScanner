package com.docs.scanner.presentation.screens.camera

import android.app.Activity
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.remote.camera.DocumentScannerWrapper
import com.docs.scanner.domain.model.Result
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
        if (isScannerActive) {
            android.util.Log.w("CameraViewModel", "⚠️ Scanner already active")
            return
        }
        
        viewModelScope.launch {
            isScannerActive = true
            _uiState.value = CameraUiState.Loading
            
            try {
                android.util.Log.d("CameraViewModel", "📸 Starting document scanner...")
                documentScanner.startScan(activity, launcher)
                _uiState.value = CameraUiState.Ready
                android.util.Log.d("CameraViewModel", "✅ Scanner ready")
            } catch (e: Exception) {
                android.util.Log.e("CameraViewModel", "❌ Scanner init failed", e)
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
                val firstUri = uris.firstOrNull()
                if (firstUri == null) {
                    android.util.Log.e("CameraViewModel", "❌ No images captured")
                    _uiState.value = CameraUiState.Error("No images captured")
                    return@launch
                }
                
                android.util.Log.d("CameraViewModel", "📄 Processing ${uris.size} image(s)")
                _uiState.value = CameraUiState.Processing("Processing document...")
                
                when (val result = quickScanUseCase(firstUri)) {
                    is Result.Success -> {
                        android.util.Log.d("CameraViewModel", "✅ Document created: ${result.data}")
                        onComplete(result.data)
                    }
                    is Result.Error -> {
                        val errorMsg = result.exception.message ?: "Processing failed"
                        android.util.Log.e("CameraViewModel", "❌ Processing failed: $errorMsg")
                        
                        // ✅ НОВОЕ: Специфичные сообщения об ошибках
                        val userMessage = when {
                            errorMsg.contains("quota", ignoreCase = true) -> 
                                "⚠️ API quota exceeded. Document saved without translation."
                            errorMsg.contains("Invalid API key", ignoreCase = true) -> 
                                "❌ Invalid API key. Please check settings."
                            errorMsg.contains("network", ignoreCase = true) -> 
                                "📡 Network error. Document saved, translation pending."
                            else -> errorMsg
                        }
                        
                        _uiState.value = CameraUiState.Error(userMessage)
                    }
                    else -> {
                        android.util.Log.e("CameraViewModel", "❌ Unknown error")
                        _uiState.value = CameraUiState.Error("Unknown error")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CameraViewModel", "❌ Exception processing images", e)
                _uiState.value = CameraUiState.Error(
                    e.message ?: "Failed to process images"
                )
            }
        }
    }
    
    fun onScanCancelled() {
        android.util.Log.d("CameraViewModel", "🚫 Scan cancelled")
        isScannerActive = false
        _uiState.value = CameraUiState.Loading
    }
    
    fun onError(message: String) {
        android.util.Log.e("CameraViewModel", "❌ External error: $message")
        isScannerActive = false
        _uiState.value = CameraUiState.Error(message)
    }
}