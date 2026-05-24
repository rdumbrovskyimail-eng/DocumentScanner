package com.docs.scanner.presentation.screens.analytics.notes

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.core.AnalyticsNote
import com.docs.scanner.presentation.components.ConfirmDialog

private val NOTE_COLORS: List<String> = listOf(
    "#FFEF5350", "#FFFFA726", "#FFFFD54F", "#FF66BB6A",
    "#FF42A5F5", "#FFAB47BC", "#FF78909C"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSearchField by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AnalyticsNote?>(null) }
    var creatingNew by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AnalyticsNote?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchField) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("Search notes…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else Text("Notes")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showSearchField) {
                            showSearchField = false
                            viewModel.setSearchQuery("")
                        } else onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchField = !showSearchField }) {
                        Icon(
                            if (showSearchField) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creatingNew = true }) {
                Icon(Icons.Default.Add, "Create note")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.errorMessage != null -> Text(
                    uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
                uiState.notes.isEmpty() -> EmptyNotesState(searchActive = searchQuery.isNotBlank())
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.notes, key = { it.id.value }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { editing = note },
                            onPinToggle = { viewModel.togglePin(note) },
                            onArchive = { viewModel.archive(note) },
                            onDelete = { pendingDelete = note }
                        )
                    }
                }
            }
        }
    }

    if (creatingNew) {
        NoteEditorDialog(
            initial = null,
            onDismiss = { creatingNew = false },
            onSave = { title, content, tags, color ->
                viewModel.createNote(title, content, tags, color)
                creatingNew = false
            }
        )
    }

    editing?.let { target ->
        NoteEditorDialog(
            initial = target,
            onDismiss = { editing = null },
            onSave = { title, content, tags, color ->
                viewModel.updateNote(
                    target.copy(
                        title = title,
                        content = content,
                        tags = tags,
                        color = color
                    )
                )
                editing = null
            }
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete note?",
            message = "This note will be permanently removed.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.delete(target.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// LIST CARD
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun NoteCard(
    note: AnalyticsNote,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = note.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (accent != null) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(120.dp)
                        .background(accent)
                )
            }
            Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        text = note.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onPinToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onArchive, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = "Archive",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (note.contentPreview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.contentPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (note.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(note.tags) { tag ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text("#$tag", style = MaterialTheme.typography.labelSmall)
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// EDITOR DIALOG (fullscreen)
// ──────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorDialog(
    initial: AnalyticsNote?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, tags: List<String>, color: String?) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var title by rememberSaveable(initial?.id?.value) {
        mutableStateOf(initial?.title.orEmpty())
    }
    var content by rememberSaveable(initial?.id?.value) {
        mutableStateOf(initial?.content.orEmpty())
    }
    val tags = remember(initial?.id?.value) {
        mutableStateListOf<String>().also { it.addAll(initial?.tags ?: emptyList()) }
    }
    var newTag by remember(initial?.id?.value) { mutableStateOf("") }
    var selectedColor by rememberSaveable(initial?.id?.value) {
        mutableStateOf(initial?.color)
    }

    val canSave = title.isNotBlank() || content.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (initial == null) "New note" else "Edit note") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "Close")
                            }
                        },
                        actions = {
                            if (initial != null) {
                                IconButton(onClick = {
                                    val full = buildString {
                                        if (title.isNotBlank()) appendLine(title).appendLine()
                                        append(content)
                                    }
                                    clipboard.setText(AnnotatedString(full))
                                }) {
                                    Icon(Icons.Default.ContentCopy, "Copy")
                                }
                                IconButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        if (title.isNotBlank()) putExtra(Intent.EXTRA_TITLE, title)
                                        putExtra(Intent.EXTRA_TEXT, content)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(send, "Share note"))
                                    }
                                }) {
                                    Icon(Icons.Default.Share, "Share")
                                }
                            }
                            TextButton(
                                enabled = canSave,
                                onClick = {
                                    onSave(title, content, tags.toList(), selectedColor)
                                }
                            ) {
                                Icon(Icons.Default.Done, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Save")
                            }
                        }
                    )
                }
            ) { inner ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Color swatches
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                ColorSwatch(
                                    color = null,
                                    isSelected = selectedColor == null,
                                    onClick = { selectedColor = null }
                                )
                            }
                            items(NOTE_COLORS) { hex ->
                                ColorSwatch(
                                    color = hex,
                                    isSelected = selectedColor == hex,
                                    onClick = { selectedColor = hex }
                                )
                            }
                        }
                    }

                    // Tags
                    if (tags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(tags.toList()) { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text("#$tag") },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { tags.remove(tag) },
                                            modifier = Modifier.size(InputChipDefaults.IconSize)
                                        ) {
                                            Icon(Icons.Default.Close, "Remove")
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it.replace(" ", "").take(30) },
                            label = { Text("Add tag") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.size(8.dp))
                        TextButton(
                            enabled = newTag.isNotBlank() && tags.none { it.equals(newTag.trim(), true) },
                            onClick = {
                                val t = newTag.trim()
                                if (t.isNotBlank()) tags.add(t)
                                newTag = ""
                            }
                        ) { Text("Add") }
                    }

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val parsed = color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(parsed ?: MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isSelected) Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (parsed == null) {
            Text("∅", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (isSelected) {
            Icon(
                Icons.Default.Done,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmptyNotesState(searchActive: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.NoteAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (searchActive) "No notes match your search"
                else "No notes yet.\nTap + to create one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

