package com.docs.scanner.presentation.screens.terms

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.core.Term
import com.docs.scanner.domain.core.TermPriority
import com.docs.scanner.domain.core.TermStatus
import com.docs.scanner.presentation.components.ConfirmDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TermsViewModel = hiltViewModel(),
    openTermId: Long? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTerm by viewModel.selectedTerm.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteAllMenu by remember { mutableStateOf(false) }
    var pendingDeleteTerm by remember { mutableStateOf<Term?>(null) }
    var pendingDeleteAllCompleted by remember { mutableStateOf(false) }
    var pendingDeleteAllCancelled by remember { mutableStateOf(false) }

    LaunchedEffect(openTermId) {
        if (openTermId != null && openTermId > 0) {
            viewModel.openTerm(openTermId)
        }
    }

    LaunchedEffect(message) {
        val msg = message
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val success = uiState as? TermsUiState.Success
    val now = success?.now ?: System.currentTimeMillis()
    val filter = success?.filter ?: TermsFilter.ACTIVE
    val visible = success?.visible ?: emptyList()
    val filtered = remember(visible, query) {
        val q = query.trim()
        if (q.isBlank()) visible
        else visible.filter {
            it.title.contains(q, ignoreCase = true) ||
                (it.description?.contains(q, ignoreCase = true) == true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Deadlines") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                ,
                actions = {
                    IconButton(onClick = { showDeleteAllMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showDeleteAllMenu, onDismissRequest = { showDeleteAllMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete all completed") },
                            leadingIcon = { Icon(Icons.Default.AutoDelete, contentDescription = null) },
                            onClick = {
                                showDeleteAllMenu = false
                                pendingDeleteAllCompleted = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete all cancelled") },
                            leadingIcon = { Icon(Icons.Default.AutoDelete, contentDescription = null) },
                            onClick = {
                                showDeleteAllMenu = false
                                pendingDeleteAllCancelled = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (uiState) {
                is TermsUiState.Error -> Text((uiState as TermsUiState.Error).message, color = MaterialTheme.colorScheme.error)
                TermsUiState.Loading -> Text("Loading...")
                is TermsUiState.Success -> {
                    val s = uiState as TermsUiState.Success
                    var tabIndex by remember { mutableIntStateOf(0) }
                    val tabs = listOf(
                        TermsFilter.ACTIVE,
                        TermsFilter.UPCOMING,
                        TermsFilter.DUE_TODAY,
                        TermsFilter.OVERDUE,
                        TermsFilter.COMPLETED,
                        TermsFilter.CANCELLED,
                        TermsFilter.ALL
                    )
                    tabIndex = tabs.indexOf(filter).coerceAtLeast(0)

                    TabRow(selectedTabIndex = tabIndex) {
                        tabs.forEachIndexed { index, f ->
                            val count = when (f) {
                                TermsFilter.ACTIVE -> s.active.size
                                TermsFilter.UPCOMING -> s.upcoming.size
                                TermsFilter.DUE_TODAY -> s.dueToday.size
                                TermsFilter.OVERDUE -> s.overdue.size
                                TermsFilter.COMPLETED -> s.completed.size
                                TermsFilter.CANCELLED -> s.cancelled.size
                                TermsFilter.ALL -> s.all.size
                            }
                            Tab(
                                selected = tabIndex == index,
                                onClick = { viewModel.setFilter(f) },
                                text = { Text("${f.name.replace('_', ' ')} ($count)") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )

                    TermsList(
                        now = now,
                        terms = filtered,
                        onEdit = { viewModel.openTerm(it.id.value) },
                        onComplete = { viewModel.completeTerm(it.id.value) },
                        onUncomplete = { viewModel.uncompleteTerm(it.id.value) },
                        onCancel = { viewModel.cancelTerm(it.id.value) },
                        onRestore = { viewModel.restoreTerm(it.id.value) },
                        onDelete = { pendingDeleteTerm = it }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        TermEditorDialog(
            title = "Create term",
            initial = null,
            now = now,
            onDismiss = { showCreateDialog = false },
            onSave = { data ->
                viewModel.createTerm(
                    title = data.title,
                    description = data.description,
                    dueDate = data.dueDate,
                    reminderMinutesBefore = data.reminderMinutesBefore,
                    priority = data.priority,
                    folderId = null
                )
                showCreateDialog = false
            }
        )
    }

    selectedTerm?.let { term ->
        TermEditorDialog(
            title = "Edit term",
            initial = term,
            now = now,
            onDismiss = { viewModel.closeDialog() },
            onSave = { data ->
                viewModel.updateTerm(
                    term.copy(
                        title = data.title,
                        description = data.description,
                        dueDate = data.dueDate,
                        reminderMinutesBefore = data.reminderMinutesBefore,
                        priority = data.priority
                    )
                )
                viewModel.closeDialog()
            }
        )
    }

    pendingDeleteTerm?.let { term ->
        ConfirmDialog(
            title = "Delete term?",
            message = "This will delete \"${term.title}\". This action cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteTerm(term.id.value)
                pendingDeleteTerm = null
            },
            onDismiss = { pendingDeleteTerm = null }
        )
    }

    if (pendingDeleteAllCompleted) {
        ConfirmDialog(
            title = "Delete all completed?",
            message = "This will permanently delete all completed terms.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteAllCompleted()
                pendingDeleteAllCompleted = false
            },
            onDismiss = { pendingDeleteAllCompleted = false }
        )
    }

    if (pendingDeleteAllCancelled) {
        ConfirmDialog(
            title = "Delete all cancelled?",
            message = "This will permanently delete all cancelled terms.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteAllCancelled()
                pendingDeleteAllCancelled = false
            },
            onDismiss = { pendingDeleteAllCancelled = false }
        )
    }
}

@Composable
private fun TermsList(
    now: Long,
    terms: List<Term>,
    onEdit: (Term) -> Unit,
    onComplete: (Term) -> Unit,
    onUncomplete: (Term) -> Unit,
    onCancel: (Term) -> Unit,
    onRestore: (Term) -> Unit,
    onDelete: (Term) -> Unit
) {
    if (terms.isEmpty()) {
        Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(terms, key = { it.id.value }) { term ->
            var menuExpanded by remember(term.id.value) { mutableStateOf(false) }
            val status = term.computeStatus(now)
            val dueText = remember(term.dueDate) { formatDue(term.dueDate) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(term.title, style = MaterialTheme.typography.titleMedium)
                        term.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            text = "Due: $dueText • Reminder: ${term.reminderMinutesBefore}m • ${term.priority.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (status) {
                                TermStatus.OVERDUE -> MaterialTheme.colorScheme.error
                                TermStatus.DUE_TODAY -> MaterialTheme.colorScheme.tertiary
                                TermStatus.UPCOMING -> MaterialTheme.colorScheme.primary
                                TermStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                                TermStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit(term)
                            }
                        )
                        if (!term.isCancelled && !term.isCompleted) {
                            DropdownMenuItem(
                                text = { Text("Complete") },
                                leadingIcon = { Icon(Icons.Default.Done, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onComplete(term)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cancel") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onCancel(term)
                                }
                            )
                        }
                        if (term.isCompleted) {
                            DropdownMenuItem(
                                text = { Text("Mark as not completed") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onUncomplete(term)
                                }
                            )
                        }
                        if (term.isCancelled) {
                            DropdownMenuItem(
                                text = { Text("Restore") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRestore(term)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete(term)
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class TermEditorData(
    val title: String,
    val description: String?,
    val dueDate: Long,
    val reminderMinutesBefore: Int,
    val priority: TermPriority
)

@Composable
private fun TermEditorDialog(
    title: String,
    initial: Term?,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (TermEditorData) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    var termTitle by remember(initial?.id?.value) { mutableStateOf(initial?.title.orEmpty()) }
    var desc by remember(initial?.id?.value) { mutableStateOf(initial?.description.orEmpty()) }

    val initialDue = initial?.dueDate ?: (now + 24 * 60 * 60 * 1000L)
    var date by remember(initial?.id?.value) { mutableStateOf(Instant.ofEpochMilli(initialDue).atZone(zone).toLocalDate()) }
    var time by remember(initial?.id?.value) { mutableStateOf(Instant.ofEpochMilli(initialDue).atZone(zone).toLocalTime().withSecond(0).withNano(0)) }

    var reminder by remember(initial?.id?.value) { mutableIntStateOf(initial?.reminderMinutesBefore ?: 60) }
    var priority by remember(initial?.id?.value) { mutableStateOf(initial?.priority ?: TermPriority.NORMAL) }

    val dueMillis = remember(date, time) { ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli() }

    var priorityMenu by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = termTitle,
                    onValueChange = { termTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth
                            ).show()
                        }
                    ) { Text("Date: ${date.toString()}") }

                    TextButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hh, mm -> time = LocalTime.of(hh, mm) },
                                time.hour,
                                time.minute,
                                true
                            ).show()
                        }
                    ) { Text("Time: ${time.toString()}") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { reminderMenu = true }) { Text("Reminder: ${reminder}m") }
                    DropdownMenu(expanded = reminderMenu, onDismissRequest = { reminderMenu = false }) {
                        listOf(0, 5, 15, 30, 60, 120, 240, 1440, 2880).forEach { m ->
                            DropdownMenuItem(
                                text = { Text(if (m == 0) "At due time" else "$m minutes") },
                                onClick = {
                                    reminderMenu = false
                                    reminder = m
                                }
                            )
                        }
                    }

                    TextButton(onClick = { priorityMenu = true }) { Text("Priority: ${priority.name}") }
                    DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                        TermPriority.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    priorityMenu = false
                                    priority = p
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = termTitle.trim().isNotBlank() && dueMillis > now,
                onClick = {
                    onSave(
                        TermEditorData(
                            title = termTitle.trim(),
                            description = desc.trim().ifBlank { null },
                            dueDate = dueMillis,
                            reminderMinutesBefore = reminder,
                            priority = priority
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatDue(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return dt.format(fmt)
}
