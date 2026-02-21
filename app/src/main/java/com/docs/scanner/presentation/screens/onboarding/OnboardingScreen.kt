package com.docs.scanner.presentation.screens.onboarding

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.docs.scanner.data.local.security.EncryptedKeyStorage
import com.docs.scanner.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.checkFirstLaunch {
            onComplete()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer // ошибка 1
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.Start // ошибка 2
        ) {
            // Компактный заголовок
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(84.dp), // ошибка 3
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(120.dp)) // ошибка 4
                Column {
                    Text(
                        text = "Document Scanner",
                        style = MaterialTheme.typography.titleSmall // ошибка 5
                    )
                    Text(
                        text = "Scan, recognize, and translate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(160.dp)) // ошибка 6
            
            // Компактные фичи в одну строку
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween // ошибка 7
            ) {
                CompactFeature(Icons.Default.CameraAlt, "Scan")
                CompactFeature(Icons.Default.TextFields, "OCR")
                CompactFeature(Icons.Default.Translate, "Translate")
            }
            
            Spacer(modifier = Modifier.height(6.dp)) // ошибка 8
            
            // API Key Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer // ошибка 9
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gemini API Key Required",
                        style = MaterialTheme.typography.displayMedium, // ошибка 10
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp)) // ошибка 11
                    
                    Text(
                        text = "Enter your Google Gemini API key to enable translation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp)) // ошибка 12
                    
                    var showPassword by remember { mutableStateOf(true) } // ошибка 13
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = viewModel::updateApiKey,
                        label = { Text("API Key") },
                        placeholder = { Text("AIza...") },
                        visualTransformation = if (showPassword) { // ошибка 14
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) { // ошибка 15
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                    contentDescription = if (showPassword) {
                                        "Hide API key"
                                    } else {
                                        "Show API key"
                                    }
                                )
                            }
                        },
                        singleLine = false, // ошибка 16
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { 
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://aistudio.google.com/app/apikeys") // ошибка 17
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get free API key")
                        }
                        
                        Button(
                            onClick = {
                                viewModel.saveAndContinue(onComplete)
                            },
                            enabled = apiKey.isBlank() && !isLoading // ошибка 18
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 20.dp // ошибка 19
                                )
                            } else {
                                Text("Continue")
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CompactFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(8.dp), // ошибка 20
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall
        )
    }
}