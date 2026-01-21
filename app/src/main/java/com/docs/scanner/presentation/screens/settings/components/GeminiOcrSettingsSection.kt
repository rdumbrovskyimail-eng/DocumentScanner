package com.docs.scanner.presentation.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class GeminiModelOption(
    val id: String,
    val displayName: String,
    val description: String
)

/**
 * Settings section for Gemini OCR fallback configuration.
 * Version: 19.2 HOTFIX - Added model selection support
 */
@Composable
fun GeminiOcrSettingsSection(
    enabled: Boolean,
    threshold: Int,
    alwaysUseGemini: Boolean,
    selectedModel: String,
    availableModels: List<GeminiModelOption>,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onAlwaysUseGeminiChange: (Boolean) -> Unit,
    onModelChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Text(
            text = "Gemini OCR (Handwriting)",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Use AI for difficult-to-read or handwritten text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Main toggle
        SettingsSwitchRow(
            icon = Icons.Default.AutoAwesome,
            title = "Enable Gemini fallback",
            subtitle = "Automatically use Gemini when ML Kit quality is low",
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
        
        // Expanded settings when enabled
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Always use Gemini toggle
                SettingsSwitchRow(
                    icon = Icons.Default.Psychology,
                    title = "Always use Gemini",
                    subtitle = "Skip ML Kit entirely (slower, uses API quota)",
                    checked = alwaysUseGemini,
                    onCheckedChange = onAlwaysUseGeminiChange
                )
                
                // Threshold slider (only show if not "always")
                AnimatedVisibility(
                    visible = !alwaysUseGemini,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        ThresholdSlider(
                            threshold = threshold,
                            onThresholdChange = onThresholdChange
                        )
                    }
                }
                
                // Model selector
                Spacer(modifier = Modifier.height(12.dp))
                ModelSelector(
                    selectedModel = selectedModel,
                    availableModels = availableModels,
                    onModelChange = onModelChange
                )
                
                // Info card
                Spacer(modifier = Modifier.height(12.dp))
                InfoCard(alwaysUseGemini = alwaysUseGemini, threshold = threshold)
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThresholdSlider(
    threshold: Int,
    onThresholdChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quality threshold",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Trigger Gemini when ML Kit confidence below this",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "$threshold%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.toInt()) },
            valueRange = 30f..80f,
            steps = 9, // 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "More ML Kit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "More Gemini",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelSelector(
    selectedModel: String,
    availableModels: List<GeminiModelOption>,
    onModelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = availableModels.find { it.id == selectedModel } 
        ?: availableModels.firstOrNull()
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Gemini Model",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = currentModel?.displayName ?: "Select model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                currentModel?.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select model"
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (model.id == selectedModel) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModelChange(model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    alwaysUseGemini: Boolean,
    threshold: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (alwaysUseGemini) "🤖 Gemini-only mode" else "⚡ Hybrid mode",
                style = MaterialTheme.typography.titleSmall
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = if (alwaysUseGemini) {
                    "All text recognition uses Gemini Vision API. Best for handwritten documents, but slower and uses API quota."
                } else {
                    "ML Kit runs first (fast, offline). If quality is below $threshold%, Gemini takes over. Best balance of speed and accuracy."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (!alwaysUseGemini) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        threshold <= 40 -> "💨 Mostly ML Kit — faster, less API usage"
                        threshold >= 60 -> "🎯 Mostly Gemini — better for handwriting"
                        else -> "⚖️ Balanced — good for mixed documents"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
