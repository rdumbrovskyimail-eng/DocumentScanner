/*
 * SettingsScreen.kt
 * Version: 18.0.0 - CANCEL BUTTON INTEGRATION (2026)
 * 
 * ✅ NEW IN 18.0.0:
 * - Added onCancelOcr callback integration
 * - Wired viewModel::cancelOcrTest to UI
 * - Complete cancel button support
 * 
 * ✅ NEW IN 17.0.0:
 * - Renamed "ML Kit" tab to "OCR Components"
 * - Added TranslationTestSection integration
 * - Added translation test parameters
 * - Added Gemini model selection callback
 * 
 * ✅ FIXED in 16.0.0:
 * - Unified ApiKeyEntry model throughout
 * - Removed ApiKeyData references
 * - Fixed .id → .key for API key identification
 * - Removed unnecessary .map { it.toApiKeyEntry() }
 * - All type mismatches resolved
 * 
 * ✅ PREVIOUS FIXES:
 * - Line 667 - Added Spacer import
 * - Lines 1288-1292 - Fixed ApiKeyEntry constructor
 * - All unresolved references
 * - Full multi-key failover support
 */

package com.docs.scanner.presentation.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.docs.scanner.data.local.security.ApiKeyEntry
import com.docs.scanner.domain.core.BackupInfo
import com.docs.scanner.domain.core.ImageQuality
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.ThemeMode
import com.docs.scanner.presentation.screens.settings.components.MlkitSettingsSection
import com.docs.scanner.presentation.screens.settings.components.TranslationTestSection
import com.docs.scanner.util.LogcatCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS TABS
// ═══════════════════════════════════════════════════════════════════════════════

enum class SettingsTab(val title: String, val icon: @Composable () -> Unit) {
    GENERAL("General", { Icon(Icons.Default.Settings, null) }),
    OCR_COMPONENTS("OCR Components", { Icon(Icons.Default.TextFields, null) }),
    BACKUP("Backup", { Icon(Icons.Default.CloudSync, null) }),
    DEBUG("Debug", { Icon(Icons.Default.BugReport, null) })
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SETTINGS SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

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
    val isLoadingKeys by viewModel.isSaving.collectAsStateWithLifecycle()
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
                        onCopyKey = viewModel::copyApiKey,
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

