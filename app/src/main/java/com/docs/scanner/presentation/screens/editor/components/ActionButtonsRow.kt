package com.docs.scanner.presentation.screens.editor.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.components.MicroButton
import com.docs.scanner.presentation.components.MoreButton

// ============================================
// ACTION BUTTONS ROW (Google Docs Style)
// ============================================

@Composable
fun ActionButtonsRow(
    text: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // AI Button
        MicroButton(
            text = "AI",
            icon = Icons.Default.AutoAwesome,
            onClick = { openGptWithPrompt(context, text, isTranslation = false) }
        )
        
        // Copy Button
        MicroButton(
            text = "Copy",
            icon = Icons.Default.ContentCopy,
            onClick = { copyToClipboard(context, text) }
        )
        
        // Paste Button
        MicroButton(
            text = "Paste",
            icon = Icons.Default.ContentPaste,
            onClick = {
                val clipboard = getClipboardText(context)
                // Handle paste - will be implemented in ViewModel
            }
        )
        
        // Share Button
        MicroButton(
            text = "Share",
            icon = Icons.Default.Share,
            onClick = { shareText(context, text) }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // More Button
        MoreButton(
            onClick = { showMenu = true }
        )
    }
    
    // TODO: Add DropdownMenu for more options (Retry, Delete, etc.)
}

// ============================================
// HELPER FUNCTIONS
// ============================================

private fun openGptWithPrompt(context: Context, text: String, isTranslation: Boolean) {
    val prompt = if (isTranslation) {
        "Improve this translation:\n\n$text"
    } else {
        "Correct this OCR text:\n\n$text"
    }
    
    val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
    val uri = Uri.parse("https://chatgpt.com/?q=$encodedPrompt")
    
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Document text", text)
    clipboard.setPrimaryClip(clip)
}

private fun getClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share text"))
}
