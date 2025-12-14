package com.docs.scanner.presentation.screens.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.presentation.components.*
import java.io.File

@Composable
fun EditorScreen(
    recordId: Long,
    viewModel: EditorViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onImageClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val record by viewModel.record.collectAsState()
    val context = LocalContext.current
    
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditDescDialog by remember { mutableStateOf(false) }
    var editingDocument by remember { mutableStateOf<Document?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addDocument(it) }
    }
    
    LaunchedEffect(recordId) {
        viewModel.loadRecord(recordId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(record?.name ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { galleryLauncher.launch("image/*") }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Document")
            }
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
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is EditorUiState.Empty -> {
                    EmptyState(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Record Header
                        item {
                            RecordHeaderCard(
                                name = record?.name ?: "",
                                description = record?.description,
                                onNameClick = { showEditNameDialog = true },
                                onDescriptionClick = { showEditDescDialog = true }
                            )
                        }
                        
                        // Documents
                        items(documents, key = { it.id }) { document ->
                            DocumentCard(
                                document = document,
                                onImageClick = { onImageClick(document.id) },
                                onOriginalTextClick = { editingDocument = document },
                                onGptOriginalClick = {
                                    openGptWithPrompt(
                                        context = context,
                                        text = document.originalText ?: "",
                                        isTranslation = false
                                    )
                                },
                                onCopyOriginal = {
                                    copyToClipboard(context, document.originalText ?: "")
                                },
                                onPasteOriginal = {
                                    val clipboard = getClipboardText(context)
                                    if (clipboard != null) {
                                        viewModel.updateOriginalText(document.id, clipboard)
                                    }
                                },
                                onGptTranslationClick = {
                                    openGptWithPrompt(
                                        context = context,
                                        text = document.translatedText ?: "",
                                        isTranslation = true
                                    )
                                },
                                onDelete = { viewModel.deleteDocument(document.id) }
                            )
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
    
    // Edit name dialog
    if (showEditNameDialog && record != null) {
        var newName by remember { mutableStateOf(record!!.name) }
        
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Name") },
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
                        viewModel.updateRecordName(newName)
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
    
    // Edit description dialog
    if (showEditDescDialog && record != null) {
        var newDesc by remember { mutableStateOf(record!!.description ?: "") }
        
        AlertDialog(
            onDismissRequest = { showEditDescDialog = false },
            title = { Text("Edit Description") },
            text = {
                OutlinedTextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateRecordDescription(newDesc.ifBlank { null })
                        showEditDescDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDescDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Fullscreen text editor
    editingDocument?.let { document ->
        FullscreenTextEditor(
            initialText = document.originalText ?: "",
            onDismiss = { editingDocument = null },
            onSave = { newText ->
                viewModel.updateOriginalText(document.id, newText)
                editingDocument = null
            }
        )
    }
}

@Composable
private fun RecordHeaderCard(
    name: String,
    description: String?,
    onNameClick: () -> Unit,
    onDescriptionClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onNameClick)
            )
            
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.clickable(onClick = onDescriptionClick)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add description...",
                    fontSize = 14.sp,
                    color = Color(0xFFBDBDBD),
                    modifier = Modifier.clickable(onClick = onDescriptionClick)
                )
            }
        }
    }
}

@Composable
private fun DocumentCard(
    document: Document,
    onImageClick: () -> Unit,
    onOriginalTextClick: () -> Unit,
    onGptOriginalClick: () -> Unit,
    onCopyOriginal: () -> Unit,
    onPasteOriginal: () -> Unit,
    onGptTranslationClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp)
        ) {
            // Photo + Original Text (40% + 60%)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                // PHOTO - 40%
                Card(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    onClick = onImageClick
                ) {
                    AsyncImage(
                        model = File(document.imagePath),
                        contentDescription = "Document image",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // ORIGINAL TEXT - 60%
                Card(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Text content
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clickable(onClick = onOriginalTextClick)
                                .padding(8.dp)
                        ) {
                            when (document.processingStatus) {
                                ProcessingStatus.INITIAL,
                                ProcessingStatus.OCR_IN_PROGRESS -> {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Scanning...",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                ProcessingStatus.ERROR -> {
                                    Text(
                                        "OCR failed",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                
                                else -> {
                                    Text(
                                        text = document.originalText ?: "",
                                        fontSize = 12.sp,
                                        maxLines = 10,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // Buttons under Original
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = onGptOriginalClick,
                                enabled = document.originalText != null,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "GPT",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = onCopyOriginal,
                                enabled = document.originalText != null,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = onPasteOriginal,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Translation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE3F2FD)
                )
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 300.dp)
                            .padding(12.dp)
                    ) {
                        when (document.processingStatus) {
                            ProcessingStatus.INITIAL,
                            ProcessingStatus.OCR_IN_PROGRESS,
                            ProcessingStatus.OCR_COMPLETE,
                            ProcessingStatus.TRANSLATION_IN_PROGRESS -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Translating...",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            
                            ProcessingStatus.ERROR -> {
                                Text(
                                    "Translation failed",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            
                            ProcessingStatus.COMPLETE -> {
                                Text(
                                    text = document.translatedText ?: "",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = onGptTranslationClick,
                                enabled = document.translatedText != null,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "GPT",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = "More",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// Utility functions
private fun openGptWithPrompt(context: Context, text: String, isTranslation: Boolean) {
    val prompt = if (isTranslation) {
        "Привет, это переведенный текст, пойми контекст, проанализируй, исправь ошибки, и выдай мне текст как в оригинале, но испраленный, откоректированный. С сохранением смысла. Дай только текст. Отображи его в кодовом поле, с кнопкой копировать!\n\n$text"
    } else {
        "Привет, это отсканированный текст, пойми контекст, проанализируй, исправь ошибки, и выдай мне текст как в оригинале, но испраленный, откоректированный. Дай только текст. Отображи его в кодовом поле, с кнопкой копировать!\n\n$text"
    }
    
    copyToClipboard(context, prompt)
    
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://chatgpt.com/")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.openai.com/"))
        context.startActivity(browserIntent)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("text", text)
    clipboard.setPrimaryClip(clip)
}

private fun getClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object Empty : EditorUiState
    data class Success(val documents: List<Document>) : EditorUiState
    data class Error(val message: String) : EditorUiState
}
