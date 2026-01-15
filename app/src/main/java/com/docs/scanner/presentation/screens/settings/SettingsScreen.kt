/*
 * SettingsScreen.kt
 * Version: 8.0.0 - PRODUCTION READY 2026
 * 
 * Complete Settings Screen with:
 * - API Keys management
 * - Appearance settings
 * - Translation settings  
 * - ML Kit OCR Settings (NEW TAB)
 * - Cache management
 * - Storage management
 * - Local backup
 * - Google Drive sync
 * - Debug tools
 */

package com.docs.scanner.presentation.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.App
import com.docs.scanner.BuildConfig
import com.docs.scanner.domain.core.BackupInfo
import com.docs.scanner.domain.core.ImageQuality
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.ThemeMode
import com.docs.scanner.presentation.screens.settings.components.MlkitSettingsSection
import kotlinx.coroutines.launch
import java.io.File

/**
 * Settings tabs enumeration.
 */
enum class SettingsTab(val title: String, val icon: @Composable () -> Unit) {
    GENERAL("General", { Icon(Icons.Default.Settings, null) }),
    MLKIT("ML Kit", { Icon(Icons.Default.TextFields, null) }),
    BACKUP("Backup", { Icon(Icons.Default.CloudSync, null) }),
    DEBUG("Debug", { Icon(Icons.Default.BugReport, null) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onDebugClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State collectors
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
    val mlkitSettings by viewModel.mlkitSettings.collectAsStateWithLifecycle()

    // UI State
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showClearOldCacheDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf<LocalBackup?>(null) }
    var includeImagesInBackup by remember { mutableStateOf(true) }
    var showDriveRestoreDialog by remember { mutableStateOf<BackupInfo?>(null) }
    var showDriveDeleteDialog by remember { mutableStateOf<BackupInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { SettingsTab.entries.size })

    // Show messages
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
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                SettingsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title) },
                        icon = tab.icon
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (SettingsTab.entries[page]) {
                    SettingsTab.GENERAL -> GeneralSettingsTab(
                        apiKeys = apiKeys,
                        themeMode = themeMode,
                        appLanguage = appLanguage,
                        ocrMode = ocrMode,
                        targetLanguage = targetLanguage,
                        autoTranslate = autoTranslate,
                        cacheEnabled = cacheEnabled,
                        cacheTtlDays = cacheTtlDays,
                        cacheStats = cacheStats,
                        storageUsage = storageUsage,
                        onAddKeyClick = { showAddKeyDialog = true },
                        onActivateKey = viewModel::activateKey,
                        onCopyKey = { viewModel.copyApiKey(context, it) },
                        onDeleteKey = viewModel::deleteKey,
                        onTestKey = viewModel::testApiKey,
                        onThemeModeChange = viewModel::setThemeMode,
                        onAppLanguageChange = { code ->
                            viewModel.setAppLanguage(code)
                            (context.applicationContext as? App)?.setAppLocale(code)
                        },
                        onOcrModeChange = viewModel::setOcrMode,
                        onTargetLanguageChange = viewModel::setTargetLanguage,
                        onAutoTranslateChange = viewModel::setAutoTranslate,
                        onCacheEnabledChange = viewModel::setCacheEnabled,
                        onCacheTtlChange = viewModel::setCacheTtl,
                        onRefreshCacheStats = viewModel::refreshCacheStats,
                        onClearCache = viewModel::clearCache,
                        onClearOldCache = { showClearOldCacheDialog = true },
                        onRefreshStorage = viewModel::refreshStorageUsage,
                        onClearTemp = viewModel::clearTempFiles,
                        onImageQualityChange = viewModel::setImageQuality
                    )

                    SettingsTab.MLKIT -> MlkitSettingsTab(
                        mlkitSettings = mlkitSettings,
                        onScriptModeChange = viewModel::setMlkitScriptMode,
                        onAutoDetectChange = viewModel::setMlkitAutoDetect,
                        onConfidenceThresholdChange = viewModel::setMlkitConfidenceThreshold,
                        onHighlightLowConfidenceChange = viewModel::setMlkitHighlightLowConfidence,
                        onShowWordConfidencesChange = viewModel::setMlkitShowWordConfidences,
                        onImageSelected = viewModel::setMlkitSelectedImage,
                        onTestOcr = viewModel::runMlkitOcrTest,
                        onClearTestResult = viewModel::clearMlkitTestResult,
                        onClearMlkitCache = viewModel::clearMlkitCache
                    )

                    SettingsTab.BACKUP -> BackupSettingsTab(
                        localBackups = localBackups,
                        driveEmail = driveEmail,
                        driveBackups = driveBackups,
                        isBackingUp = isBackingUp,
                        includeImages = includeImagesInBackup,
                        onIncludeImagesChange = { includeImagesInBackup = it },
                        onCreateLocalBackup = { viewModel.createLocalBackup(includeImagesInBackup) },
                        onRestoreLocalBackup = { showRestoreDialog = it },
                        onShareBackup = { backup ->
                            runCatching {
                                val file = File(backup.path)
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
                        onSignInDrive = { viewModel.signInGoogleDrive(context, signInLauncher) },
                        onSignOutDrive = viewModel::signOutGoogleDrive,
                        onUploadToDrive = { viewModel.uploadBackupToGoogleDrive(includeImagesInBackup) },
                        onRefreshDriveBackups = viewModel::refreshDriveBackups,
                        onRestoreDriveBackup = { showDriveRestoreDialog = it },
                        onDeleteDriveBackup = { showDriveDeleteDialog = it }
                    )

                    SettingsTab.DEBUG -> DebugSettingsTab(
                        onDebugClick = onDebugClick
                    )
                }
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

    if (showClearOldCacheDialog) {
        ClearOldCacheDialog(
            initialDays = cacheTtlDays,
            onDismiss = { showClearOldCacheDialog = false },
            onConfirm = { days ->
                viewModel.clearOldCache(days)
                showClearOldCacheDialog = false
            }
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
// TAB CONTENT COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GeneralSettingsTab(
    apiKeys: List<com.docs.scanner.data.local.security.ApiKeyData>,
    themeMode: ThemeMode,
    appLanguage: String,
    ocrMode: String,
    targetLanguage: Language,
    autoTranslate: Boolean,
    cacheEnabled: Boolean,
    cacheTtlDays: Int,
    cacheStats: com.docs.scanner.domain.core.TranslationCacheStats?,
    storageUsage: com.docs.scanner.domain.repository.StorageUsage?,
    onAddKeyClick: () -> Unit,
    onActivateKey: (String) -> Unit,
    onCopyKey: (String) -> Unit,
    onDeleteKey: (String) -> Unit,
    onTestKey: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAppLanguageChange: (String) -> Unit,
    onOcrModeChange: (String) -> Unit,
    onTargetLanguageChange: (Language) -> Unit,
    onAutoTranslateChange: (Boolean) -> Unit,
    onCacheEnabledChange: (Boolean) -> Unit,
    onCacheTtlChange: (Int) -> Unit,
    onRefreshCacheStats: () -> Unit,
    onClearCache: () -> Unit,
    onClearOldCache: () -> Unit,
    onRefreshStorage: () -> Unit,
    onClearTemp: () -> Unit,
    onImageQualityChange: (ImageQuality) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // API Keys
        SettingsCard(title = "Gemini API Keys", icon = Icons.Default.Key) {
            if (apiKeys.isEmpty()) {
                Text("No keys added", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                apiKeys.forEach { key ->
                    ApiKeyItem(
                        key = key,
                        onActivate = onActivateKey,
                        onCopy = { onCopyKey(key.key) },
                        onDelete = onDeleteKey,
                        onTest = onTestKey
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAddKeyClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add API Key")
            }
        }

        // Appearance
        SettingsCard(title = "Appearance", icon = Icons.Default.Palette) {
            SettingDropdown(
                title = "Theme",
                value = themeMode.name,
                options = ThemeMode.entries.map { it.name },
                onSelect = { onThemeModeChange(ThemeMode.valueOf(it)) }
            )
            Spacer(Modifier.height(12.dp))
            SettingDropdown(
                title = "App Language",
                value = if (appLanguage.isBlank()) "System" else appLanguage.uppercase(),
                options = listOf("System", "EN", "RU", "ES", "DE", "FR", "IT", "PT", "ZH"),
                onSelect = { onAppLanguageChange(if (it == "System") "" else it.lowercase()) }
            )
        }

        // Translation
        SettingsCard(title = "Translation", icon = Icons.Default.Translate) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-translate after OCR")
                Switch(checked = autoTranslate, onCheckedChange = onAutoTranslateChange)
            }
            Spacer(Modifier.height(12.dp))
            val targetOptions = Language.translationSupported.filter { it != Language.AUTO }
            SettingDropdown(
                title = "Target Language",
                value = "${targetLanguage.displayName} (${targetLanguage.code})",
                options = targetOptions.map { "${it.displayName} (${it.code})" },
                onSelect = { sel ->
                    val code = sel.substringAfterLast("(").substringBefore(")").trim()
                    Language.fromCode(code)?.let { onTargetLanguageChange(it) }
                }
            )
        }

        // Cache
        SettingsCard(title = "Translation Cache", icon = Icons.Default.Cached) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable cache")
                Switch(checked = cacheEnabled, onCheckedChange = onCacheEnabledChange)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TTL (days): $cacheTtlDays")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onCacheTtlChange((cacheTtlDays - 1).coerceIn(1, 365)) }) { Text("-") }
                    OutlinedButton(onClick = { onCacheTtlChange((cacheTtlDays + 1).coerceIn(1, 365)) }) { Text("+") }
                }
            }
            cacheStats?.let { s ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "Entries: ${s.totalEntries} • Size: ${(s.totalSizeBytes / (1024.0 * 1024.0)).format(2)} MB",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefreshCacheStats, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
                OutlinedButton(onClick = onClearCache, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClearOldCache, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoDelete, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear old entries...")
            }
        }

        // Storage
        SettingsCard(title = "Storage", icon = Icons.Default.Storage) {
            Text(
                text = storageUsage?.formatTotal() ?: "Calculating...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefreshStorage, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
                OutlinedButton(onClick = onClearTemp, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CleaningServices, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear temp")
                }
            }
        }

        // Image Quality
        SettingsCard(title = "Image Quality", icon = Icons.Default.HighQuality) {
            SettingDropdown(
                title = "Quality preset",
                value = "Select quality",
                options = ImageQuality.entries.map { it.name },
                onSelect = { onImageQualityChange(ImageQuality.valueOf(it)) }
            )
        }
    }
}

@Composable
private fun MlkitSettingsTab(
    mlkitSettings: com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState,
    onScriptModeChange: (com.docs.scanner.data.remote.mlkit.OcrScriptMode) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onConfidenceThresholdChange: (Float) -> Unit,
    onHighlightLowConfidenceChange: (Boolean) -> Unit,
    onShowWordConfidencesChange: (Boolean) -> Unit,
    onImageSelected: (android.net.Uri?) -> Unit,
    onTestOcr: () -> Unit,
    onClearTestResult: () -> Unit,
    onClearMlkitCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MlkitSettingsSection(
            state = mlkitSettings,
            onScriptModeChange = onScriptModeChange,
            onAutoDetectChange = onAutoDetectChange,
            onConfidenceThresholdChange = onConfidenceThresholdChange,
            onHighlightLowConfidenceChange = onHighlightLowConfidenceChange,
            onShowWordConfidencesChange = onShowWordConfidencesChange,
            onImageSelected = onImageSelected,
            onTestOcr = onTestOcr,
            onClearTestResult = onClearTestResult
        )

        // Additional MLKit actions
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MLKit Cache",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onClearMlkitCache,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear MLKit Recognizer Cache")
                }
                Text(
                    text = "Frees memory by releasing cached ML Kit recognizers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BackupSettingsTab(
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Local Backup
        SettingsCard(title = "Local Backup", icon = Icons.Default.Save) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Include images")
                Switch(checked = includeImages, onCheckedChange = onIncludeImagesChange)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCreateLocalBackup,
                enabled = !isBackingUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBackingUp) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Working...")
                } else {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Backup")
                }
            }

            if (localBackups.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Recent backups:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                localBackups.take(5).forEach { backup ->
                    BackupItem(
                        name = backup.name,
                        size = "${(backup.sizeBytes / (1024.0 * 1024.0)).format(2)} MB",
                        onRestore = { onRestoreLocalBackup(backup) },
                        onShare = { onShareBackup(backup) }
                    )
                }
            }
        }

        // Google Drive
        SettingsCard(title = "Google Drive", icon = Icons.Default.CloudSync) {
            Text(
                text = driveEmail?.let { "Connected: $it" } ?: "Not connected",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSignInDrive,
                    enabled = driveEmail == null && !isBackingUp,
                    modifier = Modifier.weight(1f)
                ) { Text("Connect") }
                OutlinedButton(
                    onClick = onSignOutDrive,
                    enabled = driveEmail != null && !isBackingUp,
                    modifier = Modifier.weight(1f)
                ) { Text("Disconnect") }
            }

            if (driveEmail != null) {
                Spacer(Modifier.height(16.dp))
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
                    Text("Refresh list")
                }

                if (driveBackups.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Drive backups:", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    driveBackups.take(5).forEach { backup ->
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
}

