package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.presentation.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: FoldersViewModel = hiltViewModel(),
    onFolderClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTermsClick: () -> Unit,
    onCameraClick: () -> Unit,
    onQuickScanComplete: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    var showDeleteFolderDialog by remember { mutableStateOf<Folder?>(null) }
    var deleteFolderWithContents by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToEditor -> onQuickScanComplete(event.recordId)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(viewModel::quickScan)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Scanner") },
                actions = {
                    Row {
                        Text(
                            text = "Archived",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Switch(
                            checked = showArchived,
                            onCheckedChange = viewModel::setShowArchived
                        )
                    }
                    IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    IconButton(onClick = onTermsClick) { Icon(Icons.Default.Event, contentDescription = "Terms") }
                    IconButton(onClick = onCameraClick) { Icon(Icons.Default.CameraAlt, contentDescription = "Camera") }
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Quick scan")
                    }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                }
            )
        }
        ,
        floatingActionButton = {
            IconButton(onClick = { showCreateFolderDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create folder")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = uiState) {
                is FoldersUiState.Loading -> CircularProgressIndicator()
                is FoldersUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is FoldersUiState.Empty -> Text("No folders yet. Create one or run a quick scan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is FoldersUiState.Processing -> {
                    Text(state.message)
                    CircularProgressIndicator()
                }
                is FoldersUiState.Success -> {
                    state.errorMessage?.let { msg ->
                        LaunchedEffect(msg) {
                            snackbarHostState.showSnackbar(msg)
                            viewModel.clearError()
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.folders, key = { it.id.value }) { folder ->
                            var menuExpanded by remember(folder.id.value) { mutableStateOf(false) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onFolderClick(folder.id.value) },
                                        onLongClick = { menuExpanded = true }
                                    )
                            ) {
                                Row(modifier = Modifier.padding(12.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(folder.name, style = MaterialTheme.typography.titleMedium)
                                            if (folder.isPinned) {
                                                Icon(Icons.Default.PushPin, contentDescription = "Pinned")
                                            }
                                            if (folder.isArchived) {
                                                Icon(Icons.Default.Archive, contentDescription = "Archived")
                                            }
                                        }
                                        folder.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                        Text(
                                            text = "${folder.recordCount} records",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Folder menu")
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        menuExpanded = false
                                        editingFolder = folder
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )

                                DropdownMenuItem(
                                    text = { Text(if (folder.isPinned) "Unpin" else "Pin") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.setPinned(folder.id.value, !folder.isPinned)
                                    },
                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                                )

                                DropdownMenuItem(
                                    text = { Text(if (folder.isArchived) "Unarchive" else "Archive") },
                                    onClick = {
                                        menuExpanded = false
                                        if (folder.isArchived) viewModel.unarchive(folder.id.value) else viewModel.archive(folder.id.value)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (folder.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                            contentDescription = null
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        menuExpanded = false
                                        deleteFolderWithContents = false
                                        showDeleteFolderDialog = folder
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.createFolder(name, description.ifBlank { null })
                        showCreateFolderDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    editingFolder?.let { folder ->
        var name by remember(folder.id.value) { mutableStateOf(folder.name) }
        var description by remember(folder.id.value) { mutableStateOf(folder.description ?: "") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingFolder = null },
            title = { Text("Edit folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.updateFolder(folder.copy(name = name.trim(), description = description.ifBlank { null }))
                        editingFolder = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editingFolder = null }) { Text("Cancel") }
            }
        )
    }

    showDeleteFolderDialog?.let { folder ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteFolderDialog = null },
            title = { Text("Delete folder?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will delete \"${folder.name}\".")
                    if (folder.recordCount > 0) {
                        Text(
                            text = "Folder contains ${folder.recordCount} records.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(checked = deleteFolderWithContents, onCheckedChange = { deleteFolderWithContents = it })
                            Text("Delete contents too")
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id.value, deleteContents = deleteFolderWithContents)
                        showDeleteFolderDialog = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteFolderDialog = null }) { Text("Cancel") }
            }
        )
    }
}

