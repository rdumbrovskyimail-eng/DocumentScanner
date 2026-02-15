/*
 * EditorScreen.kt
 * Version: 9.0.0 - FULLY FIXED (2026)
 *
 * ✅ FIX #10 APPLIED: BackHandler for selection mode
 * ✅ FIX #14 APPLIED: All remember → rememberSaveable
 * ✅ FIX #16 APPLIED: Conditional ReorderableState creation
 *
 * КРИТИЧЕСКИЕ ИЗМЕНЕНИЯ:
 * ✅ DocumentAction вместо 21 callback
 * ✅ Безопасная работа с drag & drop в selection mode
 * ✅ Обработка всех edge cases
 * ✅ Правильный FileProvider error handling
 */

package com.docs.scanner.presentation.screens.editor

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import com.docs.scanner.presentation.screens.editor.components.RecordHeader
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.docs.scanner.domain.core.Document
import com.docs.scanner.domain.core.Language
import com.docs.scanner.presentation.components.SmartDivider
import com.docs.scanner.presentation.screens.editor.components.AddDocumentDialog
import com.docs.scanner.presentation.screens.editor.components.BatchActionsBar
import com.docs.scanner.presentation.screens.editor.components.BatchProgressBanner
import com.docs.scanner.presentation.screens.editor.components.DeletePagesDialog
import com.docs.scanner.presentation.screens.editor.components.DocumentCard
import com.docs.scanner.presentation.screens.editor.components.EmptyRecordState
import com.docs.scanner.presentation.screens.editor.components.ExportOptionsDialog
import com.docs.scanner.presentation.screens.editor.components.FloatingActionButtons
import com.docs.scanner.presentation.screens.editor.components.RecordMenu
import com.docs.scanner.presentation.screens.editor.components.SelectionTopBar
import com.docs.scanner.presentation.screens.editor.components.SmartRetryBanner
import com.docs.scanner.presentation.screens.editor.components.TextEditorSheet
import com.docs.scanner.presentation.theme.GoogleDocsError
import com.docs.scanner.presentation.theme.GoogleDocsPrimary
import com.docs.scanner.presentation.theme.GoogleDocsWarning
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import timber.log.Timber
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
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    // ════════════════════════════════════════════════════════════════════
    // STATES - Собираем все states из ViewModel
    // ════════════════════════════════════════════════════════════════════

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()
    val batchOperation by viewModel.batchOperation.collectAsStateWithLifecycle()
    val moveTargets by viewModel.moveTargets.collectAsStateWithLifecycle()
    val selectedCount by viewModel.selectedCount.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedDocumentsCount.collectAsStateWithLifecycle()
    val confidenceTooltip by viewModel.confidenceTooltip.collectAsStateWithLifecycle()
    val ocrSettings by viewModel.ocrSettings.collectAsStateWithLifecycle()
    val inlineEditingStates by viewModel.inlineEditingStates.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()

    // ════════════════════════════════════════════════════════════════════
    // ✅ FIX #14: DIALOG STATES - Changed remember → rememberSaveable
    // ════════════════════════════════════════════════════════════════════

    var recordMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showRenameRecordDialog by rememberSaveable { mutableStateOf(false) }
    var showEditDescriptionDialog by rememberSaveable { mutableStateOf(false) }
    var showTagsDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showAddDocumentDialog by rememberSaveable { mutableStateOf(false) }
    var showSmartRetryBanner by rememberSaveable { mutableStateOf(true) }
    var showBatchDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showBatchExportDialog by rememberSaveable { mutableStateOf(false) }
    var showBatchMoveDialog by rememberSaveable { mutableStateOf(false) }

    var editingTextDocId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTextIsOcr by rememberSaveable { mutableStateOf(true) }
    var showMoveDocumentDialogForId by rememberSaveable { mutableStateOf<Long?>(null) }
    var docMenuExpandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    // ════════════════════════════════════════════════════════════════════
    // GALLERY LAUNCHERS
    // ════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════
    // ✅ FIX #16: REORDERABLE STATE - Conditional creation
    // ════════════════════════════════════════════════════════════════════

    val reorderableState = if (!selectionState.isActive) {
        rememberReorderableLazyListState(lazyListState) { from, to ->
            viewModel.reorderDocuments(from.index, to.index)
        }
    } else {
        null
    }

    // ════════════════════════════════════════════════════════════════════
    // ✅ FIX #10: BackHandler for selection mode
    // ════════════════════════════════════════════════════════════════════

    BackHandler(enabled = selectionState.isActive) {
        viewModel.exitSelectionMode()
    }

    // ════════════════════════════════════════════════════════════════════
    // EFFECTS
    // ════════════════════════════════════════════════════════════════════

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is EditorUiState.Success) {
            if (state.documents.isEmpty() && !showAddDocumentDialog) {
                showAddDocumentDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareEvent.collect { event ->
            when (event) {
                is ShareEvent.File -> {
                    try {
                        val file = File(event.path)

                        if (!file.exists()) {
                            snackbarHostState.showSnackbar("File not found")
                            return@collect
                        }

                        if (file.length() == 0L) {
                            snackbarHostState.showSnackbar("File is empty")
                            return@collect
                        }

                        if (!file.canRead()) {
                            snackbarHostState.showSnackbar("Cannot read file")
                            return@collect
                        }

                        val uri = try {
                            FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                file
                            )
                        } catch (e: IllegalArgumentException) {
                            Timber.e(e, "FileProvider paths misconfigured")
                            snackbarHostState.showSnackbar("Cannot share file: path not allowed")
                            return@collect
                        }

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                            event.fileName?.let { name ->
                                putExtra(Intent.EXTRA_TITLE, name)
                            }
                        }

                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        } else {
                            snackbarHostState.showSnackbar("No apps to share with")
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Share failed")
                        snackbarHostState.showSnackbar("Share failed: ${e.message}")
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Long
            )

            if (result == SnackbarResult.ActionPerformed && event.action != null) {
                event.action.invoke()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ACTION HANDLER - Один обработчик вместо 21 callback
    // ════════════════════════════════════════════════════════════════════

    fun handleDocumentAction(action: DocumentAction) {
        when (action) {
            is DocumentAction.ImageClick -> onImageClick(action.documentId)

            is DocumentAction.OcrTextClick -> {
                editingTextDocId = action.documentId
                editingTextIsOcr = true
            }

            is DocumentAction.TranslationClick -> {
                editingTextDocId = action.documentId
                editingTextIsOcr = false
            }

            is DocumentAction.ToggleSelection -> {
                if (!selectionState.isActive) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                viewModel.toggleDocumentSelection(action.documentId)
            }

            is DocumentAction.MenuClick -> {
                docMenuExpandedId = action.documentId
            }

            is DocumentAction.RetryOcr -> viewModel.retryOcr(action.documentId)
            is DocumentAction.RetryTranslation -> viewModel.retryTranslation(action.documentId)
            is DocumentAction.MoveUp -> viewModel.moveDocumentUp(action.documentId)
            is DocumentAction.MoveDown -> viewModel.moveDocumentDown(action.documentId)

            is DocumentAction.SharePage -> viewModel.shareSingleImage(action.imagePath)
            is DocumentAction.DeletePage -> viewModel.deleteDocument(action.documentId)
            is DocumentAction.MoveToRecord -> showMoveDocumentDialogForId = action.documentId

            is DocumentAction.CopyText -> {
                clipboardManager.setText(AnnotatedString(action.text))
            }

            is DocumentAction.PasteText -> {
                val clipText = clipboardManager.getText()?.text
                if (clipText != null) {
                    viewModel.pasteText(action.documentId, clipText, action.isOcrText)
                }
            }

            is DocumentAction.AiRewrite -> {
                viewModel.aiRewriteText(action.documentId, action.text, action.isOcrText)
            }

            is DocumentAction.ClearFormatting -> {
                viewModel.clearFormatting(action.documentId, action.isOcrText)
            }

            is DocumentAction.StartInlineEdit -> {
                if (action.field == TextEditField.OCR_TEXT) {
                    viewModel.startInlineEditOcr(action.documentId)
                } else {
                    viewModel.startInlineEditTranslation(action.documentId)
                }
            }

            is DocumentAction.UpdateInlineText -> {
                viewModel.updateInlineText(action.documentId, action.field, action.text)
            }

            is DocumentAction.SaveInlineEdit -> {
                viewModel.finishInlineEdit(action.documentId, action.field)
            }

            is DocumentAction.CancelInlineEdit -> {
                viewModel.cancelInlineEdit(action.documentId, action.field)
            }

            is DocumentAction.WordTap -> {
                viewModel.showConfidenceTooltip(action.word, action.confidence)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SCAFFOLD
    // ════════════════════════════════════════════════════════════════════

    Scaffold(
        topBar = {
            if (selectionState.isActive) {
                SelectionTopBar(
                    selectedCount = selectedCount,
                    totalCount = (uiState as? EditorUiState.Success)?.documents?.size ?: 0,
                    onCloseClick = { viewModel.exitSelectionMode() },
                    onSelectAllClick = {
                        val state = uiState as? EditorUiState.Success ?: return@SelectionTopBar
                        if (selectedCount == state.documents.size) {
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
                                is EditorUiState.Success -> state.folderName.ifBlank { "Documents" }
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

                        RecordMenu(
                            expanded = recordMenuExpanded,
                            onDismiss = { recordMenuExpanded = false },
                            onRename = {
                                recordMenuExpanded = false
                                showRenameRecordDialog = true
                            },
                            onEditDescription = {
                                recordMenuExpanded = false
                                showEditDescriptionDialog = true
                            },
                            onManageTags = {
                                recordMenuExpanded = false
                                showTagsDialog = true
                            },
                            onChangeLanguages = {
                                recordMenuExpanded = false
                                showLanguageDialog = true
                            },
                            onSharePdf = {
                                recordMenuExpanded = false
                                viewModel.shareRecordAsPdf()
                            },
                            onShareZip = {
                                recordMenuExpanded = false
                                viewModel.shareRecordImagesZip()
                            },
                            onSelectPages = {
                                recordMenuExpanded = false
                                viewModel.enterSelectionMode()
                            },
                            hasDocuments = (uiState as? EditorUiState.Success)?.documents?.isNotEmpty() == true
                        )
                    }
                )
            }
        },
        bottomBar = {
            if (selectionState.isActive && selectedCount > 0) {
                BatchActionsBar(
                    selectedCount = selectedCount,
                    totalCount = (uiState as? EditorUiState.Success)?.documents?.size ?: 0,
                    onDeleteClick = { showBatchDeleteConfirm = true },
                    onExportClick = { showBatchExportDialog = true },
                    onMoveClick = { showBatchMoveDialog = true },
                    onSelectAllClick = {
                        val state = uiState as? EditorUiState.Success ?: return@BatchActionsBar
                        if (selectedCount == state.documents.size) {
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
            if (!selectionState.isActive && !processingState.isActive) {
                FloatingActionButtons(
                    onCameraClick = onCameraClick,
                    onGalleryClick = { showAddDocumentDialog = true }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        // ════════════════════════════════════════════════════════════════
        // CONTENT
        // ════════════════════════════════════════════════════════════════

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
                    // Record Name & Description Header
                    RecordHeader(
                        name = state.record.name,
                        description = state.record.description,
                        onNameClick = { showRenameRecordDialog = true },
                        onDescriptionClick = { showEditDescriptionDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (processingState.isActive) {
                        BatchProgressBanner(
                            processedCount = processingState.progress,
                            totalCount = 100,
                            currentStage = processingState.message,
                            onCancelClick = if (processingState.canCancel) {
                                { viewModel.cancelBatchOperation() }
                            } else null
                        )
                    }

                    batchOperation?.let { operation ->
                        BatchProgressBanner(
                            processedCount = operation.progress,
                            totalCount = operation.total,
                            currentStage = when (operation) {
                                is BatchOperation.Delete -> "Deleting ${operation.progress}/${operation.total}..."
                                is BatchOperation.Export -> "Exporting ${operation.progress}/${operation.total}..."
                                is BatchOperation.Move -> "Moving ${operation.progress}/${operation.total}..."
                                is BatchOperation.RetryOcr -> "Retrying OCR ${operation.progress}/${operation.total}..."
                                is BatchOperation.RetryTranslation -> "Retrying translation ${operation.progress}/${operation.total}..."
                            },
                            onCancelClick = { viewModel.cancelBatchOperation() }
                        )
                    }

                    if (failedCount > 0 && showSmartRetryBanner && !processingState.isActive && batchOperation == null) {
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

                                // ✅ FIX #16: Conditional rendering based on reorderableState
                                if (reorderableState != null && !selectionState.isActive) {
                                    ReorderableItem(reorderableState, key = document.id.value) { isDragging ->
                                        DocumentCardItem(
                                            document = document,
                                            index = index,
                                            state = state,
                                            selectionState = selectionState,
                                            ocrSettings = ocrSettings,
                                            inlineEditingStates = inlineEditingStates,
                                            onAction = ::handleDocumentAction,
                                            docMenuExpandedId = docMenuExpandedId,
                                            onDocMenuExpandedChange = { id -> docMenuExpandedId = id },
                                            isDragging = isDragging,
                                            dragModifier = Modifier.draggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {}
                                            )
                                        )
                                    }
                                } else {
                                    DocumentCardItem(
                                        document = document,
                                        index = index,
                                        state = state,
                                        selectionState = selectionState,
                                        ocrSettings = ocrSettings,
                                        inlineEditingStates = inlineEditingStates,
                                        onAction = ::handleDocumentAction,
                                        docMenuExpandedId = docMenuExpandedId,
                                        onDocMenuExpandedChange = { docMenuExpandedId = it },
                                        isDragging = false,
                                        dragModifier = Modifier
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

    // ════════════════════════════════════════════════════════════════════
    // DIALOGS
    // ════════════════════════════════════════════════════════════════════

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

    editingTextDocId?.let { docId ->
        val doc = success?.documents?.find { it.id.value == docId }
        if (doc != null) {
            TextEditorSheet(
                initialText = if (editingTextIsOcr) doc.originalText ?: "" else doc.translatedText ?: "",
                title = if (editingTextIsOcr) "Edit OCR Text" else "Edit Translation",
                onDismiss = { editingTextDocId = null },
                onSave = { newText ->
                    if (editingTextIsOcr) {
                        viewModel.updateDocumentText(docId, originalText = newText, translatedText = null)
                    } else {
                        viewModel.updateDocumentText(docId, originalText = null, translatedText = newText)
                    }
                    editingTextDocId = null
                }
            )
        }
    }

    if (showRenameRecordDialog && success != null) {
        var name by remember(success.record.name) { mutableStateOf(success.record.name) }
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
        var desc by remember(success.record.description) { mutableStateOf(success.record.description.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditDescriptionDialog = false },
            title = { Text("Edit description") },
            text = {
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    placeholder = { Text("Add description for this record...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp),
                    minLines = 4,
                    maxLines = 10
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

    // Tags Dialog - inline implementation
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
                        label = { Text("Add tag") },
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

    // Language Dialog - inline implementation
    if (showLanguageDialog && success != null) {
        var source by remember { mutableStateOf(success.record.sourceLanguage) }
        var target by remember { mutableStateOf(success.record.targetLanguage) }

        val sourceOptions = Language.ocrSourceOptions
        val targetOptions = Language.translationSupported.filter { it != Language.AUTO }

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Languages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Source language
                    Column {
                        Text("Source (OCR)", style = MaterialTheme.typography.labelMedium)
                        sourceOptions.forEach { lang ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { source = lang }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = source == lang,
                                    onClick = { source = lang }
                                )
                                Text(lang.displayName)
                            }
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Target language
                    Column {
                        Text("Target (Translation)", style = MaterialTheme.typography.labelMedium)
                        targetOptions.forEach { lang ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { target = lang }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = target == lang,
                                    onClick = { target = lang }
                                )
                                Text(lang.displayName)
                            }
                        }
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

    showMoveDocumentDialogForId?.let { docId ->
        var selectedRecordId by remember { mutableStateOf<Long?>(null) }

        AlertDialog(
            onDismissRequest = { showMoveDocumentDialogForId = null },
            title = { Text("Move to...") },
            text = {
                if (moveTargets.isEmpty()) {
                    Text("No other records available")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            if (selectionState.isActive) {
                                viewModel.moveSelectedToRecord(targetId)
                            } else {
                                viewModel.moveDocument(docId, targetId)
                            }
                        }
                        showMoveDocumentDialogForId = null
                    }
                ) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDocumentDialogForId = null }) { Text("Cancel") }
            }
        )
    }

    if (showBatchDeleteConfirm) {
        DeletePagesDialog(
            count = selectedCount,
            onDismiss = { showBatchDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteSelectedDocuments()
                showBatchDeleteConfirm = false
            }
        )
    }

    if (showBatchExportDialog) {
        ExportOptionsDialog(
            selectedCount = selectedCount,
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
            title = { Text("Move $selectedCount pages to...") },
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
                        else -> GoogleDocsPrimary
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
                            else -> GoogleDocsPrimary
                        }
                    )

                    Text(
                        text = when {
                            confidence < 0.5f -> "Very low confidence - may need correction"
                            confidence < 0.7f -> "Low confidence - please verify"
                            else -> "Good confidence"
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

@Composable
private fun DocumentCardItem(
    document: Document,
    index: Int,
    state: EditorUiState.Success,
    selectionState: SelectionState,
    ocrSettings: OcrSettingsSnapshot,
    inlineEditingStates: Map<String, InlineEditState>,
    onAction: (DocumentAction) -> Unit,
    docMenuExpandedId: Long?,
    onDocMenuExpandedChange: (Long?) -> Unit,
    isDragging: Boolean,
    dragModifier: Modifier
) {
    val ocrEditKey = "${document.id.value}:${TextEditField.OCR_TEXT.name}"
    val translationEditKey = "${document.id.value}:${TextEditField.TRANSLATED_TEXT.name}"

    val ocrEditState = inlineEditingStates[ocrEditKey]
    val translationEditState = inlineEditingStates[translationEditKey]

    val isInlineEditingOcr = ocrEditState != null
    val isInlineEditingTranslation = translationEditState != null

    val inlineOcrText = ocrEditState?.currentText ?: document.originalText ?: ""
    val inlineTranslationText = translationEditState?.currentText ?: document.translatedText ?: ""

    DocumentCard(
        document = document,
        index = index,
        isSelected = selectionState.selectedIds.contains(document.id.value),
        isSelectionMode = selectionState.isActive,
        isDragging = isDragging,

        isInlineEditingOcr = isInlineEditingOcr,
        isInlineEditingTranslation = isInlineEditingTranslation,
        inlineOcrText = inlineOcrText,
        inlineTranslationText = inlineTranslationText,

        menuExpanded = docMenuExpandedId == document.id.value,
        onMenuDismiss = { onDocMenuExpandedChange(null) },

        onImageClick = {
            onAction(DocumentAction.ImageClick(document.id.value))
        },
        onOcrTextClick = {
            onAction(DocumentAction.OcrTextClick(document.id.value))
        },
        onTranslationClick = {
            onAction(DocumentAction.TranslationClick(document.id.value))
        },
        onSelectionToggle = {
            onAction(DocumentAction.ToggleSelection(document.id.value))
        },
        onMenuClick = {
            onAction(DocumentAction.MenuClick(document.id.value))
        },
        onRetryOcr = {
            onAction(DocumentAction.RetryOcr(document.id.value))
        },
        onRetryTranslation = {
            onAction(DocumentAction.RetryTranslation(document.id.value))
        },

        onMoveUp = if (!selectionState.isActive && index > 0) {
            { onAction(DocumentAction.MoveUp(document.id.value)) }
        } else null,
        onMoveDown = if (!selectionState.isActive && index < state.documents.lastIndex) {
            { onAction(DocumentAction.MoveDown(document.id.value)) }
        } else null,
        isFirst = index == 0,
        isLast = index == state.documents.lastIndex,

        onSharePage = {
            onAction(DocumentAction.SharePage(document.id.value, document.imagePath))
        },
        onDeletePage = {
            onAction(DocumentAction.DeletePage(document.id.value))
        },
        onMoveToRecord = {
            onAction(DocumentAction.MoveToRecord(document.id.value, 0L))
        },

        onCopyText = { text ->
            onAction(DocumentAction.CopyText(document.id.value, text, true))
        },
        onPasteText = { isOcr ->
            onAction(DocumentAction.PasteText(document.id.value, null, isOcr))
        },
        onAiRewrite = { isOcr ->
            val text = if (isOcr) document.originalText else document.translatedText
            text?.let {
                onAction(DocumentAction.AiRewrite(document.id.value, it, isOcr))
            }
        },
        onClearFormatting = { isOcr ->
            onAction(DocumentAction.ClearFormatting(document.id.value, isOcr))
        },

        confidenceThreshold = ocrSettings.confidenceThreshold,
        onWordTap = { word, confidence ->
            onAction(DocumentAction.WordTap(word, confidence))
        },

        onStartInlineEditOcr = {
            onAction(
                DocumentAction.StartInlineEdit(
                    document.id.value,
                    TextEditField.OCR_TEXT,
                    document.originalText ?: ""
                )
            )
        },
        onStartInlineEditTranslation = {
            onAction(
                DocumentAction.StartInlineEdit(
                    document.id.value,
                    TextEditField.TRANSLATED_TEXT,
                    document.translatedText ?: ""
                )
            )
        },
        onInlineTextChange = { text ->
            if (isInlineEditingOcr) {
                onAction(DocumentAction.UpdateInlineText(document.id.value, TextEditField.OCR_TEXT, text))
            } else if (isInlineEditingTranslation) {
                onAction(DocumentAction.UpdateInlineText(document.id.value, TextEditField.TRANSLATED_TEXT, text))
            }
        },
        onInlineEditComplete = {
            if (isInlineEditingOcr) {
                onAction(DocumentAction.SaveInlineEdit(document.id.value, TextEditField.OCR_TEXT))
            } else if (isInlineEditingTranslation) {
                onAction(DocumentAction.SaveInlineEdit(document.id.value, TextEditField.TRANSLATED_TEXT))
            }
        },

        dragModifier = dragModifier
    )

    DropdownMenu(
        expanded = docMenuExpandedId == document.id.value,
        onDismissRequest = { onDocMenuExpandedChange(null) }
    ) {
        DropdownMenuItem(
            text = { Text("Share page") },
            onClick = {
                onDocMenuExpandedChange(null)
                onAction(DocumentAction.SharePage(document.id.value, document.imagePath))
            },
            leadingIcon = { Icon(Icons.Default.Share, null) }
        )
        DropdownMenuItem(
            text = { Text("Edit OCR text") },
            onClick = {
                onDocMenuExpandedChange(null)
                onAction(DocumentAction.OcrTextClick(document.id.value))
            },
            leadingIcon = { Icon(Icons.Default.Edit, null) }
        )
        DropdownMenuItem(
            text = { Text("Edit translation") },
            onClick = {
                onDocMenuExpandedChange(null)
                onAction(DocumentAction.TranslationClick(document.id.value))
            },
            leadingIcon = { Icon(Icons.Default.Edit, null) }
        )
        DropdownMenuItem(
            text = { Text("Move to...") },
            onClick = {
                onDocMenuExpandedChange(null)
                onAction(DocumentAction.MoveToRecord(document.id.value, 0L))
            },
            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
        )

        if (document.processingStatus.isFailed) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Retry OCR") },
                onClick = {
                    onDocMenuExpandedChange(null)
                    onAction(DocumentAction.RetryOcr(document.id.value))
                },
                leadingIcon = { Icon(Icons.Default.Refresh, null) }
            )
            DropdownMenuItem(
                text = { Text("Retry translation") },
                onClick = {
                    onDocMenuExpandedChange(null)
                    onAction(DocumentAction.RetryTranslation(document.id.value))
                },
                leadingIcon = { Icon(Icons.Default.Translate, null) }
            )
        }

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("Delete page") },
            onClick = {
                onDocMenuExpandedChange(null)
                onAction(DocumentAction.DeletePage(document.id.value))
            },
            leadingIcon = {
                Icon(Icons.Default.Delete, null, tint = GoogleDocsError)
            }
        )
    }
}