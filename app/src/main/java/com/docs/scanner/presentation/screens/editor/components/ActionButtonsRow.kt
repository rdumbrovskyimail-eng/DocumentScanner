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
// ✅ ACTION BUTTONS ROW (Google Docs Style)
// AI | Copy | Paste | Share | More
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
        // ✅ AI BUTTON
        MicroButton(
            text = "AI",
            icon = Icons.Default.AutoAwesome,
            onClick = { openGptWithPrompt(context, text) }
        )
        
        // ✅ COPY BUTTON
        MicroButton(
            text = "Copy",
            icon = Icons.Default.ContentCopy,
            onClick = { 
                copyToClipboard(context, text)
                android.util.Log.d("ActionButtons", "✅ Copied to clipboard")
            }
        )
        
        // ✅ PASTE BUTTON
        MicroButton(
            text = "Paste",
            icon = Icons.Default.ContentPaste,
            onClick = {
                val clipboard = getClipboardText(context)
                android.util.Log.d("ActionButtons", "📋 Pasted: $clipboard")
                // TODO: Implement paste handler
            }
        )
        
        // ✅ SHARE BUTTON
        MicroButton(
            text = "Share",
            icon = Icons.Default.Share,
            onClick = { shareText(context, text) }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // ✅ MORE BUTTON
        MoreButton(
            onClick = { showMenu = true }
        )
    }
    
    // TODO: DropdownMenu для More (Retry, Delete, Export)
}

// ============================================
// HELPER FUNCTIONS
// ============================================

private fun openGptWithPrompt(context: Context, text: String) {
    try {
        val prompt = "Improve this text:\n\n$text"
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        val uri = Uri.parse("https://chatgpt.com/?q=$encodedPrompt")
        
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
        
        android.util.Log.d("ActionButtons", "🤖 Opened ChatGPT")
    } catch (e: Exception) {
        android.util.Log.e("ActionButtons", "❌ Failed to open ChatGPT", e)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Document text", text)
        clipboard.setPrimaryClip(clip)
    } catch (e: Exception) {
        android.util.Log.e("ActionButtons", "❌ Failed to copy", e)
    }
}

private fun getClipboardText(context: Context): String? {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    } catch (e: Exception) {
        android.util.Log.e("ActionButtons", "❌ Failed to get clipboard", e)
        null
    }
}

private fun shareText(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share text"))
        
        android.util.Log.d("ActionButtons", "📤 Shared text")
    } catch (e: Exception) {
        android.util.Log.e("ActionButtons", "❌ Failed to share", e)
    }
}
