package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.presentation.components.ConfirmDialog
import com.docs.scanner.presentation.components.dragdrop.*

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
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    var showDeleteFolderDialog by remember { mutableStateOf<Folder?>(null) }
    var deleteFolderWithContents by remember { mutableStateOf(false) }
    var showClearQuickScansDialog by remember { mutableStateOf(false) }
    var menuFolder by remember { mutableStateOf<Folder?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToEditor -> onQuickScanComplete(event.recordId)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(viewModel::quickScan) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.SwapVert, "Sort", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortMenuItem("By Date", Icons.Default.CalendarToday, sortMode == SortMode.BY_DATE) { viewModel.setSortMode(SortMode.BY_DATE); showSortMenu = false }
                            SortMenuItem("By Name", Icons.Default.SortByAlpha, sortMode == SortMode.BY_NAME) { viewModel.setSortMode(SortMode.BY_NAME); showSortMenu = false }
                            SortMenuItem("Manual", Icons.Default.DragHandle, sortMode == SortMode.MANUAL) { viewModel.setSortMode(SortMode.MANUAL); showSortMenu = false }
                        }
                    }
                    IconButton(onClick = { viewModel.setShowArchived(!showArchived) }) {
                        Icon(Icons.Default.Inventory2, "Archive", tint = if (showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = onTermsClick) { Icon(Icons.Default.Event, "Terms") }
                    IconButton(onClick = onCameraClick) { Icon(Icons.Default.CameraAlt, "Camera") }
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) { Icon(Icons.Default.PhotoLibrary, "Gallery") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateFolderDialog = true }) {
                Icon(Icons.Default.Add, "Create folder")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is FoldersUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is FoldersUiState.Error -> Text(state.message, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                is FoldersUiState.Empty -> {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(64.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No folders yet")
                    }
                }
                is FoldersUiState.Processing -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is FoldersUiState.Success -> {
                    FoldersList(
                        folders = state.folders,
                        sortMode = sortMode,
                        onFolderClick = onFolderClick,
                        onMenuClick = { menuFolder = it },
                        onClearQuickScans = { showClearQuickScansDialog = true },
                        onReorder = viewModel::reorderFolders
                    )
                }
            }
        }
    }
    
    // Menus and Dialogs
    menuFolder?.let { folder ->
        FolderMenu(
            folder = folder,
            onDismiss = { menuFolder = null },
            onRename = { menuFolder = null; editingFolder = folder },
            onPin = { viewModel.setPinned(folder.id.value, !folder.isPinned); menuFolder = null },
            onArchive = { if (folder.isArchived) viewModel.unarchive(folder.id.value) else viewModel.archive(folder.id.value); menuFolder = null },
            onDelete = { menuFolder = null; deleteFolderWithContents = false; showDeleteFolderDialog = folder }
        )
    }

    if (showClearQuickScansDialog) {
        ConfirmDialog(
            title = "Clear Quick Scans?",
            message = "Delete all records in Quick Scans?",
            confirmText = "Clear",
            onConfirm = { viewModel.clearQuickScans(); showClearQuickScansDialog = false },
            onDismiss = { showClearQuickScansDialog = false }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name, desc -> viewModel.createFolder(name, desc); showCreateFolderDialog = false }
        )
    }

    editingFolder?.let { folder ->
        EditFolderDialog(
            folder = folder,
            onDismiss = { editingFolder = null },
            onSave = { name, desc -> viewModel.updateFolder(folder.copy(name = name.trim(), description = desc)); editingFolder = null }
        )
    }

    showDeleteFolderDialog?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            deleteContents = deleteFolderWithContents,
            onDeleteContentsChange = { deleteFolderWithContents = it },
            onDismiss = { showDeleteFolderDialog = null },
            onConfirm = { viewModel.deleteFolder(folder.id.value, deleteFolderWithContents); showDeleteFolderDialog = null }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// FOLDERS LIST (UPDATED FOR NEW API)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FoldersList(
    folders: List<Folder>,
    sortMode: SortMode,
    onFolderClick: (Long) -> Unit,
    onMenuClick: (Folder) -> Unit,
    onClearQuickScans: () -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val quickScansFolder = folders.find { it.isQuickScans }
    val otherFolders = folders.filter { !it.isQuickScans }
    val isManualMode = sortMode == SortMode.MANUAL
    
    // Объединяем папки в один список для DragDropLazyColumn
    val displayItems = remember(quickScansFolder, otherFolders) {
        if (quickScansFolder != null) listOf(quickScansFolder) + otherFolders else otherFolders
    }
    
    DragDropLazyColumn(
        items = displayItems,
        key = { _, folder -> folder.id.value },
        onMove = { from, to ->
            // Защита: нельзя перемещать QuickScans (индекс 0) и перемещать на её место
            if (quickScansFolder != null) {
                if (from == 0 || to == 0) return@DragDropLazyColumn
            }
            onReorder(from, to)
        },
        modifier = Modifier.fillMaxSize(),
        enabled = isManualMode
    ) { index, folder, isDragging, dragModifier ->
        
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (folder.isQuickScans) {
                // Quick Scans Card (never draggable via handle, as handle is hidden)
                QuickScansFolderCard(
                    folder = folder,
                    onClick = { onFolderClick(folder.id.value) },
                    onClearClick = onClearQuickScans
                )
            } else {
                FolderCard(
                    folder = folder,
                    isDragging = isDragging,
                    isManualMode = isManualMode,
                    dragModifier = dragModifier,
                    onClick = { onFolderClick(folder.id.value) },
                    onMenuClick = { onMenuClick(folder) }
                )
            }
        }
    }
}

