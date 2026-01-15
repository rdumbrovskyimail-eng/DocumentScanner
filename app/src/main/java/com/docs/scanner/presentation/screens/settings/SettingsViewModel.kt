/*
 * SettingsViewModel.kt
 * Version: 8.0.0 - PRODUCTION READY 2026
 * 
 * ✅ ALL FEATURES:
 * - API Keys management (encrypted)
 * - Google Drive backup
 * - Theme & Language settings
 * - Cache management
 * - MLKit OCR Settings & Testing
 * - Storage management
 * - Local backup
 */

package com.docs.scanner.presentation.screens.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.local.security.ApiKeyData
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.data.remote.drive.DriveRepository
import com.docs.scanner.data.remote.gemini.GeminiApi
import com.docs.scanner.data.remote.mlkit.MLKitScanner
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.data.remote.mlkit.OcrTestResult
import com.docs.scanner.domain.core.BackupInfo
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.ImageQuality
import com.docs.scanner.domain.core.Language
import com.docs.scanner.domain.core.ThemeMode
import com.docs.scanner.domain.core.TranslationCacheStats
import com.docs.scanner.domain.repository.FileRepository
import com.docs.scanner.domain.repository.SettingsRepository
import com.docs.scanner.domain.repository.StorageUsage
import com.docs.scanner.domain.usecase.AllUseCases
import com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val driveRepository: DriveRepository,
    private val encryptedKeyStorage: EncryptedKeyStorage,
    private val settingsDataStore: SettingsDataStore,
    private val fileRepository: FileRepository,
    private val geminiApi: GeminiApi,
    private val mlKitScanner: MLKitScanner,
    private val useCases: AllUseCases
) : ViewModel() {

    // ═══════════════════════════════════════════════════════════════════════════
    // API KEYS STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private val _apiKeys = MutableStateFlow<List<ApiKeyData>>(emptyList())
    val apiKeys: StateFlow<List<ApiKeyData>> = _apiKeys.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveMessage = MutableStateFlow("")
    val saveMessage: StateFlow<String> = _saveMessage.asStateFlow()

    private val _keyTestMessage = MutableStateFlow("")
    val keyTestMessage: StateFlow<String> = _keyTestMessage.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    // GOOGLE DRIVE STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private val _driveEmail = MutableStateFlow<String?>(null)
    val driveEmail: StateFlow<String?> = _driveEmail.asStateFlow()

    private val _driveBackups = MutableStateFlow<List<BackupInfo>>(emptyList())
    val driveBackups: StateFlow<List<BackupInfo>> = _driveBackups.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _backupMessage = MutableStateFlow("")
    val backupMessage: StateFlow<String> = _backupMessage.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    // APP SETTINGS STATE
    // ═══════════════════════════════════════════════════════════════════════════

    val themeMode: StateFlow<ThemeMode> =
        useCases.settings.observeThemeMode()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val appLanguage: StateFlow<String> =
        useCases.settings.observeAppLanguage()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val autoTranslate: StateFlow<Boolean> =
        useCases.settings.observeAutoTranslate()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val targetLanguage: StateFlow<Language> =
        useCases.settings.observeTargetLanguage()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Language.ENGLISH)

    val ocrMode: StateFlow<String> =
        settingsDataStore.ocrLanguage
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "AUTO")

    val cacheEnabled: StateFlow<Boolean> =
        settingsDataStore.cacheEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val cacheTtlDays: StateFlow<Int> =
        settingsDataStore.cacheTtlDays
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    // ═══════════════════════════════════════════════════════════════════════════
    // STORAGE & CACHE STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private val _storageUsage = MutableStateFlow<StorageUsage?>(null)
    val storageUsage: StateFlow<StorageUsage?> = _storageUsage.asStateFlow()

    private val _cacheStats = MutableStateFlow<TranslationCacheStats?>(null)
    val cacheStats: StateFlow<TranslationCacheStats?> = _cacheStats.asStateFlow()

    private val _localBackups = MutableStateFlow<List<LocalBackup>>(emptyList())
    val localBackups: StateFlow<List<LocalBackup>> = _localBackups.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    // ML KIT SETTINGS STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private val _mlkitSettings = MutableStateFlow(MlkitSettingsState())
    val mlkitSettings: StateFlow<MlkitSettingsState> = _mlkitSettings.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    init {
        checkDriveConnection()
        loadApiKeys()
        refreshCacheStats()
        refreshStorageUsage()
        refreshLocalBackups()
        loadMlkitSettings()
    }

    private fun loadApiKeys() {
        viewModelScope.launch {
            _apiKeys.value = encryptedKeyStorage.getAllKeys()
        }
    }

    private fun loadMlkitSettings() {
        viewModelScope.launch {
            try {
                val scriptMode = when (settingsDataStore.ocrLanguage.first().uppercase()) {
                    "LATIN" -> OcrScriptMode.LATIN
                    "CHINESE" -> OcrScriptMode.CHINESE
                    "JAPANESE" -> OcrScriptMode.JAPANESE
                    "KOREAN" -> OcrScriptMode.KOREAN
                    "DEVANAGARI" -> OcrScriptMode.DEVANAGARI
                    else -> OcrScriptMode.AUTO
                }
                _mlkitSettings.update { it.copy(scriptMode = scriptMode) }
            } catch (e: Exception) {
                Timber.w(e, "⚠️ Failed to load MLKit settings")
            }
        }
    }

    private fun checkDriveConnection() {
        viewModelScope.launch {
            val isConnected = driveRepository.isSignedIn()
            if (isConnected) {
                when (val result = driveRepository.signIn()) {
                    is com.docs.scanner.domain.model.Result.Success -> {
                        _driveEmail.value = result.data
                        refreshDriveBackups()
                    }
                    else -> {
                        _driveEmail.value = null
                        _driveBackups.value = emptyList()
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // API KEYS MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    fun addApiKey(key: String, label: String?) {
        viewModelScope.launch {
            try {
                if (!isValidApiKey(key)) {
                    _saveMessage.value = "✗ Invalid API key format"
                    return@launch
                }
                val trimmedKey = key.trim()
                val newKey = ApiKeyData(
                    id = System.currentTimeMillis().toString(),
                    key = trimmedKey,
                    label = label?.ifBlank { null },
                    isActive = true,
                    createdAt = System.currentTimeMillis()
                )
                encryptedKeyStorage.addKey(newKey)
                encryptedKeyStorage.setActiveApiKey(trimmedKey)
                loadApiKeys()
                _saveMessage.value = "✓ API key added successfully"
            } catch (e: Exception) {
                _saveMessage.value = "✗ Failed to add key: ${e.message}"
            }
        }
    }

    fun activateKey(keyId: String) {
        viewModelScope.launch {
            try {
                val key = _apiKeys.value.find { it.id == keyId }
                if (key != null) {
                    encryptedKeyStorage.setActiveApiKey(key.key)
                    loadApiKeys()
                    _saveMessage.value = "✓ API key activated"
                } else {
                    _saveMessage.value = "✗ Key not found"
                }
            } catch (e: Exception) {
                _saveMessage.value = "✗ Failed to activate key: ${e.message}"
            }
        }
    }

    fun deleteKey(keyId: String) {
        viewModelScope.launch {
            try {
                encryptedKeyStorage.deleteKey(keyId)
                loadApiKeys()
                _saveMessage.value = "✓ API key deleted"
            } catch (e: Exception) {
                _saveMessage.value = "✗ Failed to delete key: ${e.message}"
            }
        }
    }

    fun copyApiKey(context: Context, key: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("API Key", key)
            clipboard.setPrimaryClip(clip)
            _saveMessage.value = "✓ API key copied to clipboard"
        } catch (e: Exception) {
            _saveMessage.value = "✗ Failed to copy: ${e.message}"
        }
    }

    fun testApiKey(keyId: String) {
        val key = _apiKeys.value.find { it.id == keyId }?.key ?: return
        testApiKeyRaw(key)
    }

    fun testApiKeyRaw(key: String) {
        viewModelScope.launch {
            _keyTestMessage.value = "Testing key..."
            when (
                val result = geminiApi.generateText(
                    apiKey = key.trim(),
                    prompt = "Reply with: OK",
                    model = "gemini-2.5-flash-lite",
                    fallbackModels = listOf("gemini-1.5-flash")
                )
            ) {
                is DomainResult.Success -> _keyTestMessage.value = "✓ OK: ${result.data.take(80)}"
                is DomainResult.Failure -> _keyTestMessage.value = "✗ Failed: ${result.error.message}"
            }
        }
    }

    fun clearMessages() {
        _saveMessage.value = ""
        _backupMessage.value = ""
        _keyTestMessage.value = ""
    }

    private fun isValidApiKey(key: String): Boolean = key.matches(Regex("^AIza[A-Za-z0-9_-]{35}$"))

    // ═══════════════════════════════════════════════════════════════════════════
    // APP SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            when (val r = useCases.settings.setThemeMode(mode)) {
                is DomainResult.Failure -> _saveMessage.value = "✗ Theme: ${r.error.message}"
                else -> Unit
            }
        }
    }

    fun setAppLanguage(code: String) {
        viewModelScope.launch {
            when (val r = useCases.settings.setAppLanguage(code)) {
                is DomainResult.Failure -> _saveMessage.value = "✗ Language: ${r.error.message}"
                else -> Unit
            }
        }
    }

    fun setOcrMode(mode: String) {
        viewModelScope.launch {
            runCatching { settingsDataStore.setOcrLanguage(mode) }
                .onFailure { _saveMessage.value = "✗ OCR: ${it.message}" }
        }
    }

    fun setTargetLanguage(lang: Language) {
        viewModelScope.launch {
            when (val r = useCases.settings.setTargetLanguage(lang)) {
                is DomainResult.Failure -> _saveMessage.value = "✗ Target: ${r.error.message}"
                else -> Unit
            }
        }
    }

    fun setAutoTranslate(enabled: Boolean) {
        viewModelScope.launch {
            when (val r = useCases.settings.setAutoTranslate(enabled)) {
                is DomainResult.Failure -> _saveMessage.value = "✗ Auto-translate: ${r.error.message}"
                else -> Unit
            }
        }
    }

    fun setCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsDataStore.setCacheEnabled(enabled) }
                .onFailure { _saveMessage.value = "✗ Cache: ${it.message}" }
        }
    }

    fun setCacheTtl(days: Int) {
        viewModelScope.launch {
            runCatching { settingsDataStore.setCacheTtl(days) }
                .onFailure { _saveMessage.value = "✗ Cache TTL: ${it.message}" }
        }
    }

    fun setImageQuality(quality: ImageQuality) {
        viewModelScope.launch {
            when (val r = useCases.settings.setImageQuality(quality)) {
                is DomainResult.Success -> _saveMessage.value = "✓ Image quality: ${quality.name}"
                is DomainResult.Failure -> _saveMessage.value = "✗ Image quality: ${r.error.message}"
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHE & STORAGE
    // ═══════════════════════════════════════════════════════════════════════════

    fun refreshCacheStats() {
        viewModelScope.launch {
            _cacheStats.value = runCatching { useCases.translation.getCacheStats() }.getOrNull()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            when (val r = useCases.translation.clearCache()) {
                is DomainResult.Success -> {
                    _saveMessage.value = "✓ Cache cleared"
                    refreshCacheStats()
                }
                is DomainResult.Failure -> _saveMessage.value = "✗ Cache: ${r.error.message}"
            }
        }
    }

    fun clearOldCache(days: Int) {
        viewModelScope.launch {
            when (val r = useCases.translation.clearOldCache(days)) {
                is DomainResult.Success -> {
                    _saveMessage.value = "✓ Deleted ${r.data} expired entries"
                    refreshCacheStats()
                }
                is DomainResult.Failure -> _saveMessage.value = "✗ Cache: ${r.error.message}"
            }
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            _storageUsage.value = runCatching { fileRepository.getStorageUsage() }.getOrNull()
        }
    }

    fun clearTempFiles() {
        viewModelScope.launch {
            val deleted = runCatching { fileRepository.clearTempFiles() }.getOrNull() ?: 0
            _saveMessage.value = "✓ Cleared $deleted temp files"
            refreshStorageUsage()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOCAL BACKUP
    // ═══════════════════════════════════════════════════════════════════════════

    fun createLocalBackup(includeImages: Boolean) {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Creating backup..."
            try {
                when (val r = useCases.backup.createLocal(includeImages)) {
                    is DomainResult.Success -> {
                        _backupMessage.value = "✓ Backup created"
                        refreshLocalBackups()
                    }
                    is DomainResult.Failure -> _backupMessage.value = "✗ Backup failed: ${r.error.message}"
                }
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun restoreLocalBackup(path: String, merge: Boolean) {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Restoring..."
            try {
                when (val r = useCases.backup.restoreFromLocal(path, merge)) {
                    is DomainResult.Success -> {
                        val rr = r.data
                        _backupMessage.value =
                            if (rr.isFullSuccess) "✓ Restored ${rr.totalRestored} items"
                            else "⚠️ Restored ${rr.totalRestored} items with ${rr.errors.size} warnings"
                    }
                    is DomainResult.Failure -> _backupMessage.value = "✗ Restore failed: ${r.error.message}"
                }
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun refreshLocalBackups() {
        viewModelScope.launch {
            val dir = appContext.getExternalFilesDir("backups")
            val files = dir?.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            _localBackups.value = files.map {
                LocalBackup(it.name, it.absolutePath, it.length(), it.lastModified())
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GOOGLE DRIVE
    // ═══════════════════════════════════════════════════════════════════════════

    fun refreshDriveBackups() {
        viewModelScope.launch {
            when (val r = useCases.backup.listGoogleDriveBackups()) {
                is DomainResult.Success -> _driveBackups.value = r.data.sortedByDescending { it.timestamp }
                is DomainResult.Failure -> _backupMessage.value = "✗ Drive list failed: ${r.error.message}"
            }
        }
    }

    fun signInGoogleDrive(context: Context, launcher: ActivityResultLauncher<Intent>) {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
                .build()
            launcher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
        } catch (e: Exception) {
            _backupMessage.value = "✗ Failed to start sign in: ${e.message}"
        }
    }

    fun handleSignInResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                    val account = task.getResult(ApiException::class.java)
                    if (account != null) {
                        when (val result = driveRepository.signIn()) {
                            is com.docs.scanner.domain.model.Result.Success -> {
                                _driveEmail.value = result.data
                                _backupMessage.value = "✓ Connected to Google Drive"
                                refreshDriveBackups()
                            }
                            is com.docs.scanner.domain.model.Result.Error -> {
                                _backupMessage.value = "✗ Connection failed: ${result.exception.message}"
                            }
                            else -> _backupMessage.value = "✗ Connection failed"
                        }
                    } else {
                        _backupMessage.value = "✗ No account selected"
                    }
                } else {
                    _backupMessage.value = "Sign in cancelled"
                }
            } catch (e: ApiException) {
                _backupMessage.value = "✗ Sign in failed: ${e.statusCode}"
            } catch (e: Exception) {
                _backupMessage.value = "✗ Connection failed: ${e.message}"
            }
        }
    }

    fun uploadBackupToGoogleDrive(includeImages: Boolean) {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Creating backup..."
            try {
                if (!driveRepository.isSignedIn()) {
                    _backupMessage.value = "✗ Not signed in to Google Drive"
                    return@launch
                }
                val local = useCases.backup.createLocal(includeImages).getOrElse {
                    _backupMessage.value = "✗ Backup create failed: ${it.message}"
                    return@launch
                }
                _backupMessage.value = "Uploading to Drive..."
                when (val upload = useCases.backup.uploadToGoogleDrive(local) { p ->
                    _backupMessage.value = "Uploading… ${p.percent}%"
                }) {
                    is DomainResult.Success -> {
                        _backupMessage.value = "✓ Uploaded to Google Drive"
                        refreshDriveBackups()
                    }
                    is DomainResult.Failure -> _backupMessage.value = "✗ Upload failed: ${upload.error.message}"
                }
            } catch (e: Exception) {
                _backupMessage.value = "✗ Backup error: ${e.message}"
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun restoreDriveBackup(fileId: String, merge: Boolean) {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Downloading backup..."
            try {
                if (!driveRepository.isSignedIn()) {
                    _backupMessage.value = "✗ Not signed in to Google Drive"
                    return@launch
                }
                val localPath = when (val d = useCases.backup.downloadFromGoogleDrive(fileId) { p ->
                    _backupMessage.value = "Downloading… ${p.percent}%"
                }) {
                    is DomainResult.Success -> d.data
                    is DomainResult.Failure -> {
                        _backupMessage.value = "✗ Download failed: ${d.error.message}"
                        return@launch
                    }
                }
                _backupMessage.value = "Restoring..."
                when (val r = useCases.backup.restoreFromLocal(localPath, merge)) {
                    is DomainResult.Success -> {
                        _backupMessage.value = if (merge) "✓ Restore merged" else "✓ Restore completed! Restart app."
                    }
                    is DomainResult.Failure -> _backupMessage.value = "✗ Restore failed: ${r.error.message}"
                }
            } catch (e: Exception) {
                _backupMessage.value = "✗ Restore error: ${e.message}"
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun deleteDriveBackup(fileId: String) {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Deleting backup..."
            try {
                when (val r = useCases.backup.deleteGoogleDriveBackup(fileId)) {
                    is DomainResult.Success -> {
                        _backupMessage.value = "✓ Deleted"
                        refreshDriveBackups()
                    }
                    is DomainResult.Failure -> _backupMessage.value = "✗ Delete failed: ${r.error.message}"
                }
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun signOutGoogleDrive() {
        viewModelScope.launch {
            runCatching {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
                    .build()
                GoogleSignIn.getClient(appContext, gso).signOut()
            }
            driveRepository.signOut()
            _driveEmail.value = null
            _driveBackups.value = emptyList()
            _backupMessage.value = "Disconnected from Google Drive"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ML KIT SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    fun setMlkitScriptMode(mode: OcrScriptMode) {
        viewModelScope.launch {
            _mlkitSettings.update { it.copy(scriptMode = mode) }
            val modeStr = when (mode) {
                OcrScriptMode.AUTO -> "AUTO"
                OcrScriptMode.LATIN -> "LATIN"
                OcrScriptMode.CHINESE -> "CHINESE"
                OcrScriptMode.JAPANESE -> "JAPANESE"
                OcrScriptMode.KOREAN -> "KOREAN"
                OcrScriptMode.DEVANAGARI -> "DEVANAGARI"
            }
            runCatching { settingsDataStore.setOcrLanguage(modeStr) }
        }
    }

    fun setMlkitAutoDetect(enabled: Boolean) {
        _mlkitSettings.update { it.copy(autoDetectLanguage = enabled) }
    }

    fun setMlkitConfidenceThreshold(threshold: Float) {
        _mlkitSettings.update { it.copy(confidenceThreshold = threshold) }
    }

    fun setMlkitHighlightLowConfidence(enabled: Boolean) {
        _mlkitSettings.update { it.copy(highlightLowConfidence = enabled) }
    }

    fun setMlkitShowWordConfidences(enabled: Boolean) {
        _mlkitSettings.update { it.copy(showWordConfidences = enabled) }
    }

    fun setMlkitSelectedImage(uri: Uri?) {
        _mlkitSettings.update { it.copy(selectedImageUri = uri) }
    }

    fun clearMlkitTestResult() {
        _mlkitSettings.update { it.copy(testResult = null, testError = null) }
    }

    fun runMlkitOcrTest() {
        val currentState = _mlkitSettings.value
        val imageUri = currentState.selectedImageUri
        if (imageUri == null) {
            _mlkitSettings.update { it.copy(testError = "No image selected") }
            return
        }
        viewModelScope.launch {
            _mlkitSettings.update { it.copy(isTestRunning = true, testResult = null, testError = null) }
            try {
                Timber.d("🔍 Running OCR test with mode: ${currentState.scriptMode}")
                when (val result = mlKitScanner.testOcr(
                    uri = imageUri,
                    scriptMode = currentState.scriptMode,
                    autoDetectLanguage = currentState.autoDetectLanguage,
                    confidenceThreshold = currentState.confidenceThreshold
                )) {
                    is DomainResult.Success -> {
                        Timber.d("✅ OCR test success: ${result.data.totalWords} words")
                        _mlkitSettings.update { it.copy(testResult = result.data, isTestRunning = false) }
                    }
                    is DomainResult.Failure -> {
                        Timber.e("❌ OCR test failed: ${result.error.message}")
                        _mlkitSettings.update { it.copy(testError = result.error.message, isTestRunning = false) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ OCR test exception")
                _mlkitSettings.update { it.copy(testError = "OCR failed: ${e.message}", isTestRunning = false) }
            }
        }
    }

    fun clearMlkitCache() {
        viewModelScope.launch {
            mlKitScanner.clearCache()
            _saveMessage.value = "✓ MLKit cache cleared"
        }
    }

    fun getAvailableScriptModes(): List<OcrScriptMode> = mlKitScanner.getAvailableScriptModes()

    override fun onCleared() {
        super.onCleared()
        mlKitScanner.clearCache()
    }
}

data class LocalBackup(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)
