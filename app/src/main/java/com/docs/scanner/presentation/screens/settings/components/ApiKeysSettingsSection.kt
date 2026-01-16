package com.docs.scanner.presentation.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docs.scanner.data.local.security.ApiKeyEntry

/**
 * Settings section for managing multiple Gemini API keys.
 * Supports add, remove, set primary, and view error status.
 */
@Composable
fun ApiKeysSettingsSection(
    keys: List<ApiKeyEntry>,
    isLoading: Boolean,
    onAddKey: (key: String, label: String) -> Unit,
    onRemoveKey: (key: String) -> Unit,
    onSetPrimary: (key: String) -> Unit,
    onResetErrors: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<ApiKeyEntry?>(null) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gemini API Keys",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Add multiple keys for automatic failover",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (keys.any { it.errorCount > 0 }) {
                IconButton(onClick = onResetErrors) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset errors",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Loading state
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading keys...")
            }
        }
        
        // Keys list
        if (keys.isEmpty() && !isLoading) {
            EmptyKeysCard(onAddClick = { showAddDialog = true })
        } else {
            keys.forEachIndexed { index, entry ->
                ApiKeyCard(
                    entry = entry,
                    isPrimary = index == 0,
                    onSetPrimary = { onSetPrimary(entry.key) },
                    onRemove = { keyToDelete = entry }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        // Add button
        if (keys.size < 5 && keys.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Backup Key")
            }
        }
        
        // Info text
        if (keys.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "💡 If the primary key fails or hits rate limits, backup keys are used automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    // Add key dialog
    if (showAddDialog) {
        AddKeyDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { key, label ->
                onAddKey(key, label)
                showAddDialog = false
            }
        )
    }
    
    // Delete confirmation dialog
    keyToDelete?.let { entry ->
        DeleteKeyDialog(
            entry = entry,
            onDismiss = { keyToDelete = null },
            onConfirm = {
                onRemoveKey(entry.key)
                keyToDelete = null
            }
        )
    }
}

@Composable
private fun EmptyKeysCard(
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No API Keys",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Add a Gemini API key to enable translation and handwriting recognition",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add API Key")
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    entry: ApiKeyEntry,
    isPrimary: Boolean,
    onSetPrimary: () -> Unit,
    onRemove: () -> Unit
) {
    val hasErrors = entry.errorCount > 0
    val isInactive = !entry.isActive
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isInactive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                hasErrors -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                isPrimary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Key icon with status
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = when {
                    isInactive -> MaterialTheme.colorScheme.error
                    hasErrors -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    isPrimary -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Key info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.label.ifEmpty { "API Key" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (isPrimary) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text("Primary", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    
                    if (isInactive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Text("Inactive", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                Text(
                    text = entry.maskedKey,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Error info
                AnimatedVisibility(
                    visible = hasErrors,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${entry.errorCount} error${if (entry.errorCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Actions
            if (!isPrimary && entry.isActive) {
                IconButton(onClick = onSetPrimary) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = "Set as primary",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (isPrimary) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Primary key",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove key",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun AddKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (key: String, label: String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val focusManager = LocalFocusManager.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add API Key") },
        text = {
            Column {
                Text(
                    text = "Get your API key from Google AI Studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { 
                        apiKey = it.trim()
                        error = null
                    },
                    label = { Text("API Key") },
                    placeholder = { Text("AIza...") },
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g., Personal, Work") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        apiKey.isBlank() -> error = "API key is required"
                        apiKey.length < 20 -> error = "Invalid API key format"
                        else -> onConfirm(apiKey, label)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteKeyDialog(
    entry: ApiKeyEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove API Key?") },
        text = {
            Text("Remove \"${entry.label.ifEmpty { entry.maskedKey }}\"? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}