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
import com.docs.scanner.data.local.database.entities.TermEntity
import com.docs.scanner.presentation.components.EmptyState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
                            Icons.Default.Event,
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
            onCreate = { title, description, dateTime, _ ->
                // Умная система напоминаний включена по умолчанию
                viewModel.createTerm(title, description, dateTime, null)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTermDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, Long, Int?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    // Date/Time state
    val calendar = remember { Calendar.getInstance() }
    var selectedDate by remember { mutableStateOf(calendar.timeInMillis) }
    var selectedTime by remember { mutableStateOf(calendar.timeInMillis) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate
    )
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE)
    )
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    
    // Combine date and time
    val finalDateTime = remember(selectedDate, selectedTime) {
        val dateCalendar = Calendar.getInstance().apply {
            timeInMillis = selectedDate
        }
        val timeCalendar = Calendar.getInstance().apply {
            timeInMillis = selectedTime
        }
        
        Calendar.getInstance().apply {
            set(Calendar.YEAR, dateCalendar.get(Calendar.YEAR))
            set(Calendar.MONTH, dateCalendar.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, dateCalendar.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    val isDateInPast = finalDateTime < System.currentTimeMillis()
    
    // Вычисляем время до термина
    val timeUntilTerm = finalDateTime - System.currentTimeMillis()
    val daysUntil = (timeUntilTerm / (1000 * 60 * 60 * 24)).toInt()
    val hoursUntil = (timeUntilTerm / (1000 * 60 * 60)).toInt() % 24
    
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
                
                // Date Picker Button
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(dateFormat.format(Date(selectedDate)))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Time Picker Button
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(timeFormat.format(Date(selectedTime)))
                }
                
                if (isDateInPast) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Selected time is in the past",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (timeUntilTerm > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⏰ Time until term: $daysUntil days, $hoursUntil hours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Информация об умной системе напоминаний
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Smart Reminders Enabled",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "• 2-4 days before: Daily reminders\n" +
                            "• 12-24 hours before: 3 reminders\n" +
                            "• 5 hours before: Hourly reminders\n" +
                            "• Last hour: Every 15 minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        title,
                        description.ifBlank { null },
                        finalDateTime,
                        null // Умная система включена по умолчанию
                    )
                },
                enabled = title.isNotBlank() && !isDateInPast
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
    
    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = it
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
            DatePicker(
                state = datePickerState,
                dateValidator = { timestamp ->
                    timestamp >= System.currentTimeMillis() - 86400000
                }
            )
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                        }
                        selectedTime = cal.timeInMillis
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
private fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = { content() }
    )
}

@Composable
private fun TermCard(
    term: TermEntity,
    onComplete: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }
    
    // Вычисляем время до термина
    val timeUntilTerm = term.dateTime - System.currentTimeMillis()
    val isOverdue = timeUntilTerm < 0
    val daysUntil = (timeUntilTerm / (1000 * 60 * 60 * 24)).toInt()
    val hoursUntil = (timeUntilTerm / (1000 * 60 * 60)).toInt() % 24
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = when {
            term.isCompleted -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
            isOverdue -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
            else -> CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    term.isCompleted -> Icons.Default.CheckCircle
                    isOverdue -> Icons.Default.Warning
                    else -> Icons.Default.Event
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = when {
                    term.isCompleted -> MaterialTheme.colorScheme.primary
                    isOverdue -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
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
                
                if (!term.isCompleted && !isOverdue && timeUntilTerm > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (daysUntil > 0) {
                            "In $daysUntil days, $hoursUntil hours"
                        } else if (hoursUntil > 0) {
                            "In $hoursUntil hours"
                        } else {
                            val minutesUntil = (timeUntilTerm / (1000 * 60)).toInt()
                            "In $minutesUntil minutes"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (isOverdue && !term.isCompleted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ OVERDUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
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
