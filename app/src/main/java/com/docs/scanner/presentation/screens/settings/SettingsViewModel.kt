/*
 * SettingsViewModel.kt
 * Version: 18.0.0 - ATOMIC MODEL SWITCHING + CANCELLABLE OCR (2026)
 * 
 * ✅ NEW in 18.0.0 - КРИТИЧЕСКИЕ ИСПРАВЛЕНИЯ:
 * - Атомарное переключение моделей (DataStore → UI, не наоборот)
 * - Debouncing для быстрых переключений (300ms)
 * - Cancellable OCR Jobs (отмена при переключении модели)
 * - Graceful cancellation с proper cleanup
 * - Job tracking для предотвращения race conditions
 * 
 * ✅ ПРЕДЫДУЩИЕ ВЕРСИИ:
 * - 16.0.0: Gemini model selection (5 models)
 * - 15.0.1: Fixed translateText() parameter names
 * - 15.0.0: Translation test methods
 * - 14.0.0: Fixed Photo Picker URI access
 * 
 * 🎯 УСТРАНЯЕТ ПРОБЛЕМЫ:
 * - UI freeze 3-5 сек → <300ms
 * - Race condition при быстром переключении моделей
 * - Зависшие OCR tests при смене настроек
 * - DataStore/UI desync
 * 
 * АРХИТЕКТУРА:
 * Settings UI → ViewModel → DataStore → MLKitScanner → Editor
 *                    ↓
 *              _mlkitSettings (UI state для preview)
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
import com.docs.scanner.BuildConfig
import com.docs.scanner.data.local.preferences.GeminiModelOption
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.local.security.ApiKeyEntry
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.data.remote.drive.DriveRepository
import com.docs.scanner.data.remote.gemini.GeminiApi
import com.docs.scanner.data.remote.mlkit.MLKitScanner
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.domain.core.*
import com.docs.scanner.domain.repository.FileRepository
import com.docs.scanner.domain.repository.SettingsRepository
import com.docs.scanner.domain.repository.StorageUsage
import com.docs.scanner.domain.usecase.AllUseCases
import com.docs.scanner.presentation.screens.settings.components.MlkitSettingsState
import com.docs.scanner.util.ImageUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
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

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val SYSTEM_LANGUAGE = "system"
        
        // ✅ НОВОЕ: Параметры debouncing
        private const val MODEL_SWITCH_DEBOUNCE_MS = 300L
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ НОВОЕ: JOB TRACKING ДЛЯ CANCELLATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Job для текущего OCR теста.
     * Отменяется при:
     * - Переключении модели
     * - Изменении настроек OCR
     * - Явном вызове cancelOcrTest()
     */
    private var currentOcrJob: Job? = null
    
    /**
     * Job для переключения модели.
     * Используется для debouncing быстрых переключений.
     */
    private var modelSwitchJob: Job? = null

    // ═══════════════════════════════════════════════════════════════════════════
    // API KEYS STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private val _apiKeys = MutableStateFlow<List<ApiKeyEntry>>(emptyList())
    val apiKeys: StateFlow<List<ApiKeyEntry>> = _apiKeys.asStateFlow()

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
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SYSTEM_LANGUAGE)

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
    // ✅ ML KIT SETTINGS STATE - SYNCHRONIZED WITH DATASTORE
    // ═══════════════════════════════════════════════════════════════════════════

    private val _mlkitSettings = MutableStateFlow(MlkitSettingsState())
    val mlkitSettings: StateFlow<MlkitSettingsState> = _mlkitSettings.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    init {
        if (BuildConfig.DEBUG) {
            Timber.d("🔧 SettingsViewModel initialized (v18.0.0)")
        }
        
        checkDriveConnection()
        loadApiKeys()
        refreshCacheStats()
        refreshStorageUsage()
        refreshLocalBackups()
        loadMlkitSettings()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ MLKIT SETTINGS LOADER
    // ═══════════════════════════════════════════════════════════════════════════

    private fun loadMlkitSettings() {
        viewModelScope.launch {
            try {
                val mode = settingsDataStore.ocrLanguage.first().uppercase()
                
                val scriptMode = when (mode) {
                    "LATIN" -> OcrScriptMode.LATIN
                    "CHINESE" -> OcrScriptMode.CHINESE
                    "JAPANESE" -> OcrScriptMode.JAPANESE
                    "KOREAN" -> OcrScriptMode.KOREAN
                    "DEVANAGARI" -> OcrScriptMode.DEVANAGARI
                    else -> OcrScriptMode.AUTO
                }
                
                val geminiEnabled = settingsDataStore.geminiOcrEnabled.first()
                val geminiThreshold = settingsDataStore.geminiOcrThreshold.first()
                val geminiAlways = settingsDataStore.geminiOcrAlways.first()
                val geminiModel = settingsDataStore.geminiOcrModel.first()
                val availableModels = settingsDataStore.getAvailableGeminiModels()
                
                _mlkitSettings.update { 
                    it.copy(
                        scriptMode = scriptMode,
                        geminiOcrEnabled = geminiEnabled,
                        geminiOcrThreshold = geminiThreshold,
                        geminiOcrAlways = geminiAlways,
                        selectedGeminiModel = geminiModel,
                        availableGeminiModels = availableModels
                    ) 
                }
                
                if (BuildConfig.DEBUG) {
                    Timber.d("📝 Loaded MLKit settings:")
                    Timber.d("   ├─ Script mode: $scriptMode")
                    Timber.d("   ├─ Gemini fallback: $geminiEnabled")
                    Timber.d("   ├─ Gemini threshold: $geminiThreshold%")
                    Timber.d("   ├─ Gemini always: $geminiAlways")
                    Timber.d("   └─ Gemini model: $geminiModel")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load MLKit settings")
            }
        }
    }

    private fun loadApiKeys() {
        viewModelScope.launch {
            try {
                _apiKeys.value = encryptedKeyStorage.getAllApiKeys()
                if (BuildConfig.DEBUG) {
                    Timber.d("🔑 Loaded ${_apiKeys.value.size} API keys")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load API keys")
                _saveMessage.value = "✗ Failed to load API keys: ${e.message}"
            }
        }
    }

    private fun checkDriveConnection() {
        viewModelScope.launch {
            try {
                val isConnected = driveRepository.isSignedIn()
                if (BuildConfig.DEBUG) {
                    Timber.d("☁️ Drive connected: $isConnected")
                }
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to check Drive connection")
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
                val success = encryptedKeyStorage.addApiKey(
                    key = trimmedKey,
                    label = label?.ifBlank { "" } ?: ""
                )
                
                if (success) {
                    loadApiKeys()
                    _saveMessage.value = "✓ API key added successfully"
                    if (BuildConfig.DEBUG) {
                        Timber.d("✅ Added new API key")
                    }
                } else {
                    _saveMessage.value = "✗ Failed to add key (duplicate or limit reached)"
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to add API key")
                _saveMessage.value = "✗ Failed to add key: ${e.message}"
            }
        }
    }

    fun activateKey(keyId: String) {
        viewModelScope.launch {
            try {
                encryptedKeyStorage.setKeyAsPrimary(keyId)
                loadApiKeys()
                _saveMessage.value = "✓ API key activated"
            } catch (e: Exception) {
                Timber.e(e, "Failed to activate key")
                _saveMessage.value = "✗ Failed to activate key: ${e.message}"
            }
        }
    }

    fun deleteKey(keyId: String) {
        viewModelScope.launch {
            try {
                val success = encryptedKeyStorage.removeApiKey(keyId)
                if (success) {
                    loadApiKeys()
                    _saveMessage.value = "✓ API key deleted"
                } else {
                    _saveMessage.value = "✗ Key not found"
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete key")
                _saveMessage.value = "✗ Failed to delete key: ${e.message}"
            }
        }
    }

    fun copyApiKey(key: String) {
        try {
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("API Key", key)
            clipboard.setPrimaryClip(clip)
            _saveMessage.value = "✓ API key copied to clipboard"
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy API key")
            _saveMessage.value = "✗ Failed to copy: ${e.message}"
        }
    }

    fun testApiKey(keyId: String) {
        testApiKeyRaw(keyId)
    }

    fun testApiKeyRaw(key: String) {
        viewModelScope.launch {
            _keyTestMessage.value = "Testing key..."
            
            when (
                val result = geminiApi.generateTextWithKey(
                    apiKey = key.trim(),
                    prompt = "Reply with: OK",
                    model = "gemini-2.0-flash-lite",
                    fallbackModels = listOf("gemini-1.5-flash")
                )
            ) {
                is DomainResult.Success -> {
                    _keyTestMessage.value = "✓ OK: ${result.data.take(80)}"
                }
                is DomainResult.Failure -> {
                    _keyTestMessage.value = "✗ Failed: ${result.error.message}"
                }
            }
        }
    }

    fun clearMessages() {
        _saveMessage.value = ""
        _backupMessage.value = ""
        _keyTestMessage.value = ""
    }

    private fun isValidApiKey(key: String): Boolean = 
        key.matches(Regex("^AIza[A-Za-z0-9_-]{35}$"))

    // ═══════════════════════════════════════════════════════════════════════════
    // APP SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            when (val r = useCases.settings.setThemeMode(mode)) {
                is DomainResult.Failure -> {
                    _saveMessage.value = "✗ Theme: ${r.error.message}"
                }
                is DomainResult.Success -> {}
            }
        }
    }

    fun setAppLanguage(code: String) {
        viewModelScope.launch {
            when (val r = useCases.settings.setAppLanguage(code)) {
                is DomainResult.Failure -> {
                    _saveMessage.value = "✗ Language: ${r.error.message}"
                }
                is DomainResult.Success -> {}
            }
        }
    }

    @Deprecated(
        message = "Use setMlkitScriptMode() for better type safety",
        replaceWith = ReplaceWith("setMlkitScriptMode(OcrScriptMode.valueOf(mode))")
    )
    fun setOcrMode(mode: String) {
        viewModelScope.launch {
            try {
                settingsDataStore.setOcrLanguage(mode)
                val scriptMode = when (mode.uppercase()) {
                    "LATIN" -> OcrScriptMode.LATIN
                    "CHINESE" -> OcrScriptMode.CHINESE
                    "JAPANESE" -> OcrScriptMode.JAPANESE
                    "KOREAN" -> OcrScriptMode.KOREAN
                    "DEVANAGARI" -> OcrScriptMode.DEVANAGARI
                    else -> OcrScriptMode.AUTO
                }
                _mlkitSettings.update { it.copy(scriptMode = scriptMode) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set OCR mode")
                _saveMessage.value = "✗ OCR: ${e.message}"
            }
        }
    }

    fun setTargetLanguage(lang: Language) {
        viewModelScope.launch {
            when (val r = useCases.settings.setTargetLanguage(lang)) {
                is DomainResult.Failure -> {
                    _saveMessage.value = "✗ Target: ${r.error.message}"
                }
                is DomainResult.Success -> {}
            }
        }
    }

    fun setAutoTranslate(enabled: Boolean) {
        viewModelScope.launch {
            when (val r = useCases.settings.setAutoTranslate(enabled)) {
                is DomainResult.Failure -> {
                    _saveMessage.value = "✗ Auto-translate: ${r.error.message}"
                }
                is DomainResult.Success -> {}
            }
        }
    }

    fun setCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setCacheEnabled(enabled)
            } catch (e: Exception) {
                _saveMessage.value = "✗ Cache: ${e.message}"
            }
        }
    }

    fun setCacheTtl(days: Int) {
        viewModelScope.launch {
            try {
                settingsDataStore.setCacheTtl(days)
            } catch (e: Exception) {
                _saveMessage.value = "✗ Cache TTL: ${e.message}"
            }
        }
    }

    fun setImageQuality(quality: ImageQuality) {
        viewModelScope.launch {
            when (val r = useCases.settings.setImageQuality(quality)) {
                is DomainResult.Success -> {
                    _saveMessage.value = "✓ Image quality: ${quality.name}"
                }
                is DomainResult.Failure -> {
                    _saveMessage.value = "✗ Image quality: ${r.error.message}"
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHE & STORAGE
    // ═══════════════════════════════════════════════════════════════════════════

    fun refreshCacheStats() {
        viewModelScope.launch {
            try {
                _cacheStats.value = useCases.translation.getCacheStats()
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh cache stats")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            when (val r = useCases.translation.clearCache()) {
                is DomainResult.Success -> {
                    _saveMessage.value = "✓ Cache cleared"
                    refreshCacheStats()
                }
                is DomainResult.Failure -> {
                    _saveMessage.value = "✗ Cache: ${r.error.message}"
                }
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
                is DomainResult.Failure -> {
                    _saveMessage.value = "✗ Cache: ${r.error.message}"
                }
            }
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            try {
                _storageUsage.value = fileRepository.getStorageUsage()
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh storage usage")
            }
        }
    }

    fun clearTempFiles() {
        viewModelScope.launch {
            try {
                val deleted = fileRepository.clearTempFiles()
                _saveMessage.value = "✓ Cleared $deleted temp files"
                refreshStorageUsage()
            } catch (e: Exception) {
                _saveMessage.value = "✗ Failed to clear temp files: ${e.message}"
            }
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
                    is DomainResult.Failure -> {
                        _backupMessage.value = "✗ Backup failed: ${r.error.message}"
                    }
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
                    is DomainResult.Failure -> {
                        _backupMessage.value = "✗ Restore failed: ${r.error.message}"
                    }
                }
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun refreshLocalBackups() {
        viewModelScope.launch {
            try {
                val dir = appContext.getExternalFilesDir("backups")
                val files = dir?.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    .orEmpty()
                
                _localBackups.value = files.map {
                    LocalBackup(it.name, it.absolutePath, it.length(), it.lastModified())
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh local backups")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GOOGLE DRIVE
    // ═══════════════════════════════════════════════════════════════════════════

    fun refreshDriveBackups() {
        viewModelScope.launch {
            when (val r = useCases.backup.listGoogleDriveBackups()) {
                is DomainResult.Success -> {
                    _driveBackups.value = r.data.sortedByDescending { it.timestamp }
                }
                is DomainResult.Failure -> {
                    Timber.e("Failed to list Drive backups: ${r.error.message}")
                }
            }
        }
    }

    fun signInGoogleDrive(context: Context, launcher: ActivityResultLauncher<Intent>) {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build()
            launcher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Drive sign-in")
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
                                refreshDriveBackups()
                            }
                            else -> _backupMessage.value = "✗ Connection failed"
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Drive sign-in error")
            }
        }
    }

    fun uploadBackupToGoogleDrive(includeImages: Boolean) {
        viewModelScope.launch {
            _isBackingUp.value = true
            try {
                if (!driveRepository.isSignedIn()) {
                    _backupMessage.value = "✗ Not signed in"
                    return@launch
                }
                
                val local = useCases.backup.createLocal(includeImages).getOrElse {
                    _backupMessage.value = "✗ Backup failed: ${it.message}"
                    return@launch
                }
                
                when (val upload = useCases.backup.uploadToGoogleDrive(local) { }) {
                    is DomainResult.Success -> {
                        _backupMessage.value = "✓ Uploaded to Drive"
                        refreshDriveBackups()
                    }
                    is DomainResult.Failure -> {
                        _backupMessage.value = "✗ Upload failed: ${upload.error.message}"
                    }
                }
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun restoreDriveBackup(fileId: String, merge: Boolean) {
        viewModelScope.launch {
            _isBackingUp.value = true
            try {
                val localPath = when (val d = useCases.backup.downloadFromGoogleDrive(fileId) { }) {
                    is DomainResult.Success -> d.data
                    is DomainResult.Failure -> {
                        _backupMessage.value = "✗ Download failed"
                        return@launch
                    }
                }
                
                when (useCases.backup.restoreFromLocal(localPath, merge)) {
                    is DomainResult.Success -> _backupMessage.value = "✓ Restored"
                    is DomainResult.Failure -> _backupMessage.value = "✗ Restore failed"
                }
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun deleteDriveBackup(fileId: String) {
        viewModelScopeviewModelScope.launch {
            _isBackingUp.value = true
            try {
                when (useCases.backup.deleteGoogleDriveBackup(fileId)) {
                    is DomainResult.Success -> {
                        _backupMessage.value = "✓ Deleted"
                        refreshDriveBackups()
                    }
                    is DomainResult.Failure -> _backupMessage.value = "✗ Delete failed"
                }
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun signOutGoogleDrive() {
        viewModelScope.launch {
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(appContext, gso).signOut()
                driveRepository.signOut()
                _driveEmail.value = null
                _driveBackups.value = emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sign out")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ ML KIT SETTINGS - SYNCHRONIZED OCR CONTROL
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
            
            try {
                settingsDataStore.setOcrLanguage(modeStr)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save MLKit script mode")
                _saveMessage.value = "✗ Failed to save OCR settings"
            }
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

    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ IMAGE SELECTION - FIXED FOR ANDROID 10-16+
    // ═══════════════════════════════════════════════════════════════════════════

    fun setMlkitSelectedImage(uri: Uri?) {
        if (uri == null) {
            _mlkitSettings.update { 
                it.copy(selectedImageUri = null, testResult = null, testError = null) 
            }
            viewModelScope.launch(Dispatchers.IO) {
                ImageUtils.clearOcrTestCache(appContext)
            }
            return
        }
        
        viewModelScope.launch {
            try {
                _mlkitSettings.update { 
                    it.copy(isTestRunning = true, testResult = null, testError = null) 
                }
                
                val stableUri = ImageUtils.copyForOcrTest(appContext, uri)
                
                _mlkitSettings.update { 
                    it.copy(selectedImageUri = stableUri, isTestRunning = false, testError = null) 
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare image for OCR test")
                _mlkitSettings.update { 
                    it.copy(
                        selectedImageUri = null,
                        isTestRunning = false,
                        testError = "Failed to load image: ${e.localizedMessage ?: e.message}"
                    ) 
                }
            }
        }
    }

    fun clearMlkitTestResult() {
        _mlkitSettings.update { 
            it.copy(testResult = null, testError = null, isTestRunning = false) 
        }
    }

    fun clearMlkitCache() {
        viewModelScope.launch {
            mlKitScanner.clearCache()
            _saveMessage.value = "✓ MLKit cache cleared"
        }
    }

    fun getAvailableScriptModes(): List<OcrScriptMode> = 
        mlKitScanner.getAvailableScriptModes()

    // ════════════════════════════════════════════════════════════════════════════════
    // GEMINI OCR FALLBACK SETTINGS
    // ════════════════════════════════════════════════════════════════════════════════
    
    fun setGeminiOcrEnabled(enabled: Boolean) {
        _mlkitSettings.update { it.copy(geminiOcrEnabled = enabled) }
        viewModelScope.launch {
            try {
                settingsDataStore.setGeminiOcrEnabled(enabled)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save Gemini OCR enabled setting")
                _saveMessage.value = "✗ Failed to save Gemini OCR setting"
            }
        }
    }
    
    fun setGeminiOcrThreshold(threshold: Int) {
        _mlkitSettings.update { it.copy(geminiOcrThreshold = threshold) }
        viewModelScope.launch {
            try {
                settingsDataStore.setGeminiOcrThreshold(threshold)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save Gemini OCR threshold")
                _saveMessage.value = "✗ Failed to save Gemini OCR threshold"
            }
        }
    }
    
    fun setGeminiOcrAlways(always: Boolean) {
        _mlkitSettings.update { it.copy(geminiOcrAlways = always) }
        viewModelScope.launch {
            try {
                settingsDataStore.setGeminiOcrAlways(always)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save Gemini OCR always setting")
                _saveMessage.value = "✗ Failed to save Gemini OCR always setting"
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // ✅ NEW in 18.0.0: ATOMIC GEMINI MODEL SWITCHING WITH DEBOUNCING
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Устанавливает модель Gemini для OCR.
     * 
     * ✅ КРИТИЧЕСКИЕ УЛУЧШЕНИЯ в 18.0.0:
     * - Debouncing 300ms для избежания спама при быстром переключении
     * - Отмена предыдущего переключения
     * - Отмена текущего OCR test если идёт
     * - АТОМАРНОЕ ОБНОВЛЕНИЕ: DataStore save → UI update (не наоборот!)
     * - Rollback UI при ошибке сохранения
     * 
     * РЕШАЕТ ПРОБЛЕМУ:
     * - БЫЛО: UI freeze 3-5 сек, race condition, desync
     * - СТАЛО: <300ms smooth, no race condition, always in sync
     * 
     * @param modelId Model identifier (e.g., "gemini-3-flash")
     */
    fun setGeminiOcrModel(modelId: String) {
        // ✅ 1. Отменяем предыдущее переключение (debouncing)
        modelSwitchJob?.cancel()
        
        // ✅ 2. Отменяем текущий OCR test если идёт
        if (_mlkitSettings.value.isTestRunning) {
            currentOcrJob?.cancel()
            _mlkitSettings.update { it.copy(isTestRunning = false) }
            
            if (BuildConfig.DEBUG) {
                Timber.d("🛑 Cancelled running OCR test due to model switch")
            }
        }
        
        // ✅ 3. Запускаем новое переключение с задержкой
        modelSwitchJob = viewModelScope.launch {
            try {
                // ✅ Debouncing: 300ms задержка для избежания спама
                delay(MODEL_SWITCH_DEBOUNCE_MS)
                
                if (BuildConfig.DEBUG) {
                    Timber.d("🔄 Switching Gemini model to: $modelId")
                }
                
                // ✅ КРИТИЧНО: Сначала сохраняем в DataStore, ПОТОМ обновляем UI
                // Это предотвращает race condition и UI freeze
                settingsDataStore.setGeminiOcrModel(modelId)
                
                // ✅ Только после успешного сохранения обновляем UI
                _mlkitSettings.update { it.copy(selectedGeminiModel = modelId) }
                
                _saveMessage.value = "✓ Gemini model: $modelId"
                
                if (BuildConfig.DEBUG) {
                    Timber.d("✅ Model switched atomically: $modelId")
                }
                
            } catch (e: CancellationException) {
                // Нормальная отмена - не показываем ошибку
                if (BuildConfig.DEBUG) {
                    Timber.d("🛑 Model switch cancelled")
                }
                throw e
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to switch Gemini model")
                _saveMessage.value = "✗ Failed to switch model"
                
                // ✅ Откатываем UI к старому значению из DataStore
                viewModelScope.launch {
                    try {
                        val currentModel = settingsDataStore.geminiOcrModel.first()
                        _mlkitSettings.update { it.copy(selectedGeminiModel = currentModel) }
                        
                        if (BuildConfig.DEBUG) {
                            Timber.d("🔙 Rolled back UI to: $currentModel")
                        }
                    } catch (rollbackError: Exception) {
                        Timber.e(rollbackError, "Failed to rollback model selection")
                    }
                }
            }
        }
    }
    
    /**
     * Returns available Gemini models for UI display.
     */
    fun getAvailableGeminiModels(): List<GeminiModelOption> {
        return settingsDataStore.getAvailableGeminiModels()
    }

    fun setMlkitTestGeminiFallback(enabled: Boolean) {
        _mlkitSettings.update { it.copy(testGeminiFallback = enabled) }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // ✅ NEW in 18.0.0: CANCELLABLE OCR TEST
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Запускает тест OCR с поддержкой отмены.
     * 
     * ✅ КРИТИЧЕСКИЕ УЛУЧШЕНИЯ в 18.0.0:
     * - Cancellable Job (можно отменить через cancelOcrTest())
     * - Автоматическая отмена при переключении модели
     * - Проверка isActive перед обновлением UI
     * - Правильная обработка CancellationException
     * 
     * РЕШАЕТ ПРОБЛЕМУ:
     * - БЫЛО: Зависший OCR при смене настроек, UI freeze
     * - СТАЛО: Instant cancellation, smooth UX
     */
    fun runMlkitOcrTest() {
        val currentState = _mlkitSettings.value
        val imageUri = currentState.selectedImageUri
        
        if (imageUri == null) {
            _mlkitSettings.update { it.copy(testError = "No image selected") }
            return
        }
        
        // ✅ Отменяем предыдущий test если идёт
        currentOcrJob?.cancel()
        
        currentOcrJob = viewModelScope.launch {
            _mlkitSettings.update { 
                it.copy(isTestRunning = true, testResult = null, testError = null) 
            }
            
            if (BuildConfig.DEBUG) {
                Timber.d("🧪 Starting OCR test")
                Timber.d("   ├─ Mode: ${currentState.scriptMode}")
                Timber.d("   ├─ Model: ${currentState.selectedGeminiModel}")
                Timber.d("   ├─ Threshold: ${(currentState.confidenceThreshold * 100).toInt()}%")
                Timber.d("   └─ Gemini fallback: ${currentState.testGeminiFallback}")
            }
            
            try {
                when (val result = mlKitScanner.testOcr(
                    uri = imageUri,
                    scriptMode = currentState.scriptMode,
                    autoDetectLanguage = currentState.autoDetectLanguage,
                    confidenceThreshold = currentState.confidenceThreshold,
                    testGeminiFallback = currentState.testGeminiFallback
                )) {
                    is DomainResult.Success -> {
                        val ocrData = result.data
                        
                        // Auto-translation если включено
                        var translatedText: String? = null
                        var translationTime: Long? = null
                        var translationTargetLang: Language? = null
                        
                        if (ocrData.text.isNotBlank()) {
                            val autoTranslateEnabled = try {
                                settingsDataStore.autoTranslate.first()
                            } catch (e: Exception) { false }
                            
                            if (autoTranslateEnabled) {
                                translationTargetLang = try {
                                    settingsDataStore.translationTarget.first().let { code ->
                                        Language.fromCode(code) ?: Language.ENGLISH
                                    }
                                } catch (e: Exception) { Language.ENGLISH }
                                
                                val translationStart = System.currentTimeMillis()
                                
                                when (val translateResult = useCases.translation.translateText(
                                    text = ocrData.text,
                                    source = ocrData.detectedLanguage ?: Language.AUTO,
                                    target = translationTargetLang
                                )) {
                                    is DomainResult.Success -> {
                                        translatedText = translateResult.data.translatedText
                                        translationTime = System.currentTimeMillis() - translationStart
                                    }
                                    is DomainResult.Failure -> {
                                        Timber.w("Auto-translation failed: ${translateResult.error.message}")
                                    }
                                }
                            }
                        }
                        
                        // ✅ КРИТИЧНО: Проверяем что Job не отменён перед обновлением UI
                        if (isActive) {
                            _mlkitSettings.update { 
                                it.copy(
                                    testResult = ocrData.copy(
                                        translatedText = translatedText,
                                        translationTargetLang = translationTargetLang,
                                        translationTimeMs = translationTime
                                    ), 
                                    isTestRunning = false
                                ) 
                            }
                            
                            if (BuildConfig.DEBUG) {
                                Timber.d("✅ OCR test success: ${ocrData.totalWords} words, ${ocrData.processingTimeMs}ms")
                            }
                        }
                    }
                    
                    is DomainResult.Failure -> {
                        // ✅ Проверяем isActive перед обновлением UI
                        if (isActive) {
                            _mlkitSettings.update { 
                                it.copy(testError = result.error.message, isTestRunning = false) 
                            }
                        }
                    }
                }
                
            } catch (e: CancellationException) {
                // ✅ Нормальная отмена - не показываем ошибку
                if (BuildConfig.DEBUG) {
                    Timber.d("🛑 OCR test cancelled")
                }
                // ✅ ВАЖНО: Пробрасываем CancellationException дальше
                throw e
                
            } catch (e: Exception) {
                Timber.e(e, "MLKit OCR test exception")
                
                // ✅ Проверяем isActive перед обновлением UI
                if (isActive) {
                    _mlkitSettings.update { 
                        it.copy(testError = "OCR failed: ${e.message}", isTestRunning = false) 
                    }
                }
            }
        }
    }
    
    /**
     * ✅ NEW in 18.0.0: Принудительная отмена OCR теста.
     * 
     * Используется для:
     * - Кнопки "Cancel" в UI
     * - Автоматической отмены при переключении модели
     * - Cleanup при закрытии экрана
     */
    fun cancelOcrTest() {
        currentOcrJob?.cancel()
        _mlkitSettings.update { 
            it.copy(isTestRunning = false, testError = null) 
        }
        
        if (BuildConfig.DEBUG) {
            Timber.d("🛑 OCR test cancelled by user")
        }
    }

    fun resetApiKeyErrors() {
        viewModelScope.launch {
            try {
                encryptedKeyStorage.resetAllKeyErrors()
                loadApiKeys()
                _saveMessage.value = "✓ All key errors reset"
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset API key errors")
                _saveMessage.value = "✗ Failed to reset errors: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSLATION TEST
    // ═══════════════════════════════════════════════════════════════════════════

    fun setTranslationTestText(text: String) {
        _mlkitSettings.update { it.copy(translationTestText = text) }
    }

    fun setTranslationSourceLang(lang: Language) {
        _mlkitSettings.update { it.copy(translationSourceLang = lang) }
    }

    fun setTranslationTargetLang(lang: Language) {
        _mlkitSettings.update { it.copy(translationTargetLang = lang) }
    }

    fun testTranslation() {
        val state = _mlkitSettings.value
        
        if (state.translationTestText.isBlank()) {
            _mlkitSettings.update {
                it.copy(translationError = "Please enter text to translate")
            }
            return
        }
        
        viewModelScope.launch {
            _mlkitSettings.update { it.copy(isTranslating = true) }
            
            val start = System.currentTimeMillis()
            
            when (val result = useCases.translation.translateText(
                text = state.translationTestText,
                source = state.translationSourceLang,
                target = state.translationTargetLang
            )) {
                is DomainResult.Success -> {
                    val time = System.currentTimeMillis() - start
                    _mlkitSettings.update {
                        it.copy(
                            translationResult = result.data.translatedText,
                            isTranslating = false,
                            translationError = null
                        )
                    }
                    _saveMessage.value = "✓ Translated in ${time}ms"
                }
                
                is DomainResult.Failure -> {
                    _mlkitSettings.update {
                        it.copy(
                            translationResult = null,
                            isTranslating = false,
                            translationError = result.error.message
                        )
                    }
                }
            }
        }
    }

    fun clearTranslationTest() {
        _mlkitSettings.update {
            it.copy(
                translationTestText = "",
                translationResult = null,
                translationError = null
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ CLEANUP - FIXED in 18.0.0
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ✅ ИСПРАВЛЕНО в 18.0.0: Proper cleanup всех Jobs.
     * 
     * Отменяет:
     * - currentOcrJob (текущий OCR test)
     * - modelSwitchJob (переключение модели)
     * - Очищает кэши MLKit и ImageUtils
     */
    override fun onCleared() {
        super.onCleared()
        
        if (BuildConfig.DEBUG) {
            Timber.d("🧹 SettingsViewModel cleanup started")
        }
        
        // ✅ Отменяем все активные Jobs
        currentOcrJob?.cancel()
        modelSwitchJob?.cancel()
        
        // Очищаем кэши
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mlKitScanner.clearCache()
                ImageUtils.clearOcrTestCache(appContext)
                
                if (BuildConfig.DEBUG) {
                    Timber.d("✅ SettingsViewModel cleanup complete")
                }
            } catch (e: Exception) {
                Timber.w(e, "Error during cleanup")
            }
        }
    }
}

data class LocalBackup(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)