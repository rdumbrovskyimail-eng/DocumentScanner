package com.docs.scanner.presentation.screens.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.presentation.components.*
import java.io.File

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
                title = {
                    Column {
                        folderName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Text(
                            text = record?.name ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (record?.description != null) {
                            Text(
                                text = record!!.description!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditNameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
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
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            DocumentItemCard(
                                document = doc,
                                onImageClick = { onImageClick(doc.id) },
                                onOriginalTextClick = { editingDocument = doc },
                                onGptOriginalClick = {
                                    openGptWithPrompt(
                                        context = context,
                                        text = doc.originalText ?: "",
                                        isTranslation = false
                                    )
                                },
                                onCopyOriginal = {
                                    copyToClipboard(context, doc.originalText ?: "")
                                },
                                onPasteOriginal = {
                                    val clipboard = getClipboardText(context)
                                    if (clipboard != null) {
                                        viewModel.updateOriginalText(doc.id, clipboard)
                                    }
                                },
                                onGptTranslationClick = {
                                    openGptWithPrompt(
                                        context = context,
                                        text = doc.translatedText ?: "",
                                        isTranslation = true
                                    )
                                },
                                onRetryOcr = { viewModel.retryOcr(doc.id) },
                                onRetryTranslation = { viewModel.retryTranslation(doc.id) },
                                onDelete = { viewModel.deleteDocument(doc.id) }
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

@Composable
private fun DocumentItemCard(
    document: Document,
    onImageClick: () -> Unit,
    onOriginalTextClick: () -> Unit,
    onGptOriginalClick: () -> Unit,
    onCopyOriginal: () -> Unit,
    onPasteOriginal: () -> Unit,
    onGptTranslationClick: () -> Unit,
    onRetryOcr: () -> Unit,
    onRetryTranslation: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(LocalContext.current.filesDir, document.imagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Document",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onImageClick),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Original Text",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (document.processingStatus == ProcessingStatus.OCR_IN_PROGRESS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (document.originalText.isNullOrBlank()) {
                Text(
                    text = "No text detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                ActionButton(
                    icon = Icons.Default.Refresh,
                    label = "Retry OCR",
                    onClick = onRetryOcr
                )
            } else {
                Text(
                    text = document.originalText ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOriginalTextClick)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        icon = Icons.Default.Edit,
                        label = "Edit",
                        onClick = onOriginalTextClick
                    )
                    ActionButton(
                        icon = Icons.Default.SmartToy,
                        label = "GPT",
                        onClick = onGptOriginalClick
                    )
                    ActionButton(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy",
                        onClick = onCopyOriginal
                    )
                    ActionButton(
                        icon = Icons.Default.ContentPaste,
                        label = "Paste",
                        onClick = onPasteOriginal
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Translation",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            when {
                document.processingStatus == ProcessingStatus.TRANSLATION_IN_PROGRESS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Translating...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                document.translatedText.isNullOrBlank() -> {
                    Text(
                        text = "Translation failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    ActionButton(
                        icon = Icons.Default.Refresh,
                        label = "Retry",
                        onClick = onRetryTranslation
                    )
                }
                else -> {
                    Text(
                        text = document.translatedText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    ActionButton(
                        icon = Icons.Default.SmartToy,
                        label = "GPT",
                        onClick = onGptTranslationClick
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete")
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun openGptWithPrompt(context: Context, text: String, isTranslation: Boolean) {
    val prompt = if (isTranslation) {
        "Improve this translation:\n\n$text"
    } else {
        "Correct this OCR text:\n\n$text"
    }
    
    val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
    val uri = Uri.parse("https://chatgpt.com/?q=$encodedPrompt")
    
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Document text", text)
    clipboard.setPrimaryClip(clip)
}

private fun getClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}
