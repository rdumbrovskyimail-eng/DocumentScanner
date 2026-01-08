package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import org.burnoutcrew.reorderable.*

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
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Меню для конкретной папки
    var menuFolder by remember { mutableStateOf<Folder?>(null) }

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
                    // Сортировка
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name A-Z") },
                                onClick = { 
                                    viewModel.setSortOrder(SortOrder.NAME_ASC)
                                    showSortMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Name Z-A") },
                                onClick = { 
                                    viewModel.setSortOrder(SortOrder.NAME_DESC)
                                    showSortMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Newest first") },
                                onClick = { 
                                    viewModel.setSortOrder(SortOrder.DATE_DESC)
                                    showSortMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.CalendarToday, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Oldest first") },
                                onClick = { 
                                    viewModel.setSortOrder(SortOrder.DATE_ASC)
                                    showSortMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.CalendarToday, null) }
                            )
                        }
                    }
                    
                    // Архив
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
                    
                    val quickScansFolder = state.folders.find { it.isQuickScans }
                    val otherFolders = state.folders.filter { !it.isQuickScans }
                    
                    // Reorderable state
                    val reorderState = rememberReorderableLazyListState(
                        onMove = { from, to ->
                            // Учитываем что Quick Scans всегда первый (index 0)
                            val fromIndex = from.index - 1
                            val toIndex = to.index - 1
                            if (fromIndex >= 0 && toIndex >= 0) {
                                viewModel.reorderFolders(fromIndex, toIndex)
                            }
                        },
                        onDragEnd = { _, _ ->
                            viewModel.saveFolderOrder()
                        }
                    )
                    
                    LazyColumn(
                        state = reorderState.listState,
                        modifier = Modifier.reorderable(reorderState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick Scans - не перетаскивается
                        quickScansFolder?.let { folder ->
                            item(key = "quickscans") {
                                QuickScansFolderCard(
                                    folder = folder,
                                    onClick = { onFolderClick(folder.id.value) },
                                    onClearClick = { showClearQuickScansDialog = true }
                                )
                            }
                        }
                        
                        // Остальные папки с drag & drop
                        itemsIndexed(
                            items = otherFolders,
                            key = { _, folder -> folder.id.value }
                        ) { index, folder ->
                            ReorderableItem(
                                reorderableState = reorderState,
                                key = folder.id.value
                            ) { isDragging ->
                                val elevation by animateDpAsState(
                                    if (isDragging) 8.dp else 0.dp,
                                    label = "elevation"
                                )
                                
                                RegularFolderCard(
                                    folder = folder,
                                    isDragging = isDragging,
                                    elevation = elevation,
                                    modifier = Modifier.detectReorderAfterLongPress(reorderState),
                                    onClick = { onFolderClick(folder.id.value) },
                                    onMenuClick = { menuFolder = folder }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // МЕНЮ ПАПКИ (современный стиль)
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
            .combinedClickable(onClick = onClick, onLongClick = {}),
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
// REGULAR FOLDER CARD (с поддержкой drag & drop)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RegularFolderCard(
    folder: Folder,
    isDragging: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { /* Handled by reorderable */ }
            ),
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
            // Drag handle
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
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

// SortOrder enum определён в FoldersViewModel.kt - НЕ дублируем здесь!
