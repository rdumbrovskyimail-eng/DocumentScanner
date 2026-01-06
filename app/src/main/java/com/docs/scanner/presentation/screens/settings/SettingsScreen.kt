package com.docs.scanner.presentation.screens.settings

import android.content.Intent
import androidx.core.content.FileProvider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.App
import com.docs.scanner.BuildConfig
import com.docs.scanner.domain.core.BackupInfo
import com.docs.scanner.domain.core.ImageQuality
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.ThemeMode
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onDebugClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val keyTestMessage by viewModel.keyTestMessage.collectAsStateWithLifecycle()
    val driveEmail by viewModel.driveEmail.collectAsStateWithLifecycle()
    val driveBackups by viewModel.driveBackups.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val isBackingUp by viewModel.isBackingUp.collectAsStateWithLifecycle()

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val ocrMode by viewModel.ocrMode.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val autoTranslate by viewModel.autoTranslate.collectAsStateWithLifecycle()
    val cacheEnabled by viewModel.cacheEnabled.collectAsStateWithLifecycle()
    val cacheTtlDays by viewModel.cacheTtlDays.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showClearOldCacheDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf<LocalBackup?>(null) }
    var includeImagesInBackup by remember { mutableStateOf(true) }
    var showDriveRestoreDialog by remember { mutableStateOf<BackupInfo?>(null) }
    var showDriveDeleteDialog by remember { mutableStateOf<BackupInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveMessage, keyTestMessage, backupMessage) {
        val msg = listOf(saveMessage, keyTestMessage, backupMessage).firstOrNull { it.isNotBlank() }
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.handleSignInResult(it.resultCode, it.data)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                            ApiKeyItem(
                                key = key,
                                onActivate = viewModel::activateKey,
                                onCopy = viewModel::copyApiKey,
                                onDelete = viewModel::deleteKey,
                                onTest = viewModel::testApiKey
                            )
                        }
                    }
                }
            }

            // Appearance
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)

                    SettingDropdown(
                        title = "Theme",
                        value = themeMode.name,
                        options = listOf(
                            ThemeMode.SYSTEM.name,
                            ThemeMode.LIGHT.name,
                            ThemeMode.DARK.name
                        ),
                        onSelect = { selected ->
                            viewModel.setThemeMode(ThemeMode.valueOf(selected))
                        }
                    )

                    SettingDropdown(
                        title = "App language",
                        value = if (appLanguage.isBlank()) "SYSTEM" else appLanguage,
                        options = listOf("SYSTEM", "en", "ru", "es", "de", "fr", "it", "pt", "zh"),
                        onSelect = { selected ->
                            val code = if (selected == "SYSTEM") "" else selected
                            viewModel.setAppLanguage(code)
                            (context.applicationContext as? App)?.setAppLocale(code)
                        }
                    )
                }
            }

            // OCR + Translation
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("OCR & Translation", style = MaterialTheme.typography.titleMedium)

                    SettingDropdown(
                        title = "OCR mode",
                        value = ocrMode,
                        options = listOf("LATIN", "CHINESE", "JAPANESE", "KOREAN", "DEVANAGARI"),
                        onSelect = viewModel::setOcrMode
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Auto-translate after OCR")
                        Switch(checked = autoTranslate, onCheckedChange = viewModel::setAutoTranslate)
                    }

                    val targetOptions = Language.translationSupported.filter { it != Language.AUTO }
                    SettingDropdown(
                        title = "Target language",
                        value = "${targetLanguage.displayName} (${targetLanguage.code})",
                        options = targetOptions.map { "${it.displayName} (${it.code})" },
                        onSelect = { sel ->
                            val code = sel.substringAfterLast("(").substringBefore(")").trim()
                            val lang = Language.fromCode(code) ?: Language.ENGLISH
                            viewModel.setTargetLanguage(lang)
                        }
                    )
                }
            }

            // Cache
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Translation cache", style = MaterialTheme.typography.titleMedium)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Enable cache")
                        Switch(checked = cacheEnabled, onCheckedChange = viewModel::setCacheEnabled)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TTL (days): $cacheTtlDays")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.setCacheTtl((cacheTtlDays - 1).coerceIn(1, 365)) }) {
                                Text("-")
                            }
                            OutlinedButton(onClick = { viewModel.setCacheTtl((cacheTtlDays + 1).coerceIn(1, 365)) }) {
                                Text("+")
                            }
                        }
                    }

                    cacheStats?.let { s ->
                        Text(
                            text = "Entries: ${s.totalEntries} • Size: ${(s.totalSizeBytes / (1024.0 * 1024.0)).format(2)} MB",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } ?: Text("Stats: —", color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::refreshCacheStats, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh")
                        }
                        OutlinedButton(onClick = viewModel::clearCache, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear")
                        }
                    }

                    OutlinedButton(onClick = { showClearOldCacheDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AutoDelete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear old…")
                    }
                }
            }

            // Image quality
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Image quality", style = MaterialTheme.typography.titleMedium)
                    SettingDropdown(
                        title = "Preset",
                        value = "Choose (applies on save)",
                        options = ImageQuality.entries.map { it.name },
                        onSelect = { viewModel.setImageQuality(ImageQuality.valueOf(it)) }
                    )
                }
            }

            // Storage
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Storage & maintenance", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = storageUsage?.formatTotal() ?: "Calculating…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::refreshStorageUsage, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh")
                        }
                        OutlinedButton(onClick = viewModel::clearTempFiles, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CleaningServices, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Clear temp")
                        }
                    }
                }
            }

            // Local backup (no Drive in Stage 1)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Local backup", style = MaterialTheme.typography.titleMedium)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Include images")
                        Switch(checked = includeImagesInBackup, onCheckedChange = { includeImagesInBackup = it })
                    }

                    Button(
                        onClick = { viewModel.createLocalBackup(includeImagesInBackup) },
                        enabled = !isBackingUp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isBackingUp) "Working…" else "Create backup")
                    }

                    if (localBackups.isEmpty()) {
                        Text("No local backups yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        localBackups.take(10).forEach { b ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(b.name, style = MaterialTheme.typography.bodyMedium)
                                    Text("${(b.sizeBytes / (1024.0 * 1024.0)).format(2)} MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { showRestoreDialog = b }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Restore, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Restore")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    val file = File(b.path)
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                                                        file
                                                    )
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/zip"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Share backup"))
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Share, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Share")
                                        }
                                    }
                                }
                            }
                        }
                        if (localBackups.size > 10) {
                            Text("…more backups available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Google Drive (Stage 5)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Google Drive", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = driveEmail?.let { "Connected: $it" } ?: "Not connected",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.signInGoogleDrive(context, signInLauncher) },
                            enabled = driveEmail == null && !isBackingUp,
                            modifier = Modifier.weight(1f)
                        ) { Text("Connect") }
                        OutlinedButton(
                            onClick = viewModel::signOutGoogleDrive,
                            enabled = driveEmail != null && !isBackingUp,
                            modifier = Modifier.weight(1f)
                        ) { Text("Disconnect") }
                    }

                    if (driveEmail != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Include images")
                            Switch(checked = includeImagesInBackup, onCheckedChange = { includeImagesInBackup = it })
                        }

                        Button(
                            onClick = { viewModel.uploadBackupToGoogleDrive(includeImagesInBackup) },
                            enabled = !isBackingUp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isBackingUp) "Working…" else "Upload backup")
                        }

                        OutlinedButton(
                            onClick = viewModel::refreshDriveBackups,
                            enabled = !isBackingUp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh list")
                        }

                        if (driveBackups.isEmpty()) {
                            Text("No Drive backups yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            driveBackups.take(10).forEach { b ->
                                Card(
                                    Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(b.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${(b.sizeBytes / (1024.0 * 1024.0)).format(2)} MB",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { showDriveRestoreDialog = b },
                                                enabled = !isBackingUp,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Restore, null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Restore")
                                            }
                                            OutlinedButton(
                                                onClick = { showDriveDeleteDialog = b },
                                                enabled = !isBackingUp,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Delete, null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Delete")
                                            }
                                        }
                                    }
                                }
                            }
                            if (driveBackups.size > 10) {
                                Text("…more backups available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Debug card
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Debug", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDebugClick, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.BugReport, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open debug logs")
                    }
                }
            }
        }
    }

    if (showAddKeyDialog) {
        var key by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            title = { Text("Add Gemini API key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = key.isNotBlank(),
                    onClick = {
                        viewModel.addApiKey(key, label.ifBlank { null })
                        showAddKeyDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = key.isNotBlank(),
                        onClick = { viewModel.testApiKeyRaw(key) }
                    ) { Text("Test") }
                    TextButton(onClick = { showAddKeyDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showClearOldCacheDialog) {
        var days by remember { mutableStateOf(cacheTtlDays) }
        AlertDialog(
            onDismissRequest = { showClearOldCacheDialog = false },
            title = { Text("Clear old cache") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete entries older than $days days.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { days = (days - 1).coerceIn(1, 365) }) { Text("-") }
                        OutlinedButton(onClick = { days = (days + 1).coerceIn(1, 365) }) { Text("+") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearOldCache(days)
                    showClearOldCacheDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showClearOldCacheDialog = false }) { Text("Cancel") }
            }
        )
    }

    showRestoreDialog?.let { b ->
        var merge by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showRestoreDialog = null },
            title = { Text("Restore backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore from:")
                    Text(b.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Merge into existing")
                        Switch(checked = merge, onCheckedChange = { merge = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBackingUp,
                    onClick = {
                        viewModel.restoreLocalBackup(b.path, merge)
                        showRestoreDialog = null
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = null }) { Text("Cancel") }
            }
        )
    }

    showDriveRestoreDialog?.let { b ->
        var merge by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDriveRestoreDialog = null },
            title = { Text("Restore from Google Drive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore from:")
                    Text(b.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Merge into existing")
                        Switch(checked = merge, onCheckedChange = { merge = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBackingUp,
                    onClick = {
                        viewModel.restoreDriveBackup(b.id, merge)
                        showDriveRestoreDialog = null
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showDriveRestoreDialog = null }) { Text("Cancel") }
            }
        )
    }

    showDriveDeleteDialog?.let { b ->
        AlertDialog(
            onDismissRequest = { showDriveDeleteDialog = null },
            title = { Text("Delete Drive backup?") },
            text = { Text("This will delete \"${b.name}\" from Google Drive.") },
            confirmButton = {
                TextButton(
                    enabled = !isBackingUp,
                    onClick = {
                        viewModel.deleteDriveBackup(b.id)
                        showDriveDeleteDialog = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDriveDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingDropdown(
    title: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        expanded = false
                        onSelect(opt)
                    }
                )
            }
        }
    }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)