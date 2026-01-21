/*
 * SettingsScreen.kt
 * Version: 19.1 FINAL - UX REORGANIZATION 2026
 * 
 * ✅ ИНСТРУКЦИЯ: Скопируйте все 3 части подряд в один файл
 * 
 * ЧАСТЬ 1 из 3: Импорты + Main Screen
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
// ✅ КОНЕЦ ЧАСТИ 1 из 3
// Продолжайте копировать ЧАСТЬ 2 (Tab Composables)
// ═══════════════════════════════════════════════════════════════════════════════
// ═══════════════════════════════════════════════════════════════════════════════
// ✅ ЧАСТЬ 2 из 3: Tab Composables
// Добавьте это СРАЗУ ПОСЛЕ части 1 (без пробела между ними)
// ═══════════════════════════════════════════════════════════════════════════════

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
                Slider(mlkitSettings.confidenceThreshold, onConfidenceThresholdChange, valueRange = 0.3f..0.95f, steps = 12)
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
                Switch(autoTranslate, onAutoTranslateChange)
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(16.dp)) {
        OcrTestCard(mlkitSettings, onImageSelected, onTestOcr, onClearTestResult, onCancelOcr, onTestGeminiFallbackChange)
        TranslationTestSection(mlkitSettings, onTranslationTestTextChange, onTranslationSourceLangChange, onTranslationTargetLangChange, onTranslationTest, onClearTranslationTest)
        
        SettingsCard(title = "Debug Tools", icon = Icons.Default.BugReport) {
            Text("OCR Log Collector", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Capture real-time logs to diagnose OCR/MLKit issues", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(if (isCollecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCollecting) "Collecting..." else "Stopped", style = MaterialTheme.typography.labelLarge)
                }
                if (isCollecting || collectedLines > 0) {
                    Text("$collectedLines lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ if (isCollecting) { logCollector.stopCollecting(); isCollecting = false } else { logCollector.startCollecting(); isCollecting = true; collectedLines = 0 } }, Modifier.weight(1f)) {
                    Icon(if (isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCollecting) "Stop" else "Start")
                }
                OutlinedButton({
                    scope.launch {
                        if (logCollector.getCollectedLinesCount() == 0) {
                            snackbarHostState.showSnackbar("⚠️ No logs collected. Press START first.", duration = SnackbarDuration.Short)
                            return@launch
                        }
                        logCollector.saveLogsNow()
                        delay(500)
                        snackbarHostState.showSnackbar("✅ ${logCollector.getCollectedLinesCount()} lines saved to Downloads/DocumentScanner_OCR_Logs/", duration = SnackbarDuration.Long)
                    }
                }, collectedLines > 0, Modifier.weight(1f)) {
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
            Button(onDebugClick, Modifier.fillMaxWidth()) {
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
            OutlinedButton({ imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) {
                Icon(Icons.Default.Image, null)
                Spacer(Modifier.width(8.dp))
                Text("Select Image")
            }
            if (mlkitSettings.isTestRunning) {
                OutlinedButton(onCancelOcr, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
            } else {
                Button(onTestOcr, mlkitSettings.selectedImageUri != null, Modifier.weight(1f)) {
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
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), Alignment.Center) {
                                    Column(Alignment.CenterHorizontally, Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(color = Color.White)
                                        Text("Processing OCR...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            IconButton({ onImageSelected(null); onClearTestResult() }, Modifier.align(Alignment.TopEnd).padding(8.dp).background(MaterialTheme.colorScheme.surface.copy(0.8f), RoundedCornerShape(50))) {
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
                        Checkbox(mlkitSettings.testGeminiFallback, onTestGeminiFallbackChange)
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(16.dp)) {
        SettingsCard(title = "Appearance", icon = Icons.Default.Palette) {
            SettingDropdown("Theme", themeMode.name, ThemeMode.entries.map { it.name }) { onThemeModeChange(ThemeMode.valueOf(it)) }
            Spacer(Modifier.height(12.dp))
            SettingDropdown("App Language", if (appLanguage.isBlank() || appLanguage == "system") "System" else appLanguage.uppercase(), listOf("System", "EN", "RU", "ES", "DE", "FR", "IT", "PT", "ZH")) { onAppLanguageChange(if (it == "System") "" else it.lowercase()) }
        }

        SettingsCard(title = "Translation Cache", icon = Icons.Default.Cached) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Enable cache"); Switch(cacheEnabled, onCacheEnabledChange) }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("TTL (days): $cacheTtlDays")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onCacheTtlChange((cacheTtlDays - 1).coerceIn(1, 365)) }) { Text("-") }
                    OutlinedButton({ onCacheTtlChange((cacheTtlDays + 1).coerceIn(1, 365)) }) { Text("+") }
                }
            }
            cacheStats?.let { s ->
                Spacer(Modifier.height(8.dp))
                Text("Entries: ${s.totalEntries} • Size: ${(s.totalSizeBytes / (1024.0 * 1024.0)).format(2)} MB", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onRefreshCacheStats, Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Refresh") }
                OutlinedButton(onClearCache, Modifier.weight(1f)) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClearOldCache, Modifier.fillMaxWidth()) { Icon(Icons.Default.AutoDelete, null); Spacer(Modifier.width(8.dp)); Text("Clear old entries...") }
        }

        SettingsCard(title = "Storage", icon = Icons.Default.Storage) {
            Text(storageUsage?.formatTotal() ?: "Calculating...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onRefreshStorage, Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Refresh") }
                OutlinedButton(onClearTemp, Modifier.weight(1f)) { Icon(Icons.Default.CleaningServices, null); Spacer(Modifier.width(4.dp)); Text("Clear temp") }
            }
        }

        SettingsCard(title = "Image Quality", icon = Icons.Default.HighQuality) {
            SettingDropdown("Quality preset", "Select quality", ImageQuality.entries.map { it.name }) { onImageQualityChange(ImageQuality.valueOf(it)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ✅ КОНЕЦ ЧАСТИ 2 из 3
// Продолжайте копировать ЧАСТЬ 3 (BackupTab + Helper Functions + Dialogs)
// ═══════════════════════════════════════════════════════════════════════════════
// ═══════════════════════════════════════════════════════════════════════════════
// ✅ ЧАСТЬ 3 из 3 FINAL: BackupTab + Helper Functions + Dialogs
// Добавьте это СРАЗУ ПОСЛЕ части 2 (без пробела между ними)
// ═══════════════════════════════════════════════════════════════════════════════

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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(16.dp)) {
        SettingsCard(title = "Local Backup", icon = Icons.Default.Save) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Include images")
                Switch(includeImages, onIncludeImagesChange)
            }
            Spacer(Modifier.height(12.dp))
            Button(onCreateLocalBackup, !isBackingUp, Modifier.fillMaxWidth()) {
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
                OutlinedButton(onSignInDrive, driveEmail == null && !isBackingUp, Modifier.weight(1f)) { Text("Connect") }
                OutlinedButton(onSignOutDrive, driveEmail != null && !isBackingUp, Modifier.weight(1f)) { Text("Disconnect") }
            }
            if (driveEmail != null) {
                Spacer(Modifier.height(16.dp))
                Button(onUploadToDrive, !isBackingUp, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload to Drive")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onRefreshDriveBackups, !isBackingUp, Modifier.fillMaxWidth()) {
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
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) {
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
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(8.dp)) {
        com.docs.scanner.data.remote.mlkit.OcrScriptMode.entries.forEach { mode ->
            FilterChip(mode == selectedMode, { onModeSelected(mode) }, { Text(mode.displayName) }, if (mode == selectedMode) {{ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }} else null)
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
        Switch(checked, onCheckedChange)
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
                OutlinedButton(onRestore, Modifier.weight(1f)) { Icon(Icons.Default.Restore, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Restore") }
                OutlinedButton(onShare, Modifier.weight(1f)) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Share") }
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
                OutlinedButton(onRestore, Modifier.weight(1f)) { Icon(Icons.Default.Restore, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Restore") }
                OutlinedButton(onDelete, Modifier.weight(1f)) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Delete") }
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
    AlertDialog(onDismiss, { Text("Add Gemini API Key") }, {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(label, { label = it }, { Text("Label (optional)") }, Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(key, { key = it }, { Text("API Key") }, Modifier.fillMaxWidth())
        }
    }, { TextButton({ onSave(key, label.ifBlank { null }) }, key.isNotBlank()) { Text("Save") } }, {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ onTest(key) }, key.isNotBlank()) { Text("Test") }
            TextButton(onDismiss) { Text("Cancel") }
        }
    })
}

@Composable
private fun ClearOldCacheDialog(initialDays: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var days by remember { mutableStateOf(initialDays) }
    AlertDialog(onDismiss, { Text("Clear Old Cache") }, {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Delete entries older than $days days.")
            Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                OutlinedButton({ days = (days - 1).coerceIn(1, 365) }) { Text("-") }
                Spacer(Modifier.width(16.dp))
                Text("$days days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                OutlinedButton({ days = (days + 1).coerceIn(1, 365) }) { Text("+") }
            }
        }
    }, { TextButton({ onConfirm(days) }) { Text("Delete") } }, { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun RestoreBackupDialog(backupName: String, onDismiss: () -> Unit, onConfirm: (merge: Boolean) -> Unit) {
    var merge by remember { mutableStateOf(false) }
    AlertDialog(onDismiss, { Text("Restore Backup") }, {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Restore from: $backupName")
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Merge into existing data")
                Switch(merge, { merge = it })
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
    }, { TextButton({ onConfirm(merge) }) { Text(if (merge) "Merge" else "Replace") } }, { TextButton(onDismiss) { Text("Cancel") } })
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

data class LocalBackup(val name: String, val path: String, val sizeBytes: Long, val lastModified: Long)

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

// ═══════════════════════════════════════════════════════════════════════════════
// ✅ КОНЕЦ ЧАСТИ 3 из 3
// 
// 🎉 ПОЗДРАВЛЯЕМ! Файл SettingsScreen.kt собран полностью.
// 
// Теперь:
// 1. Замените TranslationTestSection.kt файлом из скачанных
// 2. Скомпилируйте проект
// 3. Наслаждайтесь новой структурой настроек!
// ═══════════════════════════════════════════════════════════════════════════════
