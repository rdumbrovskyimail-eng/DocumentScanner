package com.docs.scanner.presentation.screens.terms
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.alarm.AlarmScheduler
import com.docs.scanner.data.local.database.dao.TermDao
import com.docs.scanner.data.local.database.entities.TermEntity
import com.docs.scanner.presentation.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
@Composable
fun TermsScreen(
viewModel: TermsViewModel = hiltViewModel(),
onBackClick: () -> Unit
) {
val upcomingTerms by viewModel.upcomingTerms.collectAsState()
val completedTerms by viewModel.completedTerms.collectAsState()
var showCreateDialog by remember { mutableStateOf(false) }

Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Terms") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
    },
    floatingActionButton = {
        FloatingActionButton(
            onClick = { showCreateDialog = true }
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Term")
        }
    }
) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        if (upcomingTerms.isEmpty() && completedTerms.isEmpty()) {
            EmptyState(
                icon = {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = "No terms yet",
                message = "Create your first term reminder",
                actionText = "Create Term",
                onActionClick = { showCreateDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (upcomingTerms.isNotEmpty()) {
                    item {
                        Text(
                            "Upcoming",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(upcomingTerms, key = { it.id }) { term ->
                        TermCard(
                            term = term,
                            onComplete = { viewModel.completeTerm(term.id) },
                            onDelete = { viewModel.deleteTerm(term.id) }
                        )
                    }
                }
                
                if (completedTerms.isNotEmpty()) {
                    item {
                        Text(
                            "Completed",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(completedTerms, key = { it.id }) { term ->
                        TermCard(
                            term = term,
                            onComplete = null,
                            onDelete = { viewModel.deleteTerm(term.id) }
                        )
                    }
                }
            }
        }
    }
}

if (showCreateDialog) {
    CreateTermDialog(
        onDismiss = { showCreateDialog = false },
        onCreate = { title, description, dateTime, reminderMinutes ->
            viewModel.createTerm(title, description, dateTime, reminderMinutes)
            showCreateDialog = false
        }
    )
}
}
@Composable
private fun TermCard(
term: TermEntity,
onComplete: (() -> Unit)?,
onDelete: () -> Unit
) {
val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }
Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    colors = if (term.isCompleted) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    } else {
        CardDefaults.cardColors()
    }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (term.isCompleted) Icons.Default.CheckCircle else Icons.Default.Alarm,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (term.isCompleted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            }
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = term.title,
                style = MaterialTheme.typography.titleMedium
            )
            
            if (term.description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = term.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = dateFormat.format(Date(term.dateTime)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (term.reminderMinutesBefore != null) {
                Text(
                    text = "Reminder: ${term.reminderMinutesBefore} min before",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        if (onComplete != null) {
            IconButton(onClick = onComplete) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Complete",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
}
@Composable
private fun CreateTermDialog(
onDismiss: () -> Unit,
onCreate: (String, String?, Long, Int?) -> Unit
) {
var title by remember { mutableStateOf("") }
var description by remember { mutableStateOf("") }
var dateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
var reminderMinutes by remember { mutableStateOf<Int?>(null) }
AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create Term") },
    text = {
        Column {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Reminder:", style = MaterialTheme.typography.labelMedium)
            
            Row {
                FilterChip(
                    selected = reminderMinutes == 15,
                    onClick = { reminderMinutes = if (reminderMinutes == 15) null else 15 },
                    label = { Text("15 min") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = reminderMinutes == 30,
                    onClick = { reminderMinutes = if (reminderMinutes == 30) null else 30 },
                    label = { Text("30 min") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = reminderMinutes == 60,
                    onClick = { reminderMinutes = if (reminderMinutes == 60) null else 60 },
                    label = { Text("1 hour") }
                )
            }
        }
    },
    confirmButton = {
        TextButton(
            onClick = {
                onCreate(
                    title,
                    description.ifBlank { null },
                    dateTime,
                    reminderMinutes
                )
            },
            enabled = title.isNotBlank()
        ) {
            Text("Create")
        }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    }
)
}
@HiltViewModel
class TermsViewModel @Inject constructor(
private val termDao: TermDao,
private val alarmScheduler: AlarmScheduler
) : ViewModel() {
val upcomingTerms: StateFlow<List<TermEntity>> = termDao.getUpcomingTerms()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

val completedTerms: StateFlow<List<TermEntity>> = termDao.getCompletedTerms()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

fun createTerm(
    title: String,
    description: String?,
    dateTime: Long,
    reminderMinutes: Int?
) {
    viewModelScope.launch {
        val term = TermEntity(
            title = title,
            description = description,
            dateTime = dateTime,
            reminderMinutesBefore = reminderMinutes
        )
        
        val id = termDao.insertTerm(term)
        alarmScheduler.scheduleTerm(term.copy(id = id))
    }
}

fun completeTerm(termId: Long) {
    viewModelScope.launch {
        termDao.markCompleted(termId, true)
        alarmScheduler.cancelTerm(termId)
    }
}

fun deleteTerm(termId: Long) {
    viewModelScope.launch {
        termDao.deleteTermById(termId)
        alarmScheduler.cancelTerm(termId)
    }
}
}