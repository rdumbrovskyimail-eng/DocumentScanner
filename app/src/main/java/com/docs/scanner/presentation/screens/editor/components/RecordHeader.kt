package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.GoogleDocsTextSecondary
import com.docs.scanner.presentation.theme.GoogleDocsTextTertiary

@Composable
fun RecordHeader(
    name: String,
    description: String?,
    onNameClick: () -> Unit,
    onDescriptionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Name Row (Bold, clickable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNameClick() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit name",
                modifier = Modifier.size(18.dp),
                tint = GoogleDocsTextTertiary
            )
        }
        
        // Description Row (Smaller, expandable, clickable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDescriptionClick() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = description?.ifBlank { "Add description..." } ?: "Add description...",
                style = MaterialTheme.typography.bodyMedium,
                color = if (description.isNullOrBlank()) GoogleDocsTextTertiary else GoogleDocsTextSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit description",
                modifier = Modifier.size(16.dp),
                tint = GoogleDocsTextTertiary
            )
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}