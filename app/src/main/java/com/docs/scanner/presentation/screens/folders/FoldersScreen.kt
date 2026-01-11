package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.presentation.components.ConfirmDialog
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CalendarToday, null, Modifier.size(20.dp))
                                        Text("By Date")
                                        if (sortMode == SortMode.BY_DATE) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setSortMode(SortMode.BY_DATE)
                                    showSortMenu = false
                                }
                            )
                            
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.SortByAlpha, null, Modifier.size(20.dp))
                                        Text("By Name")
                                        if (sortMode == SortMode.BY_NAME) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setSortMode(SortMode.BY_NAME)
                                    showSortMenu = false
                                }
                            )
                            
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.DragHandle, null, Modifier.size(20.dp))
                                        Text("Manual")
                                        if (sortMode == SortMode.MANUAL) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
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
        AlertDialog(
            onDismissRequest = { menuFolder = null }
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
                        modifier = Modifier.clickable {
                            menuFolder = null
                            editingFolder = folder
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text(if (folder.isPinned) "Unpin" else "Pin to top") },
                        leadingContent = { Icon(Icons.Default.PushPin, null) },
                        modifier = Modifier.clickable {
                            viewModel.setPinned(folder.id.value, !folder.isPinned)
                            menuFolder = null
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text(if (folder.isArchived) "Unarchive" else "Archive") },
                        leadingContent = { 
                            Icon(
                                if (folder.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, 
                                null
                            ) 
                        },
                        modifier = Modifier.clickable {
                            if (folder.isArchived) {
                                viewModel.unarchive(folder.id.value)
                            } else {
                                viewModel.archive(folder.id.value)
                            }
                            menuFolder = null
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text("Delete") },
                        leadingContent = { 
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) 
                        },
                        modifier = Modifier.clickable {
                            menuFolder = null
                            deleteFolderWithContents = false
                            showDeleteFolderDialog = folder
                        }
                    )
                }
            }
        }
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
// FOLDERS LIST
// ═══════════════════════════════════════════════════════════════════════════════

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
    
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (isManualMode) {
            val offset = if (quickScansFolder != null) 1 else 0
            val fromIndex = from.index - offset
            val toIndex = to.index - offset
            if (fromIndex >= 0 && toIndex >= 0) {
                onReorder(fromIndex, toIndex)
            }
        }
    }
    
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        quickScansFolder?.let { folder ->
            item(key = "quickscans") {
                QuickScansFolderCard(
                    folder = folder,
                    onClick = { onFolderClick(folder.id.value) },
                    onClearClick = onClearQuickScans
                )
            }
        }
        
        itemsIndexed(
            items = otherFolders,
            key = { _, folder -> folder.id.value }
        ) { _, folder ->
            ReorderableItem(
                state = reorderableLazyListState,
                key = folder.id.value
            ) { isDragging ->
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "elevation"
                )
                
                // Передаём scope (this) в дочерний composable
                RegularFolderCard(
                    scope = this,
                    folder = folder,
                    isDragging = isDragging,
                    elevation = elevation,
                    isManualMode = isManualMode,
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd,
                    onClick = { onFolderClick(folder.id.value) },
                    onMenuClick = { onMenuClick(folder) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// QUICK SCANS FOLDER CARD
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

// ═══════════════════════════════════════════════════════════════════════════════
// REGULAR FOLDER CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RegularFolderCard(
    scope: ReorderableCollectionItemScope,
    folder: Folder,
    isDragging: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    isManualMode: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle - только в режиме MANUAL
            if (isManualMode) {
                // Используем scope для доступа к draggableHandle
                with(scope) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .draggableHandle(
                                onDragStarted = { onDragStart() },
                                onDragStopped = { onDragEnd() }
                            )
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
                    Text(it, style = MaterialTheme.typography.bodyMedium) 
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