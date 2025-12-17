package com.docs.scanner.presentation.screens.terms

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docs.scanner.data.local.database.entities.TermEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TermsViewModel = hiltViewModel()
) {
    val upcomingTerms by viewModel.upcomingTerms.collectAsState()
    val completedTerms by viewModel.completedTerms.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Deadlines") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, "Add Term")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Upcoming") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Completed") }
                )
            }
            
            // Content
            when (selectedTab) {
                0 -> TermsList(
                    terms = upcomingTerms,
                    onComplete = { viewModel.completeTerm(it) },
                    onDelete = { viewModel.deleteTerm(it) }
                )
                1 -> TermsList(
                    terms = completedTerms,
                    onComplete = null,
                    onDelete = { viewModel.deleteTerm(it) }
                )
            }
        }
    }
    
    if (showDialog) {
        CreateTermDialog(
            onDismiss = { showDialog = false },
            onCreate = { title, date, reminderMinutes ->
                viewModel.createTerm(title, date, reminderMinutes)
                showDialog = false
            }
        )
    }
}

@Composable
private fun TermsList(
    terms: List<TermEntity>,
    onComplete: ((TermEntity) -> Unit)?,
    onDelete: (TermEntity) -> Unit
) {
    if (terms.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No terms yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(terms) { term ->
                TermCard(
                    term = term,
                    onComplete = onComplete,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun TermCard(
    term: TermEntity,
    onComplete: ((TermEntity) -> Unit)?,
    onDelete: (TermEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = term.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // ✅ ИСПОЛЬЗУЕТ dueDate
                    Text(
                        text = dateFormat.format(Date(term.dueDate)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (term.reminderMinutesBefore > 0) {
                        Text(
                            text = "Reminder: ${term.reminderMinutesBefore} min before",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Row {
                    if (onComplete != null) {
                        IconButton(onClick = { onComplete(term) }) {
                            Icon(
                                Icons.Default.Check,
                                "Complete",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    IconButton(onClick = { onDelete(term) }) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTermDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Long, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedHour by remember { mutableIntStateOf(12) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var reminderMinutes by remember { mutableIntStateOf(15) }
    
    val context = LocalContext.current
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis
    )
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Term") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Date Picker Button
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(dateFormat.format(Date(selectedDateMillis)))
                }
                
                // Time Picker Button
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(String.format("%02d:%02d", selectedHour, selectedMinute))
                }
                
                // Reminder Selector
                Column {
                    Text(
                        text = "Reminder",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = reminderMinutes == minutes,
                                onClick = { reminderMinutes = minutes },
                                label = { Text("$minutes min") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = selectedDateMillis
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, selectedMinute)
                            set(Calendar.SECOND, 0)
                        }
                        onCreate(title, calendar.timeInMillis, reminderMinutes)
                    }
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
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDateMillis = it
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    if (showTimePicker) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                showTimePicker = false
            },
            selectedHour,
            selectedMinute,
            true
        ).apply {
            setOnCancelListener { showTimePicker = false }
            show()
        }
    }
}

