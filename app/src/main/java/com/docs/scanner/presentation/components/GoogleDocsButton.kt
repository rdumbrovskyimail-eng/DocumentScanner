package com.docs.scanner.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.GoogleDocsButtonBackground
import com.docs.scanner.presentation.theme.GoogleDocsButtonBorder
import com.docs.scanner.presentation.theme.GoogleDocsButtonHover
import com.docs.scanner.presentation.theme.GoogleDocsButtonText

// ============================================
// MICRO BUTTON (Google Docs Style)
// ============================================

@Composable
fun MicroButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "button_scale"
    )
    
    val backgroundColor = if (isPressed) GoogleDocsButtonHover else GoogleDocsButtonBackground
    
    Box(
        modifier = modifier
            .scale(scale)
            .height(32.dp)
            .background(
                color = backgroundColor,
                shape = MaterialTheme.shapes.medium
            )
            .border(
                width = 1.dp,
                color = GoogleDocsButtonBorder,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(14.dp),
                tint = GoogleDocsButtonText
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = GoogleDocsButtonText
            )
        }
    }
}

// ============================================
// MORE BUTTON (3 dots)
// ============================================

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
