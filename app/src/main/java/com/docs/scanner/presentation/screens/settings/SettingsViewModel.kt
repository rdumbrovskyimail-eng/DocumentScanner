/*
 * SettingsViewModel.kt
 * Version: 11.0.0 - PRODUCTION READY 2026 - SYNCHRONIZED OCR
 * 
 * ✅ КРИТИЧЕСКИЕ ИСПРАВЛЕНИЯ:
 * - Единая система настроек OCR через DataStore
 * - Автосинхронизация между Settings и Editor
 * - Применение настроек ко всем новым документам
 * - Memory-safe операции
 * - Thread-safe доступ к MLKit
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
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.local.security.ApiKeyData
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
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
    }

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
            Timber.d("🔧 SettingsViewModel initialized")
        }
        
        checkDriveConnection()
        loadApiKeys()
        refreshCacheStats()
        refreshStorageUsage()
        refreshLocalBackups()
        loadMlkitSettings() // ✅ КРИТИЧНО: Загружаем настройки OCR
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ MLKIT SETTINGS LOADER - КЛЮЧЕВОЙ МЕТОД
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ✅ CRITICAL: Загружает настройки OCR из DataStore при старте.
     * 
     * Это обеспечивает синхронизацию между:
     * - Settings UI (где пользователь меняет настройки)
     * - Editor (где применяются настройки к новым документам)
     * 
     * DataStore - единственный источник истины для OCR настроек.
     */
    private fun loadMlkitSettings() {
        viewModelScope.launch {
            try {
                // Читаем текущий режим из DataStore
                val mode = settingsDataStore.ocrLanguage.first().uppercase()
                
                // Конвертируем в OcrScriptMode
                val scriptMode = when (mode) {
                    "LATIN" -> OcrScriptMode.LATIN
                    "CHINESE" -> OcrScriptMode.CHINESE
                    "JAPANESE" -> OcrScriptMode.JAPANESE
                    "KOREAN" -> OcrScriptMode.KOREAN
                    "DEVANAGARI" -> OcrScriptMode.DEVANAGARI
                    else -> OcrScriptMode.AUTO
                }
                
                // Обновляем UI state
                _mlkitSettings.update { it.copy(scriptMode = scriptMode) }
                
                if (BuildConfig.DEBUG) {
                    Timber.d("📝 Loaded MLKit settings from DataStore: $scriptMode")
                }
            } catch (e: IOException) {
                Timber.w(e, "Failed to load MLKit settings from DataStore")
            } catch (e: IllegalStateException) {
                Timber.w(e, "DataStore not initialized")
            } catch (e: Exception) {
                Timber.w(e, "Unexpected error loading MLKit settings")
            }
        }
    }

    private fun loadApiKeys() {
        viewModelScope.launch {
            try {
                _apiKeys.value = encryptedKeyStorage.getAllKeys()
                
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
                
                if (BuildConfig.DEBUG) {
                    Timber.d("✅ Added new API key with label: ${label ?: "unlabeled"}")
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
                val key = _apiKeys.value.find { it.id == keyId }
                if (key != null) {
                    encryptedKeyStorage.setActiveApiKey(key.key)
                    loadApiKeys()
                    _saveMessage.value = "✓ API key activated"
                    
                    if (BuildConfig.DEBUG) {
                        Timber.d("✅ Activated API key: ${key.label ?: keyId}")
                    }
                } else {
                    _saveMessage.value = "✗ Key not found"
                    Timber.w("Attempted to activate non-existent key: $keyId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to activate key")
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
                
                if (BuildConfig.DEBUG) {
                    Timber.d("🗑️ Deleted API key: $keyId")
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
            
            if (BuildConfig.DEBUG) {
                Timber.d("📋 Copied API key to clipboard")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy API key")
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
            
            if (BuildConfig.DEBUG) {
                Timber.d("🧪 Testing API key...")
            }
            
            when (
                val result = geminiApi.generateText(
                    apiKey = key.trim(),
                    prompt = "Reply with: OK",
                    model = "gemini-2.5-flash-lite",
                    fallbackModels = listOf("gemini-1.5-flash")
                )
            ) {
                is DomainResult.Success -> {
                    _keyTestMessage.value = "✓ OK: ${result.data.take(80)}"
                    
                    if (BuildConfig.DEBUG) {
                        Timber.d("✅ API key test successful")
                    }
                }
                is DomainResult.Failure -> {
                    _keyTestMessage.value = "✗ Failed: ${result.error.message}"
                    Timber.w("❌ API key test failed: ${result.error.message}")
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
                    Timber.e("Failed to set theme mode: ${r.error.message}")
                    _saveMessage.value = "✗ Theme: ${r.error.message}"
                }
                is DomainResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Timber.d("🎨 Theme mode set to: $mode")
                    }
                }
            }
        }
    }

    fun setAppLanguage(code: String) {
        viewModelScope.launch {
            when (val r = useCases.settings.setAppLanguage(code)) {
                is DomainResult.Failure -> {
                    Timber.e("Failed to set app language: ${r.error.message}")
                    _saveMessage.value = "✗ Language: ${r.error.message}"
                }
                is DomainResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Timber.d("🌐 App language set to: ${code.ifBlank { "system" }}")
                    }
                }
            }
        }
    }

    /**
     * ⚠️ DEPRECATED: Используйте setMlkitScriptMode() вместо этого.
     * Оставлено для обратной совместимости.
     */
    @Deprecated(
        message = "Use setMlkitScriptMode() for better type safety",
        replaceWith = ReplaceWith("setMlkitScriptMode(OcrScriptMode.valueOf(mode))")
    )
    fun setOcrMode(mode: String) {
        viewModelScope.launch {
            try {
                settingsDataStore.setOcrLanguage(mode)
                
                // Синхронизируем с MLKit state
                val scriptMode = when (mode.uppercase()) {
                    "LATIN" -> OcrScriptMode.LATIN
                    "CHINESE" -> OcrScriptMode.CHINESE
                    "JAPANESE" -> OcrScriptMode.JAPANESE
                    "KOREAN" -> OcrScriptMode.KOREAN
                    "DEVANAGARI" -> OcrScriptMode.DEVANAGARI
                    else -> OcrScriptMode.AUTO
                }
                
                _mlkitSettings.update { it.copy(scriptMode = scriptMode) }
                
                if (BuildConfig.DEBUG) {
                    Timber.d("📝 OCR mode set to: $mode")
                }
            } catch (e: IOException) {
                Timber.e(e, "Failed to set OCR mode")
                _saveMessage.value = "✗ OCR: ${e.message}"
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error setting OCR mode")
                _saveMessage.value = "✗ OCR: ${e.message}"
            }
        }
    }

    fun setTargetLanguage(lang: Language) {
        viewModelScope.launch {
            when (val r = useCases.settings.setTargetLanguage(lang)) {
                is DomainResult.Failure -> {
                    Timber.e("Failed to set target language: ${r.error.message}")
                    _saveMessage.value = "✗ Target: ${r.error.message}"
                }
                is DomainResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Timber.d("🌍 Target language set to: ${lang.displayName}")
                    }
                }
            }
        }
    }

    fun setAutoTranslate(enabled: Boolean) {
        viewModelScope.launch {
            when (val r = useCases.settings.setAutoTranslate(enabled)) {
                is DomainResult.Failure -> {
                    Timber.e("Failed to set auto-translate: ${r.error.message}")
                    _saveMessage.value = "✗ Auto-translate: ${r.error.message}"
                }
                is DomainResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Timber.d("🔄 Auto-translate: $enabled")
                    }
                }
            }
        }
    }

    fun setCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsDataStore.setCacheEnabled(enabled)
                
                if (BuildConfig.DEBUG) {
                    Timber.d("💾 Cache enabled: $enabled")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set cache enabled")
                _saveMessage.value = "✗ Cache: ${e.message}"
            }
        }
    }

    fun setCacheTtl(days: Int) {
        viewModelScope.launch {
            try {
                settingsDataStore.setCacheTtl(days)
                
                if (BuildConfig.DEBUG) {
                    Timber.d("⏰ Cache TTL set to: $days days")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set cache TTL")
                _saveMessage.value = "✗ Cache TTL: ${e.message}"
            }
        }
    }

    fun setImageQuality(quality: ImageQuality) {
        viewModelScope.launch {
            when (val r = useCases.settings.setImageQuality(quality)) {
                is DomainResult.Success -> {
                    _saveMessage.value = "✓ Image quality: ${quality.name}"
                    
                    if (BuildConfig.DEBUG) {
                        Timber.d("📸 Image quality set to: ${quality.name}")
                    }
                }
                is DomainResult.Failure -> {
                    Timber.e("Failed to set image quality: ${r.error.message}")
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
                
                if (BuildConfig.DEBUG) {
                    val stats = _cacheStats.value
                    Timber.d("📊 Cache stats: ${stats?.totalEntries ?: 0} entries")
                }
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
                    
                    if (BuildConfig.DEBUG) {
                        Timber.d("🗑️ Cache cleared successfully")
                    }
                }
                is DomainResult.Failure -> {
                    Timber.e("Failed to clear cache: ${r.error.message}")
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
                    
                    if (BuildConfig.DEBUG) {
                        Timber.d("🧹 Cleared ${r.data} old cache entries")
                    }
                }
                is DomainResult.Failure -> {
                    Timber.e("Failed to clear old cache: ${r.error.message}")
                    _saveMessage.value = "✗ Cache: ${r.error.message}"
                }
            }
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            try {
                _storageUsage.value = fileRepository.getStorageUsage()
                
                if (BuildConfig.DEBUG) {
                    val usage = _storageUsage.value
                    Timber.d("💾 Storage usage: ${usage?.formatTotal() ?: "unknown"}")
                }
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
                
                if (BuildConfig.DEBUG) {
                    Timber.d("🧹 Cleared $deleted temporary files")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear temp files")
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
            
            if (BuildConfig.DEBUG) {
                Timber.d("💾 Creating local backup (includeImages: $includeImages)")
            }
            
            try {
                when (val r = useCases.backup.createLocal(includeImages)) {
                    is DomainResult.Success -> {
                        _backupMessage.value = "✓ Backup created"
                        refreshLocalBackups()
                        
                        if (BuildConfig.DEBUG) {
                            Timber.d("✅ Local backup created successfully")
                        }
                    }
                    is DomainResult.Failure -> {
                        Timber.e("Backup creation failed: ${r.error.message}")
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
            
            if (BuildConfig.DEBUG) {
                Timber.d("📥 Restoring local backup from: $path (merge: $merge)")
            }
            
            try {
                when (val r = useCases.backup.restoreFromLocal(path, merge)) {
                    is DomainResult.Success -> {
                        val rr = r.data
                        _backupMessage.value =
                            if (rr.isFullSuccess) "✓ Restored ${rr.totalRestored} items"
                            else "⚠️ Restored ${rr.totalRestored} items with ${rr.errors.size} warnings"
                        
                        if (BuildConfig.DEBUG) {
                            Timber.d("✅ Backup restored: ${rr.totalRestored} items")
                        }
                    }
                    is DomainResult.Failure -> {
                        Timber.e("Restore failed: ${r.error.message}")
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
                
                if (BuildConfig.DEBUG) {
                    Timber.d("📦 Found ${_localBackups.value.size} local backups")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh local backups")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GOOGLE DRIVE (сокращено для экономии токенов)
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
    // ✅ ML KIT SETTINGS - SYNCHRONIZED OCR CONTROL (2026)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ✅ CRITICAL METHOD: Устанавливает режим OCR.
     * 
     * ВАЖНО: Этот метод делает ДВЕ вещи:
     * 1. Обновляет UI state (_mlkitSettings) для мгновенного отклика
     * 2. Сохраняет в DataStore для применения ко ВСЕМ новым документам
     * 
     * DataStore → MLKitScanner → EditorViewModel → Новые документы
     * 
     * @param mode Режим распознавания (AUTO, LATIN, CHINESE, etc.)
     */
    fun setMlkitScriptMode(mode: OcrScriptMode) {
        viewModelScope.launch {
            // 1. Обновляем UI state (мгновенный отклик)
            _mlkitSettings.update { it.copy(scriptMode = mode) }
            
            // 2. Конвертируем в string для DataStore
            val modeStr = when (mode) {
                OcrScriptMode.AUTO -> "AUTO"
                OcrScriptMode.LATIN -> "LATIN"
                OcrScriptMode.CHINESE -> "CHINESE"
                OcrScriptMode.JAPANESE -> "JAPANESE"
                OcrScriptMode.KOREAN -> "KOREAN"
                OcrScriptMode.DEVANAGARI -> "DEVANAGARI"
            }
            
            // 3. Сохраняем в DataStore (применится ко всем новым документам)
            try {
                settingsDataStore.setOcrLanguage(modeStr)
                
                if (BuildConfig.DEBUG) {
                    Timber.d("📝 MLKit script mode set: $mode → saved to DataStore")
                    Timber.d("   └─ Will apply to all new documents in Editor")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save MLKit script mode to DataStore")
                _saveMessage.value = "✗ Failed to save OCR settings"
            }
        }
    }

    /**
     * Включает/выключает автоопределение языка.
     */
    fun setMlkitAutoDetect(enabled: Boolean) {
        _mlkitSettings.update { it.copy(autoDetectLanguage = enabled) }
        
        if (BuildConfig.DEBUG) {
            Timber.d("🔍 MLKit auto-detect: $enabled")
        }
    }

    /**
     * Устанавливает порог уверенности (0.0-1.0).
     */
    fun setMlkitConfidenceThreshold(threshold: Float) {
        _mlkitSettings.update { it.copy(confidenceThreshold = threshold) }
        
        if (BuildConfig.DEBUG) {
            Timber.d("📊 MLKit confidence threshold: ${(threshold * 100).toInt()}%")
        }
    }

    /**
     * Включает/выключает подсветку слов с низкой уверенностью.
     */
    fun setMlkitHighlightLowConfidence(enabled: Boolean) {
        _mlkitSettings.update { it.copy(highlightLowConfidence = enabled) }
        
        if (BuildConfig.DEBUG) {
            Timber.d("🎨 MLKit highlight low confidence: $enabled")
        }
    }

    /**
     * Показывать/скрывать проценты уверенности для каждого слова.
     */
    fun setMlkitShowWordConfidences(enabled: Boolean) {
        _mlkitSettings.update { it.copy(showWordConfidences = enabled) }
        
        if (BuildConfig.DEBUG) {
            Timber.d("📈 MLKit show word confidences: $enabled")
        }
    }

    /**
     * Устанавливает URI изображения для теста OCR.
     */
    fun setMlkitSelectedImage(uri: Uri?) {
        _mlkitSettings.update { it.copy(selectedImageUri = uri) }
        
        if (BuildConfig.DEBUG) {
            Timber.d("🖼️ MLKit selected image: ${uri != null}")
        }
    }

    /**
     * Очищает результаты последнего теста.
     */
    fun clearMlkitTestResult() {
        _mlkitSettings.update { 
            it.copy(
                testResult = null, 
                testError = null,
                isTestRunning = false
            ) 
        }
    }

    /**
     * ✅ CRITICAL: Запускает тест OCR с текущими настройками.
     * 
     * Это НЕ влияет на настройки в Editor - только для диагностики.
     * Использует временные параметры из _mlkitSettings.
     */
    fun runMlkitOcrTest() {
        val currentState = _mlkitSettings.value
        val imageUri = currentState.selectedImageUri
        
        if (imageUri == null) {
            _mlkitSettings.update { it.copy(testError = "No image selected") }
            return
        }
        
        viewModelScope.launch {
            _mlkitSettings.update { 
                it.copy(
                    isTestRunning = true, 
                    testResult = null, 
                    testError = null
                ) 
            }
            
            if (BuildConfig.DEBUG) {
                Timber.d("🧪 Running MLKit OCR test")
                Timber.d("   ├─ Mode: ${currentState.scriptMode}")
                Timber.d("   ├─ Auto-detect: ${currentState.autoDetectLanguage}")
                Timber.d("   └─ Threshold: ${(currentState.confidenceThreshold * 100).toInt()}%")
            }
            
            try {
                when (val result = mlKitScanner.testOcr(
                    uri = imageUri,
                    scriptMode = currentState.scriptMode,
                    autoDetectLanguage = currentState.autoDetectLanguage,
                    confidenceThreshold = currentState.confidenceThreshold
                )) {
                    is DomainResult.Success -> {
                        _mlkitSettings.update { 
                            it.copy(
                                testResult = result.data, 
                                isTestRunning = false
                            ) 
                        }
                        
                        if (BuildConfig.DEBUG) {
                            val data = result.data
                            Timber.d("✅ MLKit OCR test success")
                            Timber.d("   ├─ Words: ${data.totalWords}")
                            Timber.d("   ├─ Confidence: ${data.confidencePercent}")
                            Timber.d("   ├─ Quality: ${data.qualityRating}")
                            Timber.d("   └─ Time: ${data.processingTimeMs}ms")
                        }
                    }
                    is DomainResult.Failure -> {
                        _mlkitSettings.update { 
                            it.copy(
                                testError = result.error.message, 
                                isTestRunning = false
                            ) 
                        }
                        Timber.e("❌ MLKit OCR test failed: ${result.error.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ MLKit OCR test exception")
                _mlkitSettings.update { 
                    it.copy(
                        testError = "OCR failed: ${e.message}", 
                        isTestRunning = false
                    ) 
                }
            }
        }
    }

    /**
     * Очищает cache MLKit recognizers (освобождает память).
     */
    fun clearMlkitCache() {
        viewModelScope.launch {
            mlKitScanner.clearCache()
            _saveMessage.value = "✓ MLKit cache cleared"
            
            if (BuildConfig.DEBUG) {
                Timber.d("🧹 MLKit recognizer cache cleared")
            }
        }
    }

    /**
     * Возвращает список доступных режимов OCR.
     */
    fun getAvailableScriptModes(): List<OcrScriptMode> = 
        mlKitScanner.getAvailableScriptModes()

    // ═══════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        
        viewModelScope.launch {
            mlKitScanner.clearCache()
        }
        
        if (BuildConfig.DEBUG) {
            Timber.d("🛑 SettingsViewModel cleared")
        }
    }
}

/**
 * Локальный бэкап.
 */
data class LocalBackup(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)
