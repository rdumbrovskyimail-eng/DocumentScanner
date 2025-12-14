package com.docs.scanner.presentation.screens.camera

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.remote.camera.DocumentScannerWrapper
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel(),
    onImageCaptured: (List<Uri>) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        scanResult?.pages?.let { pages ->
            val uris = pages.mapNotNull { it.imageUri }
            if (uris.isNotEmpty()) {
                onImageCaptured(uris)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.startScanner { request ->
            scannerLauncher.launch(request)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is CameraUiState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Initializing scanner...")
                    }
                }
                
                is CameraUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Scanner Error",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (uiState as CameraUiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.startScanner { request ->
                                scannerLauncher.launch(request)
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                
                is CameraUiState.Ready -> {
                    // Scanner UI handled by Google ML Kit
                }
            }
        }
    }
}

sealed interface CameraUiState {
    data object Loading : CameraUiState
    data object Ready : CameraUiState
    data class Error(val message: String) : CameraUiState
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val documentScanner: DocumentScannerWrapper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Loading)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    
    fun startScanner(onRequestReady: (IntentSenderRequest) -> Unit) {
        viewModelScope.launch {
            _uiState.value = CameraUiState.Loading
            
            try {
                val request = documentScanner.getStartScanIntent()
                _uiState.value = CameraUiState.Ready
                onRequestReady(request)
            } catch (e: Exception) {
                _uiState.value = CameraUiState.Error(
                    e.message ?: "Failed to initialize scanner"
                )
            }
        }
    }
}