package com.docs.scanner.presentation.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docs.scanner.util.LogcatCollector

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apiKeys by viewModel.apiKeys.collectAsState()
    val driveEmail by viewModel.driveEmail.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()

    var showAddKeyDialog by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.handleSignInResult(it.resultCode, it.data)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Keys card
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gemini API Keys", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showAddKeyDialog = true }) { Icon(Icons.Default.Add, null) }
                    }
                    if (apiKeys.isEmpty()) {
                        Text("No keys added", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        apiKeys.forEach { key ->
                            ApiKeyItem(key, viewModel::activateKey, viewModel::copyApiKey, viewModel::deleteKey)
                        }
                    }
                }
            }

            // Google Drive card — без изменений

            // Debug card
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Debug", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        LogcatCollector.getInstance(context).forceSave()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Debug Log")
                    }
                }
            }
        }
    }

    // Add key dialog — без изменений
}