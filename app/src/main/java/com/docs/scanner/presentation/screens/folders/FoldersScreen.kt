package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.presentation.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var showClearQuickScansDialog by remember { mutableStateOf(false) }
    
    // Track which folder is in "reorder mode" (long press on normal folder)
    var reorderingFolderId by remember { mutableStateOf<Long?>(null) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Archived",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Switch(
                            checked = showArchived,
                            onCheckedChange = viewModel::setShowArchived
                        )
                    }
                    IconButton(onClick = onSearchClick) { 
                        Icon(Icons.Default.Search, contentDescription = "Search") 
                    }
                    IconButton(onClick = onTermsClick) { 
                        Icon(Icons.Default.Event, contentDescription = "Terms") 
                    }
                    IconButton(onClick = onCameraClick) { 
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera") 
                    }
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Quick scan")
                    }
                    IconButton(onClick = onSettingsClick) { 
                        Icon(Icons.Default.Settings, contentDescription = "Settings") 
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateFolderDialog = true }) {
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
                is FoldersUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                
                is FoldersUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                
                is FoldersUiState.Empty -> {
                    Text(
                        "No folders yet. Create one or run a quick scan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
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
                    
                    // Separate Quick Scans from other folders
                    val quickScansFolder = state.folders.find { it.isQuickScans }
                    val otherFolders = state.folders.filter { !it.isQuickScans }
                    
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ═══════════════════════════════════════════════════════
                        // QUICK SCANS FOLDER (always first, special treatment)
                        // ═══════════════════════════════════════════════════════
                        quickScansFolder?.let { folder ->
                            item(key = "quickscans") {
                                QuickScansFolderCard(
                                    folder = folder,
                                    onClick = { onFolderClick(folder.id.value) },
                                    onClearClick = { showClearQuickScansDialog = true }
                                )
                            }
                        }
                        
                        // ═══════════════════════════════════════════════════════
                        // OTHER FOLDERS (with reorder support)
                        // ═══════════════════════════════════════════════════════
                        itemsIndexed(
                            items = otherFolders,
                            key = { _, folder -> folder.id.value }
                        ) { index, folder ->
                            val isReordering = reorderingFolderId == folder.id.value
                            var menuExpanded by remember(folder.id.value) { mutableStateOf(false) }

                            RegularFolderCard(
                                folder = folder,
                                isReordering = isReordering,
                                canMoveUp = index > 0,
                                canMoveDown = index < otherFolders.lastIndex,
                                onClick = { 
                                    if (isReordering) {
                                        reorderingFolderId = null
                                    } else {
                                        onFolderClick(folder.id.value) 
                                    }
                                },
                                onLongClick = { 
                                    reorderingFolderId = if (isReordering) null else folder.id.value
                                },
                                onMenuClick = { menuExpanded = true },
                                onMoveUp = { 
                                    viewModel.moveFolderPosition(folder.id.value, -1)
                                },
                                onMoveDown = { 
                                    viewModel.moveFolderPosition(folder.id.value, 1)
                                },
                                menuExpanded = menuExpanded,
                                onMenuDismiss = { menuExpanded = false },
                                onRename = {
                                    menuExpanded = false
                                    editingFolder = folder
                                },
                                onPin = {
                                    menuExpanded = false
                                    viewModel.setPinned(folder.id.value, !folder.isPinned)
                                },
                                onArchive = {
                                    menuExpanded = false
                                    if (folder.isArchived) {
                                        viewModel.unarchive(folder.id.value)
                                    } else {
                                        viewModel.archive(folder.id.value)
                                    }
                                },
                                onDelete = {
                                    menuExpanded = false
                                    deleteFolderWithContents = false
                                    showDeleteFolderDialog = folder
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════════════════

    // Clear Quick Scans confirmation
    if (showClearQuickScansDialog) {
        ConfirmDialog(
            title = "Clear Quick Scans?",
            message = "This will delete all records in the Quick Scans folder. This action cannot be undone.",
            confirmText = "Clear",
            onConfirm = {
                viewModel.clearQuickScans()
                showClearQuickScansDialog = false
            },
            onDismiss = { showClearQuickScansDialog = false }
        )
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.createFolder(name, description.ifBlank { null })
                        showCreateFolderDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit/Rename folder dialog
    editingFolder?.let { folder ->
        var name by remember(folder.id.value) { mutableStateOf(folder.name) }
        var description by remember(folder.id.value) { mutableStateOf(folder.description ?: "") }

        AlertDialog(
            onDismissRequest = { editingFolder = null },
            title = { Text("Rename folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.updateFolder(folder.copy(name = name.trim(), description = description.ifBlank { null }))
                        editingFolder = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingFolder = null }) { Text("Cancel") }
            }
        )
    }

    // Delete folder dialog
    showDeleteFolderDialog?.let { folder ->
        AlertDialog(
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = deleteFolderWithContents,
                                onCheckedChange = { deleteFolderWithContents = it }
                            )
                            Text("Delete contents too")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id.value, deleteContents = deleteFolderWithContents)
                        showDeleteFolderDialog = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFolderDialog = null }) { Text("Cancel") }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// QUICK SCANS FOLDER CARD (Special - no long press, only Clear action)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickScansFolderCard(
    folder: Folder,
    onClick: () -> Unit,
    onClearClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { /* No action for Quick Scans */ }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash icon for Quick Scans
            Icon(
                imageVector = Icons.Default.FlashOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                folder.description?.let { 
                    Text(it, style = MaterialTheme.typography.bodySmall) 
                }
                Text(
                    text = "${folder.recordCount} records",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Menu button - only "Clear folder" option
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }
            
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Clear folder") },
                    onClick = {
                        menuExpanded = false
                        onClearClick()
                    },
                    leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null) },
                    enabled = folder.recordCount > 0
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// REGULAR FOLDER CARD (with reorder buttons on long press)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RegularFolderCard(
    folder: Folder,
    isReordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = if (isReordering) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder buttons (shown when long pressed)
            if (isReordering) {
                Column {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp, 
                            contentDescription = "Move up",
                            tint = if (canMoveUp) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown, 
                            contentDescription = "Move down",
                            tint = if (canMoveDown) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(folder.name, style = MaterialTheme.typography.titleMedium)
                    if (folder.isPinned) {
                        Icon(
                            Icons.Default.PushPin, 
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (folder.isArchived) {
                        Icon(
                            Icons.Default.Archive, 
                            contentDescription = "Archived",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                folder.description?.let { 
                    Text(it, style = MaterialTheme.typography.bodyMedium) 
                }
                Text(
                    text = "${folder.recordCount} records",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Menu button (3 dots)
            if (!isReordering) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Folder menu")
                }
            }
        }
        
        // Dropdown menu
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onMenuDismiss
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = onRename,
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )

            DropdownMenuItem(
                text = { Text(if (folder.isPinned) "Unpin" else "Pin") },
                onClick = onPin,
                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
            )

            DropdownMenuItem(
                text = { Text(if (folder.isArchived) "Unarchive" else "Archive") },
                onClick = onArchive,
                leadingIcon = {
                    Icon(
                        if (folder.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null
                    )
                }
            )

            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = onDelete,
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }
    }
}
