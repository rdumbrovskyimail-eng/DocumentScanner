package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.*

// ============================================
// FLOATING ACTION BUTTONS (Google Docs Style)
// ============================================

@Composable
fun FloatingActionButtons(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gallery Button (Secondary)
        SecondaryFab(
            onClick = onGalleryClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        
        // Camera Button (Primary)
        PrimaryFab(
            onClick = onCameraClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    modifier = Modifier.size(24.dp)
                )
            }
        )
    }
}

@Composable
private fun PrimaryFab(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "fab_scale"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 4.dp.value else 8.dp.value,
        label = "fab_elevation"
    )
    
    Box(
        modifier = modifier
            .size(56.dp)
            .scale(scale)
            .shadow(
                elevation = elevation.dp,
                shape = CircleShape,
                spotColor = GoogleDocsFabShadow
            )
            .background(
                color = GoogleDocsFabPrimary,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // ✅ ИСПРАВЛЕНО: правильное использование CompositionLocalProvider
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            icon()
        }
    }
}

@Composable
private fun SecondaryFab(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "fab_scale"
    )
    
    val backgroundColor = if (isPressed) GoogleDocsButtonHover else GoogleDocsFabSecondary
    
    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .shadow(
                elevation = 2.dp,
                shape = CircleShape
            )
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .border(
                width = 1.5.dp,
                color = GoogleDocsBorder,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // ✅ ИСПРАВЛЕНО: правильное использование CompositionLocalProvider
        CompositionLocalProvider(LocalContentColor provides GoogleDocsPrimary) {
            icon()
        }
    }
}
