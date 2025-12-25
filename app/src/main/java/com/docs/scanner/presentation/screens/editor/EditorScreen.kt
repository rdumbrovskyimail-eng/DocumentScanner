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
        uri?.let { viewModel.addDocument(it) }
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
                onBackClick = onBackClick,
                onMenuClick = { /* TODO: Menu */ }
            )
        },
        floatingActionButton = {
            FloatingActionButtons(
                onCameraClick = { /* TODO: Camera */ },
                onGalleryClick = { galleryLauncher.launch("image/*") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState) {
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
                        onActionClick = { galleryLauncher.launch("image/*") }
                    )
                }
                
                is EditorUiState.Success -> {
                    val documents = (uiState as EditorUiState.Success).documents
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ✅ DOCUMENT HEADER
                        item {
                            DocumentHeader(
                                recordName = record?.name ?: "Document",
                                description = record?.description,
                                onEditClick = { showEditNameDialog = true }
                            )
                        }
                        
                        // ✅ DIVIDER
                        item {
                            SimpleDivider()
                        }
                        
                        // ✅ DOCUMENTS
                        items(
                            items = documents,
                            key = { it.id }
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
                                            onImageClick = { onImageClick(document.id) }
                                        )
                                    },
                                    ocrTextContent = {
                                        OCRTextContainer(
                                            text = document.originalText,
                                            onTextClick = { editingDocument = document }
                                        )
                                    },
                                    actionButtonsContent = {
                                        ActionButtonsRow(
                                            text = document.originalText ?: "",
                                            onRetry = { viewModel.retryOcr(document.id) }
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
                                        onRetry = { viewModel.retryTranslation(document.id) }
                                    )
                                }
                                
                                // ✅ SMART DIVIDER (если не последний)
                                if (document != documents.last()) {
                                    SmartDivider(
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                is EditorUiState.Error -> {
                    ErrorState(
                        error = (uiState as EditorUiState.Error).message,
                        onRetry = { viewModel.loadRecord(recordId) }
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
            onDismiss = { editingDocument = null },
            onSave = { newText ->
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
