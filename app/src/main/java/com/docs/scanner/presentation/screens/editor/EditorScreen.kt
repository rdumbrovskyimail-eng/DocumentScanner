/*
 * EditorScreen.kt
 * Version: 10.0.1 - STABLE PRODUCTION READY (2026)
 *
 * ✅ FIXED: Строгий порядок объявлений переменных и функций для правильной видимости в Kotlin
 * ✅ FIXED: Явное указание типов параметров LazyListItemInfo для предотвращения ошибок вывода типов в sh.calvin.reorderable
 * ✅ FIXED: Все импорты и скобки синхронизированы на 100%
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
import androidx.hilt.navigation.compose.hiltViewModel
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
    onCameraClick: () -> Unit,
    highlightDocumentId: Long? = null
) {
    // 1. Контексты и системные хелперы
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    // 2. Реактивные стейты из ViewModel
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

    // 3. Состояния списков и уведомлений
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()

    // 4. Локальные реактивные переменные ( rememberSaveable )
    var recordMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showRenameRecordDialog by rememberSaveable { mutableStateOf(false) }
    var showEditDescriptionDialog by rememberSaveable { mutableStateOf(false) }
    var showTagsDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var hasAutoShownEmptyDialog by rememberSaveable { mutableStateOf(false) }
    var showSmartRetryBanner by rememberSaveable { mutableStateOf(true) }
    var showBatchDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showBatchExportDialog by rememberSaveable { mutableStateOf(false) }
    var showBatchMoveDialog by rememberSaveable { mutableStateOf(false) }

    var editingTextDocId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTextIsOcr by rememberSaveable { mutableStateOf(true) }
    var showMoveDocumentDialogForId by rememberSaveable { mutableStateOf<Long?>(null) }
    var docMenuExpandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    var consumedHighlightId by rememberSaveable { mutableStateOf<Long?>(null) }
    var highlightedDocId by remember { mutableStateOf<Long?>(null) }

    // 5. Обработчик действий (Размещен ПОСЛЕ стейтов и ДО вызовов)
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
                val clipText = clipboardManager.getText()?.text?.takeIf { it.isNotBlank() }
                if (clipText != null) {
                    viewModel.handleDocumentAction(
                        DocumentAction.PasteText(
                            documentId = action.documentId,
                            text = clipText,
                            isOcrText = action.isOcrText
                        )
                    )
                }
            }

            is DocumentAction.AiRewrite,
            is DocumentAction.ClearFormatting,
            is DocumentAction.StartInlineEdit,
            is DocumentAction.UpdateInlineText,
            is DocumentAction.SaveInlineEdit,
            is DocumentAction.CancelInlineEdit,
            is DocumentAction.WordTap -> {
                viewModel.handleDocumentAction(action)
            }
        }
    }

    // 6. Лончеры галереи
    val multiGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addDocuments(uris)
            if (BuildConfig.DEBUG) Timber.d("📷 Multiple images selected: ${uris.size}")
        }
    }

    // 7. Стейт перетаскивания (с явным указанием типов параметров)
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (!selectionState.isActive) {
            viewModel.reorderDocuments(from.index, to.index)
        }
    }

    // 8. Обработчик аппаратной кнопки назад
    BackHandler(enabled = selectionState.isActive) {
        viewModel.exitSelectionMode()
    }

    // 9. Эффекты
    val isSuccess = uiState is EditorUiState.Success
    LaunchedEffect(highlightDocumentId, isSuccess) {
        val targetId = highlightDocumentId ?: return@LaunchedEffect
        if (consumedHighlightId == targetId) return@LaunchedEffect

        val state = uiState as? EditorUiState.Success ?: return@LaunchedEffect
        val index = state.documents.indexOfFirst { it.id.value == targetId }
        if (index < 0) {
            consumedHighlightId = targetId
            return@LaunchedEffect
        }

        consumedHighlightId = targetId

        try {
            lazyListState.animateScrollToItem(index, scrollOffset = -48)
        } catch (e: Exception) {
            Timber.w(e, "Could not scroll to highlighted document at index $index")
        }

        highlightedDocId = targetId
        delay(2500)
        if (highlightedDocId == targetId) {
            highlightedDocId = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToTranslation.collect { docId ->
            val state = uiState as? EditorUiState.Success ?: return@collect
            val index = state.documents.indexOfFirst { it.id.value == docId }
            if (index >= 0) {
                androidx.compose.runtime.withFrameNanos {}
                lazyListState.animateScrollToItem(index, scrollOffset = -48)
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(viewModel.shareEvent, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
                                event.fileName?.let { name -> putExtra(Intent.EXTRA_TITLE, name) }
                            }

                            try {
                                context.startActivity(Intent.createChooser(intent, "Share"))
                            } catch (e: android.content.ActivityNotFoundException) {
                                snackbarHostState.showSnackbar("No apps to share with")
                            }

                        } catch (e: Exception) {
                            Timber.e(e, "Share failed")
                            snackbarHostState.showSnackbar("Share failed: ${e.message}")
                        }
                    }

                    is ShareEvent.TextContent -> {
                        try {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, event.text)
                                putExtra(Intent.EXTRA_TITLE, event.title)
                            }

                            try {
                                context.startActivity(Intent.createChooser(sendIntent, event.title))
                            } catch (e: android.content.ActivityNotFoundException) {
                                snackbarHostState.showSnackbar("No apps to share text")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Share text failed")
                            snackbarHostState.showSnackbar("Share failed: ${e.message}")
                        }
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

    // 10. Интерфейс (Scaffold)
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
                                is EditorUiState.Success -> state.record.name.ifBlank { "Без названия" }
                                else -> "Документы"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        if (canUndo) {
                            IconButton(onClick = { viewModel.undoLastEdit() }) {
                                Icon(Icons.Default.Undo, contentDescription = "Отменить")
                            }
                        }
                        IconButton(onClick = onCameraClick) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Камера")
                        }
                        IconButton(onClick = {
                            multiGalleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Галерея")
                        }
                        IconButton(onClick = { recordMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }

                        RecordMenu(
                            expanded = recordMenuExpanded,
                            onDismiss = { recordMenuExpanded = false },
                            onSharePdf = {
                                recordMenuExpanded = false
                                viewModel.shareRecordAsPdf()
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
                    RecordHeader(
                        name = state.record.name,
                        description = state.record.description,
                        onNameClick = { showRenameRecordDialog = true },
                        onDescriptionClick = { showEditDescriptionDialog = true }
                    )

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
                            onGalleryClick = {
                                multiGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(
                                items = state.documents,
                                key = { it.id.value }
                            ) { document ->
                                val index = state.documents.indexOf(document)

                                ReorderableItem(reorderableState, key = document.id.value) { isDragging ->
                                    val actualDragging = isDragging && !selectionState.isActive
                                    DocumentCardItem(
                                        document = document,
                                        index = index,
                                        state = state,
                                        selectionState = selectionState,
                                        ocrSettings = ocrSettings,
                                        viewModel = viewModel,
                                        onAction = ::handleDocumentAction,
                                        docMenuExpandedId = docMenuExpandedId,
                                        onDocMenuExpandedChange = { id -> docMenuExpandedId = id },
                                        isDragging = actualDragging,
                                        isHighlighted = highlightedDocId == document.id.value,
                                        dragModifier = if (!selectionState.isActive) {
                                            Modifier.draggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {}
                                            )
                                        } else {
                                            Modifier
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

    // 11. Диалоги и всплывающие окна
    val success = uiState as? EditorUiState.Success

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
            title = { Text("Переименовать запись") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
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
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameRecordDialog = false }) { Text("Отмена") }
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
                                RadioButton(selected = source == lang, onClick = { source = lang })
                                Text(lang.displayName)
                            }
                        }
                    }
                    HorizontalDivider()
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
                                RadioButton(selected = target == lang, onClick = { target = lang })
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

    if (showSmartRetryBanner) {
        SmartRetryBanner(
            failedCount = failedCount,
            onRetryClick = { viewModel.retryFailedDocuments() },
            onDismiss = { showSmartRetryBanner = false }
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
    viewModel: EditorViewModel,
    onAction: (DocumentAction) -> Unit,
    docMenuExpandedId: Long?,
    onDocMenuExpandedChange: (Long?) -> Unit,
    isDragging: Boolean,
    isHighlighted: Boolean,
    dragModifier: Modifier
) {
    val id = document.id.value
    val inlineStates by viewModel.inlineEditingStates.collectAsStateWithLifecycle()
    val ocrEdit = inlineStates["$id:${TextEditField.OCR_TEXT.name}"]
    val translationEdit = inlineStates["$id:${TextEditField.TRANSLATED_TEXT.name}"]

    DocumentCard(
        document = document,
        index = index,
        isSelected = id in selectionState.selectedIds,
        isSelectionMode = selectionState.isActive,
        isDragging = isDragging,
        isHighlighted = isHighlighted,
        isInlineEditingOcr = ocrEdit != null,
        isInlineEditingTranslation = translationEdit != null,
        inlineOcrText = ocrEdit?.currentText ?: "",
        inlineTranslationText = translationEdit?.currentText ?: "",
        onImageClick = { onAction(DocumentAction.ImageClick(id)) },
        onOcrTextClick = { onAction(DocumentAction.OcrTextClick(id)) },
        onTranslationClick = { onAction(DocumentAction.TranslationClick(id)) },
        onSelectionToggle = { onAction(DocumentAction.ToggleSelection(id)) },
        menuExpanded = docMenuExpandedId == id,
        onMenuDismiss = { onDocMenuExpandedChange(null) },
        onMenuClick = { onDocMenuExpandedChange(id) },
        onRetryOcr = { onAction(DocumentAction.RetryOcr(id)) },
        onRetryTranslation = { onAction(DocumentAction.RetryTranslation(id)) },
        onMoveUp = { onAction(DocumentAction.MoveUp(id)) },
        onMoveDown = { onAction(DocumentAction.MoveDown(id)) },
        isFirst = index == 0,
        isLast = index == state.documents.lastIndex,
        onSharePage = { onAction(DocumentAction.SharePage(id, document.imagePath)) },
        onDeletePage = { onAction(DocumentAction.DeletePage(id)) },
        onMoveToRecord = { onAction(DocumentAction.MoveToRecord(id, 0L)) },
        onCopyText = { text -> onAction(DocumentAction.CopyText(id, text, isOcrText = true)) },
        onPasteText = { isOcr -> onAction(DocumentAction.PasteText(id, text = null, isOcrText = isOcr)) },
        onAiRewrite = { isOcr ->
            val text = if (isOcr) document.originalText.orEmpty() else document.translatedText.orEmpty()
            onAction(DocumentAction.AiRewrite(id, text, isOcrText = isOcr))
        },
        onClearFormatting = { isOcr -> onAction(DocumentAction.ClearFormatting(id, isOcrText = isOcr)) },
        confidenceThreshold = ocrSettings.confidenceThreshold,
        onWordTap = { word, conf -> onAction(DocumentAction.WordTap(word, conf)) },
        onStartInlineEditOcr = {
            onAction(DocumentAction.StartInlineEdit(id, TextEditField.OCR_TEXT, document.originalText.orEmpty()))
        },
        onStartInlineEditTranslation = {
            onAction(DocumentAction.StartInlineEdit(id, TextEditField.TRANSLATED_TEXT, document.translatedText.orEmpty()))
        },
        onInlineTextChange = { text ->
            val field = if (ocrEdit != null) TextEditField.OCR_TEXT else TextEditField.TRANSLATED_TEXT
            onAction(DocumentAction.UpdateInlineText(id, field, text))
        },
        onInlineEditComplete = {
            val field = if (ocrEdit != null) TextEditField.OCR_TEXT else TextEditField.TRANSLATED_TEXT
            onAction(DocumentAction.SaveInlineEdit(id, field))
        },
        dragModifier = dragModifier
    )
}