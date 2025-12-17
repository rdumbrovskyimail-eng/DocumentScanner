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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.*
import com.docs.scanner.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun RecordsScreen(
    folderId: Long,
    viewModel: RecordsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onRecordClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val folderName by viewModel.folderName.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<Record?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Record?>(null) }
    var showMoveDialog by remember { mutableStateOf<Record?>(null) }
    
    LaunchedEffect(folderId) {
        viewModel.loadRecords(folderId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Record")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState) {
                is RecordsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is RecordsUiState.Empty -> {
                    EmptyState(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = "No records yet",
                        message = "Create your first record to add documents",
                        actionText = "Create Record",
                        onActionClick = { showCreateDialog = true }
                    )
                }
                
                is RecordsUiState.Success -> {
                    val records = (uiState as RecordsUiState.Success).records
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(records, key = { it.id }) { record ->
                            RecordCard(
                                record = record,
                                onClick = { onRecordClick(record.id) },
                                onLongClick = { editingRecord = record },
                                onDelete = { showDeleteDialog = record }
                            )
                        }
                    }
                }
                
                is RecordsUiState.Error -> {
                    ErrorState(
                        error = (uiState as RecordsUiState.Error).message,
                        onRetry = { viewModel.loadRecords(folderId) }
                    )
                }
            }
        }
    }
    
    // Create dialog
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
    
    // ✅ Edit menu (центрированное)
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
                
                // ✅ НОВОЕ: Move to folder
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
        
        // ✅ Rename dialog с Description
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
    
    // ✅ Move dialog
    showMoveDialog?.let { record ->
        AlertDialog(
            onDismissRequest = { showMoveDialog = null },
            title = { Text("Move to folder") },
            text = {
                LazyColumn {
                    items(allFolders.filter { it.id != record.folderId }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                viewModel.moveRecord(record.id, folder.id)
                                showMoveDialog = null
                            }
                        )
                    }
                }
            },
            confirmButton = {},
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
                viewModel.deleteRecord(record.id)
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

sealed interface RecordsUiState {data object Loading : RecordsUiState
    data object Empty : RecordsUiState
    data class Success(val records: List<Record>) : RecordsUiState
    data class Error(val message: String) : RecordsUiState
}

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val getRecordsUseCase: GetRecordsUseCase,
    private val createRecordUseCase: CreateRecordUseCase,
    private val updateRecordUseCase: UpdateRecordUseCase,
    private val deleteRecordUseCase: DeleteRecordUseCase,
    private val getFoldersUseCase: GetFoldersUseCase,  // ✅ ДОБАВЛЕНО
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val folderId: Long = savedStateHandle.get<Long>("folderId") ?: 0L
    
    private val _uiState = MutableStateFlow<RecordsUiState>(RecordsUiState.Loading)
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()
    
    private val _folderName = MutableStateFlow("Records")
    val folderName: StateFlow<String> = _folderName.asStateFlow()
    
    // ✅ ДОБАВЛЕНО: Все папки для Move dialog
    val allFolders: StateFlow<List<Folder>> = getFoldersUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun loadRecords(folderId: Long) {
        viewModelScope.launch {
            _uiState.value = RecordsUiState.Loading
            
            getRecordsUseCase(folderId)
                .catch { e ->
                    _uiState.value = RecordsUiState.Error(
                        e.message ?: "Failed to load records"
                    )
                }
                .collect { records ->
                    _uiState.value = if (records.isEmpty()) {
                        RecordsUiState.Empty
                    } else {
                        RecordsUiState.Success(records)
                    }
                }
        }
    }
    
    fun createRecord(name: String, description: String?) {
        viewModelScope.launch {
            createRecordUseCase(folderId, name, description)
        }
    }
    
    fun updateRecord(record: Record) {
        viewModelScope.launch {
            updateRecordUseCase(record)
        }
    }
    
    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            deleteRecordUseCase(id)
        }
    }
    
    // ✅ НОВОЕ: Перемещение записи
    fun moveRecord(recordId: Long, newFolderId: Long) {
        viewModelScope.launch {
            try {
                // Получаем запись
                val allRecords = (uiState.value as? RecordsUiState.Success)?.records ?: return@launch
                val record = allRecords.find { it.id == recordId } ?: return@launch
                
                // Обновляем folderId
                updateRecordUseCase(record.copy(folderId = newFolderId))
                
                println("✅ Record moved to folder $newFolderId")
            } catch (e: Exception) {
                println("❌ Failed to move record: ${e.message}")
            }
        }
    }
}

