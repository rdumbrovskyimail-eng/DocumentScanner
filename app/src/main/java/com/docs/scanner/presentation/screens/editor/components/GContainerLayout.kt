package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.GoogleDocsBorder
import com.docs.scanner.presentation.theme.GoogleDocsSurface

// ============================================
// G-CONTAINER LAYOUT (Google Docs Style)
// ============================================
// This creates the "G-shaped" layout:
// ┌─────────┬─────────┐
// │ Preview │   OCR   │  <- 48% each
// │  +AI    │  Text   │
// └─────────┴─────────┘
// └─ Action Buttons ──┘  <- Full width

@Composable
fun GContainerLayout(
    previewContent: @Composable () -> Unit,
    ocrTextContent: @Composable () -> Unit,
    actionButtonsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = GoogleDocsSurface,
                shape = MaterialTheme.shapes.extraLarge
            )
            .border(
                width = 1.5.dp,
                color = GoogleDocsBorder,
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top row: Preview (left) + OCR Text (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left: Preview (48%)
                Box(modifier = Modifier.weight(0.48f)) {
                    previewContent()
                }
                
                // Right: OCR Text (48%)
                Box(modifier = Modifier.weight(0.48f)) {
                    ocrTextContent()
                }
            }
            
            // Bottom: Action Buttons (full width)
            actionButtonsContent()
        }
    }
}
