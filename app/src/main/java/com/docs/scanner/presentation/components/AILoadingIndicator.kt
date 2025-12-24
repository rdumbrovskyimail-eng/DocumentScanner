package com.docs.scanner.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.*

// ============================================
// AI LOADING INDICATOR (Google Docs Style)
// ============================================

@Composable
fun AILoadingIndicator(
    text: String = "AI is processing...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Pulsing dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                PulsingDot(delay = index * 200)
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = GoogleDocsTextSecondary
            )
        }
        
        // Smart underline animation
        SmartUnderline()
    }
}

@Composable
private fun PulsingDot(delay: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 700,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 700,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    
    Canvas(modifier = Modifier.size(8.dp)) {
        drawCircle(
            color = GoogleDocsAiLoadingDot.copy(alpha = alpha),
            radius = size.minDimension / 2 * scale
        )
    }
}

@Composable
private fun SmartUnderline() {
    val infiniteTransition = rememberInfiniteTransition(label = "underline")
    
    val animatedWidth by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "underline_width"
    )
    
    val animatedOpacity by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "underline_opacity"
    )
    
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .height(3.dp)
    ) {
        val lineWidth = size.width * animatedWidth
        val startX = 0f
        
        val gradient = Brush.horizontalGradient(
            colors = listOf(
                GoogleDocsPrimary.copy(alpha = animatedOpacity),
                GoogleDocsAiLoadingUnderline.copy(alpha = animatedOpacity),
                GoogleDocsPrimary.copy(alpha = animatedOpacity * 0.3f)
            ),
            startX = startX,
            endX = startX + lineWidth
        )
        
        drawLine(
            brush = gradient,
            start = Offset(startX, size.height / 2),
            end = Offset(startX + lineWidth, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
    }
}
