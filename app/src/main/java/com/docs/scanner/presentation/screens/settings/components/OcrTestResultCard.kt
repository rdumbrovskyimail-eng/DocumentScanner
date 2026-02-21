package com.docs.scanner.presentation.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docs.scanner.data.remote.mlkit.OcrTestResult
import com.docs.scanner.domain.core.OcrSource

/**
 * Displays OCR test results with Gemini fallback information.
 * 
 * ✅ FIXED: Uses only fields that actually exist in OcrTestResult.
 */
@Composable
fun OcrTestResultCard(
    result: OcrTestResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with quality and source
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Test Results",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                SourceBadge(source = result.source)
                Spacer(modifier = Modifier.width(8.dp))
                QualityBadge(quality = result.qualityRating)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats
            StatsRow(
                icon = if (result.source == OcrSource.GEMINI) Icons.Default.AutoAwesome else Icons.Default.Speed,
                label = result.sourceDisplayName,
                stats = listOf(
                    "Confidence: ${result.confidencePercent}",
                    "Words: ${result.totalWords}",
                    "Time: ${result.processingTimeMs}ms"
                )
            )
            
            // Gemini fallback info (if triggered)
            if (result.geminiFallbackTriggered) {
                Spacer(modifier = Modifier.height(8.dp))
                
                result.geminiFallbackReason?.let { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fallback reason: $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                
                result.geminiProcessingTimeMs?.let { time ->
                    Text(
                        text = "Gemini processing: ${time}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Low confidence warning
            if (result.lowConfidenceWords > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${result.lowConfidenceWords} low confidence words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Recognized text preview
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Recognized Text:",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = result.text.take(500) + if (result.text.length > 500) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SourceBadge(source: OcrSource) {
    val (text, color, bgColor) = when (source) {
        OcrSource.ML_KIT -> Triple(
            "ML Kit",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
        OcrSource.GEMINI -> Triple(
            "Gemini",
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer
        )
        OcrSource.UNKNOWN -> Triple(
            "Unknown",
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.surfaceVariant
        )
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun QualityBadge(quality: String) {
    val (color, bgColor) = when (quality) {
        "Excellent" -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
        "Good" -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer
        "Fair" -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Text(
            text = quality,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    stats: List<String>
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stats.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
