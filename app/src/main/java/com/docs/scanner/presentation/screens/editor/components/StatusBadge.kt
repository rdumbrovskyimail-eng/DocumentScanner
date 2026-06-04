package com.docs.scanner.presentation.screens.editor.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docs.scanner.domain.core.ProcessingStatus
import com.docs.scanner.presentation.theme.*

// ============================================
// STATUS BADGE (Google Docs Style 2026)
// ============================================

/**
 * Компактный индикатор статуса для отображения на карточке документа.
 * Показывает текущее состояние обработки: Pending, Processing, Complete, Error
 */
@Composable
fun StatusBadge(
    status: ProcessingStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false
) {
    val statusInfo = status.toStatusInfo()
    
    Row(
        modifier = modifier
            .shadow(2.dp, CircleShape)
            .background(
                color = statusInfo.backgroundColor,
                shape = if (showLabel) MaterialTheme.shapes.medium else CircleShape
            )
            .border(
                width = 1.5.dp,
                color = statusInfo.borderColor,
                shape = if (showLabel) MaterialTheme.shapes.medium else CircleShape
            )
            .padding(
                horizontal = if (showLabel) 8.dp else 6.dp,
                vertical = if (showLabel) 4.dp else 6.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated icon for processing states
        if (status.isInProgress) {
            PulsingIcon(
                icon = statusInfo.icon,
                tint = statusInfo.iconColor,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                imageVector = statusInfo.icon,
                contentDescription = statusInfo.label,
                tint = statusInfo.iconColor,
                modifier = Modifier.size(14.dp)
            )
        }
        
        if (showLabel) {
            Text(
                text = statusInfo.label,
                style = MaterialTheme.typography.labelSmall,
                color = statusInfo.textColor,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Минималистичный круглый индикатор (только цвет)
 */
@Composable
fun StatusDot(
    status: ProcessingStatus,
    modifier: Modifier = Modifier
) {
    val statusInfo = status.toStatusInfo()
    
    if (status.isInProgress) {
        PulsingDot(
            color = statusInfo.dotColor,
            modifier = modifier.size(10.dp)
        )
    } else {
        Box(
            modifier = modifier
                .size(10.dp)
                .shadow(1.dp, CircleShape)
                .background(statusInfo.dotColor, CircleShape)
                .border(1.dp, statusInfo.borderColor, CircleShape)
        )
    }
}

/**
 * Большой статус для отображения в центре при обработке
 */
@Composable
fun StatusOverlay(
    status: ProcessingStatus,
    progress: Int = 0,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    if (!status.isInProgress && !status.isFailed) return
    
    val statusInfo = status.toStatusInfo()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large
                )
                .padding(24.dp)
        ) {
            if (status.isInProgress) {
                PulsingIcon(
                    icon = statusInfo.icon,
                    tint = statusInfo.iconColor,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = statusInfo.icon,
                    contentDescription = null,
                    tint = statusInfo.iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Text(
                text = message ?: statusInfo.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (status.isInProgress && progress > 0) {
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================
// ANIMATED COMPONENTS
// ============================================

@Composable
private fun PulsingIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_alpha"
    )
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint.copy(alpha = alpha),
        modifier = modifier
    )
}

@Composable
private fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    
    Box(
        modifier = modifier
            .shadow(1.dp, CircleShape)
            .background(color.copy(alpha = alpha), CircleShape)
    )
}

// ============================================
// STATUS INFO DATA CLASS
// ============================================

private data class StatusInfo(
    val icon: ImageVector,
    val label: String,
    val dotColor: Color,
    val iconColor: Color,
    val textColor: Color,
    val backgroundColor: Color,
    val borderColor: Color
)

private fun ProcessingStatus.toStatusInfo(): StatusInfo = when (this) {
    ProcessingStatus.Pending -> StatusInfo(
        icon = Icons.Default.Schedule,
        label = "Pending",
        dotColor = Color(0xFF9E9E9E),
        iconColor = Color(0xFF757575),
        textColor = Color(0xFF616161),
        backgroundColor = Color(0xFFF5F5F5),
        borderColor = Color(0xFFE0E0E0)
    )
    
    ProcessingStatus.Queued -> StatusInfo(
        icon = Icons.Default.HourglassEmpty,
        label = "Queued",
        dotColor = Color(0xFF90A4AE),
        iconColor = Color(0xFF607D8B),
        textColor = Color(0xFF546E7A),
        backgroundColor = Color(0xFFECEFF1),
        borderColor = Color(0xFFCFD8DC)
    )
    
    is ProcessingStatus.Ocr.InProgress -> StatusInfo(
        icon = Icons.Default.DocumentScanner,
        label = "Scanning...",
        dotColor = GoogleDocsWarning,
        iconColor = Color(0xFFFF9800),
        textColor = Color(0xFFE65100),
        backgroundColor = Color(0xFFFFF3E0),
        borderColor = Color(0xFFFFE0B2)
    )
    
    ProcessingStatus.Ocr.Complete -> StatusInfo(
        icon = Icons.Default.TextFields,
        label = "OCR Done",
        dotColor = Color(0xFF66BB6A),
        iconColor = Color(0xFF43A047),
        textColor = Color(0xFF2E7D32),
        backgroundColor = Color(0xFFE8F5E9),
        borderColor = Color(0xFFC8E6C9)
    )
    
    ProcessingStatus.Ocr.Failed -> StatusInfo(
        icon = Icons.Default.ErrorOutline,
        label = "OCR Failed",
        dotColor = GoogleDocsError,
        iconColor = Color(0xFFE53935),
        textColor = Color(0xFFC62828),
        backgroundColor = Color(0xFFFFEBEE),
        borderColor = Color(0xFFFFCDD2)
    )
    
    is ProcessingStatus.Translation.InProgress -> StatusInfo(
        icon = Icons.Default.Translate,
        label = "Translating...",
        dotColor = GoogleDocsWarning,
        iconColor = Color(0xFFFF9800),
        textColor = Color(0xFFE65100),
        backgroundColor = Color(0xFFFFF3E0),
        borderColor = Color(0xFFFFE0B2)
    )
    
    ProcessingStatus.Translation.Complete -> StatusInfo(
        icon = Icons.Default.Translate,
        label = "Translated",
        dotColor = GoogleDocsSuccess,
        iconColor = Color(0xFF43A047),
        textColor = Color(0xFF2E7D32),
        backgroundColor = Color(0xFFE8F5E9),
        borderColor = Color(0xFFC8E6C9)
    )
    
    ProcessingStatus.Translation.Failed -> StatusInfo(
        icon = Icons.Outlined.Translate,
        label = "Translation Failed",
        dotColor = Color(0xFFFF9800),
        iconColor = Color(0xFFEF6C00),
        textColor = Color(0xFFE65100),
        backgroundColor = Color(0xFFFFF3E0),
        borderColor = Color(0xFFFFE0B2)
    )
    
    ProcessingStatus.Complete -> StatusInfo(
        icon = Icons.Default.CheckCircle,
        label = "Complete",
        dotColor = GoogleDocsSuccess,
        iconColor = Color(0xFF43A047),
        textColor = Color(0xFF2E7D32),
        backgroundColor = Color(0xFFE8F5E9),
        borderColor = Color(0xFFC8E6C9)
    )
    
    ProcessingStatus.Cancelled -> StatusInfo(
        icon = Icons.Default.Cancel,
        label = "Cancelled",
        dotColor = Color(0xFF9E9E9E),
        iconColor = Color(0xFF757575),
        textColor = Color(0xFF616161),
        backgroundColor = Color(0xFFF5F5F5),
        borderColor = Color(0xFFE0E0E0)
    )
    
    ProcessingStatus.Error -> StatusInfo(
        icon = Icons.Default.Error,
        label = "Error",
        dotColor = GoogleDocsError,
        iconColor = Color(0xFFE53935),
        textColor = Color(0xFFC62828),
        backgroundColor = Color(0xFFFFEBEE),
        borderColor = Color(0xFFFFCDD2)
    )
}

// ============================================
// HELPER EXTENSIONS
// ============================================

/**
 * Проверяет, можно ли показать кнопку Retry для данного статуса
 */
fun ProcessingStatus.canRetryOcr(): Boolean = this is ProcessingStatus.Ocr.Failed || this is ProcessingStatus.Error

fun ProcessingStatus.canRetryTranslation(): Boolean = this is ProcessingStatus.Translation.Failed

fun ProcessingStatus.getRetryLabel(): String? = when {
    this is ProcessingStatus.Ocr.Failed -> "Retry OCR"
    this is ProcessingStatus.Translation.Failed -> "Retry Translation"
    this is ProcessingStatus.Error -> "Retry"
    else -> null
}