                    SettingsTab.OCR_COMPONENTS -> OcrComponentsTab(
                        mlkitSettings = mlkitSettings,
                        apiKeys = apiKeys,
                        isLoadingKeys = isLoadingKeys,
                        onScriptModeChange = viewModel::setMlkitScriptMode,
                        onAutoDetectChange = viewModel::setMlkitAutoDetect,
                        onConfidenceThresholdChange = viewModel::setMlkitConfidenceThreshold,
                        onHighlightLowConfidenceChange = viewModel::setMlkitHighlightLowConfidence,
                        onShowWordConfidencesChange = viewModel::setMlkitShowWordConfidences,
                        onImageSelected = viewModel::setMlkitSelectedImage,
                        onTestOcr = viewModel::runMlkitOcrTest,
                        onClearTestResult = viewModel::clearMlkitTestResult,
                        onCancelOcr = viewModel::cancelOcrTest,  // ✅ ДОБАВЛЕНО
                        onClearMlkitCache = viewModel::clearMlkitCache,
                        onGeminiOcrEnabledChange = viewModel::setGeminiOcrEnabled,
                        onGeminiOcrThresholdChange = viewModel::setGeminiOcrThreshold,
                        onGeminiOcrAlwaysChange = viewModel::setGeminiOcrAlways,
                        onGeminiOcrModelChange = viewModel::setGeminiOcrModel,
                        onTestGeminiFallbackChange = viewModel::setMlkitTestGeminiFallback,
                        onAddApiKey = { key, label -> viewModel.addApiKey(key, label) },
                        onRemoveApiKey = { viewModel.deleteKey(it) },
                        onSetPrimaryApiKey = { viewModel.activateKey(it) },
                        onResetApiKeyErrors = viewModel::resetApiKeyErrors,
                        onTranslationTestTextChange = viewModel::setTranslationTestText,
                        onTranslationSourceLangChange = viewModel::setTranslationSourceLang,
                        onTranslationTargetLangChange = viewModel::setTranslationTargetLang,
                        onTranslationTest = viewModel::testTranslation,
                        onClearTranslationTest = viewModel::clearTranslationTest
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
                            try {
                                val file = File(backup.path)
                                
                                if (!file.exists()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("File not found: ${backup.name}")
                                    }
                                    if (BuildConfig.DEBUG) {
                                        Timber.w("File not found: ${backup.path}")
                                    }
                                    return@BackupSettingsTab
                                }
                                
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
                                
                                if (BuildConfig.DEBUG) {
                                    Timber.d("📤 Sharing: ${backup.name}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Share failed")
                                scope.launch {
                                    snackbarHostState.showSnackbar("Share failed: ${e.message}")
                                }
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
                        onDebugClick = onDebugClick,
                        snackbarHostState = snackbarHostState
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
// TAB COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GeneralSettingsTab(
    apiKeys: List<ApiKeyEntry>,
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
                value = if (appLanguage.isBlank() || appLanguage == "system") "System" else appLanguage.uppercase(),
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

// ✅ ОБНОВЛЕННАЯ ФУНКЦИЯ С ПАРАМЕТРОМ onCancelOcr
@Composable
private fun OcrComponentsTab(
    mlkitSettings: com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState,
    apiKeys: List<ApiKeyEntry>,
    isLoadingKeys: Boolean,
    onScriptModeChange: (com.docs.scanner.data.remote.mlkit.OcrScriptMode) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onConfidenceThresholdChange: (Float) -> Unit,
    onHighlightLowConfidenceChange: (Boolean) -> Unit,
    onShowWordConfidencesChange: (Boolean) -> Unit,
    onImageSelected: (android.net.Uri?) -> Unit,
    onTestOcr: () -> Unit,
    onClearTestResult: () -> Unit,
    onCancelOcr: () -> Unit,  // ✅ ДОБАВЛЕНО
    onClearMlkitCache: () -> Unit,
    onGeminiOcrEnabledChange: (Boolean) -> Unit,
    onGeminiOcrThresholdChange: (Int) -> Unit,
    onGeminiOcrAlwaysChange: (Boolean) -> Unit,
    onGeminiOcrModelChange: (String) -> Unit,
    onTestGeminiFallbackChange: (Boolean) -> Unit,
    onAddApiKey: (key: String, label: String) -> Unit,
    onRemoveApiKey: (key: String) -> Unit,
    onSetPrimaryApiKey: (key: String) -> Unit,
    onResetApiKeyErrors: () -> Unit,
    onTranslationTestTextChange: (String) -> Unit,
    onTranslationSourceLangChange: (Language) -> Unit,
    onTranslationTargetLangChange: (Language) -> Unit,
    onTranslationTest: () -> Unit,
    onClearTranslationTest: () -> Unit
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
            apiKeys = apiKeys,
            isLoadingKeys = isLoadingKeys,
            onScriptModeChange = onScriptModeChange,
            onAutoDetectChange = onAutoDetectChange,
            onConfidenceThresholdChange = onConfidenceThresholdChange,
            onHighlightLowConfidenceChange = onHighlightLowConfidenceChange,
            onShowWordConfidencesChange = onShowWordConfidencesChange,
            onImageSelected = onImageSelected,
            onTestOcr = onTestOcr,
            onClearTestResult = onClearTestResult,
            onCancelOcr = onCancelOcr,  // ✅ ПЕРЕДАЕМ
            onGeminiOcrEnabledChange = onGeminiOcrEnabledChange,
            onGeminiOcrThresholdChange = onGeminiOcrThresholdChange,
            onGeminiOcrAlwaysChange = onGeminiOcrAlwaysChange,
            onGeminiOcrModelChange = onGeminiOcrModelChange,
            onTestGeminiFallbackChange = onTestGeminiFallbackChange,
            onAddApiKey = onAddApiKey,
            onRemoveApiKey = onRemoveApiKey,
            onSetPrimaryApiKey = onSetPrimaryApiKey,
            onResetApiKeyErrors = onResetApiKeyErrors
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        TranslationTestSection(
            state = mlkitSettings,
            onTextChange = onTranslationTestTextChange,
            onSourceLangChange = onTranslationSourceLangChange,
            onTargetLangChange = onTranslationTargetLangChange,
            onTranslate = onTranslationTest,
            onClear = onClearTranslationTest
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

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
                    text = "Frees memory by releasing cached ML Kit recognizers",style = MaterialTheme.typography.bodySmall,
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
private fun DebugSettingsTab(
    onDebugClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val logCollector = remember { LogcatCollector.getInstance(context) }
    var isCollecting by remember { mutableStateOf(logCollector.isCollecting()) }
    var collectedLines by remember { mutableStateOf(0) }

    // Real-time line counter
    LaunchedEffect(isCollecting) {
        while (isCollecting) {
            collectedLines = logCollector.getCollectedLinesCount()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // OCR Log Collector
        SettingsCard(title = "OCR Log Collector", icon = Icons.Default.BugReport) {
            Text(
                "Capture real-time logs to diagnose OCR/MLKit issues",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isCollecting) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isCollecting) "Collecting..." else "Stopped",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                
                if (isCollecting || collectedLines > 0) {
                    Text(
                        text = "$collectedLines lines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Control buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isCollecting) {
                            logCollector.stopCollecting()
                            isCollecting = false
                        } else {
                            logCollector.startCollecting()
                            isCollecting = true
                            collectedLines = 0
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCollecting) "Stop" else "Start")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val lines = logCollector.getCollectedLinesCount()
                            if (lines == 0) {
                                snackbarHostState.showSnackbar(
                                    "⚠️ No logs collected. Press START first.",
                                    duration = SnackbarDuration.Short
                                )
                                return@launch
                            }
                            
                            logCollector.saveLogsNow()
                            delay(500)
                            
                            snackbarHostState.showSnackbar(
                                "✅ $lines lines saved to Downloads/DocumentScanner_OCR_Logs/",
                                duration = SnackbarDuration.Long
                            )
                        }
                    },
                    enabled = collectedLines > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Instructions
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "💡 How to use:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1. Click START before testing OCR\n" +
                        "2. Reproduce the issue (scan image)\n" +
                        "3. Click SAVE to export logs\n" +
                        "4. Find file in Downloads folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Historical Debug Logs
        SettingsCard(title = "Debug Logs Viewer", icon = Icons.Default.Article) {
            Text(
                "View historical debug logs and session data",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDebugClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Debug Viewer")
            }
        }

        // App Info
        SettingsCard(title = "App Info", icon = Icons.Default.Info) {
            InfoRow("Version", BuildConfig.VERSION_NAME)
            InfoRow("Build", BuildConfig.VERSION_CODE.toString())
            InfoRow("Package", BuildConfig.APPLICATION_ID)
            InfoRow("Debug Mode", if (BuildConfig.DEBUG) "ON" else "OFF")
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
private fun ApiKeyItem(
    key: ApiKeyEntry,
    onActivate: (String) -> Unit,
    onCopy: () -> Unit,
    onDelete: (String) -> Unit,
    onTest: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (key.isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = key.label.ifBlank { "API Key" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = key.maskedKey,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                if (key.isActive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Active") },
                        leadingIcon = { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!key.isActive) {
                    FilledTonalButton(
                        onClick = { onActivate(key.key) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Activate") }
                }
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                }
                OutlinedButton(onClick = { onTest(key.key) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Verified, null, modifier = Modifier.size(16.dp))
                }
                OutlinedButton(onClick = { onDelete(key.key) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                }
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
// DIALOG COMPOSABLES
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { days = (days - 1).coerceIn(1, 365) }) { 
                        Text("-") 
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "$days days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = { days = (days + 1).coerceIn(1, 365) }) { 
                        Text("+") 
                    }
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
                if (!merge) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Existing data will be replaced",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(merge) }) { 
                Text(if (merge) "Merge" else "Replace") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)