package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.*

// ============================================
// TRANSLATION FIELD (Google Docs Style)
// ============================================

@Composable
fun TranslationField(
    translatedText: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !translatedText.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = GoogleDocsTranslationBackground,
                    shape = MaterialTheme.shapes.small
                )
                .border(
                    width = 1.5.dp,
                    color = GoogleDocsTranslationBorder,
                    shape = MaterialTheme.shapes.small
                )
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Translation",
                        modifier = Modifier.size(20.dp),
                        tint = GoogleDocsTranslationIcon
                    )
                    Text(
                        text = "Translation",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoogleDocsTranslationTitle
                    )
                }
                
                // Content
                Text(
                    text = translatedText ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = GoogleDocsTranslationText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 300.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
