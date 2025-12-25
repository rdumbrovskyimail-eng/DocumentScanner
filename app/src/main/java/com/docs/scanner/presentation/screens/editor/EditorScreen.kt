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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.components.*
import com.docs.scanner.presentation.screens.editor.components.*
import com.docs.scanner.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    recordId: Long,
    viewModel: EditorViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onImageClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val record by viewModel.record.collectAsState()
    val folderName by viewModel.folderName.collectAsState(initial = null)
    
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editingDocument by remember { mutableStateOf<Document?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            android.util.Log.d("EditorScreen", "📷 Image selected: $uri")
            viewModel.addDocument(it) 
        }
    }
    
    val listState = rememberLazyListState()
    
    LaunchedEffect(recordId) {
        android.util.Log.d("EditorScreen", "🔄 Loading record: $recordId")
        viewModel.loadRecord(recordId)
    }
    
    Scaffold(
        containerColor = GoogleDocsBackground,
        topBar = {
            GoogleDocsTopBar(
                title = folderName ?: "Documents",
                onBackClick = {
                    android.util.Log.d("EditorScreen", "⬅️ Back clicked")
                    onBackClick()
                },
                onMenuClick = { 
                    android.util.Log.d("EditorScreen", "⋮ Menu clicked")
                    // TODO: Menu
                }
            )
        },
        floatingActionButton = {
            FloatingActionButtons(
                onCameraClick = { 
                    android.util.Log.d("EditorScreen", "📸 Camera clicked")
                    // TODO: Camera
                },
                onGalleryClick = { 
                    android.util.Log.d("EditorScreen", "🖼️ Gallery clicked")
                    galleryLauncher.launch("image/*") 
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is EditorUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = GoogleDocsPrimary
                    )
                }
                
                is EditorUiState.Empty -> {
                    EmptyState(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = GoogleDocsPrimary
                            )
                        },
                        title = "No documents yet",
                        message = "Add your first document to scan and translate",
                        actionText = "Add Document",
                        onActionClick = { 
                            android.util.Log.d("EditorScreen", "➕ Add Document clicked")
                            galleryLauncher.launch("image/*") 
                        }
                    )
                }
                
                is EditorUiState.Success -> {
                    val documents = state.documents
                    
                    android.util.Log.d("EditorScreen", "✅ Documents loaded: ${documents.size}")
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ✅ DOCUMENT HEADER
                        item(key = "header") {
                            DocumentHeader(
                                recordName = record?.name ?: "Document",
                                description = record?.description,
                                onEditClick = { 
                                    android.util.Log.d("EditorScreen", "✏️ Edit name clicked")
                                    showEditNameDialog = true 
                                }
                            )
                        }
                        
                        // ✅ DIVIDER
                        item(key = "divider") {
                            SimpleDivider()
                        }
                        
                        // ✅ DOCUMENTS
                        items(
                            items = documents,
                            key = { "doc_${it.id}" }
                        ) { document ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Pagination indicator
                                if (documents.size > 1) {
                                    Text(
                                        text = "Page ${documents.indexOf(document) + 1} of ${documents.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GoogleDocsTextSecondary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                
                                // ✅ G-CONTAINER
                                GContainerLayout(
                                    previewContent = {
                                        DocumentPreview(
                                            document = document,
                                            onImageClick = { 
                                                android.util.Log.d("EditorScreen", "🖼️ Image clicked: ${document.id}")
                                                onImageClick(document.id) 
                                            }
                                        )
                                    },
                                    ocrTextContent = {
                                        OCRTextContainer(
                                            text = document.originalText,
                                            onTextClick = { 
                                                android.util.Log.d("EditorScreen", "📝 Text edit clicked")
                                                editingDocument = document 
                                            }
                                        )
                                    },
                                    actionButtonsContent = {
                                        ActionButtonsRow(
                                            text = document.originalText ?: "",
                                            onRetry = { 
                                                android.util.Log.d("EditorScreen", "🔄 Retry OCR: ${document.id}")
                                                viewModel.retryOcr(document.id) 
                                            }
                                        )
                                    }
                                )
                                
                                // ✅ TRANSLATION FIELD
                                TranslationField(
                                    translatedText = document.translatedText
                                )
                                
                                // ✅ ACTION BUTTONS (для перевода)
                                if (!document.translatedText.isNullOrBlank()) {
                                    ActionButtonsRow(
                                        text = document.translatedText ?: "",
                                        onRetry = { 
                                            android.util.Log.d("EditorScreen", "🔄 Retry translation: ${document.id}")
                                            viewModel.retryTranslation(document.id) 
                                        }
                                    )
                                }
                                
                                // ✅ SMART DIVIDER
                                if (document != documents.lastOrNull()) {
                                    SmartDivider(
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                is EditorUiState.Error -> {
                    android.util.Log.e("EditorScreen", "❌ Error: ${state.message}")
                    ErrorState(
                        error = state.message,
                        onRetry = { 
                            android.util.Log.d("EditorScreen", "🔄 Retry loading")
                            viewModel.loadRecord(recordId) 
                        }
                    )
                }
            }
        }
    }
    
    // ✅ EDIT NAME DIALOG
    if (showEditNameDialog && record != null) {
        var newName by remember { mutableStateOf(record!!.name) }
        var newDescription by remember { mutableStateOf(record!!.description ?: "") }
        
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
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
                        android.util.Log.d("EditorScreen", "💾 Saving: name=$newName")
                        viewModel.updateRecordName(newName)
                        viewModel.updateRecordDescription(newDescription.ifBlank { null })
                        showEditNameDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // ✅ FULLSCREEN TEXT EDITOR
    editingDocument?.let { doc ->
        FullscreenTextEditor(
            initialText = doc.originalText ?: "",
            onDismiss = { 
                android.util.Log.d("EditorScreen", "❌ Edit cancelled")
                editingDocument = null 
            },
            onSave = { newText ->
                android.util.Log.d("EditorScreen", "💾 Saving text: ${newText.take(50)}...")
                viewModel.updateOriginalText(doc.id, newText)
                editingDocument = null
            }
        )
    }
}

// ============================================
// GOOGLE DOCS TOP BAR
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleDocsTopBar(
    title: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Surface(
        color = GoogleDocsBackground,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = GoogleDocsTextPrimary
                        )
                    }
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = GoogleDocsTextPrimary
                    )
                }
                
                MoreButton(onClick = onMenuClick)
            }
            
            SimpleDivider()
        }
    }
}

// ============================================
// DOCUMENT HEADER
// ============================================

@Composable
private fun DocumentHeader(
    recordName: String,
    description: String?,
    onEditClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = recordName,
                style = MaterialTheme.typography.displayLarge,
                color = GoogleDocsTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = GoogleDocsPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = GoogleDocsTextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
