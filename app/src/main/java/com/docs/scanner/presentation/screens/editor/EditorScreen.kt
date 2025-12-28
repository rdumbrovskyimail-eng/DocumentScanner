package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle  // ✅ ДОБАВЛЕН IMPORT
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.components.*
import com.docs.scanner.presentation.screens.editor.components.*
import com.docs.scanner.presentation.theme.*
import com.docs.scanner.util.Debouncer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    recordId: Long,
    viewModel: EditorViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onImageClick: (Long) -> Unit,
    onCameraClick: () -> Unit  // ✅ ДОБАВЛЕН ПАРАМЕТР (Session 9 Problem #5)
) {
    // ✅ ИСПРАВЛЕНО: collectAsState() → collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val record by viewModel.record.collectAsStateWithLifecycle()
    val folderName by viewModel.folderName.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var editingDocument by remember { mutableStateOf<Document?>(null) }

    val galleryDebouncer = remember { Debouncer(800L, coroutineScope) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addDocument(it) }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(recordId) {
        viewModel.loadRecord(recordId)
    }

    Scaffold(
        containerColor = GoogleDocsBackground,
        topBar = {
            GoogleDocsTopBar(
                title = folderName ?: "Documents",
                onBackClick = onBackClick,
                onMenuClick = { /* TODO */ }
            )
        },
        floatingActionButton = {
            FloatingActionButtons(
                onCameraClick = onCameraClick,  // ✅ ИСПРАВЛЕНО: передаётся callback
                onGalleryClick = {
                    galleryDebouncer.invoke {
                        galleryLauncher.launch("image/*")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is EditorUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                // ✅ ДОБАВЛЕНО: Processing state (Session 8 Problem #6)
                is EditorUiState.Processing -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Processing document...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                is EditorUiState.Empty -> {
                    EmptyState(
                        title = "No documents yet",
                        message = "Add your first document to scan and translate",
                        actionText = "Add Document",
                        onActionClick = { galleryLauncher.launch("image/*") }
                    )
                }
                
                is EditorUiState.Success -> {
                    val documents = state.documents
                    
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ✅ ДОБАВЛЕНО: Document Header (Session 9 missing code)
                        item {
                            DocumentHeader(
                                title = record?.name ?: "Document",
                                description = record?.description,
                                onEditClick = { showEditNameDialog = true }
                            )
                        }
                        
                        item {
                            SimpleDivider()
                        }
                        
                        // ✅ ВОССТАНОВЛЕНО: items{} блок (Session 9 missing ~80 lines)
                        items(documents, key = { it.id }) { document ->
                            GContainerLayout(
                                previewContent = {
                                    DocumentImage(
                                        imagePath = document.imagePath,
                                        onClick = { onImageClick(document.id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                    )
                                },
                                ocrTextContent = {
                                    OcrTextField(
                                        text = document.originalText ?: "No text detected",
                                        onTextChange = { newText ->
                                            viewModel.updateOriginalText(document.id, newText)
                                        },
                                        onEditClick = {
                                            editingDocument = document
                                        }
                                    )
                                },
                                actionButtonsContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Translation field
                                        TranslationField(
                                            translatedText = document.translatedText
                                        )
                                        
                                        // Action buttons
                                        ActionButtonsRow(
                                            text = document.originalText ?: "",
                                            onRetry = {
                                                viewModel.retryTranslation(document.id)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                
                is EditorUiState.Error -> {
                    ErrorState(
                        error = state.message,
                        onRetry = { viewModel.loadRecord(recordId) }
                    )
                }
            }
        }
    }
    
    // ✅ ДОБАВЛЕНО: Edit Name Dialog (Session 9 missing ~60 lines)
    if (showEditNameDialog) {
        EditRecordNameDialog(
            currentName = record?.name ?: "",
            currentDescription = record?.description,
            onDismiss = { showEditNameDialog = false },
            onConfirm = { newName, newDescription ->
                viewModel.updateRecordName(newName, newDescription)
                showEditNameDialog = false
            }
        )
    }
    
    // ✅ ДОБАВЛЕНО: Edit Document Dialog (Session 9 missing ~80 lines)
    editingDocument?.let { document ->
        EditDocumentDialog(
            document = document,
            onDismiss = { editingDocument = null },
            onSave = { updatedText ->
                viewModel.updateOriginalText(document.id, updatedText)
                editingDocument = null
            }
        )
    }
}

// ============================================
// ✅ СОЗДАНО: DocumentHeader (Session 9 missing component)
// ============================================

@Composable
private fun DocumentHeader(
    title: String,
    description: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = GoogleDocsPrimary
            )
            
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit name",
                    tint = GoogleDocsPrimary
                )
            }
        }
        
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = GoogleDocsSecondaryText
            )
        }
    }
}

// ============================================
// ✅ СОЗДАНО: Edit Record Name Dialog (Session 9 missing ~60 lines)
// ============================================

@Composable
private fun EditRecordNameDialog(
    currentName: String,
    currentDescription: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var description by remember { mutableStateOf(currentDescription ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, description.ifBlank { null })
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ============================================
// ✅ СОЗДАНО: Edit Document Dialog (Session 9 missing ~80 lines)
// ============================================

@Composable
private fun EditDocumentDialog(
    document: Document,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(document.originalText ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Text") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("OCR Text") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                maxLines = 15
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}