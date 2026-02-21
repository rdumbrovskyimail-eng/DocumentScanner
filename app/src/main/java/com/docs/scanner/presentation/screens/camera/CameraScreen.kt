package com.docs.scanner.presentation.screens.camera

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel(),
    onScanComplete: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pages by viewModel.previewPages.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val targetFolderId by viewModel.targetFolderId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToEditor -> onScanComplete(event.recordId)
            }
        }
    }
    
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null) {
                viewModel.handleScanResult(scanResult)
            } else {
                viewModel.onError("Empty scan result")
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
                is CameraUiState.Idle,
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
                
                is CameraUiState.Preview -> {
                    PreviewContent(
                        pages = pages,
                        folders = folders,
                        selectedFolderId = targetFolderId,
                        onSelectFolder = viewModel::setTargetFolder,
                        onMoveUp = viewModel::movePreviewPageUp,
                        onMoveDown = viewModel::movePreviewPageDown,
                        onDelete = viewModel::removePreviewPage,
                        onRescan = {
                            val activity = context as? Activity
                            if (activity != null) {
                                viewModel.clearPreview()
                                viewModel.startScanner(activity, scannerLauncher)
                            }
                        },
                        onSave = viewModel::savePreviewAsRecord,
                        onCancel = onBackClick
                    )
                }

                is CameraUiState.Ready,
                is CameraUiState.ScannerActive -> {
                    // No UI while scanner is active.
                }

                is CameraUiState.Success -> {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    pages: List<android.net.Uri>,
    folders: List<com.docs.scanner.domain.core.Folder>,
    selectedFolderId: Long?,
    onSelectFolder: (Long?) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onRescan: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var folderMenu by remember { mutableStateOf(false) }
    val selectedFolderName = folders.firstOrNull { it.id.value == selectedFolderId }?.name ?: "Quick Scans"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Preview (${pages.size} pages)", style = MaterialTheme.typography.titleMedium)

        OutlinedButton(onClick = { folderMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save to: $selectedFolderName")
        }
        DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
            DropdownMenuItem(
                text = { Text("Quick Scans (default)") },
                onClick = {
                    folderMenu = false
                    onSelectFolder(null)
                }
            )
            folders.forEach { f ->
                DropdownMenuItem(
                    text = { Text(f.name) },
                    onClick = {
                        folderMenu = false
                        onSelectFolder(f.id.value)
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(pages, key = { _, uri -> uri.toString() }) { index, uri ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier.size(96.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Page ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            Text(uri.toString().take(60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onMoveUp(index) }, enabled = index > 0) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                            }
                            IconButton(onClick = { onMoveDown(index) }, enabled = index < pages.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                            }
                            IconButton(onClick = { onDelete(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rescan")
            }
            Button(onClick = onSave, enabled = pages.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}