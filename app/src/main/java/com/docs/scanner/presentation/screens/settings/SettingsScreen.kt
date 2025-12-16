package com.docs.scanner.presentation.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val apiKey by viewModel.apiKey.collectAsState()
    val savedApiKeys by viewModel.savedApiKeys.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val driveEmail by viewModel.driveEmail.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    
    var showApiKeyMenu by remember { mutableStateOf(false) }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.resultCode, result.data)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ============ GEMINI API KEY CARD ============
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemini API Key",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { showApiKeyMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Manage Keys")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    var showPassword by remember { mutableStateOf(false) }
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = viewModel::updateApiKey,
                        label = { Text("API Key") },
                        placeholder = { Text("AIza...") },
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = null
                                    )
                                }
                                if (apiKey.isNotBlank()) {
                                    IconButton(onClick = { viewModel.copyApiKey(context) }) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy"
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = saveMessage.contains("Invalid") || saveMessage.contains("failed")
                    )
                    
                    if (saveMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = saveMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (saveMessage.contains("✓")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { 
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("https://aistudio.google.com/app/apikey")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get API key")
                        }
                        
                        Button(
                            onClick = viewModel::saveApiKey,
                            enabled = apiKey.isNotBlank() && !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Save")
                        }
                    }
                }
            }
            
            // API Key Management Menu
            DropdownMenu(
                expanded = showApiKeyMenu,
                onDismissRequest = { showApiKeyMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add New Key") },
                    onClick = { 
                        showApiKeyMenu = false
                        showAddKeyDialog = true
                    },
                    leadingIcon = { Icon(Icons.Default.Add, null) }
                )
                
                if (savedApiKeys.isNotEmpty()) {
                    Divider()
                    savedApiKeys.forEach { key ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(
                                        text = key.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "...${key.key.takeLast(8)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = { 
                                viewModel.selectApiKey(key)
                                showApiKeyMenu = false
                            },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.deleteApiKey(key.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                }
            }
            
            // ============ GOOGLE DRIVE CARD ============
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Google Drive Backup",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (driveEmail != null) {
                        Text(
                            text = "Connected: $driveEmail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Export/Import Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.exportToGoogleDrive() },
                                enabled = !isBackingUp,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isBackingUp) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export")
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.importFromGoogleDrive() },
                                enabled = !isBackingUp,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { viewModel.signOutGoogleDrive() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.signInGoogleDrive(context, signInLauncher)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Google Drive")
                        }
                    }
                    
                    if (backupMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = backupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (backupMessage.contains("✓")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
            
            // ============ ABOUT CARD ============
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Document Scanner v2.0.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Gemini Model: gemini-2.0-flash-exp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Scan, recognize, and translate documents using ML Kit and Gemini AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Add Key Dialog
    if (showAddKeyDialog) {
        var keyName by remember { mutableStateOf("") }
        var keyValue by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            title = { Text("Add API Key") },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        label = { Text("Key Name") },
                        placeholder = { Text("My API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = keyValue,
                        onValueChange = { keyValue = it },
                        label = { Text("API Key") },
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addApiKey(keyName, keyValue)
                        showAddKeyDialog = false
                    },
                    enabled = keyName.isNotBlank() && keyValue.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =====================================================
// Data Models
// =====================================================

data class SavedApiKey(
    val id: Long,
    val name: String,
    val key: String,
    val createdAt: Long = System.currentTimeMillis()
)
