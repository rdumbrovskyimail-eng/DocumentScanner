package com.docs.scanner.presentation.screens.folders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.usecase.*
import com.docs.scanner.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val uiState by viewModel.uiState.collectAsState()
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.quickScan(uri) { recordId ->
                onQuickScanComplete(recordId)
            }
        }
    }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Folder?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Scanner") },
                actions = {
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
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Folder")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState) {
                is FoldersUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is FoldersUiState.Empty -> {
                    EmptyState(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = "No folders yet",
                        message = "Create your first folder to organize documents",
                        actionText = "Create Folder",
                        onActionClick = { showCreateDialog = true }
                    )
                }
                
                is FoldersUiState.Success -> {
                    val folders = (uiState as FoldersUiState.Success).folders
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(folders, key = { it.id }) { folder ->
                            FolderCard(
                                folder = folder,
                                onClick = { onFolderClick(folder.id) },
                                onLongClick = { editingFolder = folder },
                                onDelete = { showDeleteDialog = folder }
                            )
                        }
                    }
                }
                
                is FoldersUiState.Error -> {
                    ErrorState(
                        error = (uiState as FoldersUiState.Error).message,
                        onRetry = viewModel::loadFolders
                    )
                }
            }
        }
    }
    
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Folder") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
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
                        viewModel.createFolder(name, description.ifBlank { null })
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
    
    editingFolder?.let { folder ->
        var showMenu by remember { mutableStateOf(true) }
        var showRenameDialog by remember { mutableStateOf(false) }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { 
                showMenu = false
                editingFolder = null
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
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    showDeleteDialog = folder
                    editingFolder = null
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            )
        }
        
        if (showRenameDialog) {
            var newName by remember { mutableStateOf(folder.name) }
            
            AlertDialog(
                onDismissRequest = { 
                    showRenameDialog = false
                    editingFolder = null
                },
                title = { Text("Rename Folder") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateFolder(folder.copy(name = newName))
                            showRenameDialog = false
                            editingFolder = null
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showRenameDialog = false
                        editingFolder = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    
    showDeleteDialog?.let { folder ->
        ConfirmDialog(
            title = "Delete Folder?",
            message = "This will delete \"${folder.name}\" and all its contents. This action cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteFolder(folder.id)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
}

@Composable
private fun FolderCard(
    folder: Folder,
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
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (folder.description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = folder.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${folder.recordCount} records",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

sealed interface FoldersUiState {
    data object Loading : FoldersUiState
    data object Empty : FoldersUiState
    data class Success(val folders: List<Folder>) : FoldersUiState
    data class Error(val message: String) : FoldersUiState
}

@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val getFoldersUseCase: GetFoldersUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val updateFolderUseCase: UpdateFolderUseCase,
    private val deleteFolderUseCase: DeleteFolderUseCase,
    private val quickScanUseCase: QuickScanUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<FoldersUiState>(FoldersUiState.Loading)
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()
    
    init {
        loadFolders()
    }
    
    fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = FoldersUiState.Loading
            
            getFoldersUseCase()
                .catch { e ->
                    _uiState.value = FoldersUiState.Error(
                        e.message ?: "Failed to load folders"
                    )
                }
                .collect { folders ->
                    _uiState.value = if (folders.isEmpty()) {
                        FoldersUiState.Empty
                    } else {
                        FoldersUiState.Success(folders)
                    }
                }
        }
    }
    
    fun createFolder(name: String, description: String?) {
        viewModelScope.launch {
            createFolderUseCase(name, description)
        }
    }
    
    fun updateFolder(folder: Folder) {
        viewModelScope.launch {
            updateFolderUseCase(folder)
        }
    }
    
    fun deleteFolder(id: Long) {
        viewModelScope.launch {
            deleteFolderUseCase(id)
        }
    }
    
    fun quickScan(imageUri: Uri, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            when (val result = quickScanUseCase(imageUri)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    onComplete(result.data)
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    _uiState.value = FoldersUiState.Error(
                        result.exception.message ?: "Quick scan failed"
                    )
                }
                else -> {}
            }
        }
    }
}