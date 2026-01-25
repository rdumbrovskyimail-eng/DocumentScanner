/*
 * EditorScreen.kt
 * Version: 5.2.2 - PRODUCTION READY (2026) - 100% FIXED
 */

package com.docs.scanner.presentation.screens.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.BuildConfig
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.components.SmartDivider
import com.docs.scanner.presentation.screens.editor.components.*
import com.docs.scanner.presentation.theme.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import timber.log.Timber

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
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val moveTargets by viewModel.moveTargets.collectAsStateWithLifecycle()
    val selectedDocIds by viewModel.selectedDocIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedDocumentsCount.collectAsStateWithLifecycle()
    val selectedCount by viewModel.selectedCount.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val ocrSettings by viewModel.ocrSettings.collectAsStateWithLifecycle()
    val confidenceTooltip by viewModel.confidenceTooltip.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()
    
    var recordMenuExpanded by remember { mutableStateOf(false) }
    var showRenameRecordDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAddDocumentDialog by remember { mutableStateOf(false) }
    var showSmartRetryBanner by remember { mutableStateOf(true) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchExportDialog by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var editDocTextTarget by remember { mutableStateOf<Pair<Document, Boolean>?>(null) }
    var showMoveDocumentDialog by remember { mutableStateOf<Document?>(null) }
    var docMenuExpanded by remember { mutableStateOf<Long?>(null) }
    
    val singleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { 
            viewModel.addDocument(it)
            if (BuildConfig.DEBUG) {
                Timber.d("📷 Single image selected: $it")
            }
        }
    }
    
    val multiGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addDocuments(uris)
            if (BuildConfig.DEBUG) {
                Timber.d("📷 Multiple images selected: ${uris.size}")
            }
        }
    }
    
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.reorderDocuments(from.index, to.index)
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is EditorUiState.Success) {
            if (state.documents.isEmpty()) showAddDocumentDialog = true
            state.errorMessage?.let {
                snackbarHostState.showSnackbar(it)
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
                        context, "${BuildConfig.APPLICATION_ID}.fileprovider", file
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

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("$selectedCount selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    actions = {
                        if (canUndo) {
                            IconButton(onClick = { viewModel.undoLastEdit() }) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo")
                            }
                        }
                        IconButton(onClick = {
                            val state = uiState as? EditorUiState.Success ?: return@IconButton
                            if (selectedCount == state.documents.size) {
                                viewModel.deselectAll()
                            } else {
                                viewModel.selectAll()
                            }
                        }) {
                            val state = uiState as? EditorUiState.Success
                            val isAllSelected = state != null && selectedCount == state.documents.size
                            
                            Icon(
                                if (isAllSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "Select all"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GoogleDocsPrimary.copy(alpha = 0.1f)
                    )
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
                        if (canUndo) {
                            IconButton(onClick = { viewModel.undoLastEdit() }) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo")
                            }
                        }
                        IconButton(onClick = onCameraClick) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                        }
                        IconButton(onClick = { showAddDocumentDialog = true }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Gallery")
                        }
                        IconButton(onClick = { recordMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
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
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Description") },
                                onClick = {
                                    recordMenuExpanded = false
                                    showEditDescriptionDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Notes, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Tags") },
                                onClick = {
                                    recordMenuExpanded = false
                                    showTagsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Label, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Languages") },
                                onClick = {
                                    recordMenuExpanded = false
                                    showLanguageDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Language, null) }
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
                                    leadingIcon = { Icon(Icons.Default.Checklist, null) }
                                )
                                
                                HorizontalDivider()
                            }
                            
                            if (failedCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Retry failed ($failedCount)") },
                                    onClick = {
                                        recordMenuExpanded = false
                                        viewModel.retryFailedDocuments()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Re-scan all OCR") },
                                onClick = {
                                    recordMenuExpanded = false
                                    viewModel.retryAllOcr()
                                },
                                leadingIcon = { Icon(Icons.Default.DocumentScanner, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Re-translate all") },
                                onClick = {
                                    recordMenuExpanded = false
                                    viewModel.retryAllTranslation()
                                },
                                leadingIcon = { Icon(Icons.Default.Translate, null) }
                            )
                            
                            HorizontalDivider()
                            
                            DropdownMenuItem(
                                text = { Text("Share as PDF") },
                                onClick = {
                                    recordMenuExpanded = false
                                    viewModel.shareRecordAsPdf()
                                },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share images (ZIP)") },
                                onClick = {
                                    recordMenuExpanded = false
                                    viewModel.shareRecordImagesZip()
                                },
                                leadingIcon = { Icon(Icons.Default.FolderZip, null) }
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
                    onDeleteClick = { showBatchDeleteConfirm = true },
                    onExportClick = { showBatchExportDialog = true },
                    onMoveClick = { showBatchMoveDialog = true },
                    onSelectAllClick = {
                        val state = uiState as? EditorUiState.Success ?: return@BatchActionsBar
                        if (selectedDocIds.size == state.documents.size) {
                            viewModel.deselectAll()
                        } else {
                            viewModel.selectAll()
                        }
                    },
                    onClearSelection = { viewModel.exitSelectionMode() }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButtons(
                    onCameraClick = onCameraClick,
                    onGalleryClick = { showAddDocumentDialog = true }
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is EditorUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
                            ) { document ->
                                val index = state.documents.indexOf(document)
                                
                                ReorderableItem(reorderableState, key = document.id.value) { isDragging ->
                                    DocumentCard(
                                        document = document,
                                        index = index,
                                        isSelected = selectedDocIds.contains(document.id.value),
                                        isSelectionMode = isSelectionMode,
                                        isDragging = isDragging,
                                        
                                        onImageClick = { onImageClick(document.id.value) },
                                        onOcrTextClick = { editDocTextTarget = document to true },
                                        onTranslationClick = { editDocTextTarget = document to false },
                                        onSelectionToggle = {
                                            if (!isSelectionMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.enterSelectionMode()
                                            }
                                            viewModel.toggleDocumentSelection(document.id.value)
                                        },
                                        onMenuClick = { docMenuExpanded = document.id.value },
                                        onRetryOcr = { viewModel.retryOcr(document.id.value) },
                                        onRetryTranslation = { viewModel.retryTranslation(document.id.value) },
                                        
                                        onMoveUp = { viewModel.moveDocumentUp(document.id.value) },
                                        onMoveDown = { viewModel.moveDocumentDown(document.id.value) },
                                        isFirst = index == 0,
                                        isLast = index == state.documents.lastIndex,
                                        
                                        onSharePage = { viewModel.shareSingleImage(document.imagePath) },
                                        onDeletePage = { viewModel.deleteDocument(document.id.value) },
                                        onMoveToRecord = { showMoveDocumentDialog = document },
                                        
                                        onCopyText = { text ->
                                            clipboardManager.setText(AnnotatedString(text))
                                        },
                                        onPasteText = { isOcr ->
                                            clipboardManager.getText()?.text?.let { clipText ->
                                                viewModel.pasteText(document.id.value, clipText, isOcr)
                                            }
                                        },
                                        onAiRewrite = { isOcr ->
                                            val text = if (isOcr) document.originalText else document.translatedText
                                            text?.let { viewModel.aiRewriteText(document.id.value, it, isOcr) }
                                        },
                                        onClearFormatting = { isOcr ->
                                            viewModel.clearFormatting(document.id.value, isOcr)
                                        },
                                        
                                        confidenceThreshold = ocrSettings.confidenceThreshold,
                                        onWordTap = { word, confidence ->
                                            viewModel.showConfidenceTooltip(word, confidence)
                                        },
                                        
                                        onStartInlineEditOcr = { viewModel.startInlineEditOcr(document.id.value) },
                                        onStartInlineEditTranslation = { viewModel.startInlineEditTranslation(document.id.value) },
                                        onInlineTextChange = { viewModel.updateInlineText(it) },
                                        onInlineEditComplete = { viewModel.saveInlineChanges() },
                                        
                                        dragModifier = Modifier.draggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = docMenuExpanded == document.id.value,
                                    onDismissRequest = { docMenuExpanded = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Share page") },
                                        onClick = {
                                            docMenuExpanded = null
                                            viewModel.shareSingleImage(document.imagePath)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Share, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit OCR text") },
                                        onClick = {
                                            docMenuExpanded = null
                                            editDocTextTarget = document to true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit translation") },
                                        onClick = {
                                            docMenuExpanded = null
                                            editDocTextTarget = document to false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move to...") },
                                        onClick = {
                                            docMenuExpanded = null
                                            showMoveDocumentDialog = document
                                        },
                                        leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
                                    )
                                    
                                    if (document.processingStatus.isFailed) {
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Retry OCR") },
                                            onClick = {
                                                docMenuExpanded = null
                                                viewModel.retryOcr(document.id.value)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Retry translation") },
                                            onClick = {
                                                docMenuExpanded = null
                                                viewModel.retryTranslation(document.id.value)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Translate, null) }
                                        )
                                    }
                                    
                                    HorizontalDivider()
                                    
                                    DropdownMenuItem(
                                        text = { Text("Delete page") },
                                        onClick = {
                                            docMenuExpanded = null
                                            viewModel.deleteDocument(document.id.value)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, null, tint = GoogleDocsError)
                                        }
                                    )
                                }
                                
                                if (index < state.documents.lastIndex) {
                                    SmartDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val success = uiState as? EditorUiState.Success
    
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
    
    editDocTextTarget?.let { (doc, isOcr) ->
        TextEditorSheet(
            initialText = if (isOcr) doc.originalText ?: "" else doc.translatedText ?: "",
            title = if (isOcr) "Edit OCR Text" else "Edit Translation",
            onDismiss = { editDocTextTarget = null },
            onSave = { newText ->
                if (isOcr) {
                    viewModel.updateDocumentText(doc.id.value, originalText = newText, translatedText = null)
                } else {
                    viewModel.updateDocumentText(doc.id.value, originalText = null, translatedText = newText)
                }
                editDocTextTarget = null
            }
        )
    }
    
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
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
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
        var newTag by remember { mutableStateOf("") }
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#$tag")
                                IconButton(onClick = { viewModel.removeTag(tag) }) {
                                    Icon(Icons.Default.Delete, "Remove tag")
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = source == lang, onClick = { source = lang })
                            Text("${lang.displayName} (${lang.code})")
                        }
                    }
                    if (sourceOptions.size > 12) {
                        Text("…more languages available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(8.dp))

                    Text("Translation target language", style = MaterialTheme.typography.labelLarge)
                    targetOptions.take(12).forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { target = lang }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                        showLanguageDialog =false
}
) { Text("Save") }
},
dismissButton = {
TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
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

if (showBatchDeleteConfirm) {
    DeletePagesDialog(
        count = selectedDocIds.size,
        onDismiss = { showBatchDeleteConfirm = false },
        onConfirm = { 
            viewModel.deleteSelectedDocuments()
            showBatchDeleteConfirm = false
        }
    )
}

if (showBatchExportDialog) {
    ExportOptionsDialog(
        selectedCount = selectedDocIds.size,
        onDismiss = { showBatchExportDialog = false },
        onExportPdf = { 
            viewModel.exportSelectedDocuments(asPdf = true)
            showBatchExportDialog = false
        },
        onExportZip = { 
            viewModel.exportSelectedDocuments(asPdf = false)
            showBatchExportDialog = false
        }
    )
}

if (showBatchMoveDialog) {
    AlertDialog(
        onDismissRequest = { showBatchMoveDialog = false },
        title = { Text("Move ${selectedDocIds.size} pages to...") },
        text = {
            if (moveTargets.isEmpty()) {
                Text("No other records available")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    moveTargets.forEach { record ->
                        Surface(
                            onClick = {
                                viewModel.moveSelectedToRecord(record.id.value)
                                showBatchMoveDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showBatchMoveDialog = false }) { Text("Cancel") }
        }
    )
}

confidenceTooltip?.let { (word, confidence) ->
    AlertDialog(
        onDismissRequest = { viewModel.hideConfidenceTooltip() },
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = when {
                    confidence < 0.5f -> GoogleDocsError
                    confidence < 0.7f -> GoogleDocsWarning
                    else -> Color(0xFFFFC107)
                }
            )
        },
        title = { Text("Word: \"$word\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("OCR Confidence: ${(confidence * 100).toInt()}%")
                
                LinearProgressIndicator(
                    progress = { confidence },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        confidence < 0.5f -> GoogleDocsError
                        confidence < 0.7f -> GoogleDocsWarning
                        else -> Color(0xFFFFC107)
                    }
                )
                
                Text(
                    text = when {
                        confidence < 0.5f -> "Very low confidence - may need correction"
                        confidence < 0.7f -> "Low confidence - please verify"
                        else -> "Moderate confidence"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.hideConfidenceTooltip() }) { Text("OK") }
        }
    )
}
}