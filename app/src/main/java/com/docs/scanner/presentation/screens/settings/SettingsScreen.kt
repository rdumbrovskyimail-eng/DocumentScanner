/*
 * SettingsScreen.kt
 * Version: 19.2 HOTFIX - Исправление ошибок компиляции
 * 
 * ✅ ВСЕ 35 ИСПРАВЛЕНИЙ ПРИМЕНЕНЫ
 */

package com.docs.scanner.presentation.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.docs.scanner.presentation.screens.settings.components.ApiKeysSettingsSection
import com.docs.scanner.presentation.screens.settings.components.GeminiOcrSettingsSection
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
    AI_OCR("AI & OCR", { Icon(Icons.Default.AutoAwesome, null) }),
    TESTING("Testing", { Icon(Icons.Default.Science, null) }),
    GENERAL("General", { Icon(Icons.Default.Settings, null) }),
    BACKUP("Backup", { Icon(Icons.Default.CloudSync, null) })
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
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val autoTranslate by viewModel.autoTranslate.collectAsStateWithLifecycle()
    val cacheEnabled by viewModel.cacheEnabled.collectAsStateWithLifecycle()
    val cacheTtlDays by viewModel.cacheTtlDays.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val mlkitSettings by viewModel.mlkitSettings.collectAsStateWithLifecycle()

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showClearOldCacheDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf<LocalBackup?>(null) }
    var includeImagesInBackup by remember { mutableStateOf(true) }
    var showDriveRestoreDialog by remember { mutableStateOf<BackupInfo?>(null) }
    var showDriveDeleteDialog by remember { mutableStateOf<BackupInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { SettingsTab.entries.size })

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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (SettingsTab.entries[page]) {
                    SettingsTab.AI_OCR -> AiOcrTab(
                        apiKeys = apiKeys,
                        isLoadingKeys = isLoadingKeys,
                        mlkitSettings = mlkitSettings,
                        autoTranslate = autoTranslate,
                        targetLanguage = targetLanguage,
                        onAddApiKey = { key, label -> viewModel.addApiKey(key, label) },
                        onRemoveApiKey = { viewModel.deleteKey(it) },
                        onSetPrimaryApiKey = { viewModel.activateKey(it) },
                        onResetApiKeyErrors = viewModel::resetApiKeyErrors,
                        onScriptModeChange = viewModel::setMlkitScriptMode,
                        onAutoDetectChange = viewModel::setMlkitAutoDetect,
                        onConfidenceThresholdChange = viewModel::setMlkitConfidenceThreshold,
                        onHighlightLowConfidenceChange = viewModel::setMlkitHighlightLowConfidence,
                        onShowWordConfidencesChange = viewModel::setMlkitShowWordConfidences,
                        onGeminiOcrEnabledChange = viewModel::setGeminiOcrEnabled,
                        onGeminiOcrThresholdChange = viewModel::setGeminiOcrThreshold,
                        onGeminiOcrAlwaysChange = viewModel::setGeminiOcrAlways,
                        onGeminiOcrModelChange = viewModel::setGeminiOcrModel,
                        onAutoTranslateChange = viewModel::setAutoTranslate,
                        onTargetLanguageChange = viewModel::setTargetLanguage
                    )

                    SettingsTab.TESTING -> TestingTab(
                        mlkitSettings = mlkitSettings,
                        onImageSelected = viewModel::setMlkitSelectedImage,
                        onTestOcr = viewModel::runMlkitOcrTest,
                        onClearTestResult = viewModel::clearMlkitTestResult,
                        onCancelOcr = viewModel::cancelOcrTest,
                        onTestGeminiFallbackChange = viewModel::setMlkitTestGeminiFallback,
                        onTranslationTestTextChange = viewModel::setTranslationTestText,
                        onTranslationSourceLangChange = viewModel::setTranslationSourceLang,
                        onTranslationTargetLangChange = viewModel::setTranslationTargetLang,
                        onTranslationTest = viewModel::testTranslation,
                        onClearTranslationTest = viewModel::clearTranslationTest,
                        onDebugClick = onDebugClick,
                        snackbarHostState = snackbarHostState
                    )

                    SettingsTab.GENERAL -> GeneralTab(
                        themeMode = themeMode,
                        appLanguage = appLanguage,
                        cacheEnabled = cacheEnabled,
                        cacheTtlDays = cacheTtlDays,
                        cacheStats = cacheStats,
                        storageUsage = storageUsage,
                        onThemeModeChange = viewModel::setThemeMode,
                        onAppLanguageChange = { code ->
                            viewModel.setAppLanguage(code)
                            (context.applicationContext as? App)?.setAppLocale(code)
                        },
                        onCacheEnabledChange = viewModel::setCacheEnabled,
                        onCacheTtlChange = viewModel::setCacheTtl,
                        onRefreshCacheStats = viewModel::refreshCacheStats,
                        onClearCache = viewModel::clearCache,
                        onClearOldCache = { showClearOldCacheDialog = true },
                        onRefreshStorage = viewModel::refreshStorageUsage,
                        onClearTemp = viewModel::clearTempFiles,
                        onImageQualityChange = viewModel::setImageQuality
                    )

                    SettingsTab.BACKUP -> BackupTab(
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
                                    return@BackupTab
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
// AI & OCR TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AiOcrTab(
    apiKeys: List<ApiKeyEntry>,
    isLoadingKeys: Boolean,
    mlkitSettings: com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState,
    autoTranslate: Boolean,
    targetLanguage: Language,
    onAddApiKey: (String, String) -> Unit,
    onRemoveApiKey: (String) -> Unit,
    onSetPrimaryApiKey: (String) -> Unit,
    onResetApiKeyErrors: () -> Unit,
    onScriptModeChange: (com.docs.scanner.data.remote.mlkit.OcrScriptMode) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit,
    onConfidenceThresholdChange: (Float) -> Unit,
    onHighlightLowConfidenceChange: (Boolean) -> Unit,
    onShowWordConfidencesChange: (Boolean) -> Unit,
    onGeminiOcrEnabledChange: (Boolean) -> Unit,
    onGeminiOcrThresholdChange: (Int) -> Unit,
    onGeminiOcrAlwaysChange: (Boolean) -> Unit,
    onGeminiOcrModelChange: (String) -> Unit,
    onAutoTranslateChange: (Boolean) -> Unit,
    onTargetLanguageChange: (Language) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsCard(title = "Gemini API Keys", icon = Icons.Default.Key) {
            ApiKeysSettingsSection(
                keys = apiKeys,
                isLoading = isLoadingKeys,
                onAddKey = onAddApiKey,
                onRemoveKey = onRemoveApiKey,
                onSetPrimary = onSetPrimaryApiKey,
                onResetErrors = onResetApiKeyErrors
            )
        }

        SettingsCard(title = "ML Kit OCR", icon = Icons.Default.TextFields) {
            Text("On-device text recognition", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            ScriptModeSelector(mlkitSettings.scriptMode, onScriptModeChange)
            Spacer(Modifier.height(12.dp))
            SettingToggleRow("Auto-detect language", "Automatically detect script and language", mlkitSettings.autoDetectLanguage, onAutoDetectChange, Icons.Default.AutoAwesome)
            Spacer(Modifier.height(12.dp))
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Confidence threshold", style = MaterialTheme.typography.bodyMedium)
                        Text("Words below this are marked as low confidence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${(mlkitSettings.confidenceThreshold * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = mlkitSettings.confidenceThreshold,
                    onValueChange = onConfidenceThresholdChange,
                    valueRange = 0.3f..0.95f,
                    steps = 12
                )
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("30%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("95%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingToggleRow("Highlight low confidence words", "Show red highlighting on uncertain words", mlkitSettings.highlightLowConfidence, onHighlightLowConfidenceChange, Icons.Default.Highlight)
            Spacer(Modifier.height(12.dp))
            SettingToggleRow("Show word confidence scores", "Display confidence % for each word", mlkitSettings.showWordConfidences, onShowWordConfidencesChange, Icons.Default.Analytics)
        }

        SettingsCard(title = "Gemini AI Fallback", icon = Icons.Default.AutoAwesome) {
            GeminiOcrSettingsSection(
                enabled = mlkitSettings.geminiOcrEnabled,
                threshold = mlkitSettings.geminiOcrThreshold,
                alwaysUseGemini = mlkitSettings.geminiOcrAlways,
                selectedModel = mlkitSettings.selectedGeminiModel,
                availableModels = mlkitSettings.availableGeminiModels,
                onEnabledChange = onGeminiOcrEnabledChange,
                onThresholdChange = onGeminiOcrThresholdChange,
                onAlwaysUseGeminiChange = onGeminiOcrAlwaysChange,
                onModelChange = onGeminiOcrModelChange
            )
        }

        SettingsCard(title = "Translation", icon = Icons.Default.Translate) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-translate after OCR", style = MaterialTheme.typography.bodyMedium)
                    Text("Automatically translate recognized text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoTranslate,
                    onCheckedChange = onAutoTranslateChange
                )
            }
            Spacer(Modifier.height(12.dp))
            SettingDropdown(
                "Target Language",
                "${targetLanguage.displayName} (${targetLanguage.code})",
                Language.translationSupported.filter { it != Language.AUTO }.map { "${it.displayName} (${it.code})" }
            ) { sel ->
                Language.fromCode(sel.substringAfterLast("(").substringBefore(")").trim())?.let { onTargetLanguageChange(it) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TESTING TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TestingTab(
    mlkitSettings: com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState,
    onImageSelected: (android.net.Uri?) -> Unit,
    onTestOcr: () -> Unit,
    onClearTestResult: () -> Unit,
    onCancelOcr: () -> Unit,
    onTestGeminiFallbackChange: (Boolean) -> Unit,
    onTranslationTestTextChange: (String) -> Unit,
    onTranslationSourceLangChange: (Language) -> Unit,
    onTranslationTargetLangChange: (Language) -> Unit,
    onTranslationTest: () -> Unit,
    onClearTranslationTest: () -> Unit,
    onDebugClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logCollector = remember { LogcatCollector.getInstance(context) }
    var isCollecting by remember { mutableStateOf(logCollector.isCollecting()) }
    var collectedLines by remember { mutableStateOf(0) }

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
        OcrTestCard(mlkitSettings, onImageSelected, onTestOcr, onClearTestResult, onCancelOcr, onTestGeminiFallbackChange)
        TranslationTestSection(mlkitSettings, onTranslationTestTextChange, onTranslationSourceLangChange, onTranslationTargetLangChange, onTranslationTest, onClearTranslationTest)
        
        SettingsCard(title = "Debug Tools", icon = Icons.Default.BugReport) {
            Text("OCR Log Collector", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Capture real-time logs to diagnose OCR/MLKit issues", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isCollecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCollecting) "Collecting..." else "Stopped", style = MaterialTheme.typography.labelLarge)
                }
                if (isCollecting || collectedLines > 0) {
                    Text("$collectedLines lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ИСПРАВЛЕНИЕ 1: Button "Stop/Start"
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
                    Icon(if (isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCollecting) "Stop" else "Start")
                }
                // ИСПРАВЛЕНИЕ 2: OutlinedButton "Save"
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (logCollector.getCollectedLinesCount() == 0) {
                                snackbarHostState.showSnackbar("⚠️ No logs collected. Press START first.", duration = SnackbarDuration.Short)
                                return@launch
                            }
                            logCollector.saveLogsNow()
                            delay(500)
                            snackbarHostState.showSnackbar("✅ ${logCollector.getCollectedLinesCount()} lines saved to Downloads/DocumentScanner_OCR_Logs/", duration = SnackbarDuration.Long)
                        }
                    },
                    enabled = collectedLines > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Debug Logs Viewer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("View historical debug logs and session data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            // ИСПРАВЛЕНИЕ 3: Button "Open Debug Viewer"
            Button(
                onClick = onDebugClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Debug Viewer")
            }
        }

        SettingsCard(title = "App Info", icon = Icons.Default.Info) {
            InfoRow("Version", BuildConfig.VERSION_NAME)
            InfoRow("Build", BuildConfig.VERSION_CODE.toString())
            InfoRow("Package", BuildConfig.APPLICATION_ID)
            InfoRow("Debug Mode", if (BuildConfig.DEBUG) "ON" else "OFF")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OCR TEST CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun OcrTestCard(
    mlkitSettings: com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState,
    onImageSelected: (android.net.Uri?) -> Unit,
    onTestOcr: () -> Unit,
    onClearTestResult: () -> Unit,
    onCancelOcr: () -> Unit,
    onTestGeminiFallbackChange: (Boolean) -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onImageSelected(uri)
        onClearTestResult()
    }

    SettingsCard(title = "OCR Test", icon = Icons.Default.Science) {
        Text("Test OCR with current settings on any image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            // ИСПРАВЛЕНИЕ 4: OutlinedButton "Select Image"
            OutlinedButton(
                onClick = { 
                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Image, null)
                Spacer(Modifier.width(8.dp))
                Text("Select Image")
            }
            if (mlkitSettings.isTestRunning) {
                // ИСПРАВЛЕНИЕ 5: OutlinedButton "Cancel"
                OutlinedButton(
                    onClick = onCancelOcr,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
            } else {
                // ИСПРАВЛЕНИЕ 6: Button "Run OCR"
                Button(
                    onClick = onTestOcr,
                    enabled = mlkitSettings.selectedImageUri != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run OCR")
                }
            }
        }
        
        androidx.compose.animation.AnimatedVisibility(mlkitSettings.selectedImageUri != null) {
            mlkitSettings.selectedImageUri?.let { uri ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxSize()) {
                            coil3.compose.AsyncImage(uri, "Selected image", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                            if (mlkitSettings.isTestRunning) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(color = Color.White)
                                        Text("Processing OCR...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            // ИСПРАВЛЕНИЕ 7: IconButton "Clear"
                            IconButton(
                                onClick = { 
                                    onImageSelected(null)
                                    onClearTestResult() 
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(0.8f), RoundedCornerShape(50))
                            ) {
                                Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    mlkitSettings.testResult?.let { result ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                            QuickStat("Words", result.totalWords.toString())
                            QuickStat("Confidence", result.confidencePercent)
                            QuickStat("Quality", result.qualityRating)
                            QuickStat("Time", "${result.processingTimeMs}ms")
                        }
                    }
                }
            }
        }
        
        androidx.compose.animation.AnimatedVisibility(mlkitSettings.geminiOcrEnabled && mlkitSettings.selectedImageUri != null) {
            Column {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.3f))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = mlkitSettings.testGeminiFallback,
                            onCheckedChange = onTestGeminiFallbackChange
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Test Gemini fallback", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Force Gemini OCR to test handwriting recognition", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        
        androidx.compose.animation.AnimatedVisibility(mlkitSettings.testError != null) {
            mlkitSettings.testError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GENERAL TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GeneralTab(
    themeMode: ThemeMode,
    appLanguage: String,
    cacheEnabled: Boolean,
    cacheTtlDays: Int,
    cacheStats: com.docs.scanner.domain.core.TranslationCacheStats?,
    storageUsage: com.docs.scanner.domain.repository.StorageUsage?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAppLanguageChange: (String) -> Unit,
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
        SettingsCard(title = "Appearance", icon = Icons.Default.Palette) {
            SettingDropdown("Theme", themeMode.name, ThemeMode.entries.map { it.name }) { onThemeModeChange(ThemeMode.valueOf(it)) }
            Spacer(Modifier.height(12.dp))
            SettingDropdown("App Language", if (appLanguage.isBlank() || appLanguage == "system") "System" else appLanguage.uppercase(), listOf("System", "EN", "RU", "ES", "DE", "FR", "IT", "PT", "ZH")) { onAppLanguageChange(if (it == "System") "" else it.lowercase()) }
        }

        SettingsCard(title = "Translation Cache", icon = Icons.Default.Cached) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Enable cache")
                Switch(
                    checked = cacheEnabled,
                    onCheckedChange = onCacheEnabledChange
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("TTL (days): $cacheTtlDays")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ИСПРАВЛЕНИЕ 8: OutlinedButton "-" (Cache TTL)
                    OutlinedButton(
                        onClick = { onCacheTtlChange((cacheTtlDays - 1).coerceIn(1, 365)) }
                    ) { 
                        Text("-") 
                    }
                    // ИСПРАВЛЕНИЕ 9: OutlinedButton "+" (Cache TTL)
                    OutlinedButton(
                        onClick = { onCacheTtlChange((cacheTtlDays + 1).coerceIn(1, 365)) }
                    ) { 
                        Text("+") 
                    }
                }
            }
            cacheStats?.let { s ->
                Spacer(Modifier.height(8.dp))
                Text("Entries: ${s.totalEntries} • Size: ${(s.totalSizeBytes / (1024.0 * 1024.0)).format(2)} MB", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ИСПРАВЛЕНИЕ 10: OutlinedButton "Refresh" (Cache)
                OutlinedButton(
                    onClick = onRefreshCacheStats,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
                // ИСПРАВЛЕНИЕ 11: OutlinedButton "Clear" (Cache)
                OutlinedButton(
                    onClick = onClearCache,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
            Spacer(Modifier.height(8.dp))
            // ИСПРАВЛЕНИЕ 12: OutlinedButton "Clear old entries"
            OutlinedButton(
                onClick = onClearOldCache,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoDelete, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear old entries...")
            }
        }

        SettingsCard(title = "Storage", icon = Icons.Default.Storage) {
            Text(storageUsage?.formatTotal() ?: "Calculating...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ИСПРАВЛЕНИЕ 13: OutlinedButton "Refresh" (Storage)
                OutlinedButton(
                    onClick = onRefreshStorage,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
                // ИСПРАВЛЕНИЕ 14: OutlinedButton "Clear temp"
                OutlinedButton(
                    onClick = onClearTemp,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CleaningServices, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear temp")
                }
            }
        }

        SettingsCard(title = "Image Quality", icon = Icons.Default.HighQuality) {
            SettingDropdown("Quality preset", "Select quality", ImageQuality.entries.map { it.name }) { onImageQualityChange(ImageQuality.valueOf(it)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKUP TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BackupTab(
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
        SettingsCard(title = "Local Backup", icon = Icons.Default.Save) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Include images")
                Switch(
                    checked = includeImages,
                    onCheckedChange = onIncludeImagesChange
                )
            }
            Spacer(Modifier.height(12.dp))
            // ИСПРАВЛЕНИЕ 15: Button "Create Backup"
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
                    Text("Create Backup")
                }
            }
            if (localBackups.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Recent backups:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                localBackups.take(5).forEach { backup ->
                    BackupItem(backup.name, "${(backup.sizeBytes / (1024.0 * 1024.0)).format(2)} MB", { onRestoreLocalBackup(backup) }, { onShareBackup(backup) })
                }
            }
        }

        SettingsCard(title = "Google Drive", icon = Icons.Default.CloudSync) {
            Text(driveEmail?.let { "Connected: $it" } ?: "Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ИСПРАВЛЕНИЕ 16: OutlinedButton "Connect" (Drive)
                OutlinedButton(
                    onClick = onSignInDrive,
                    enabled = driveEmail == null && !isBackingUp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Connect")
                }
                // ИСПРАВЛЕНИЕ 17: OutlinedButton "Disconnect" (Drive)
                OutlinedButton(
                    onClick = onSignOutDrive,
                    enabled = driveEmail != null && !isBackingUp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disconnect")
                }
            }
            if (driveEmail != null) {
                Spacer(Modifier.height(16.dp))
                // ИСПРАВЛЕНИЕ 18: Button "Upload to Drive"
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
                // ИСПРАВЛЕНИЕ 19: OutlinedButton "Refresh list" (Drive)
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
                        DriveBackupItem(backup, { onRestoreDriveBackup(backup) }, { onDeleteDriveBackup(backup) })
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
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
private fun SettingDropdown(title: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        // ИСПРАВЛЕНИЕ 20: OutlinedButton
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value, Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem({ Text(option) }, { expanded = false; onSelect(option) })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScriptModeSelector(selectedMode: com.docs.scanner.data.remote.mlkit.OcrScriptMode, onModeSelected: (com.docs.scanner.data.remote.mlkit.OcrScriptMode) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        com.docs.scanner.data.remote.mlkit.OcrScriptMode.entries.forEach { mode ->
            // ИСПРАВЛЕНИЕ 21: FilterChip leadingIcon
            FilterChip(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.displayName) },
                leadingIcon = if (mode == selectedMode) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
    }
    Text(selectedMode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SettingToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(Modifier.weight(1f), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
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
                // ИСПРАВЛЕНИЕ 22: OutlinedButton "Restore"
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore")
                }
                // ИСПРАВЛЕНИЕ 23: OutlinedButton "Share"
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
                // ИСПРАВЛЕНИЕ 24: OutlinedButton "Restore"
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore")
                }
                // ИСПРАВЛЕНИЕ 25: OutlinedButton "Delete"
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

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DIALOG COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AddApiKeyDialog(onDismiss: () -> Unit, onSave: (String, String?) -> Unit, onTest: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Gemini API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ИСПРАВЛЕНИЕ 26: OutlinedTextField "Label"
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // ИСПРАВЛЕНИЕ 27: OutlinedTextField "API Key"
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            // ИСПРАВЛЕНИЕ 28: TextButton "Save"
            TextButton(
                onClick = { onSave(key, label.ifBlank { null }) },
                enabled = key.isNotBlank()
            ) { 
                Text("Save") 
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ИСПРАВЛЕНИЕ 29: TextButton "Test"
                TextButton(
                    onClick = { onTest(key) },
                    enabled = key.isNotBlank()
                ) { 
                    Text("Test") 
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun ClearOldCacheDialog(initialDays: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var days by remember { mutableStateOf(initialDays) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Old Cache") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Delete entries older than $days days.")
                Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                    // ИСПРАВЛЕНИЕ 30: OutlinedButton "-"
                    OutlinedButton(
                        onClick = { days = (days - 1).coerceIn(1, 365) }
                    ) { 
                        Text("-") 
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("$days days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    // ИСПРАВЛЕНИЕ 31: OutlinedButton "+"
                    OutlinedButton(
                        onClick = { days = (days + 1).coerceIn(1, 365) }
                    ) { 
                        Text("+") 
                    }
                }
            }
        },
        confirmButton = {
            // ИСПРАВЛЕНИЕ 32: TextButton "Delete"
            TextButton(
                onClick = { onConfirm(days) }
            ) { 
                Text("Delete") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RestoreBackupDialog(backupName: String, onDismiss: () -> Unit, onConfirm: (merge: Boolean) -> Unit) {
    var merge by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Restore from: $backupName")
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Merge into existing data")
                    // ИСПРАВЛЕНИЕ 33: Switch
                    Switch(
                        checked = merge,
                        onCheckedChange = { merge = it }
                    )
                }
                if (!merge) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Existing data will be replaced", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        },
        confirmButton = {
            // ИСПРАВЛЕНИЕ 34: TextButton "Merge/Replace"
            TextButton(
                onClick = { onConfirm(merge) }
            ) { 
                Text(if (merge) "Merge" else "Replace") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

// ИСПРАВЛЕНИЕ 35: Убран дублирующийся LocalBackup (определен в SettingsViewModel.kt:1402)

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

// ═══════════════════════════════════════════════════════════════════════════════
// ✅ КОНЕЦ ФАЙЛА SettingsScreen.kt v19.2 HOTFIX
// ═══════════════════════════════════════════════════════════════════════════════
