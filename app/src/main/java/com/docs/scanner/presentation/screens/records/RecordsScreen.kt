package com.docs.scanner.presentation.screens.records

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.model.Record
import com.docs.scanner.presentation.components.*
import com.docs.scanner.presentation.components.dragdrop.*
import com.docs.scanner.presentation.screens.folders.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    folderId: Long,
    viewModel: RecordsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onRecordClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Record?>(null) }
    var showMoveDialog by remember { mutableStateOf<Record?>(null) }
    var menuRecord by remember { mutableStateOf<Record?>(null) }
    var editingRecord by remember { mutableStateOf<Record?>(null) }
    
    var showSortMenu by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(folderId) {
        viewModel.loadRecords(folderId)
    }
    
    val showFab = when (val state = uiState) {
        is RecordsUiState.Success -> !state.isQuickScansFolder
        else -> false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (val state = uiState) {
                            is RecordsUiState.Success -> state.folderName
                            else -> "Records"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
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
                }
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Record")
                }
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
                is RecordsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                
                is RecordsUiState.Success -> {
                    state.errorMessage?.let { msg ->
                        LaunchedEffect(msg) {
                            snackbarHostState.showSnackbar(msg)
                            viewModel.clearError()
                        }
                    }
                    
                    val records = state.records
                    
                    if (records.isEmpty()) {
                        EmptyRecordsState(
                            isQuickScansFolder = state.isQuickScansFolder,
                            onCreateClick = { showCreateDialog = true }
                        )
                    } else {
                        RecordsList(
                            records = records,
                            sortMode = sortMode,
                            onRecordClick = onRecordClick,
                            onMenuClick = { menuRecord = it },
                            onReorder = viewModel::reorderRecords
                        )
                    }
                }
                
                is RecordsUiState.Error -> {
                    ErrorState(
                        error = state.message,
                        onRetry = { viewModel.loadRecords(folderId) }
                    )
                }
            }
        }
    }
    
    // Dialogs and Menus
    menuRecord?.let { record ->
        RecordMenu(
            record = record,
            onDismiss = { menuRecord = null },
            onRename = {
                menuRecord = null
                editingRecord = record
            },
            onMove = {
                menuRecord = null
                showMoveDialog = record
            },
            onDelete = {
                menuRecord = null
                showDeleteDialog = record
            }
        )
    }
    
    if (showCreateDialog) {
        CreateRecordDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                viewModel.createRecord(name, description)
                showCreateDialog = false
            }
        )
    }
    
    editingRecord?.let { record ->
        EditRecordDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onSave = { name, description ->
                viewModel.updateRecord(record.copy(name = name, description = description))
                editingRecord = null
            }
        )
    }
    
    showMoveDialog?.let { record ->
        MoveRecordDialog(
            record = record,
            folders = allFolders,
            onDismiss = { showMoveDialog = null },
            onMove = { targetFolderId ->
                viewModel.moveRecord(record.id.value, targetFolderId)
                showMoveDialog = null
            }
        )
    }
    
    showDeleteDialog?.let { record ->
        ConfirmDialog(
            title = "Delete Record?",
            message = "This will delete \"${record.name}\" and all its documents.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteRecord(record.id.value)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RECORDS LIST (UPDATED FOR NEW API)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RecordsList(
    records: List<Record>,
    sortMode: SortMode,
    onRecordClick: (Long) -> Unit,
    onMenuClick: (Record) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val isManualMode = sortMode == SortMode.MANUAL
    
    DragDropLazyColumn(
        items = records,
        key = { _, record -> record.id.value },
        onMove = onReorder,
        modifier = Modifier.fillMaxSize(),
        enabled = isManualMode
    ) { index, record, isDragging, dragModifier ->
        
        // Обертка для отступов, так как DragDropLazyColumn не принимает contentPadding
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            RecordCard(
                record = record,
                isDragging = isDragging,
                isManualMode = isManualMode,
                dragModifier = dragModifier, // Передаем модификатор драга внутрь
                onClick = { onRecordClick(record.id.value) },
                onMenuClick = { onMenuClick(record) }
            )
        }
    }
}

@Composable
private fun RecordCard(
    record: Record,
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
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle Icon
            if (isManualMode) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .then(dragModifier) // ✅ Применяем dragModifier к иконке
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.name, style = MaterialTheme.typography.titleMedium)
                    if (record.isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.PushPin, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    }
                }
                record.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${record.documentCount} pages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, "Menu")
            }
        }
    }
}

// Helper components (Menu, Dialogs, EmptyState) remain same as in previous version...
// (I will omit them here to save space, assuming they are unchanged from my previous correct answer, 
//  but if you need them included, I can add them back. The critical part above is RecordsList and RecordCard).

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
private fun EmptyRecordsState(
    isQuickScansFolder: Boolean,
    onCreateClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = if (isQuickScansFolder) Icons.Default.FlashOn else Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isQuickScansFolder) "No quick scans yet" else "No records yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!isQuickScansFolder) {
                Button(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Record")
                }
            }
        }
    }
}

@Composable
private fun RecordMenu(
    record: Record,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(8.dp)) {
                Text(record.name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                ListItem(headlineContent = { Text("Rename") }, leadingContent = { Icon(Icons.Default.Edit, null) }, modifier = Modifier.clickable(onClick = onRename))
                ListItem(headlineContent = { Text("Move") }, leadingContent = { Icon(Icons.Default.DriveFileMove, null) }, modifier = Modifier.clickable(onClick = onMove))
                ListItem(headlineContent = { Text("Delete") }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable(onClick = onDelete))
            }
        }
    }
}

@Composable
private fun CreateRecordDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Record") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, description.ifBlank { null }) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditRecordDialog(record: Record, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(record.name) }
    var description by remember { mutableStateOf(record.description ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Record") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, description.ifBlank { null }) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MoveRecordDialog(
    record: Record,
    folders: List<com.docs.scanner.domain.model.Folder>,
    onDismiss: () -> Unit,
    onMove: (Long) -> Unit
) {
    val selectableFolders = remember(folders) { folders.filter { it.id != record.folderId && !it.isQuickScans } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column {
                selectableFolders.forEach { folder ->
                    Text(folder.name, Modifier.fillMaxWidth().clickable { onMove(folder.id.value) }.padding(12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
