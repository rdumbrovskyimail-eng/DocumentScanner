package com.docs.scanner.presentation.screens.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.remote.drive.DriveRepository
import com.docs.scanner.domain.repository.SettingsRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val driveRepository: DriveRepository
) : ViewModel() {
    
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    
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
        loadSettings()
        checkDriveConnection()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            val key = settingsRepository.getApiKey()
            _apiKey.value = key ?: ""
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
    
    fun updateApiKey(key: String) {
        _apiKey.value = key
        _saveMessage.value = ""
    }
    
    fun saveApiKey() {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = ""
            
            try {
                val key = _apiKey.value.trim()
                
                if (!isValidApiKey(key)) {
                    _saveMessage.value = "✗ Invalid API key format"
                    _isSaving.value = false
                    return@launch
                }
                
                settingsRepository.setApiKey(key)
                _saveMessage.value = "✓ API key saved successfully"
            } catch (e: Exception) {
                _saveMessage.value = "✗ Failed to save: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    fun copyApiKey(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("API Key", _apiKey.value)
            clipboard.setPrimaryClip(clip)
            _saveMessage.value = "✓ API key copied to clipboard"
        } catch (e: Exception) {
            _saveMessage.value = "✗ Failed to copy: ${e.message}"
        }
    }
    
    // ✅ ИСПРАВЛЕНО: Sign In через Google Drive
    fun signInGoogleDrive(context: Context, launcher: ActivityResultLauncher<Intent>) {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
                .build()
            
            val client = GoogleSignIn.getClient(context, gso)
            
            // ✅ Сначала signOut для очистки предыдущих сессий
            viewModelScope.launch {
                try {
                    client.signOut().addOnCompleteListener {
                        launcher.launch(client.signInIntent)
                    }
                } catch (e: Exception) {
                    launcher.launch(client.signInIntent)
                }
            }
        } catch (e: Exception) {
            _backupMessage.value = "✗ Failed to start sign in: ${e.message}"
        }
    }
    
    fun handleSignInResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            try {
                if (resultCode == Activity.RESULT_OK) {
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
                    _backupMessage.value = "Sign in cancelled"
                }
            } catch (e: Exception) {
                _backupMessage.value = "✗ Connection failed: ${e.message}"
            }
        }
    }
    
    // ✅ ИСПРАВЛЕНО: Backup с проверкой подключения
    fun backupToGoogleDrive() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Backing up..."
            
            try {
                // Проверяем подключение
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
    
    // ✅ ИСПРАВЛЕНО: Restore с проверкой подключения
    fun restoreFromGoogleDrive() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Fetching backups..."
            
            try {
                // Проверяем подключение
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
