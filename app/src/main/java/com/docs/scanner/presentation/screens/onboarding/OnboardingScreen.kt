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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.checkFirstLaunch { onComplete() }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer // Исправлено 1
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
            horizontalAlignment = Alignment.Start // Исправлено 2
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp), // Исправлено 3
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp)) // Исправлено 4
                Column {
                    Text(
                        text = "Document Scanner",
                        style = MaterialTheme.typography.headlineMedium // Исправлено 5
                    )
                    Text(
                        text = "Scan, recognize, and translate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp)) // Исправлено 6
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween // Исправлено 7
            ) {
                CompactFeature(Icons.Default.CameraAlt, "Scan")
                CompactFeature(Icons.Default.TextFields, "OCR")
                CompactFeature(Icons.Default.Translate, "Translate")
            }
            
            Spacer(modifier = Modifier.height(32.dp)) // Исправлено 8
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant // Исправлено 9
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gemini API Key Required",
                        style = MaterialTheme.typography.titleLarge, // Исправлено 10
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp)) // Исправлено 11
                    
                    Text(
                        text = "Enter your Google Gemini API key to enable translation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp)) // Исправлено 12
                    
                    var showPassword by rememberSaveable { mutableStateOf(false) } // Исправлено 13
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = viewModel::updateApiKey,
                        label = { Text("API Key") },
                        placeholder = { Text("AIza...") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), // Исправлено 14
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, // Исправлено 15
                                    contentDescription = if (showPassword) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        singleLine = true, // Исправлено 16
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { 
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://aistudio.google.com/app/apikey") // Исправлено 17
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Get free API key")
                        }
                        
                        Button(
                            onClick = { viewModel.saveAndContinue(onComplete) },
                            enabled = apiKey.isNotBlank() && !isLoading // Исправлено 18
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 3.dp // Исправлено 19
                                )
                            } else {
                                Text("Continue")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp), // Исправлено 20
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, style = MaterialTheme.typography.labelMedium)
    }
}