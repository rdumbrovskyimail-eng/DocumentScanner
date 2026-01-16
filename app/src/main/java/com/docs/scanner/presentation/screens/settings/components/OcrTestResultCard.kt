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
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
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

/**
 * Displays OCR test results including Gemini comparison if available.
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
            // Header with quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Test Results",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                QualityBadge(quality = result.qualityRating)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // ML Kit Stats
            StatsRow(
                icon = Icons.Default.Speed,
                label = "ML Kit",
                stats = listOf(
                    "Confidence: ${result.confidencePercent}",
                    "Words: ${result.totalWords}",
                    "Time: ${result.processingTimeMs}ms"
                )
            )
            
            // Quality metrics
            result.qualityMetrics?.let { metrics ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Quality: ${metrics.quality.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (metrics.quality.name) {
                        "EXCELLENT", "GOOD" -> MaterialTheme.colorScheme.primary
                        "FAIR" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                if (metrics.isLikelyHandwritten) {
                    Text(
                        text = "📝 Likely handwritten text detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (metrics.fallbackReasons.isNotEmpty()) {
                    Text(
                        text = "⚠️ ${metrics.fallbackReasons.first()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Gemini comparison (if available)
            AnimatedVisibility(visible = result.hasGeminiComparison) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    StatsRow(
                        icon = Icons.Default.Psychology,
                        label = "Gemini",
                        stats = listOf(
                            "Characters: ${result.geminiText?.length ?: 0}",
                            "Time: ${result.geminiProcessingTimeMs}ms"
                        )
                    )
                    
                    result.geminiImprovement?.let { improvement ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = improvement,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            
            // Recognized text preview
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "ML Kit Text:",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = result.text.take(500) + if (result.text.length > 500) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            // Gemini text (if different)
            if (result.geminiText != null && result.geminiText != result.text) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gemini Text:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = result.geminiText.take(500) + if (result.geminiText.length > 500) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
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