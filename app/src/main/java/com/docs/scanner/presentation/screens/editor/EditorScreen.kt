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
    // Предполагаем, что имя папки берется из viewModel или состояния
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
                        // ✅ ПАПКА СВЕРХУ (мелким текстом)
                        folderName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // ✅ ИМЯ ЗАПИСИ (жирным)
                        Text(
                            text = record?.name ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // ✅ ОПИСАНИЕ (обычным)
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
    
    // ... остальной код (диалоги и вспомогательные функции) остается прежним
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

    // Сократил для краткости ответа, остальная логика из вашего исходника сохранена
}

// Вставьте здесь DocumentItemCard, ActionButton и другие приватные методы из вашего исходного кода