@Composable
private fun DebugSettingsTab(onDebugClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard(title = "Debug Tools", icon = Icons.Default.BugReport) {
            Text(
                "Access debug logs and diagnostic information",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDebugClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BugReport, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Debug Logs")
            }
        }

        SettingsCard(title = "App Info", icon = Icons.Default.Info) {
            InfoRow("Version", BuildConfig.VERSION_NAME)
            InfoRow("Build", BuildConfig.VERSION_CODE.toString())
            InfoRow("Package", BuildConfig.APPLICATION_ID)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun BackupItem(
    name: String,
    size: String,
    onRestore: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun DriveBackupItem(
    backup: BackupInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(backup.name, style = MaterialTheme.typography.bodyMedium)
            Text(backup.formatSize(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore")
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DIALOGS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AddApiKeyDialog(
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
    onTest: (String) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Gemini API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank(),
                onClick = { onSave(key, label.ifBlank { null }) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = key.isNotBlank(), onClick = { onTest(key) }) { Text("Test") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ClearOldCacheDialog(
    initialDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var days by remember { mutableStateOf(initialDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Old Cache") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Delete entries older than $days days.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { days = (days - 1).coerceIn(1, 365) }) { Text("-") }
                    OutlinedButton(onClick = { days = (days + 1).coerceIn(1, 365) }) { Text("+") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(days) }) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RestoreBackupDialog(
    backupName: String,
    onDismiss: () -> Unit,
    onConfirm: (merge: Boolean) -> Unit
) {
    var merge by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Restore from: $backupName")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Merge into existing data")
                    Switch(checked = merge, onCheckedChange = { merge = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(merge) }) { Text("Restore") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
