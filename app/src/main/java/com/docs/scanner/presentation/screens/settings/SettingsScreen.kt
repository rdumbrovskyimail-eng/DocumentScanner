/*
 * SettingsScreen.kt
 * Version: 22.3.0 - PRODUCTION-READY SINGLE SCREEN (2026)
 * 
 * ✅ REMOVED:
 * - All Tabs (AI_OCR, GENERAL, BACKUP, TESTING)
 * - Testing/TestingTab (OCR test, Translation test)
 * - Debug/Debug Tools Card (onDebugClick, logs, clear cache)
 * - Translation Cache settings card (now always enabled)
 * - Storage usage card
 */

package com.docs.scanner.presentation.screens.settings

import android.content.Intent
import com.docs.scanner.data.local.preferences.GeminiModelOption
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.security.ApiKeyEntry
import com.docs.scanner.domain.core.BackupInfo
import com.docs.scanner.domain.core.ImageQuality
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.ThemeMode
import com.docs.scanner.presentation.screens.settings.components.ApiKeysSettingsSection
import com.docs.scanner.presentation.screens.settings.components.GeminiOcrSettingsSection
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val isLoadingKeys by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val keyTestMessage by viewModel.keyTestMessage.collectAsStateWithLifecycle()
    val driveEmail by viewModel.driveEmail.collectAsStateWithLifecycle()
    val driveBackups by viewModel.driveBackups.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val isBackingUp by viewModel.isBackingUp.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val imageQuality by viewModel.imageQuality.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val mlkitSettings by viewModel.mlkitSettings.collectAsStateWithLifecycle()

    var showAddKeyDialog by remember { mutableStateOf(false) }
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

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.handleSignInResult(it.resultCode, it.data)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // Единая вертикальная лента настроек без вкладок
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Gemini AI Fallback (Всегда активно, порог 60%)
            SettingsCard(title = "Gemini AI Fallback", icon = Icons.Default.AutoAwesome) {
                GeminiOcrSettingsSection(
                    selectedModel = mlkitSettings.selectedGeminiModel,
                    availableModels = mlkitSettings.availableGeminiModels,
                    onModelChange = viewModel::setGeminiOcrModel,
                    onAddNewModel = viewModel::addNewOcrModel
                )
            }

            // 2. Translation (Всегда активно, по дефолту Русский)
            SettingsCard(title = "Translation", icon = Icons.Default.Translate) {
                TranslationSettingsSection(
                    targetLanguage = targetLanguage,
                    selectedModel = mlkitSettings.selectedTranslationModel,
                    availableModels = mlkitSettings.availableTranslationModels,
                    onTargetLanguageChange = viewModel::setTargetLanguage,
                    onModelChange = viewModel::setTranslationModel,
                    onAddNewModel = viewModel::addNewTranslationModel
                )
            }

            // 3. Gemini API Keys
            SettingsCard(title = "Gemini API Keys", icon = Icons.Default.Key) {
                ApiKeysSettingsSection(
                    keys = apiKeys,
                    isLoading = isLoadingKeys,
                    onAddKey = { key, label -> viewModel.addApiKey(key, label) },
                    onRemoveKey = { viewModel.deleteKey(it) },
                    onSetPrimary = { viewModel.activateKey(it) },
                    onResetErrors = viewModel::resetApiKeyErrors
                )
            }

            // 4. Backup & Drive Cloud
            SettingsCard(title = "Backup & Cloud", icon = Icons.Default.CloudSync) {
                BackupSectionContent(
                    localBackups = localBackups,
                    driveEmail = driveEmail,
                    driveBackups = driveBackups,
                    isBackingUp = isBackingUp,
                    includeImages = includeImagesInBackup,
                    onIncludeImagesChange = { includeImagesInBackup = it },
                    onCreateLocalBackup = { viewModel.createLocalBackup(includeImagesInBackup) },
                    onRestoreLocalBackup = { showRestoreDialog = it },
                    onShareBackup = { backup ->
                        try {
                            val file = File(backup.path)
                            if (file.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share backup"))
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Share failed")
                        }
                    },
                    onSignInDrive = { viewModel.signInGoogleDrive(context, signInLauncher) },
                    onSignOutDrive = viewModel::signOutGoogleDrive,
                    onUploadToDrive = { viewModel.uploadBackupToGoogleDrive(includeImagesInBackup) },
                    onRefreshDriveBackups = viewModel::refreshDriveBackups,
                    onRestoreDriveBackup = { showDriveRestoreDialog = it },
                    onDeleteDriveBackup = { showDriveDeleteDialog = it }
                )
            }

            // 5. Appearance & Quality (Качество по умолчанию HIGH)
            SettingsCard(title = "Appearance & Quality", icon = Icons.Default.Palette) {
                SettingDropdown(
                    title = "Theme",
                    value = themeMode.name,
                    options = ThemeMode.entries.map { it.name }
                ) { onThemeModeChange -> viewModel.setThemeMode(ThemeMode.valueOf(onThemeModeChange)) }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingDropdown(
                    title = "Image Quality",
                    value = imageQuality.name,
                    options = ImageQuality.entries.map { it.name }
                ) { onImageQualityChange -> viewModel.setImageQuality(ImageQuality.valueOf(onImageQualityChange)) }
            }
        }
    }

    // Dialogs
    if (showAddKeyDialog) {
        AddApiKeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onSave = { key, label ->
                viewModel.addApiKey(key, label)
                showAddKeyDialog = false
            },
            onTest = viewModel::testApiKeyRaw
        )
    }

    showRestoreDialog?.let { backup ->
        RestoreBackupDialog(
            backupName = backup.name,
            onDismiss = { showRestoreDialog = null },
            onConfirm = { merge ->
                viewModel.restoreLocalBackup(backup.path, merge)
                showRestoreDialog = null
            }
        )
    }

    showDriveRestoreDialog?.let { backup ->
        RestoreBackupDialog(
            backupName = backup.name,
            onDismiss = { showDriveRestoreDialog = null },
            onConfirm = { merge ->
                viewModel.restoreDriveBackup(backup.id, merge)
                showDriveRestoreDialog = null
            }
        )
    }

    showDriveDeleteDialog?.let { backup ->
        AlertDialog(
            onDismissRequest = { showDriveDeleteDialog = null },
            title = { Text("Delete Drive backup?") },
            text = { Text("This will delete \"${backup.name}\" from Google Drive.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDriveBackup(backup.id)
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

// ═══════════════════════════════════════════════════════════════════════════════
// ВНУТРЕННИЕ ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ НАСТРОЕК
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TranslationSettingsSection(
    targetLanguage: Language,
    selectedModel: String,
    availableModels: List<GeminiModelOption>,
    onTargetLanguageChange: (Language) -> Unit,
    onModelChange: (String) -> Unit,
    onAddNewModel: (String) -> Unit
) {
    var customModelInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingDropdown(
            "Target Language",
            "${targetLanguage.displayName} (${targetLanguage.code})",
            Language.translationSupported.filter { it != Language.AUTO }.map { 
                "${it.displayName} (${it.code})" 
            }
        ) { sel ->
            Language.fromCode(
                sel.substringAfterLast("(").substringBefore(")").trim()
            )?.let { onTargetLanguageChange(it) }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            "Gemini Model for Translation",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        ModelSelectorDropdown(
            selectedModel = selectedModel,
            availableModels = availableModels,
            onModelChange = onModelChange,
            label = "Translation Model"
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Зарегистрировать модель перевода",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it.replace(" ", "") },
                    placeholder = { Text("Идентификатор модели") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                
                Button(
                    onClick = {
                        if (customModelInput.isNotBlank()) {
                            onAddNewModel(customModelInput.trim())
                            customModelInput = ""
                        }
                    },
                    enabled = customModelInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "💡 Автоматический перевод включен постоянно. Все распознанные документы будут мгновенно переводиться на выбранный язык с помощью указанной модели.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BackupSectionContent(
    localBackups: List<LocalBackup>,
    driveEmail: String?,
    driveBackups: List<BackupInfo>,
    isBackingUp: Boolean,
    includeImages: Boolean,
    onIncludeImagesChange: (Boolean) -> Unit,
    onCreateLocalBackup: () -> Unit,
    onRestoreLocalBackup: (LocalBackup) -> Unit,
    onShareBackup: (LocalBackup) -> Unit,
    onSignInDrive: () -> Unit,
    onSignOutDrive: () -> Unit,
    onUploadToDrive: () -> Unit,
    onRefreshDriveBackups: () -> Unit,
    onRestoreDriveBackup: (BackupInfo) -> Unit,
    onDeleteDriveBackup: (BackupInfo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Include images in backups")
            Switch(
                checked = includeImages,
                onCheckedChange = onIncludeImagesChange
            )
        }
        
        Button(
            onClick = onCreateLocalBackup,
            enabled = !isBackingUp,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isBackingUp) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Working...")
            } else {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Create Local Backup")
            }
        }
        
        if (localBackups.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Recent backups:", style = MaterialTheme.typography.labelLarge)
            localBackups.take(3).forEach { backup ->
                BackupItem(
                    name = backup.name,
                    size = "${(backup.sizeBytes / (1024.0 * 1024.0)).format(2)} MB",
                    onRestore = { onRestoreLocalBackup(backup) },
                    onShare = { onShareBackup(backup) }
                )
            }
        }

        HorizontalDivider()

        Text("Cloud Sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text = driveEmail?.let { "Connected: $it" } ?: "Not connected to Google Drive",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onSignInDrive,
                enabled = driveEmail == null && !isBackingUp,
                modifier = Modifier.weight(1f)
            ) {
                Text("Connect")
            }
            OutlinedButton(
                onClick = onSignOutDrive,
                enabled = driveEmail != null && !isBackingUp,
                modifier = Modifier.weight(1f)
            ) {
                Text("Disconnect")
            }
        }
        
        if (driveEmail != null) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onUploadToDrive,
                enabled = !isBackingUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Upload to Drive")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRefreshDriveBackups,
                enabled = !isBackingUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh Drive backups")
            }
            if (driveBackups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Drive backups:", style = MaterialTheme.typography.labelLarge)
                driveBackups.take(3).forEach { backup ->
                    DriveBackupItem(
                        backup = backup,
                        onRestore = { onRestoreDriveBackup(backup) },
                        onDelete = { onDeleteDriveBackup(backup) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
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
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value, Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelSelectorDropdown(
    selectedModel: String,
    availableModels: List<GeminiModelOption>,
    onModelChange: (String) -> Unit,
    label: String = "Model"
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = availableModels.find { it.id == selectedModel } 
        ?: availableModels.firstOrNull()
    
    Column {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = currentModel?.displayName ?: "Select model",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    currentModel?.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select $label"
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (model.id == selectedModel) 
                                            FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    ModelSpeedBadge(model.id)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModelChange(model.id)
                        expanded = false
                    }
                )
                if (model != availableModels.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ModelSpeedBadge(modelId: String) {
    val (text, color) = when (modelId) {
        "gemini-3-flash-preview", "gemini-2.5-flash-lite", "gemini-3.1-flash-lite" -> 
            "⚡ FAST" to Color(0xFF4CAF50)
        
        "gemini-2.5-flash", "gemini-3.5-flash" -> 
            "⚖️ BALANCED" to Color(0xFF2196F3)
        
        "gemini-3-pro-preview", "gemini-2.5-pro", "gemini-3.1-pro-preview" -> 
            "🐌 SLOW" to Color(0xFFFF9800)
        
        else -> return
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun BackupItem(name: String, size: String, onRestore: () -> Unit, onShare: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore")
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun DriveBackupItem(backup: BackupInfo, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(backup.name, style = MaterialTheme.typography.bodyMedium)
            Text(backup.formatSize(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)