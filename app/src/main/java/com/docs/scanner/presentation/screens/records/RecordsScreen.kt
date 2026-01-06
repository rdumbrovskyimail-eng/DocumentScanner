package com.docs.scanner.presentation.screens.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.Record
import com.docs.scanner.presentation.components.*

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
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<Record?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Record?>(null) }
    var showMoveDialog by remember { mutableStateOf<Record?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(folderId) {
        viewModel.loadRecords(folderId)
    }
    
    // ✅ Determine if we should show FAB (hide for Quick Scans)
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
                }
            )
        },
        floatingActionButton = {
            // ✅ FIX: Only show FAB if not Quick Scans folder
            if (showFab) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Record")
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = remember { SnackbarHostState() }) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is RecordsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is RecordsUiState.Success -> {
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
                            // ✅ FIX: Don't show action button for Quick Scans
                            actionText = if (state.isQuickScansFolder) null else "Create Record",
                            onActionClick = if (state.isQuickScansFolder) null else {{ showCreateDialog = true }}
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(records, key = { it.id.value }) { record ->
                                RecordCard(
                                    record = record,
                                    onClick = { onRecordClick(record.id.value) },
                                    onLongClick = { editingRecord = record },
                                    onDelete = { showDeleteDialog = record }
                                )
                            }
                        }
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
    
    // Create dialog (only for non-Quick Scans folders)
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
    
    // Edit menu
    editingRecord?.let { record ->
        var showMenu by remember { mutableStateOf(true) }
        var showRenameDialog by remember { mutableStateOf(false) }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { 
                    showMenu = false
                    editingRecord = null
                }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { 
                        showMenu = false
                        showRenameDialog = true
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                )
                
                DropdownMenuItem(
                    text = { Text("Move to folder") },
                    onClick = {
                        showMenu = false
                        showMoveDialog = record
                        editingRecord = null
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DriveFileMove, contentDescription = null)
                    }
                )
                
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        showDeleteDialog = record
                        editingRecord = null
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                )
            }
        }
        
        // Rename dialog
        if (showRenameDialog) {
            var newName by remember { mutableStateOf(record.name) }
            var newDescription by remember { mutableStateOf(record.description ?: "") }
            
            AlertDialog(
                onDismissRequest = { 
                    showRenameDialog = false
                    editingRecord = null
                },
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
                            showRenameDialog = false
                            editingRecord = null
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showRenameDialog = false
                        editingRecord = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    
    // Move dialog
    showMoveDialog?.let { record ->
        val selectableFolders = remember(allFolders, record.folderId) {
            allFolders.filter { it.id != record.folderId && !it.isQuickScans }  // ✅ Exclude Quick Scans as target
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
    
    // Delete confirmation
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

@Composable
private fun RecordCard(
    record: Record,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (record.description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${record.documentCount} pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
