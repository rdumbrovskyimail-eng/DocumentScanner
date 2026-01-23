/*
 * SettingsViewModel.kt
 * Version: 20.0.2 - TRANSLATION MODEL FIX v2 (2026)
 * 
 * ✅ FIXED IN 20.0.2:
 * - testTranslation() теперь использует translateTextWithModel() (правильный метод для Testing Tab)
 * - Убран несуществующий параметр 'model' из translateText()
 * 
 * ✅ PREVIOUS IN 20.0.1:
 * - testTranslation() теперь использует translateWithModel() вместо translateTextWithModel()
 * - Полная совместимость с UseCases.translation API
 * 
 * ✅ PREVIOUS IN 20.0.0 - GEMINI MODEL MANAGER INTEGRATION:
 * - GeminiModelManager injection для централизованного управления моделями
 * - setGeminiOcrModel() делегирует валидацию и сохранение в ModelManager
 * - setTranslationModel() делегирует валидацию и сохранение в ModelManager
 * - getAvailableGeminiModels() делегирует в ModelManager
 * - getAvailableTranslationModels() делегирует в ModelManager
 * - loadMlkitSettings() загружает модели через ModelManager
 * - Rollback при ошибках через ModelManager
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
import com.docs.scanner.data.local.preferences.GeminiModelManager
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
    private val useCases: AllUseCases,
    private val modelManager: GeminiModelManager
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val SYSTEM_LANGUAGE = "system"
        private const val MODEL_SWITCH_DEBOUNCE_MS = 300L
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JOB TRACKING
    // ═══════════════════════════════════════════════════════════════════════════
    
    private var currentOcrJob: Job? = null
    private var modelSwitchJob: Job? = null
    private var translationModelSwitchJob: Job? = null

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
    // ML KIT SETTINGS STATE
    // ═══════════════════════════════════════════════════════════════════════════

    private val _mlkitSettings = MutableStateFlow(MlkitSettingsState())
    val mlkitSettings: StateFlow<MlkitSettingsState> = _mlkitSettings.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    init {
        if (BuildConfig.DEBUG) {
            Timber.d("🔧 SettingsViewModel initialized (v20.0.2)")
        }
        
        checkDriveConnection()
        loadApiKeys()
        refreshCacheStats()
        refreshStorageUsage()
        refreshLocalBackups()
        loadMlkitSettings()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MLKIT SETTINGS LOADER
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
                
                val geminiModel = modelManager.getGlobalOcrModel()
                val availableModels = modelManager.getAvailableModels()
                
                val translationModel = modelManager.getGlobalTranslationModel()
                val availableTranslationModels = modelManager.getAvailableModels()
                
                _mlkitSettings.update { 
                    it.copy(
                        scriptMode = scriptMode,
                        geminiOcrEnabled = geminiEnabled,
                        geminiOcrThreshold = geminiThreshold,
                        geminiOcrAlways = geminiAlways,
                        selectedGeminiModel = geminiModel,
                        availableGeminiModels = availableModels,
                        selectedTranslationModel = translationModel,
                        availableTranslationModels = availableTranslationModels
                    ) 
                }
                
                if (BuildConfig.DEBUG) {
                    Timber.d("📝 Loaded MLKit settings:")
                    Timber.d("   ├─ Script mode: $scriptMode")
                    Timber.d("   ├─ Gemini fallback: $geminiEnabled")
                    Timber.d("   ├─ Gemini threshold: $geminiThreshold%")
                    Timber.d("   ├─ Gemini always: $geminiAlways")
                    Timber.d("   ├─ OCR model: $geminiModel")
                    Timber.d("   └─ Translation model: $translationModel")
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
                    model = "gemini-2.5-flash-lite",
                    fallbackModels = listOf("gemini-2.5-flash")
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
        viewModelScope.launch {
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
    // IMAGE SELECTION
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
    // ATOMIC GEMINI MODEL SWITCHING (OCR)
    // ════════════════════════════════════════════════════════════════════════════════
    
    fun setGeminiOcrModel(modelId: String) {
        modelSwitchJob?.cancel()
        
        if (_mlkitSettings.value.isTestRunning) {
            currentOcrJob?.cancel()
            _mlkitSettings.update { it.copy(isTestRunning = false) }
            
            if (BuildConfig.DEBUG) {
                Timber.d("🛑 Cancelled running OCR test due to model switch")
            }
        }
        
        modelSwitchJob = viewModelScope.launch {
            try {
                delay(MODEL_SWITCH_DEBOUNCE_MS)
                
                if (BuildConfig.DEBUG) {
                    Timber.d("🔄 Switching OCR model to: $modelId")
                }
                
                modelManager.setGlobalOcrModel(modelId)
                _mlkitSettings.update { it.copy(selectedGeminiModel = modelId) }
                _saveMessage.value = "✓ OCR model: $modelId"
                
                if (BuildConfig.DEBUG) {
                    Timber.d("✅ OCR model switched: $modelId")
                }
                
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Timber.d("🛑 OCR model switch cancelled")
                }
                throw e
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to switch OCR model")
                _saveMessage.value = "✗ Failed to switch OCR model"
                
                viewModelScope.launch {
                    try {
                        val currentModel = modelManager.getGlobalOcrModel()
                        _mlkitSettings.update { it.copy(selectedGeminiModel = currentModel) }
                    } catch (rollbackError: Exception) {
                        Timber.e(rollbackError, "Failed to rollback OCR model selection")
                    }
                }
            }
        }
    }
    
    fun getAvailableGeminiModels(): List<GeminiModelOption> {
        return modelManager.getAvailableModels()
    }

    fun setMlkitTestGeminiFallback(enabled: Boolean) {
        _mlkitSettings.update { it.copy(testGeminiFallback = enabled) }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // TRANSLATION MODEL SELECTION
    // ════════════════════════════════════════════════════════════════════════════════
    
    fun setTranslationModel(modelId: String) {
        translationModelSwitchJob?.cancel()
        
        translationModelSwitchJob = viewModelScope.launch {
            try {
                delay(MODEL_SWITCH_DEBOUNCE_MS)
                
                if (BuildConfig.DEBUG) {
                    Timber.d("🔄 Switching Translation model to: $modelId")
                }
                
                modelManager.setGlobalTranslationModel(modelId)
                _mlkitSettings.update { it.copy(selectedTranslationModel = modelId) }
                _saveMessage.value = "✓ Translation model: $modelId"
                
                if (BuildConfig.DEBUG) {
                    Timber.d("✅ Translation model switched: $modelId")
                }
                
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Timber.d("🛑 Translation model switch cancelled")
                }
                throw e
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to switch Translation model")
                _saveMessage.value = "✗ Failed to switch model"
                
                viewModelScope.launch {
                    try {
                        val currentModel = modelManager.getGlobalTranslationModel()
                        _mlkitSettings.update { it.copy(selectedTranslationModel = currentModel) }
                    } catch (rollbackError: Exception) {
                        Timber.e(rollbackError, "Failed to rollback translation model selection")
                    }
                }
            }
        }
    }
    
    fun getAvailableTranslationModels(): List<GeminiModelOption> {
        return modelManager.getAvailableModels()
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // CANCELLABLE OCR TEST
    // ════════════════════════════════════════════════════════════════════════════════
    
    fun runMlkitOcrTest() {
        val currentState = _mlkitSettings.value
        val imageUri = currentState.selectedImageUri
        
        if (imageUri == null) {
            _mlkitSettings.update { it.copy(testError = "No image selected") }
            return
        }
        
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
                        
                        if (isActive) {
                            _mlkitSettings.update { 
                                it.copy(
                                    testResult = ocrData.copy(
                                        translatedText = translatedText,
                                        translationTargetLang = translationTargetLang,
                                        translationTimeMs = translationTime
                                    ), 
                                    isTestRunning = false,
                                    translationTestText = ocrData.text,
                                    translationSourceLang = ocrData.detectedLanguage ?: Language.AUTO,
                                    translationResult = null,
                                    translationError = null
                                ) 
                            }
                            
                            if (BuildConfig.DEBUG) {
                                Timber.d("✅ OCR test success: ${ocrData.totalWords} words, ${ocrData.processingTimeMs}ms")
                                Timber.d("📋 Synced to Translation Test: ${ocrData.text.take(50)}...")
                            }
                        }
                    }
                    
                    is DomainResult.Failure -> {
                        if (isActive) {
                            _mlkitSettings.update { 
                                it.copy(testError = result.error.message, isTestRunning = false) 
                            }
                        }
                    }
                }
                
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Timber.d("🛑 OCR test cancelled")
                }
                throw e
                
            } catch (e: Exception) {
                Timber.e(e, "MLKit OCR test exception")
                
                if (isActive) {
                    _mlkitSettings.update { 
                        it.copy(testError = "OCR failed: ${e.message}", isTestRunning = false) 
                    }
                }
            }
        }
    }
    
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
    // ✅ TRANSLATION TEST - FIXED IN 20.0.2
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

    /**
     * ✅ FIXED IN 20.0.2: Uses translateTextWithModel() (correct method for Testing Tab)
     */
    fun testTranslation() {
        val state = _mlkitSettings.value
        
        if (state.translationTestText.isBlank()) {
            _mlkitSettings.update {
                it.copy(translationError = "Please enter text to translate")
            }
            return
        }
        
        if (state.translationSourceLang != Language.AUTO && 
            state.translationSourceLang == state.translationTargetLang) {
            _mlkitSettings.update {
                it.copy(translationError = "Source and target languages must be different")
            }
            return
        }
        
        viewModelScope.launch {
            _mlkitSettings.update { it.copy(isTranslating = true, translationError = null) }
            
            val start = System.currentTimeMillis()
            val selectedModel = state.selectedTranslationModel
            
            if (BuildConfig.DEBUG) {
                Timber.d("🌐 Translation test starting")
                Timber.d("   ├─ Model: $selectedModel")
                Timber.d("   ├─ From: ${state.translationSourceLang.displayName}")
                Timber.d("   ├─ To: ${state.translationTargetLang.displayName}")
                Timber.d("   └─ Text: ${state.translationTestText.take(50)}...")
            }
            
            // ✅ CRITICAL FIX: Use translateTextWithModel (accepts 'model' parameter)
            when (val result = useCases.translation.translateTextWithModel(
                text = state.translationTestText,
                source = state.translationSourceLang,
                target = state.translationTargetLang,
                model = selectedModel
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
                    _saveMessage.value = "✓ Translated in ${time}ms using $selectedModel"
                    
                    if (BuildConfig.DEBUG) {
                        Timber.d("✅ Translation success in ${time}ms")
                    }
                }
                
                is DomainResult.Failure -> {
                    _mlkitSettings.update {
                        it.copy(
                            translationResult = null,
                            isTranslating = false,
                            translationError = result.error.message
                        )
                    }
                    
                    if (BuildConfig.DEBUG) {
                        Timber.e("❌ Translation failed: ${result.error.message}")
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
    
    fun syncOcrResultToTranslation() {
        val ocrResult = _mlkitSettings.value.testResult
        
        if (ocrResult?.text?.isNotBlank() == true) {
            _mlkitSettings.update { 
                it.copy(
                    translationTestText = ocrResult.text,
                    translationSourceLang = ocrResult.detectedLanguage ?: Language.AUTO,
                    translationResult = null,
                    translationError = null
                ) 
            }
            
            if (BuildConfig.DEBUG) {
                Timber.d("📋 Manually synced OCR result to Translation Test")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        
        if (BuildConfig.DEBUG) {
            Timber.d("🧹 SettingsViewModel cleanup started")
        }
        
        currentOcrJob?.cancel()
        modelSwitchJob?.cancel()
        translationModelSwitchJob?.cancel()
        
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