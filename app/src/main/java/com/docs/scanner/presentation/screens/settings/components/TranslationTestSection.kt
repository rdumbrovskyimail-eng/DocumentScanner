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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.docs.scanner.domain.core.Language

/**
 * ✅ НОВЫЙ КОМПОНЕНТ: Секция теста перевода в настройках OCR Components
 * 
 * Позволяет тестировать перевод текста напрямую в настройках без необходимости
 * сканировать изображение. Полезно для проверки качества перевода и кэша.
 */
@Composable
fun TranslationTestSection(
    state: MlkitSettingsState,
    onTextChange: (String) -> Unit,
    onSourceLangChange: (Language) -> Unit,
    onTargetLangChange: (Language) -> Unit,
    onTranslate: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ═══════════════════════════════════════════════════════════════
            // ЗАГОЛОВОК
            // ═══════════════════════════════════════════════════════════════
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Translation Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // ВЫБОР ЯЗЫКОВ
            // ═══════════════════════════════════════════════════════════════
            
            // Язык-источник
            LanguageDropdown(
                label = "Source Language",
                selected = state.translationSourceLang,
                options = Language.translationSupported,
                onSelect = onSourceLangChange
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Язык-цель
            LanguageDropdown(
                label = "Target Language",
                selected = state.translationTargetLang,
                options = Language.translationSupported.filter { it != Language.AUTO },
                onSelect = onTargetLangChange
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // ПОЛЕ ВВОДА ТЕКСТА
            // ═══════════════════════════════════════════════════════════════
            OutlinedTextField(
                value = state.translationTestText,
                onValueChange = onTextChange,
                label = { Text("Text to translate") },
                placeholder = { Text("Enter text...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // ═══════════════════════════════════════════════════════════════
            // КНОПКИ ДЕЙСТВИЙ
            // ═══════════════════════════════════════════════════════════════
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTranslate,
                    enabled = state.translationTestText.isNotBlank() && !state.isTranslating,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Translating...")
                    } else {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Translate")
                    }
                }
                
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // РЕЗУЛЬТАТ ПЕРЕВОДА (с анимацией)
            // ═══════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = state.translationResult != null || state.translationError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    
                    // Успешный перевод
                    state.translationResult?.let { result ->
                        Text(
                            text = "Translation:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        
                        SelectionContainer {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = result,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    
                    // Ошибка перевода
                    state.translationError?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ✅ ВСПОМОГАТЕЛЬНЫЙ КОМПОНЕНТ: Dropdown для выбора языка
 */
@Composable
private fun LanguageDropdown(
    label: String,
    selected: Language,
    options: List<Language>,
    onSelect: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(4.dp))
        
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${selected.displayName} (${selected.code})",
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text("${lang.displayName} (${lang.code})") },
                        onClick = {
                            expanded = false
                            onSelect(lang)
                        }
                    )
                }
            }
        }
    }
}