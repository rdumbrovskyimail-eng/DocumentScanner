/*
 * GoogleDocsButton.kt
 * Version: 2.0.0 - PRODUCTION READY
 * 
 * ВНИМАНИЕ: MicroButton удалён! Используйте из MicroButton.kt
 */

package com.docs.scanner.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.GoogleDocsButtonHover
import com.docs.scanner.presentation.theme.GoogleDocsButtonText

/**
 * Кнопка "три точки" для меню
 */
@Composable
fun MoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor = if (isPressed) {
        GoogleDocsButtonHover
    } else {
        Color.Transparent
    }
    
    Box(
        modifier = modifier
            .size(32.dp)
            .background(
                color = backgroundColor,
                shape = MaterialTheme.shapes.extraSmall
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = GoogleDocsButtonText,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                )
            }
        }
    }
}