package com.docs.scanner.presentation.screens.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.logger.DebugLog
import com.docs.scanner.data.local.logger.DebugLogger
import com.docs.scanner.data.local.logger.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@Composable
fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Logs") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearLogs) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                    IconButton(onClick = viewModel::exportLogs) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                LogItem(log)
            }
        }
    }
}

@Composable
private fun LogItem(log: DebugLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (log.level) {
                LogLevel.DEBUG -> Color(0xFFE8F5E9)
                LogLevel.INFO -> Color(0xFFE3F2FD)
                LogLevel.WARNING -> Color(0xFFFFF3E0)
                LogLevel.ERROR -> Color(0xFFFFEBEE)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row {
                Text(
                    text = log.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (log.level) {
                        LogLevel.DEBUG -> Color(0xFF2E7D32)
                        LogLevel.INFO -> Color(0xFF1976D2)
                        LogLevel.WARNING -> Color(0xFFF57C00)
                        LogLevel.ERROR -> Color(0xFFC62828)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall
            )
            
            if (log.stackTrace != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.stackTrace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugLogger: DebugLogger
) : ViewModel() {
    
    val logs: StateFlow<List<DebugLog>> = debugLogger.logs
    
    fun clearLogs() {
        debugLogger.clearLogs()
    }
    
    fun exportLogs() {
        // TODO: Share log file
    }
}