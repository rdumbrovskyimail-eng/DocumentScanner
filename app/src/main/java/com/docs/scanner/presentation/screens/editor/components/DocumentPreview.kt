package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.presentation.components.AILoadingIndicator
import com.docs.scanner.presentation.theme.GoogleDocsBackground
import com.docs.scanner.presentation.theme.GoogleDocsBorderDark
import java.io.File

// ============================================
// DOCUMENT PREVIEW (Google Docs Style)
// ============================================

@Composable
fun DocumentPreview(
    document: Document,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Document Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f) // A4 aspect ratio
                .clip(MaterialTheme.shapes.large)
                .background(GoogleDocsBackground)
                .border(
                    width = 1.5.dp,
                    color = GoogleDocsBorderDark,
                    shape = MaterialTheme.shapes.large
                )
                .clickable(onClick = onImageClick),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(LocalContext.current.filesDir, document.imagePath))
                    .crossfade(300)
                    .build(),
                contentDescription = "Document preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        
        // AI Loading indicator
        AnimatedVisibility(
            visible = document.processingStatus == ProcessingStatus.OCR_IN_PROGRESS ||
                     document.processingStatus == ProcessingStatus.TRANSLATION_IN_PROGRESS,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AILoadingIndicator(
                text = when (document.processingStatus) {
                    ProcessingStatus.OCR_IN_PROGRESS -> "AI is processing..."
                    ProcessingStatus.TRANSLATION_IN_PROGRESS -> "Translating..."
                    else -> "Processing..."
                }
            )
        }
    }
}
