/*
 * EditorScreen.kt
 * Version: 3.0.0 - PRODUCTION READY 2026 - HYBRID IMAGE PICKER
 * 
 * ✅ CRITICAL FIXES:
 * - Поддержка ОДНОГО изображения (GetContent)
 * - Поддержка МНОЖЕСТВА изображений (GetMultipleContents)
 * - Умное переключение между режимами
 * - Стабильная работа на Android 10-16
 * 
 * ✅ UX IMPROVEMENTS:
 * - Пользователь сам выбирает: 1 фото или несколько
 * - Чёткие подсказки в диалогах
 * - Анимированные превью
 */

package com.docs.scanner.presentation.screens.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.BuildConfig
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.components.FullscreenTextEditor
import com.docs.scanner.presentation.components.SmartDivider
import com.docs.scanner.presentation.screens.editor.components.*
import com.docs.scanner.presentation.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    recordId: Long,
    viewModel: EditorViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onImageClick: (Long) -> Unit,
    onCameraClick: () -> Unit
) {
    val context = LocalContext.current
    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val moveTargets by viewModel.moveTargets.collectAsStateWithLifecycle()
    val selectedDocIds by viewModel.selectedDocIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedDocumentsCount.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()
    
    // Dialog states
    var recordMenuExpanded by remember { mutableStateOf(false) }
    var showRenameRecordDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAddDocumentDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSmartRetryBanner by remember { mutableStateOf(true) }
    
    // Text editor state
    var editDocTextTarget by remember { mutableStateOf<Pair<Document, Boolean>?>(null) }
    var showMoveDocumentDialog by remember { mutableStateOf<Document?>(null) }
    
    // Page menu state
    var docMenuExpanded by remember { mutableStateOf<Long?>(null) }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // LAUNCHERS - CRITICAL FIX: ГИБРИДНЫЙ ПОДХОД
    // ═══════════════════════════════════════════════════════════════════════════
    
    // ✅ SINGLE IMAGE PICKER (для 1 фото)
    val singleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { 
            viewModel.addDocument(it)
            if (BuildConfig.DEBUG) {
                timber.log.Timber.d("📷 Single image selected: $it")
            }
        }
    }
    
    // ✅ MULTI IMAGE PICKER (для нескольких фото)
    val multiGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addDocuments(uris)
            if (BuildConfig.DEBUG) {
                timber.log.Timber.d("📷 Multiple images selected: ${uris.size}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is EditorUiState.Success) {
            if (state.documents.isEmpty()) {
                showAddDocumentDialog = true
            }
            if (state.errorMessage != null) {
                snackbarHostState.showSnackbar(state.errorMessage)
                viewModel.clearError()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareEvent.collect { event ->
            when (event) {
                is ShareEvent.File -> {
                    val file = File(event.path)
                    if (!file.exists()) {
                        snackbarHostState.showSnackbar("File not found")
                        return@collect
                    }

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share"))
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════════════════════

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                SelectionTopBar(
                    selectedCount = selectedDocIds.size,
                    totalCount = (uiState as? EditorUiState.Success)?.documents?.size ?: 0,
                    onCloseClick = { viewModel.exitSelectionMode() },
                    onSelectAllClick = {
                        if (selectedDocIds.size == (uiState as? EditorUiState.Success)?.documents?.size) {
                            viewModel.deselectAll()
                        } else {
                            viewModel.selectAll()
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = when (val state = uiState) {
                                is EditorUiState.Success -> state.record.name.ifBlank { 
                                    state.folderName.ifBlank { "Documents" } 
                                }
                                else -> "Documents"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onCameraClick) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                        }
                        
                        // ✅ CRITICAL: ВЫБОР РЕЖИМА (1 или много)
                        IconButton(onClick = { showAddDocumentDialog = true }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Gallery")
                        }
                        
                        IconButton(onClick = { recordMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Record menu")
                        }

                        DropdownMenu(
                            expanded = recordMenuExpanded,
                            onDismissRequest = { recordMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    recordMenuExpanded = false
                                    showRenameRecordDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Description") },
                                onClick = {
                                    recordMenuExpanded = false
                                    showEditDescriptionDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Tags") },
                                onClick = {
                                    recordMenuExpanded = false
                                    showTagsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Languages") },
                                onClick = {
                                    recordMenuExpanded = false
                                    showLanguageDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                            )

                            HorizontalDivider()
                            
                            val hasDocuments = (uiState as? EditorUiState.Success)?.documents?.isNotEmpty() == true
                            if (hasDocuments) {
                                DropdownMenuItem(
                                    text = { Text("Select pages") },
                                    onClick = {
                                        recordMenuExpanded = false
                                        viewModel.enterSelectionMode()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) }
                                )
                                
                                HorizontalDivider()
                            }

                            DropdownMenuItem(
                                text = { Text("Share as PDF") },
                                onClick = {
                                    recordMenuExpanded = false
                                    viewModel.shareRecordAsPdf()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )

                            DropdownMenuItem(
                                text = { Text("Share images (ZIP)") },
                                onClick = {
                                    recordMenuExpanded = false
                                    viewModel.shareRecordImagesZip()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isSelectionMode && selectedDocIds.isNotEmpty()) {
                BatchActionsBar(
                    selectedCount = selectedDocIds.size,
                    totalCount = (uiState as? EditorUiState.Success)?.documents?.size ?: 0,
                    onDeleteClick = { showDeleteDialog = true },
                    onExportClick = { showExportDialog = true },
                    onMoveClick = { showMoveDocumentDialog = (uiState as? EditorUiState.Success)?.documents?.firstOrNull() },
                    onSelectAllClick = {
                        if (selectedDocIds.size == (uiState as? EditorUiState.Success)?.documents?.size) {
                            viewModel.deselectAll()
                        } else {
                            viewModel.selectAll()
                        }
                    },
                    onClearSelection = { viewModel.exitSelectionMode() }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = uiState) {
                is EditorUiState.Loading -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                is EditorUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                is EditorUiState.Success -> {
                    if (state.isProcessing) {
                        BatchProgressBanner(
                            processedCount = state.processingProgress,
                            totalCount = 100,
                            currentStage = state.processingMessage
                        )
                    }
                    
                    if (failedCount > 0 && showSmartRetryBanner && !state.isProcessing) {
                        SmartRetryBanner(
                            failedCount = failedCount,
                            onRetryClick = { viewModel.retryFailedDocuments() },
                            onDismiss = { showSmartRetryBanner = false }
                        )
                    }

                    if (state.documents.isEmpty()) {
                        EmptyRecordState(
                            onCameraClick = onCameraClick,
                            onGalleryClick = { showAddDocumentDialog = true }
                        )
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(
                                items = state.documents, 
                                key = { it.id.value }
                            ) { doc ->
                                val index = state.documents.indexOf(doc)
                                
                                DocumentCard(
                                    document = doc,
                                    index = index,
                                    isSelected = selectedDocIds.contains(doc.id.value),
                                    isSelectionMode = isSelectionMode,
                                    isDragging = false,
                                    onImageClick = { onImageClick(doc.id.value) },
                                    onOcrTextClick = { editDocTextTarget = doc to true },
                                    onTranslationClick = { editDocTextTarget = doc to false },
                                    onSelectionToggle = {
                                        if (!isSelectionMode) {
                                            viewModel.enterSelectionMode()
                                        }
                                        viewModel.toggleDocumentSelection(doc.id.value)
                                    },
                                    onMenuClick = { docMenuExpanded = doc.id.value },
                                    onRetryOcr = { viewModel.retryOcr(doc.id.value) },
                                    onRetryTranslation = { viewModel.retryTranslation(doc.id.value) }
                                )
                                
                                DropdownMenu(
                                    expanded = docMenuExpanded == doc.id.value,
                                    onDismissRequest = { docMenuExpanded = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Share page") },
                                        onClick = {
                                            docMenuExpanded = null
                                            viewModel.shareSingleImage(doc.imagePath)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit OCR text") },
                                        onClick = {
                                            docMenuExpanded = null
                                            editDocTextTarget = doc to true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit translation") },
                                        onClick = {
                                            docMenuExpanded = null
                                            editDocTextTarget = doc to false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    
                                    if (doc.processingStatus.isFailed) {
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Retry OCR") },
                                            onClick = {
                                                docMenuExpanded = null
                                                viewModel.retryOcr(doc.id.value)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Retry translation") },
                                            onClick = {
                                                docMenuExpanded = null
                                                viewModel.retryTranslation(doc.id.value)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                        )
                                    }
                                    
                                    HorizontalDivider()
                                    
                                    DropdownMenuItem(
                                        enabled = moveTargets.isNotEmpty(),
                                        text = { Text("Move to another record") },
                                        onClick = {
                                            docMenuExpanded = null
                                            showMoveDocumentDialog = doc
                                        },
                                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) }
                                    )
                                    
                                    HorizontalDivider()
                                    
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = GoogleDocsError) },
                                        onClick = {
                                            docMenuExpanded = null
                                            viewModel.deleteDocument(doc.id.value)
                                        },
                                        leadingIcon = { 
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = GoogleDocsError) 
                                        }
                                    )
                                }
                                
                                if (index < state.documents.lastIndex) {
                                    SmartDivider()
                                }
                            }
                        }
                    }
                    
                    if (!isSelectionMode && state.documents.isNotEmpty()) {
                        FloatingActionButtons(
                            onCameraClick = onCameraClick,
                            onGalleryClick = { showAddDocumentDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End)
                        )
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════════════════
    
    val success = uiState as? EditorUiState.Success
    
    // ✅ CRITICAL: УЛУЧШЕННЫЙ ДИАЛОГ С ВЫБОРОМ РЕЖИМА
    if (showAddDocumentDialog) {
        AddDocumentDialog(
            onDismiss = { showAddDocumentDialog = false },
            onCameraClick = {
                showAddDocumentDialog = false
                onCameraClick()
            },
            onSinglePhotoClick = {
                showAddDocumentDialog = false
                singleGalleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onMultiplePhotosClick = {
                showAddDocumentDialog = false
                multiGalleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            isFirstTime = success?.documents?.isEmpty() == true
        )
    }

    // Остальные диалоги без изменений...
    if (showRenameRecordDialog && success != null) {
        var name by remember(success.record.id.value) { mutableStateOf(success.record.name) }
        AlertDialog(
            onDismissRequest = { showRenameRecordDialog = false },
            title = { Text("Rename record") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.updateRecordName(name)
                        showRenameRecordDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameRecordDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDescriptionDialog && success != null) {
        var desc by remember(success.record.id.value) { mutableStateOf(success.record.description.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditDescriptionDialog = false },
            title = { Text("Edit description") },
            text = {
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateRecordDescription(desc.ifBlank { null })
                        showEditDescriptionDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDescriptionDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTagsDialog && success != null) {
        var newTag by remember(success.record.id.value) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTagsDialog = false },
            title = { Text("Tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (success.record.tags.isEmpty()) {
                        Text("No tags yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        success.record.tags.forEach { tag ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(tag)
                                IconButton(onClick = { viewModel.removeTag(tag) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove tag")
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("Add tag (a-z, 0-9, _, -)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newTag.isNotBlank(),
                    onClick = {
                        viewModel.addTag(newTag)
                        newTag = ""
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTagsDialog = false }) { Text("Close") }
            }
        )
    }

    if (showLanguageDialog && success != null) {
        var source by remember(success.record.id.value) { mutableStateOf(success.record.sourceLanguage) }
        var target by remember(success.record.id.value) { mutableStateOf(success.record.targetLanguage) }

        val sourceOptions = Language.ocrSourceOptions
        val targetOptions = Language.translationSupported.filter { it != Language.AUTO }

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Languages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("OCR source language", style = MaterialTheme.typography.labelLarge)
                    sourceOptions.take(12).forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { source = lang }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(selected = source == lang, onClick = { source = lang })
                            Text("${lang.displayName} (${lang.code})")
                        }
                    }
                    if (sourceOptions.size > 12) {
                        Text("…more languages available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Text("Translation target language", style = MaterialTheme.typography.labelLarge)
                    targetOptions.take(12).forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { target = lang }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(selected = target == lang, onClick = { target = lang })
                            Text("${lang.displayName} (${lang.code})")
                        }
                    }
                    if (targetOptions.size > 12) {
                        Text("…more languages available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateLanguages(source, target)
                        showLanguageDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
        )
    }

    editDocTextTarget?.let { (doc, isOriginal) ->
        FullscreenTextEditor(
            initialText = if (isOriginal) doc.originalText.orEmpty() else doc.translatedText.orEmpty(),
            onDismiss = { editDocTextTarget = null },
            onSave = { newText ->
                if (isOriginal) {
                    viewModel.updateDocumentText(doc.id.value, newText, doc.translatedText)
                } else {
                    viewModel.updateDocumentText(doc.id.value, doc.originalText, newText)
                }
                editDocTextTarget = null
            }
        )
    }

    showMoveDocumentDialog?.let { doc ->
        var selectedRecordId by remember(doc.id.value) { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { showMoveDocumentDialog = null },
            title = { Text(if (isSelectionMode) "Move ${selectedDocIds.size} pages" else "Move page to record") },
            text = {
                if (moveTargets.isEmpty()) {
                    Text("No other records in this folder.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        moveTargets.take(20).forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRecordId = r.id.value }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedRecordId == r.id.value,
                                    onClick = { selectedRecordId = r.id.value }
                                )
                                Text(r.name)
                            }
                        }
                        if (moveTargets.size > 20) {
                            Text("…more records available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedRecordId != null,
                    onClick = {
                        val targetId = selectedRecordId
                        if (targetId != null) {
                            if (isSelectionMode) {
                                viewModel.moveSelectedToRecord(targetId)
                            } else {
                                viewModel.moveDocument(doc.id.value, targetId)
                            }
                        }
                        showMoveDocumentDialog = null
                    }
                ) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDocumentDialog = null }) { Text("Cancel") }
            }
        )
    }
    
    if (showExportDialog) {
        ExportOptionsDialog(
            selectedCount = selectedDocIds.size,
            onDismiss = { showExportDialog = false },
            onExportPdf = { 
                viewModel.exportSelectedDocuments(asPdf = true)
                showExportDialog = false
            },
            onExportZip = { 
                viewModel.exportSelectedDocuments(asPdf = false)
                showExportDialog = false
            }
        )
    }
    
    if (showDeleteDialog) {
        DeletePagesDialog(
            count = selectedDocIds.size,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { 
                viewModel.deleteSelectedDocuments()
                showDeleteDialog = false
            }
        )
    }
}