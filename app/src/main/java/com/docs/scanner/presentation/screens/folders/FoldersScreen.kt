package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
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
    ) { uri: Uri? ->
        uri?.let(viewModel::quickScan)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                actions = {
                    // Sort button
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortMenuItem(
                                text = "By Date",
                                icon = Icons.Default.CalendarToday,
                                selected = sortMode == SortMode.BY_DATE,
                                onClick = {
                                    viewModel.setSortMode(SortMode.BY_DATE)
                                    showSortMenu = false
                                }
                            )
                            
                            SortMenuItem(
                                text = "By Name",
                                icon = Icons.Default.SortByAlpha,
                                selected = sortMode == SortMode.BY_NAME,
                                onClick = {
                                    viewModel.setSortMode(SortMode.BY_NAME)
                                    showSortMenu = false
                                }
                            )
                            
                            SortMenuItem(
                                text = "Manual",
                                icon = Icons.Default.DragHandle,
                                selected = sortMode == SortMode.MANUAL,
                                onClick = {
                                    viewModel.setSortMode(SortMode.MANUAL)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                    
                    IconButton(onClick = { viewModel.setShowArchived(!showArchived) }) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = "Archive",
                            tint = if (showArchived) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
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
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is FoldersUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                
                is FoldersUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                
                is FoldersUiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No folders yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Create a folder or use quick scan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                is FoldersUiState.Processing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message)
                    }
                }
                
                is FoldersUiState.Success -> {
                    FoldersList(
                        folders = state.folders,
                        sortMode = sortMode,
                        onFolderClick = onFolderClick,
                        onMenuClick = { menuFolder = it },
                        onClearQuickScans = { showClearQuickScansDialog = true },
                        onReorder = viewModel::reorderFolders,
                        onDragStart = viewModel::startDragging,
                        onDragEnd = viewModel::saveFolderOrder
                    )
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // МЕНЮ ПАПКИ
    // ═══════════════════════════════════════════════════════════════════════════
    
    menuFolder?.let { folder ->
        FolderMenu(
            folder = folder,
            onDismiss = { menuFolder = null },
            onRename = {
                menuFolder = null
                editingFolder = folder
            },
            onPin = {
                viewModel.setPinned(folder.id.value, !folder.isPinned)
                menuFolder = null
            },
            onArchive = {
                if (folder.isArchived) {
                    viewModel.unarchive(folder.id.value)
                } else {
                    viewModel.archive(folder.id.value)
                }
                menuFolder = null
            },
            onDelete = {
                menuFolder = null
                deleteFolderWithContents = false
                showDeleteFolderDialog = folder
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════════════════

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

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name, description ->
                viewModel.createFolder(name, description)
                showCreateFolderDialog = false
            }
        )
    }

    editingFolder?.let { folder ->
        EditFolderDialog(
            folder = folder,
            onDismiss = { editingFolder = null },
            onSave = { name, description ->
                viewModel.updateFolder(folder.copy(name = name.trim(), description = description))
                editingFolder = null
            }
        )
    }

    showDeleteFolderDialog?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            deleteContents = deleteFolderWithContents,
            onDeleteContentsChange = { deleteFolderWithContents = it },
            onDismiss = { showDeleteFolderDialog = null },
            onConfirm = {
                viewModel.deleteFolder(folder.id.value, deleteContents = deleteFolderWithContents)
                showDeleteFolderDialog = null
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// FOLDERS LIST WITH DRAG & DROP
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FoldersList(
    folders: List<Folder>,
    sortMode: SortMode,
    onFolderClick: (Long) -> Unit,
    onMenuClick: (Folder) -> Unit,
    onClearQuickScans: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
) {
    val quickScansFolder = folders.find { it.isQuickScans }
    val otherFolders = folders.filter { !it.isQuickScans }
    
    val isManualMode = sortMode == SortMode.MANUAL
    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    
    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            if (isManualMode) {
                onReorder(from, to)
            }
        },
        onDragStart = { onDragStart() },
        onDragEnd = { _, _ -> onDragEnd() },
        hapticFeedback = if (isManualMode) hapticFeedback else null
    )
    
    DragDropLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        dragDropState = dragDropState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        enabled = isManualMode
    ) {
        // QuickScans — всегда первый, не перетаскивается
        quickScansFolder?.let { folder ->
            item(key = "quickscans") {
                QuickScansFolderCard(
                    folder = folder,
                    onClick = { onFolderClick(folder.id.value) },
                    onClearClick = onClearQuickScans
                )
            }
        }
        
        // Остальные папки с drag & drop
        itemsIndexed(
            items = otherFolders,
            key = { _, folder -> folder.id.value }
        ) { index, folder ->
            // Индекс с учётом QuickScans
            val actualIndex = if (quickScansFolder != null) index + 1 else index
            
            DragDropItem(
                dragDropState = dragDropState,
                index = actualIndex,
                enabled = isManualMode
            ) { isDragging ->
                FolderCard(
                    folder = folder,
                    isDragging = isDragging,
                    isManualMode = isManualMode,
                    dragDropState = dragDropState,
                    index = actualIndex,
                    onClick = { onFolderClick(folder.id.value) },
                    onMenuClick = { onMenuClick(folder) },
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// QUICK SCANS FOLDER CARD
// ══════════════════════════════════════════════════════════════════════════════

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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            
            Box {
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
                        leadingIcon = { Icon(Icons.Default.ClearAll, null) },
                        enabled = folder.recordCount > 0
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// FOLDER CARD
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FolderCard(
    folder: Folder,
    isDragging: Boolean,
    isManualMode: Boolean,
    dragDropState: DragDropState,
    index: Int,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isDragging, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle — только в режиме MANUAL
            if (isManualMode) {
                DragHandle(
                    dragDropState = dragDropState,
                    index = index,
                    enabled = true,
                    onDragStarted = onDragStart,
                    onDragEnded = onDragEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
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
                    Text(
                        it, 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                }
                Text(
                    text = "${folder.recordCount} records",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
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
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, null, Modifier.size(20.dp))
                Text(text, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        Icons.Default.Check, 
                        null, 
                        tint = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.size(20.dp)
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
    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                
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
                    leadingContent = { 
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) 
                    },
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = { onCreate(name, description.ifBlank { null }) }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditFolderDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?) -> Unit
) {
    var name by remember(folder.id.value) { mutableStateOf(folder.name) }
    var description by remember(folder.id.value) { mutableStateOf(folder.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                onClick = { onSave(name, description.ifBlank { null }) }
            ) { Text("Save") }
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
                            checked = deleteContents,
                            onCheckedChange = onDeleteContentsChange
                        )
                        Text("Delete contents too")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}