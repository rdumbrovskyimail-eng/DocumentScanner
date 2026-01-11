package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.docs.scanner.BuildConfig
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.components.FullscreenTextEditor
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val moveTargets by viewModel.moveTargets.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var recordMenuExpanded by remember { mutableStateOf(false) }
    var showRenameRecordDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var editDocTextTarget by remember { mutableStateOf<Pair<Document, Boolean>?>(null) } // (doc, isOriginal)
    var showMoveDocumentDialog by remember { mutableStateOf<Document?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addDocument(it) }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is EditorUiState.Success && state.errorMessage != null) {
            snackbarHostState.showSnackbar(state.errorMessage)
            viewModel.clearError()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (val state = uiState) {
                            is EditorUiState.Success -> state.record.name.ifBlank { state.folderName.ifBlank { "Documents" } }
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
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
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
                    CircularProgressIndicator()
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
                        Text(
                            text = state.processingMessage.ifBlank { "Processing..." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { (state.processingProgress.coerceIn(0, 100) / 100f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.documents, key = { it.id.value }) { doc ->
                            var docMenuExpanded by remember(doc.id.value) { mutableStateOf(false) }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AsyncImage(
                                        model = doc.imagePath,
                                        contentDescription = "Document image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clickable { onImageClick(doc.id.value) }
                                    )

                                    Text(
                                        text = doc.originalText?.takeIf { it.isNotBlank() } ?: "No OCR text",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    doc.translatedText?.takeIf { it.isNotBlank() }?.let { translated ->
                                        Text(
                                            text = translated,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = { viewModel.moveDocumentUp(doc.id.value) }) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                                        }
                                        IconButton(onClick = { viewModel.moveDocumentDown(doc.id.value) }) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                                        }
                                        IconButton(onClick = { docMenuExpanded = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Page menu")
                                        }
                                        IconButton(onClick = { viewModel.deleteDocument(doc.id.value) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = docMenuExpanded,
                                        onDismissRequest = { docMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Share page") },
                                            onClick = {
                                                docMenuExpanded = false
                                                viewModel.shareSingleImage(doc.imagePath)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Edit OCR text") },
                                            onClick = {
                                                docMenuExpanded = false
                                                editDocTextTarget = doc to true
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Edit translation") },
                                            onClick = {
                                                docMenuExpanded = false
                                                editDocTextTarget = doc to false
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Retry OCR") },
                                            onClick = {
                                                docMenuExpanded = false
                                                viewModel.retryOcr(doc.id.value)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Retry translation") },
                                            onClick = {
                                                docMenuExpanded = false
                                                viewModel.retryTranslation(doc.id.value)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            enabled = moveTargets.isNotEmpty(),
                                            text = { Text("Move to another record") },
                                            onClick = {
                                                docMenuExpanded = false
                                                showMoveDocumentDialog = doc
                                            },
                                            leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val success = uiState as? EditorUiState.Success

    if (showRenameRecordDialog && success != null) {
        var name by remember(success.record.id.value) { mutableStateOf(success.record.name) }
        androidx.compose.material3.AlertDialog(
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
                androidx.compose.material3.TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.updateRecordName(name)
                        showRenameRecordDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRenameRecordDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDescriptionDialog && success != null) {
        var desc by remember(success.record.id.value) { mutableStateOf(success.record.description.orEmpty()) }
        androidx.compose.material3.AlertDialog(
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
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.updateRecordDescription(desc.ifBlank { null })
                        showEditDescriptionDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showEditDescriptionDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTagsDialog && success != null) {
        var newTag by remember(success.record.id.value) { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
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
                androidx.compose.material3.TextButton(
                    enabled = newTag.isNotBlank(),
                    onClick = {
                        viewModel.addTag(newTag)
                        newTag = ""
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTagsDialog = false }) { Text("Close") }
            }
        )
    }

    if (showLanguageDialog && success != null) {
        var source by remember(success.record.id.value) { mutableStateOf(success.record.sourceLanguage) }
        var target by remember(success.record.id.value) { mutableStateOf(success.record.targetLanguage) }

        val sourceOptions = Language.ocrSourceOptions
        val targetOptions = Language.translationSupported.filter { it != Language.AUTO }

        androidx.compose.material3.AlertDialog(
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
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.updateLanguages(source, target)
                        showLanguageDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMoveDocumentDialog = null },
            title = { Text("Move page to record") },
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
                androidx.compose.material3.TextButton(
                    enabled = selectedRecordId != null,
                    onClick = {
                        val targetId = selectedRecordId
                        if (targetId != null) viewModel.moveDocument(doc.id.value, targetId)
                        showMoveDocumentDialog = null
                    }
                ) { Text("Move") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMoveDocumentDialog = null }) { Text("Cancel") }
            }
        )
    }
}