@Composable
private fun QuickScansFolderCard(folder: Folder, onClick: () -> Unit, onClearClick: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FlashOn, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                folder.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("${folder.recordCount} records", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Clear folder") },
                        onClick = { menuExpanded = false; onClearClick() },
                        leadingIcon = { Icon(Icons.Default.ClearAll, null) },
                        enabled = folder.recordCount > 0
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: Folder,
    isDragging: Boolean,
    isManualMode: Boolean,
    dragModifier: Modifier,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isDragging, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            
            if (isManualMode) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .then(dragModifier)
                )
                Spacer(Modifier.width(12.dp))
            }
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folder.name, style = MaterialTheme.typography.titleMedium)
                    if (folder.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            "Pinned",
                            Modifier.padding(start = 8.dp).size(16.dp),
                            MaterialTheme.colorScheme.primary
                        )
                    }
                    if (folder.isArchived) {
                        Icon(
                            Icons.Default.Archive,
                            "Archived",
                            Modifier.padding(start = 8.dp).size(16.dp),
                            MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                folder.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${folder.recordCount} records",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMenuClick) { Icon(Icons.Default.MoreVert, "Menu") }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HELPER COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SortMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, Modifier.size(20.dp))
                Text(text, Modifier.weight(1f))
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        Modifier.size(20.dp),
                        MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun FolderMenu(
    folder: Folder,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(8.dp)) {
                Text(folder.name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Rename") },
                    leadingContent = { Icon(Icons.Default.Edit, null) },
                    modifier = Modifier.clickable(onClick = onRename)
                )
                ListItem(
                    headlineContent = { Text(if (folder.isPinned) "Unpin" else "Pin to top") },
                    leadingContent = { Icon(Icons.Default.PushPin, null) },
                    modifier = Modifier.clickable(onClick = onPin)
                )
                ListItem(
                    headlineContent = { Text(if (folder.isArchived) "Unarchive" else "Archive") },
                    leadingContent = {
                        Icon(
                            if (folder.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            null
                        )
                    },
                    modifier = Modifier.clickable(onClick = onArchive)
                )
                ListItem(
                    headlineContent = { Text("Delete") },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
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
                onClick = { onCreate(name, description.ifBlank { null }) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditFolderDialog(folder: Folder, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(folder.name) }
    var description by remember { mutableStateOf(folder.description ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
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
                onClick = { onSave(name, description.ifBlank { null }) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteFolderDialog(
    folder: Folder,
    deleteContents: Boolean,
    onDeleteContentsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete folder?") },
        text = {
            Column {
                Text("Delete \"${folder.name}\"?")
                if (folder.recordCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Contains ${folder.recordCount} records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = deleteContents,
                            onCheckedChange = onDeleteContentsChange
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Delete contents too")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
