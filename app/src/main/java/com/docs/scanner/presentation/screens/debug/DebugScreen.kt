package com.docs.scanner.presentation.screens.debug

import android.content.Intent
import androidx.core.content.FileProvider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle  // ✅ ДОБАВЛЕН
import androidx.lifecycle.viewModelScope
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.logger.DebugLog
import com.docs.scanner.data.local.logger.DebugLogger
import com.docs.scanner.data.local.logger.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugScreen(
    viewModel: DebugViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    // ✅ ИСПРАВЛЕНО: collectAsState() → collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    
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
                    IconButton(onClick = {
                        runCatching {
                            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            val file = File(context.cacheDir, "debug-logs-$ts.txt")
                            val content = buildString {
                                appendLine("DocumentScanner Debug Logs ($ts)")
                                appendLine("Total: ${logs.size}")
                                appendLine()
                                logs.forEach { log ->
                                    appendLine("[${log.level}] ${log.tag}: ${log.message}")
                                    log.stackTrace?.let { st ->
                                        appendLine(st)
                                    }
                                    appendLine()
                                }
                            }
                            file.writeText(content)

                            val uri = FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                file
                            )

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share debug logs"))
                        }
                    }) {
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
        Column(modifier = Modifier.padding(8.dp)) {
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
}