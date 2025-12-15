package com.docs.scanner.presentation.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.remote.drive.DriveRepository
import com.docs.scanner.domain.repository.SettingsRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val apiKey by viewModel.apiKey.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val driveEmail by viewModel.driveEmail.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemini API Key",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    var showPassword by remember { mutableStateOf(false) }
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = viewModel::updateApiKey,
                        label = { Text("API Key") },
                        placeholder = { Text("AIza...") },
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { }) {
                            Icon(
                                Icons.Default.OpenInNew,contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get API key")
                        }
                        
                        Button(
                            onClick = viewModel::saveApiKey,
                            enabled = apiKey.isNotBlank() && !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Save")
                        }
                    }
                    
                    if (saveMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = saveMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Google Drive Backup",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (driveEmail != null) {
                        Text(
                            text = "Connected: $driveEmail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.backupToGoogleDrive() },
                                enabled = !isBackingUp,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isBackingUp) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup")
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.restoreFromGoogleDrive() },
                                enabled = !isBackingUp,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { viewModel.signOutGoogleDrive() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                                    .build()
                                
                                val client = GoogleSignIn.getClient(context, gso)
                                signInLauncher.launch(client.signInIntent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Google Drive")
                        }
                    }
                    
                    if (backupMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = backupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (backupMessage.contains("✓")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Document Scanner v2.0.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Scan, recognize, and translate documents using ML Kit and Gemini AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

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
                settingsRepository.setApiKey(_apiKey.value)
                _saveMessage.value = "✓ API key saved successfully"
            } catch (e: Exception) {
                _saveMessage.value = "✗ Failed to save: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val result = driveRepository.signIn()
                if (result is com.docs.scanner.domain.model.Result.Success) {
                    _driveEmail.value = result.data
                    _backupMessage.value = "✓ Connected to Google Drive"
                }
            } catch (e: Exception) {
                _backupMessage.value = "✗ Connection failed: ${e.message}"
            }
        }
    }
    
    fun backupToGoogleDrive() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Backing up..."
            
            when (val result = driveRepository.uploadBackup()) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    _backupMessage.value = "✓ Backup completed successfully"
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    _backupMessage.value = "✗ Backup failed: ${result.exception.message}"
                }
                else -> {}
            }
            
            _isBackingUp.value = false
        }
    }
    
    fun restoreFromGoogleDrive() {
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupMessage.value = "Restoring..."
            
            val backups = driveRepository.listBackups()
            if (backups is com.docs.scanner.domain.model.Result.Success && backups.data.isNotEmpty()) {
                val latestBackup = backups.data.first()
                
                when (val result = driveRepository.restoreBackup(latestBackup.fileId)) {
                    is com.docs.scanner.domain.model.Result.Success -> {
                        _backupMessage.value = "✓ Restore completed successfully"
                    }
                    is com.docs.scanner.domain.model.Result.Error -> {
                        _backupMessage.value = "✗ Restore failed: ${result.exception.message}"
                    }
                    else -> {}
                }
            } else {
                _backupMessage.value = "✗ No backups found"
            }
            
            _isBackingUp.value = false
        }
    }
    
    fun signOutGoogleDrive() {
        viewModelScope.launch {
            driveRepository.signOut()
            _driveEmail.value = null
            _backupMessage.value = "Disconnected from Google Drive"
        }
    }
}