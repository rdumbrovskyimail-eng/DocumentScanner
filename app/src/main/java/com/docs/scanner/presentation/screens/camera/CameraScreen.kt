package com.docs.scanner.presentation.screens.camera

import android.app.Activity
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
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel(),
    onScanComplete: (Long) -> Unit,  // ✅ Вызывается после создания документа
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.let { pages ->
                val uris = pages.mapNotNull { it.imageUri }
                if (uris.isNotEmpty()) {
                    // ✅ ИСПРАВЛЕНО: Передаем callback
                    viewModel.processScannedImages(uris, onScanComplete)
                }
            }
        } else {
            viewModel.onScanCancelled()
            onBackClick()
        }
    }
    
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        if (activity != null) {
            viewModel.startScanner(activity, scannerLauncher)
        } else {
            viewModel.onError("Context is not an Activity")
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Initializing scanner...")
                    }
                }
                
                is CameraUiState.Processing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = (uiState as CameraUiState.Processing).message,
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                            val activity = context as? Activity
                            if (activity != null) {
                                viewModel.startScanner(activity, scannerLauncher)
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                
                is CameraUiState.Ready -> {}
            }
        }
    }
}

sealed interface CameraUiState {
    data object Loading : CameraUiState
    data object Ready : CameraUiState
    data class Processing(val message: String) : CameraUiState
    data class Error(val message: String) : CameraUiState
}
