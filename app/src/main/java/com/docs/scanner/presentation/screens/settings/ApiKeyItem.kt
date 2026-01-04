package com.docs.scanner.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docs.scanner.data.local.security.ApiKeyData

@Composable
fun ApiKeyItem(
    key: ApiKeyData,
    onActivate: (String) -> Unit,
    onCopy: (android.content.Context, String) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key.label ?: "Key • ${key.key.take(6)}…${key.key.takeLast(4)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.weight(1f))
        if (key.isActive) {
            Icon(Icons.Default.Check, contentDescription = "Active")
        } else {
            IconButton(onClick = { onActivate(key.id) }) {
                Icon(Icons.Default.Check, contentDescription = "Set active")
            }
        }
        IconButton(onClick = { onCopy(context, key.key) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
        }
        IconButton(onClick = { onDelete(key.id) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

