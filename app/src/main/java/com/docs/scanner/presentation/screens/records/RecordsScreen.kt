package com.docs.scanner.presentation.screens.records

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.model.Record
import com.docs.scanner.presentation.components.*
import com.docs.scanner.presentation.screens.folders.SortMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableItemScope
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    folderId: Long,
    viewModel: RecordsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onRecordClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Record?>(null) }
    var showMoveDialog by remember { mutableStateOf<Record?>(null) }
    var menuRecord by remember { mutableStateOf<Record?>(null) }
    var editingRecord by remember { mutableStateOf<Record?>(null) }
    
    // Выпадающее меню сортировки
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
                    // ═══════════════════════════════════════════════════════════
                    // КНОПКА СОРТИРОВКИ С ВЫПАДАЮЩИМ МЕНЮ
                    // ═══════════════════════════════════════════════════════════
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
                                        Icon(
                                            Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text("By Date")
                                        if (sortMode == SortMode.BY_DATE) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
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
                                        Icon(
                                            Icons.Default.SortByAlpha,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text("By Name")
                                        if (sortMode == SortMode.BY_NAME) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
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
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text("Manual")
                                        if (sortMode == SortMode.MANUAL) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
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
                        EmptyState(
                            icon = {
                                Icon(
                                    imageVector = if (state.isQuickScansFolder) 
                                        Icons.Default.FlashOn 
                                    else 
                                        Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            title = if (state.isQuickScansFolder) 
                                "No quick scans yet" 
                            else 
                                "No records yet",
                            message = if (state.isQuickScansFolder)
                                "Use the gallery button on the main screen to quick scan documents"
                            else
                                "Create your first record to add documents",
                            actionText = if (state.isQuickScansFolder) null else "Create Record",
                            onActionClick = if (state.isQuickScansFolder) null else {{ showCreateDialog = true }}
                        )
                    } else {
                        RecordsList(
                            records = records,
                            sortMode = sortMode,
                            onRecordClick = onRecordClick,
                            onMenuClick = { menuRecord = it },
                            onReorder = viewModel::reorderRecords,
                            onDragStart = viewModel::startDragging,
                            onDragEnd = viewModel::saveRecordOrder
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
    
    // ═══════════════════════════════════════════════════════════════════════════
    // МЕНЮ ЗАПИСИ
    // ═══════════════════════════════════════════════════════════════════════════
    
    menuRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { menuRecord = null }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = record.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    HorizontalDivider()
                    
                    ListItem(
                        headlineContent = { Text("Rename") },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.clickable {
                            menuRecord = null
                            editingRecord = record
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text("Move to folder") },
                        leadingContent = { Icon(Icons.Default.DriveFileMove, null) },
                        modifier = Modifier.clickable {
                            menuRecord = null
                            showMoveDialog = record
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text("Delete") },
                        leadingContent = { 
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) 
                        },
                        modifier = Modifier.clickable {
                            menuRecord = null
                            showDeleteDialog = record
                        }
                    )
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════════════════
    
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Record") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createRecord(name, description.ifBlank { null })
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    editingRecord?.let { record ->
        var newName by remember { mutableStateOf(record.name) }
        var newDescription by remember { mutableStateOf(record.description ?: "") }
        
        AlertDialog(
            onDismissRequest = { editingRecord = null },
            title = { Text("Edit Record") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = newDescription,
                        onValueChange = { newDescription = it },
                        label = { Text("Description") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateRecord(
                            record.copy(
                                name = newName,
                                description = newDescription.ifBlank { null }
                            )
                        )
                        editingRecord = null
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRecord = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    showMoveDialog?.let { record ->
        val selectableFolders = remember(allFolders, record.folderId) {
            allFolders.filter { it.id != record.folderId && !it.isQuickScans }
        }
        var selectedFolderId by remember(record.id.value) { mutableStateOf<Long?>(null) }

        AlertDialog(
            onDismissRequest = { showMoveDialog = null },
            title = { Text("Move to folder") },
            text = {
                if (selectableFolders.isEmpty()) {
                    Text("No other folders available.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectableFolders.forEach { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolderId = folder.id.value }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedFolderId == folder.id.value,
                                    onClick = { selectedFolderId = folder.id.value }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                                    folder.description?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedFolderId != null,
                    onClick = {
                        val target = selectedFolderId
                        if (target != null) {
                            viewModel.moveRecord(record.id.value, target)
                        }
                        showMoveDialog = null
                    }
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    showDeleteDialog?.let { record ->
        ConfirmDialog(
            title = "Delete Record?",
            message = "This will delete \"${record.name}\" and all its documents. This action cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteRecord(record.id.value)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// RECORDS LIST WITH REORDERABLE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RecordsList(
    records: List<Record>,
    sortMode: SortMode,
    onRecordClick: (Long) -> Unit,
    onMenuClick: (Record) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
) {
    val isManualMode = sortMode == SortMode.MANUAL
    
    val lazyListState = rememberLazyListState()
    
    // ✅ ИСПРАВЛЕНО: Правильная инициализация reorderable state
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = lazyListState
    ) { from, to ->
        if (isManualMode) {
            onReorder(from.index, to.index)
        }
    }
    
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = records,
            key = { _, record -> record.id.value }
        ) { _, record ->
            ReorderableItem(
                state = reorderableLazyListState,
                key = record.id.value
            ) { isDragging ->
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 2.dp,
                    label = "elevation"
                )
                
                RecordCard(
                    record = record,
                    isDragging = isDragging,
                    elevation = elevation,
                    isManualMode = isManualMode,
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd,
                    onClick = { onRecordClick(record.id.value) },
                    onMenuClick = { onMenuClick(record) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// RECORD CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReorderableItemScope.RecordCard(
    record: Record,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ ИСПРАВЛЕНО: Drag handle с правильным modifier
            if (isManualMode) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (record.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (record.description != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = "${record.documentCount} pages",
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