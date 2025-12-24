package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.GoogleDocsBackground
import com.docs.scanner.presentation.theme.GoogleDocsBorderDark
import com.docs.scanner.presentation.theme.GoogleDocsTextPrimary

// ============================================
// OCR TEXT CONTAINER (Google Docs Style)
// ============================================

@Composable
fun OCRTextContainer(
    text: String?,
    onTextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f) // Match document preview aspect ratio
                .background(
                    color = GoogleDocsBackground,
                    shape = MaterialTheme.shapes.large
                )
                .border(
                    width = 1.5.dp,
                    color = GoogleDocsBorderDark,
                    shape = MaterialTheme.shapes.large
                )
                .clickable(onClick = onTextClick)
                .padding(16.dp)
        ) {
            Text(
                text = text ?: "",
                style = MaterialTheme.typography.bodySmall, // Times New Roman style
                color = GoogleDocsTextPrimary,
                textAlign = TextAlign.Justify,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
