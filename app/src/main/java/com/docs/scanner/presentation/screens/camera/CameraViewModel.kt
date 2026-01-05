package com.docs.scanner.presentation.screens.camera

import android.app.Activity
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.remote.camera.DocumentScannerWrapper
import com.docs.scanner.domain.core.FolderId
import com.docs.scanner.domain.core.Folder
import com.docs.scanner.domain.usecase.MultiPageScanState
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

    private val _previewPages = MutableStateFlow<List<Uri>>(emptyList())
    val previewPages: StateFlow<List<Uri>> = _previewPages.asStateFlow()

    private val _previewPdf = MutableStateFlow<Uri?>(null)
    val previewPdf: StateFlow<Uri?> = _previewPdf.asStateFlow()

    private val _targetFolderId = MutableStateFlow<Long?>(null)
    val targetFolderId: StateFlow<Long?> = _targetFolderId.asStateFlow()

    val folders: StateFlow<List<Folder>> =
        useCases.folders.observeAll()
            .map { list -> list.filter { !it.isArchived } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

                documentScanner.startScan(activity, launcher, onError = { msg ->
                    _uiState.value = CameraUiState.Error(msg)
                })
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

        // For Stage 3: always show preview (multi-page).
        _previewPages.value = uris
        _uiState.value = CameraUiState.Preview
    }

    /**
     * Handle scan result from Activity.
     * Uses DocumentScannerWrapper.handleScanResult().
     */
    fun handleScanResult(result: com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult) {
        viewModelScope.launch {
            when (val scanResult = documentScanner.handleScanResult(result)) {
                is DocumentScannerWrapper.ScanResult.Success -> {
                    _previewPdf.value = scanResult.pdfUri
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

    fun removePreviewPage(index: Int) {
        val current = _previewPages.value
        if (index !in current.indices) return
        _previewPages.value = current.toMutableList().apply { removeAt(index) }
        if (_previewPages.value.isEmpty()) {
            _uiState.value = CameraUiState.Ready
        }
    }

    fun movePreviewPageUp(index: Int) {
        val current = _previewPages.value.toMutableList()
        if (index <= 0 || index !in current.indices) return
        val tmp = current[index - 1]
        current[index - 1] = current[index]
        current[index] = tmp
        _previewPages.value = current
    }

    fun movePreviewPageDown(index: Int) {
        val current = _previewPages.value.toMutableList()
        if (index !in current.indices || index >= current.lastIndex) return
        val tmp = current[index + 1]
        current[index + 1] = current[index]
        current[index] = tmp
        _previewPages.value = current
    }

    fun setTargetFolder(folderId: Long?) {
        _targetFolderId.value = folderId
    }

    fun clearPreview() {
        _previewPages.value = emptyList()
        _previewPdf.value = null
        _uiState.value = CameraUiState.Ready
    }

    fun savePreviewAsRecord() {
        val pages = _previewPages.value
        if (pages.isEmpty()) {
            _uiState.value = CameraUiState.Error("No pages to save")
            return
        }

        viewModelScope.launch {
            _uiState.value = CameraUiState.Processing(progress = 0, message = "Preparing…")
            val folder = _targetFolderId.value?.let { FolderId(it) }

            useCases.multiPageScan(
                imageUris = pages.map { it.toString() },
                targetFolderId = folder
            )
                .catch { e ->
                    _uiState.value = CameraUiState.Error("Processing failed: ${e.message}")
                }
                .collect { state ->
                    when (state) {
                        is MultiPageScanState.Preparing -> _uiState.value =
                            CameraUiState.Processing(progress = 5, message = "Preparing…")

                        is MultiPageScanState.CreatingRecord -> _uiState.value =
                            CameraUiState.Processing(progress = 15, message = "Creating record…")

                        is MultiPageScanState.SavingImage -> {
                            val base = 20
                            val span = 30
                            val progress = base + ((state.index * span) / state.total.coerceAtLeast(1))
                            _uiState.value = CameraUiState.Processing(
                                progress = progress.coerceIn(0, 95),
                                message = "Saving page ${state.index}/${state.total}…"
                            )
                        }

                        is MultiPageScanState.Processing -> {
                            val base = 55
                            val span = 40
                            val p = base + ((state.index * span) / state.total.coerceAtLeast(1))
                            val msg = when (state.state) {
                                is com.docs.scanner.domain.usecase.ProcessingState.OcrInProgress -> "OCR page ${state.index}/${state.total}…"
                                is com.docs.scanner.domain.usecase.ProcessingState.TranslationInProgress -> "Translating page ${state.index}/${state.total}…"
                                is com.docs.scanner.domain.usecase.ProcessingState.OcrComplete -> "OCR complete"
                                is com.docs.scanner.domain.usecase.ProcessingState.Complete -> "Done"
                                is com.docs.scanner.domain.usecase.ProcessingState.Failed -> "Failed"
                                is com.docs.scanner.domain.usecase.ProcessingState.Idle -> "Working…"
                            }
                            _uiState.value = CameraUiState.Processing(progress = p.coerceIn(0, 99), message = msg)
                        }

                        is MultiPageScanState.PageFailed -> {
                            // Keep going; user can retry per-page later in Editor.
                            _uiState.value = CameraUiState.Processing(
                                progress = 70,
                                message = "Page ${state.index}/${state.total} failed: ${state.error.message}"
                            )
                        }

                        is MultiPageScanState.Error -> _uiState.value =
                            CameraUiState.Error(state.error.message)

                        is MultiPageScanState.Success -> {
                            _uiState.value = CameraUiState.Success(state.recordId.value)
                            _navigationEvent.emit(NavigationEvent.NavigateToEditor(state.recordId.value))
                        }
                    }
                }
        }
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
    object Preview : CameraUiState
    
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