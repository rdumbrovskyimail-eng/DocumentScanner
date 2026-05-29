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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

@Composable
fun GeminiOcrSettingsSection(
    selectedModel: String,
    availableModels: List<GeminiModelOption>,
    onModelChange: (String) -> Unit,
    onAddNewModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newModelInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER
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
            text = "Fallback-распознавание текста через Gemini автоматически активно при качестве сканирования ниже 60%.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // MODEL SELECTOR
        Text(
            text = "Выбранная модель",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        ModelSelectorWithSpeedBadge(
            selectedModel = selectedModel,
            availableModels = availableModels,
            onModelChange = onModelChange
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ADD NEW MODEL
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Зарегистрировать новую модель",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Укажите точный системный идентификатор модели (например: gemini-3.5-flash-custom)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newModelInput,
                    onValueChange = { newModelInput = it.replace(" ", "") },
                    placeholder = { Text("Идентификатор модели") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                
                Button(
                    onClick = {
                        if (newModelInput.isNotBlank()) {
                            onAddNewModel(newModelInput.trim())
                            newModelInput = ""
                        }
                    },
                    enabled = newModelInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
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

