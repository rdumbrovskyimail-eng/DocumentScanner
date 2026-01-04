package com.docs.scanner.presentation.screens.camera

import android.app.Activity
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.remote.camera.DocumentScannerWrapper
import com.docs.scanner.domain.usecase.AllUseCases
import com.docs.scanner.domain.usecase.QuickScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Camera Screen ViewModel.
 * 
 * Session 8 Fixes:
 * - ✅ Added progress reporting via QuickScanState Flow
 * - ✅ Removed mutable flag (isScannerActive in state)
 * - ✅ Proper error handling
 * - ✅ SharedFlow for navigation events
 * - ✅ Uses AllUseCases for consistency
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val documentScanner: DocumentScannerWrapper,
    private val useCases: AllUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // ✅ FIX: SharedFlow for one-time navigation events
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    /**
     * Start document scanner.
     * Checks availability before starting.
     */
    fun startScanner(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val currentState = _uiState.value
        if (currentState is CameraUiState.ScannerActive) {
            return  // Already active
        }

        viewModelScope.launch {
            _uiState.value = CameraUiState.Loading

            try {
                // Check if scanner is available
                if (!documentScanner.isAvailable()) {
                    _uiState.value = CameraUiState.Error(
                        "Document Scanner requires Google Play Services 23.0+"
                    )
                    return@launch
                }

                documentScanner.startScan(activity, launcher)
                _uiState.value = CameraUiState.ScannerActive
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(
                    "Failed to start scanner: ${e.message}"
                )
            }
        }
    }

    /**
     * Process scanned images.
     * ✅ FIX: Uses QuickScanState Flow for progress reporting.
     */
    fun processScannedImages(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _uiState.value = CameraUiState.Error("No images captured")
            return
        }

        viewModelScope.launch {
            _uiState.value = CameraUiState.Processing(
                progress = 0,
                message = "Starting..."
            )

            try {
                useCases.quickScan(uris.first().toString())
                    .catch { e ->
                        _uiState.value = CameraUiState.Error(
                            "Processing failed: ${e.message}"
                        )
                    }
                    .collect { state ->
                        when (state) {
                            is QuickScanState.CreatingRecord -> {
                                _uiState.value = CameraUiState.Processing(
                                    progress = 20,
                                    message = "Creating record..."
                                )
                            }
                            is QuickScanState.Success -> {
                                _uiState.value = CameraUiState.Success(state.recordId.value)
                                
                                // Emit navigation event
                                _navigationEvent.emit(
                                    NavigationEvent.NavigateToEditor(state.recordId.value)
                                )
                            }
                            is QuickScanState.Error -> {
                                _uiState.value = CameraUiState.Error(state.error.message)
                            }
                            is QuickScanState.Preparing -> {
                                _uiState.value = CameraUiState.Processing(
                                    progress = 5,
                                    message = "Preparing..."
                                )
                            }
                            is QuickScanState.SavingImage -> {
                                _uiState.value = CameraUiState.Processing(
                                    progress = 30 + (state.progress.coerceIn(0, 100) * 20 / 100),
                                    message = "Saving image..."
                                )
                            }
                            is QuickScanState.Processing -> {
                                _uiState.value = CameraUiState.Processing(
                                    progress = 60,
                                    message = when (state.state) {
                                        is com.docs.scanner.domain.usecase.ProcessingState.OcrInProgress -> "Running OCR..."
                                        is com.docs.scanner.domain.usecase.ProcessingState.TranslationInProgress -> "Translating..."
                                        is com.docs.scanner.domain.usecase.ProcessingState.OcrComplete -> "OCR complete"
                                        is com.docs.scanner.domain.usecase.ProcessingState.Complete -> "Done"
                                        is com.docs.scanner.domain.usecase.ProcessingState.Failed -> "Failed"
                                        is com.docs.scanner.domain.usecase.ProcessingState.Idle -> "Working..."
                                    }
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(
                    "Unexpected error: ${e.message}"
                )
            }
        }
    }

    /**
     * Handle scan result from Activity.
     * Uses DocumentScannerWrapper.handleScanResult().
     */
    fun handleScanResult(result: com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult) {
        viewModelScope.launch {
            when (val scanResult = documentScanner.handleScanResult(result)) {
                is DocumentScannerWrapper.ScanResult.Success -> {
                    processScannedImages(scanResult.imageUris)
                }
                is DocumentScannerWrapper.ScanResult.Error -> {
                    _uiState.value = CameraUiState.Error(scanResult.message)
                }
            }
        }
    }

    /**
     * Handle scan cancellation.
     */
    fun onScanCancelled() {
        _uiState.value = CameraUiState.Idle
    }

    fun onError(message: String) {
        _uiState.value = CameraUiState.Error(message)
    }

    /**
     * Reset to ready state.
     */
    fun resetState() {
        _uiState.value = CameraUiState.Ready
    }

    /**
     * Clear error and return to ready state.
     */
    fun clearError() {
        _uiState.value = CameraUiState.Ready
    }
}

/**
 * UI State for Camera Screen.
 * 
 * Session 8 Fix: Added granular states for progress reporting.
 */
sealed interface CameraUiState {
    object Idle : CameraUiState
    object Loading : CameraUiState
    object Ready : CameraUiState
    object ScannerActive : CameraUiState
    
    data class Processing(
        val progress: Int,
        val message: String
    ) : CameraUiState
    
    data class Success(val recordId: Long) : CameraUiState
    data class Error(val message: String) : CameraUiState
}

/**
 * Navigation events.
 * ✅ Session 8: SharedFlow for one-time events (not StateFlow).
 */
sealed interface NavigationEvent {
    data class NavigateToEditor(val recordId: Long) : NavigationEvent
}