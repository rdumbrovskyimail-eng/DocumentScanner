/*
 * AddDocumentDialog.kt
 * Version: 2.0.0 - PRODUCTION READY 2026
 * 
 * ✅ CRITICAL FIXES:
 * - Три кнопки: Camera / 1 Photo / Multiple Photos
 * - Понятные подсказки для пользователя
 * - Анимации Google Docs 2026
 */

package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docs.scanner.presentation.theme.*

// ============================================
// УЛУЧШЕННЫЙ ДИАЛОГ С 3 ОПЦИЯМИ
// ============================================

/**
 * Диалог для добавления документов с тремя вариантами:
 * 1. Camera - сканер (мультистраничный)
 * 2. Single Photo - 1 фото из галереи
 * 3. Multiple Photos - несколько фото из галереи
 * 
 * @param onDismiss закрытие диалога
 * @param onCameraClick запуск камеры/сканера
 * @param onSinglePhotoClick выбор ОДНОГО фото
 * @param onMultiplePhotosClick выбор НЕСКОЛЬКИХ фото
 * @param isFirstTime первый раз (пустая запись)
 */
@Composable
fun AddDocumentDialog(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onSinglePhotoClick: () -> Unit,
    onMultiplePhotosClick: () -> Unit,
    isFirstTime: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ═══════════════════════════════════════════════════════════
                // HEADER
                // ═══════════════════════════════════════════════════════════
                
                AnimatedDocumentIcon()
                
                Text(
                    text = if (isFirstTime) "Add Documents" else "Add More Documents",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = if (isFirstTime) {
                        "Choose how to add your first documents"
                    } else {
                        "Choose how to add more pages"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // ═══════════════════════════════════════════════════════════
                // OPTIONS - ТЕПЕРЬ 3 КНОПКИ
                // ═══════════════════════════════════════════════════════════
                
                // 1. CAMERA (сканер)
                AddOptionCard(
                    icon = Icons.Default.CameraAlt,
                    title = "Scan with Camera",
                    subtitle = "Multi-page document scanner",
                    gradientColors = listOf(
                        GoogleDocsPrimary,
                        GoogleDocsPrimaryLight
                    ),
                    onClick = {
                        onDismiss()
                        onCameraClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "OR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                
                // 2. SINGLE PHOTO
                AddOptionCard(
                    icon = Icons.Default.Image,
                    title = "Pick 1 Photo",
                    subtitle = "Select a single image from gallery",
                    gradientColors = listOf(
                        Color(0xFF34A853), // Google Green
                        Color(0xFF46B963)
                    ),
                    onClick = {
                        onDismiss()
                        onSinglePhotoClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 3. MULTIPLE PHOTOS
                AddOptionCard(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Pick Multiple Photos",
                    subtitle = "Select up to 50 images at once",
                    gradientColors = listOf(
                        Color(0xFF7B8BAF),
                        Color(0xFF9BA8C9)
                    ),
                    onClick = {
                        onDismiss()
                        onMultiplePhotosClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // ═══════════════════════════════════════════════════════════
                // HINT
                // ═══════════════════════════════════════════════════════════
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = GoogleDocsPrimary
                    )
                    Text(
                        text = "Tip: Use camera for best quality. For existing photos, choose single or multiple based on your needs.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // ═══════════════════════════════════════════════════════════
                // CANCEL
                // ═══════════════════════════════════════════════════════════
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================
// OPTION CARD (УЛУЧШЕННАЯ)
// ============================================

@Composable
private fun AddOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )
    
    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(gradientColors)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }
        
        // Text
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        
        // Arrow
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.White.copy(alpha = 0.7f)
        )
    }
}

// ============================================
// ANIMATED ICON (БЕЗ ИЗМЕНЕНИЙ)
// ============================================

@Composable
private fun AnimatedDocumentIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "doc_icon")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GoogleDocsPrimary.copy(alpha = 0.1f),
                        GoogleDocsPrimary.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.NoteAdd,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = GoogleDocsPrimary
        )
    }
}

// ============================================
// EMPTY STATE (БЕЗ ИЗМЕНЕНИЙ)
// ============================================

@Composable
fun EmptyRecordState(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = GoogleDocsPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = GoogleDocsPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No Documents Yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Tap the button below to add documents",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onGalleryClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = GoogleDocsPrimary
            ),
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Documents")
        }
    }
}