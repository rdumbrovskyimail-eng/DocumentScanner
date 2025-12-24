package com.docs.scanner.presentation.screens.settings

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.data.local.security.ApiKeyData
import com.docs.scanner.data.remote.drive.DriveRepository
import com.docs.scanner.domain.repository.SettingsRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val driveRepository: DriveRepository,
    private val encryptedKeyStorage: EncryptedKeyStorage
) : ViewModel() {
    
    // Список всех API ключей из зашифрованного хранилища
    private val _apiKeys = MutableStateFlow<List<ApiKeyData>>(emptyList())
    val apiKeys: StateFlow<List<ApiKeyData>> = _apiKeys.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveMessage = MutableStateFlow("")
    val saveMessage: StateFlow<String> = _saveMessage.asStateFlow()
    
    private val _driveEmail = MutableStateFlow<String?>(null)
    val driveEmail: StateFlow<String?> = _driveEmail.asStateFlow()
    
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()
    
    private val _backupMessage = MutableStateFlow("")
    val backupMessage: StateFlow<String> = _backupMessage.asStateFlow()
    
    init {
        checkDriveConnection()
        loadApiKeys()
    }
    
    // Загрузка ключей из зашифрованного хранилища
    private fun loadApiKeys() {
        viewModelScope.launch {
            _apiKeys.value = encryptedKeyStorage.getAllKeys()
        }
    }
    
    private fun checkDriveConnection() {
        viewModelScope.launch {
            val isConnected = driveRepository.isSignedIn()
            if (isConnected) {
                when (val result = driveRepository.signIn()) {
                    is com.docs.scanner.domain.model.Result.Success -> {
                        _driveEmail.value = result.data
                    }
                    else -> {
                        _driveEmail.value = null
                    }
                }
            }
        }
    }
    
    // Добавить API ключ в зашифрованное хранилище
    fun addApiKey(key: String, label: String?) {
        viewModelScope.launch {
            try {
                if (!isValidApiKey(key)) {
                    _saveMessage.value = "✗ Invalid API key format"
                    return@launch
                }
                
                val trimmedKey = key.trim()
                
                // Добавляем новый активный ключ
                val newKey = ApiKeyData(
                    id = System.currentTimeMillis().toString(),
                    key = trimmedKey,
                    label = label?.ifBlank { null },
                    isActive = true,
                    createdAt = System.currentTimeMillis()
                )
                
                // Сохраняем в зашифрованное хранилище
                encryptedKeyStorage.addKey(newKey)
                encryptedKeyStorage.setActiveApiKey(trimmedKey)
                
                // Сохраняем в DataStore для обратной совместимости
                settingsRepository.setApiKey(trimmedKey)
                
                // Обновляем список
                loadApiKeys()
                
                _saveMessage.value = "✓ API key added successfully"
            } catch (e: Exception) {
                _saveMessage.value = "✗ Failed to add key: ${e.message}"
            }
        }
    }
    
    // Активировать ключ
    fun activateKey(keyId: String) {
        viewModelScope.launch {
            try {
                val key = _apiKeys.value.find { it.id == keyId }
                if (key != null) {
                    encryptedKeyStorage.setActiveApiKey(key.key)
                    
                    // Обновляем DataStore
                    settingsRepository.setApiKey(key.key)
                    
                    // Обновляем список
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
    
    // Удалить ключ
    fun deleteKey(keyId: String) {
        viewModelScope.launch {
            try {
                encryptedKeyStorage.deleteKey(keyId) // ✅ ИСПРАВЛЕНО
                
                // Обновляем список
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
    
    fun signInGoogleDrive(context: Context, launcher: ActivityResultLauncher<Intent>) {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
                .build()
            
            val client = GoogleSignIn.getClient(context, gso)
            launcher.launch(client.signInIntent)
            
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
                            }
                            is com.docs.scanner.domain.model.Result.Error -> {
                                _backupMessage.value = "✗ Connection failed: ${result.exception.message}"
                            }
                            else -> {
                                _backupMessage.value = "✗ Connection failed"
                            }
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
    
    fun backupToGoogleDrive() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Backing up..."
            
            try {
                if (!driveRepository.isSignedIn()) {
                    _backupMessage.value = "✗ Not signed in to Google Drive"
                    _isBackingUp.value = false
                    return@launch
                }
                
                when (val result = driveRepository.uploadBackup()) {
                    is com.docs.scanner.domain.model.Result.Success -> {
                        _backupMessage.value = "✓ Backup completed successfully"
                    }
                    is com.docs.scanner.domain.model.Result.Error -> {
                        _backupMessage.value = "✗ Backup failed: ${result.exception.message}"
                    }
                    else -> {
                        _backupMessage.value = "✗ Backup failed"
                    }
                }
            } catch (e: Exception) {
                _backupMessage.value = "✗ Backup error: ${e.message}"
            } finally {
                _isBackingUp.value = false
            }
        }
    }
    
    fun restoreFromGoogleDrive() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Fetching backups..."
            
            try {
                if (!driveRepository.isSignedIn()) {
                    _backupMessage.value = "✗ Not signed in to Google Drive"
                    _isBackingUp.value = false
                    return@launch
                }
                
                val backupsResult = driveRepository.listBackups()
                
                if (backupsResult is com.docs.scanner.domain.model.Result.Success && backupsResult.data.isNotEmpty()) {
                    val latestBackup = backupsResult.data.first()
                    
                    _backupMessage.value = "Restoring from ${latestBackup.fileName}..."
                    
                    when (val result = driveRepository.restoreBackup(latestBackup.fileId)) {
                        is com.docs.scanner.domain.model.Result.Success -> {
                            _backupMessage.value = "✓ Restore completed! Restart app to apply changes."
                        }
                        is com.docs.scanner.domain.model.Result.Error -> {
                            _backupMessage.value = "✗ Restore failed: ${result.exception.message}"
                        }
                        else -> {
                            _backupMessage.value = "✗ Restore failed"
                        }
                    }
                } else {
                    _backupMessage.value = "✗ No backups found"
                }
            } catch (e: Exception) {
                _backupMessage.value = "✗ Restore error: ${e.message}"
            } finally {
                _isBackingUp.value = false
            }
        }
    }
    
    fun signOutGoogleDrive() {
        viewModelScope.launch {
            driveRepository.signOut()
            _driveEmail.value = null
            _backupMessage.value = "Disconnected from Google Drive"
        }
    }
    
    private fun isValidApiKey(key: String): Boolean {
        return key.matches(Regex("^AIza[A-Za-z0-9_-]{35}$"))
    }
}
