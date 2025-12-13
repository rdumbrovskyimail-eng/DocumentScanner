package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import coil3.compose.AsyncImage
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.usecase.*
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(documents, key = { it.id }) { document ->
                            DocumentCard(
                                document = document,
                                onImageClick = { onImageClick(document.id) },
                                onCopyOriginal = { viewModel.copyToClipboard(document.originalText) },
                                onCopyTranslation = { viewModel.copyToClipboard(document.translatedText) },
                                onRetryTranslation = { viewModel.retryTranslation(document.id) },
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
}

@Composable
private fun DocumentCard(
    document: Document,
    onImageClick: () -> Unit,
    onCopyOriginal: () -> Unit,
    onCopyTranslation: () -> Unit,
    onRetryTranslation: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight(),
                    onClick = onImageClick
                ) {
                    AsyncImage(
                        model = File(document.imagePath),
                        contentDescription = "Document image",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Card(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌍 ORIGINAL",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            when (document.processingStatus) {
                                ProcessingStatus.INITIAL,
                                ProcessingStatus.OCR_IN_PROGRESS -> {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Scanning...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                
                                ProcessingStatus.ERROR -> {
                                    Text(
                                        "OCR failed",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                
                                else -> {
                                    Text(
                                        text = document.originalText ?: "",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        
                        if (document.originalText != null) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = onCopyOriginal) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🇷🇺 TRANSLATION",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                    
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
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Translating...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
                            ProcessingStatus.ERROR -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Translation failed",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            
                            ProcessingStatus.COMPLETE -> {
                                Text(
                                    text = document.translatedText ?: "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (document.translatedText != null) {
                            IconButton(onClick = onCopyTranslation) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }
                        
                        if (document.processingStatus == ProcessingStatus.ERROR) {
                            IconButton(onClick = onRetryTranslation) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Document")
            }
        }
    }
}

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object Empty : EditorUiState
    data class Success(val documents: List<Document>) : EditorUiState
    data class Error(val message: String) : EditorUiState
}