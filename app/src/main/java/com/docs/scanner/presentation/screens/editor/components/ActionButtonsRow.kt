package com.docs.scanner.presentation.screens.editor.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.components.MicroButton
import com.docs.scanner.presentation.theme.*

// ============================================
// ACTION BUTTONS ROW (Google Docs Style 2026)
// AI | Copy | Paste | Share | More
// ============================================

/**
 * Панель действий для документа в стиле Google Docs.
 * 
 * @param text Текст для операций (OCR или перевод)
 * @param onRetry Callback для повторной обработки (если доступно)
 * @param onPasteText Callback когда пользователь вставляет текст
 */
@Composable
fun ActionButtonsRow(
    text: String,
    onRetry: (() -> Unit)? = null,
    onPasteText: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✅ AI BUTTON - Opens ChatGPT with text
        MicroButton(
            text = "AI",
            icon = Icons.Default.AutoAwesome,
            onClick = { openAiWithText(context, text) },
            enabled = text.isNotBlank()
        )
        
        // ✅ COPY BUTTON
        MicroButton(
            text = "Copy",
            icon = Icons.Default.ContentCopy,
            onClick = { 
                copyToClipboard(context, text)
                showToast(context, "Copied to clipboard")
            },
            enabled = text.isNotBlank()
        )
        
        // ✅ PASTE BUTTON
        MicroButton(
            text = "Paste",
            icon = Icons.Default.ContentPaste,
            onClick = {
                val clipboardText = getClipboardText(context)
                if (clipboardText != null) {
                    onPasteText?.invoke(clipboardText)
                    showToast(context, "Text pasted")
                } else {
                    showToast(context, "Clipboard is empty")
                }
            },
            enabled = onPasteText != null
        )
        
        // ✅ SHARE BUTTON
        MicroButton(
            text = "Share",
            icon = Icons.Default.Share,
            onClick = { shareText(context, text) },
            enabled = text.isNotBlank()
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // ✅ MORE BUTTON with dropdown
        Box {
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = GoogleDocsButtonText
                )
            }
            
            // Dropdown Menu
            MoreOptionsMenu(
                expanded = showMoreMenu,
                onDismiss = { showMoreMenu = false },
                onRetry = onRetry,
                onTranslateWith = { translateWithGoogle(context, text) },
                onSearchWeb = { searchOnWeb(context, text) },
                text = text
            )
        }
    }
}

// ============================================
// MORE OPTIONS MENU
// ============================================

@Composable
private fun MoreOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?,
    onTranslateWith: () -> Unit,
    onSearchWeb: () -> Unit,
    text: String
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // Retry (if available)
        if (onRetry != null) {
            DropdownMenuItem(
                text = { Text("Retry Processing") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = GoogleDocsPrimary
                    )
                },
                onClick = {
                    onRetry()
                    onDismiss()
                }
            )
            HorizontalDivider()
        }
        
        // Translate with Google
        DropdownMenuItem(
            text = { Text("Translate with Google") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null
                )
            },
            onClick = {
                onTranslateWith()
                onDismiss()
            },
            enabled = text.isNotBlank()
        )
        
        // Search on Web
        DropdownMenuItem(
            text = { Text("Search on Web") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            onClick = {
                onSearchWeb()
                onDismiss()
            },
            enabled = text.isNotBlank()
        )
        
        HorizontalDivider()
        
        // Word count info
        DropdownMenuItem(
            text = { 
                Text(
                    text = "${text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size} words, ${text.length} chars",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = { onDismiss() },
            enabled = false
        )
    }
}

// ============================================
// COMPACT ACTION BUTTONS (для ограниченного пространства)
// ============================================

@Composable
fun ActionButtonsCompact(
    text: String,
    onCopy: () -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Copy
        IconButton(
            onClick = { 
                copyToClipboard(context, text)
                showToast(context, "Copied")
            },
            modifier = Modifier.size(32.dp),
            enabled = text.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(18.dp),
                tint = GoogleDocsButtonText
            )
        }
        
        // Share
        IconButton(
            onClick = { shareText(context, text) },
            modifier = Modifier.size(32.dp),
            enabled = text.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                modifier = Modifier.size(18.dp),
                tint = GoogleDocsButtonText
            )
        }
    }
}

// ============================================
// HELPER FUNCTIONS
// ============================================

private fun openAiWithText(context: Context, text: String) {
    if (text.isBlank()) return
    
    try {
        // Prepare prompt
        val prompt = "Improve and correct this text:\n\n$text"
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        
        // Try ChatGPT first
        val chatGptUri = Uri.parse("https://chatgpt.com/?q=$encodedPrompt")
        val intent = Intent(Intent.ACTION_VIEW, chatGptUri)
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to Google Bard
        try {
            val bardUri = Uri.parse("https://bard.google.com/")
            val intent = Intent(Intent.ACTION_VIEW, bardUri)
            context.startActivity(intent)
        } catch (e2: Exception) {
            showToast(context, "Could not open AI assistant")
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) return
    
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Document text", text)
        clipboard.setPrimaryClip(clip)
    } catch (e: Exception) {
        showToast(context, "Failed to copy")
    }
}

private fun getClipboardText(context: Context): String? {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    } catch (e: Exception) {
        null
    }
}

private fun shareText(context: Context, text: String) {
    if (text.isBlank()) return
    
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share text"))
    } catch (e: Exception) {
        showToast(context, "Failed to share")
    }
}

private fun translateWithGoogle(context: Context, text: String) {
    if (text.isBlank()) return
    
    try {
        val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
        val uri = Uri.parse("https://translate.google.com/?sl=auto&tl=en&text=$encodedText")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        showToast(context, "Failed to open Google Translate")
    }
}

private fun searchOnWeb(context: Context, text: String) {
    if (text.isBlank()) return
    
    try {
        // Take first 100 characters for search
        val searchQuery = text.take(100)
        val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")
        val uri = Uri.parse("https://www.google.com/search?q=$encodedQuery")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        showToast(context, "Failed to search")
    }
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
