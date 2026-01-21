/*
 * GeminiOcrSettingsSection.kt
 * Version: 20.0.0 UNIFIED - Merged PUBLIC + PRIVATE versions
 * 
 * ✅ FIXED: Removed duplicate implementations
 * ✅ NEW: Speed badges (⚡/⚖️/🐌) now in unified version
 * ✅ NEW: Warning card for slow models
 * 
 * USAGE:
 * - Settings Screen → AI & OCR Tab
 * - MlkitSettingsSection → Testing Tab
 */

package com.docs.scanner.presentation.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docs.scanner.data.local.preferences.GeminiModelOption

/**
 * Unified Gemini OCR settings section with speed indicators.
 * 
 * ✅ PUBLIC function - accessible from both SettingsScreen and MlkitSettingsSection
 * ✅ Includes ModelSpeedBadge for visual speed indication
 * ✅ Shows warning card for slow models (gemini-3-pro, gemini-2.5-pro)
 * 
 * @param enabled Whether Gemini fallback is enabled
 * @param threshold ML Kit confidence threshold (30-90%)
 * @param alwaysUseGemini Skip ML Kit entirely
 * @param selectedModel Currently selected Gemini model ID
 * @param availableModels List of available models with metadata
 * @param onEnabledChange Callback when toggle changes
 * @param onThresholdChange Callback when slider changes
 * @param onAlwaysUseGeminiChange Callback when "always use" toggle changes
 * @param onModelChange Callback when model selection changes
 * @param modifier Optional modifier for the entire section
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════
        // HEADER
        // ═══════════════════════════════════════════════════════════════
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Gemini AI Fallback",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Use Gemini AI when ML Kit confidence is low or for handwritten text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ═══════════════════════════════════════════════════════════════
        // ENABLE TOGGLE
        // ═══════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Gemini fallback",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Automatically use Gemini for low-quality scans",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }

        // ═══════════════════════════════════════════════════════════════
        // EXPANDED SETTINGS (when enabled)
        // ═══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // ───────────────────────────────────────────────────────
                // MODEL SELECTOR with Speed Badges
                // ───────────────────────────────────────────────────────
                Text(
                    text = "Gemini Model",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                ModelSelectorWithSpeedBadge(
                    selectedModel = selectedModel,
                    availableModels = availableModels,
                    onModelChange = onModelChange
                )
                
                // ───────────────────────────────────────────────────────
                // WARNING for slow models
                // ───────────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = selectedModel in listOf("gemini-3-pro", "gemini-2.5-pro"),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "⚠️ This model is slow (4-7s per image). For real-time OCR, use Gemini 3 Flash or Flash Lite.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                // ───────────────────────────────────────────────────────
                // ALWAYS USE GEMINI toggle
                // ───────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Always use Gemini",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Skip ML Kit entirely (slower but more accurate)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = alwaysUseGemini,
                        onCheckedChange = onAlwaysUseGeminiChange
                    )
                }
                
                // ───────────────────────────────────────────────────────
                // THRESHOLD SLIDER (only when not "always")
                // ───────────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = !alwaysUseGemini,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ThresholdSlider(
                        threshold = threshold,
                        onThresholdChange = onThresholdChange
                    )
                }
                
                // ───────────────────────────────────────────────────────
                // INFO CARD
                // ───────────────────────────────────────────────────────
                InfoCard(
                    alwaysUseGemini = alwaysUseGemini,
                    threshold = threshold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ModelSelectorWithSpeedBadge(
    selectedModel: String,
    availableModels: List<GeminiModelOption>,
    onModelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = availableModels.find { it.id == selectedModel } 
        ?: availableModels.firstOrNull()
    
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
    }
    
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        availableModels.forEach { model ->
            DropdownMenuItem(
                text = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (model.id == selectedModel) 
                                        FontWeight.Bold else FontWeight.Normal
                                )
                                
                                // ✅ Speed badge под именем модели
                                Spacer(Modifier.height(4.dp))
                                ModelSpeedBadge(model.id)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
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
            if (model != availableModels.last()) {
                HorizontalDivider()
            }
        }
    }
}

/**
 * Visual badge showing model speed category.
 * 
 * Categories:
 * - ⚡ FAST: gemini-3-flash, gemini-2.5-flash-lite (1-2s)
 * - ⚖️ BALANCED: gemini-2.5-flash (2-3s)
 * - 🐌 SLOW: gemini-3-pro, gemini-2.5-pro (4-7s)
 */
@Composable
private fun ModelSpeedBadge(modelId: String) {
    val (text, color) = when (modelId) {
        "gemini-3-flash", "gemini-2.5-flash-lite" -> 
            "⚡ FAST" to Color(0xFF4CAF50)
        
        "gemini-2.5-flash" -> 
            "⚖️ BALANCED" to Color(0xFF2196F3)
        
        "gemini-3-pro", "gemini-2.5-pro" -> 
            "🐌 SLOW" to Color(0xFFFF9800)
        
        else -> return  // Don't show badge for unknown models
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
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
            steps = 9,
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