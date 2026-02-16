/*
 * DocumentActionHandler.kt - УЛУЧШЕННАЯ ВЕРСИЯ
 * Version: 9.1.0 (2026)
 *
 * ✅ Улучшенная обработка PasteText с null text
 * ✅ Все actions обрабатываются единообразно
 */

package com.docs.scanner.presentation.screens.editor

import timber.log.Timber

/**
 * Обработчик всех действий с документами
 * 
 * ИСПОЛЬЗОВАНИЕ в EditorScreen.kt:
 * 
 * val onDocumentAction: (DocumentAction) -> Unit = { action ->
 *     when (action) {
 *         // Copy обрабатываем в UI (clipboard API)
 *         is DocumentAction.CopyText -> {
 *             clipboardManager.setText(AnnotatedString(action.text))
 *         }
 *         
 *         // Paste обрабатываем в UI (получаем text из clipboard)
 *         is DocumentAction.PasteText -> {
 *             val clipText = clipboardManager.getText()?.text
 *             viewModel.handleDocumentAction(
 *                 action.copy(text = clipText?.takeIf { it.isNotBlank() })
 *             )
 *         }
 *         
 *         // Все остальное → ViewModel
 *         else -> viewModel.handleDocumentAction(action)
 *     }
 * }
 */
fun EditorViewModel.handleDocumentAction(action: DocumentAction) {
    when (action) {
        // ═══════════════════════════════════════════════════════════
        // CLICKS
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.ImageClick -> {
            Timber.d("📸 Image clicked: ${action.documentId}")
            // UI handles navigation
        }

        is DocumentAction.OcrTextClick -> {
            startInlineEditOcr(action.documentId)
        }

        is DocumentAction.TranslationClick -> {
            startInlineEditTranslation(action.documentId)
        }

        // ═══════════════════════════════════════════════════════════
        // SELECTION
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.ToggleSelection -> {
            toggleDocumentSelection(action.documentId)
        }

        // ═══════════════════════════════════════════════════════════
        // MENU
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.MenuClick -> {
            Timber.d("📋 Menu clicked for document: ${action.documentId}")
            // UI handles menu state
        }

        // ═══════════════════════════════════════════════════════════
        // RETRY OPERATIONS
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.RetryOcr -> {
            Timber.d("🔄 Retrying OCR for document ${action.documentId}")
            retryOcr(action.documentId)
        }

        is DocumentAction.RetryTranslation -> {
            Timber.d("🌐 Retrying translation for document ${action.documentId}")
            retryTranslation(action.documentId)
        }

        // ═══════════════════════════════════════════════════════════
        // MOVE OPERATIONS
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.MoveUp -> {
            moveDocumentUp(action.documentId)
        }

        is DocumentAction.MoveDown -> {
            moveDocumentDown(action.documentId)
        }

        is DocumentAction.MoveToRecord -> {
            moveDocument(action.documentId, action.targetRecordId)
        }

        // ═══════════════════════════════════════════════════════════
        // SHARE/DELETE
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.SharePage -> {
            shareSingleImage(action.imagePath)
        }

        is DocumentAction.DeletePage -> {
            Timber.d("🗑️ Deleting document ${action.documentId}")
            deleteDocument(action.documentId)
        }

        // ═══════════════════════════════════════════════════════════
        // TEXT OPERATIONS
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.CopyText -> {
            Timber.d("📋 Text copied from document ${action.documentId}: ${action.text.take(50)}...")
            // Clipboard handling in UI layer
        }

        // ✅ ИСПРАВЛЕНО: Правильная обработка nullable text
        is DocumentAction.PasteText -> {
            if (action.text != null) {
                Timber.d("📋 Pasting ${action.text.length} chars to document ${action.documentId}")
                pasteText(action.documentId, action.text, action.isOcrText)
            } else {
                Timber.w("⚠️ Paste failed: clipboard is empty")
                sendError("Clipboard is empty")
            }
        }

        is DocumentAction.AiRewrite -> {
            Timber.d("🤖 AI rewriting text for document ${action.documentId}")
            aiRewriteText(action.documentId, action.text, action.isOcrText)
        }

        is DocumentAction.ClearFormatting -> {
            Timber.d("✨ Clearing formatting for document ${action.documentId}")
            clearFormatting(action.documentId, action.isOcrText)
        }

        // ═══════════════════════════════════════════════════════════
        // CONFIDENCE
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.WordTap -> {
            showConfidenceTooltip(action.word, action.confidence)
        }

        // ═══════════════════════════════════════════════════════════
        // INLINE EDITING
        // ═══════════════════════════════════════════════════════════
        is DocumentAction.StartInlineEdit -> {
            when (action.field) {
                TextEditField.OCR_TEXT -> startInlineEditOcr(action.documentId)
                TextEditField.TRANSLATED_TEXT -> startInlineEditTranslation(action.documentId)
            }
        }

        is DocumentAction.UpdateInlineText -> {
            updateInlineText(action.documentId, action.field, action.text)
        }

        is DocumentAction.SaveInlineEdit -> {
            saveInlineChanges(action.documentId, action.field)
        }

        is DocumentAction.CancelInlineEdit -> {
            cancelInlineEdit(action.documentId, action.field)
        }
    }
}

/**
 * Обработчик действий с Record
 */
fun EditorViewModel.handleRecordAction(action: RecordAction) {
    when (action) {
        is RecordAction.Rename -> {
            Timber.d("✏️ Renaming record to: ${action.name}")
            updateRecordName(action.name)
        }

        is RecordAction.UpdateDescription -> {
            updateRecordDescription(action.description)
        }

        is RecordAction.AddTag -> {
            Timber.d("🏷️ Adding tag: ${action.tag}")
            addTag(action.tag)
        }

        is RecordAction.RemoveTag -> {
            Timber.d("🏷️ Removing tag: ${action.tag}")
            removeTag(action.tag)
        }

        is RecordAction.UpdateLanguages -> {
            Timber.d("🌐 Updating languages: ${action.source.code} → ${action.target.code}")
            updateLanguages(action.source, action.target)
        }

        RecordAction.ShareAsPdf -> {
            Timber.d("📄 Sharing as PDF")
            shareRecordAsPdf()
        }

        RecordAction.ShareAsZip -> {
            Timber.d("📦 Sharing as ZIP")
            shareRecordImagesZip()
        }

        RecordAction.EnterSelectionMode -> {
            Timber.d("✅ Entering selection mode")
            enterSelectionMode()
        }

        RecordAction.ExitSelectionMode -> {
            Timber.d("❌ Exiting selection mode")
            exitSelectionMode()
        }

        RecordAction.SelectAll -> {
            Timber.d("✅ Selecting all documents")
            selectAll()
        }

        RecordAction.DeselectAll -> {
            Timber.d("❌ Deselecting all documents")
            deselectAll()
        }

        RecordAction.DeleteSelected -> {
            Timber.d("🗑️ Deleting selected documents")
            deleteSelectedDocuments()
        }

        is RecordAction.ExportSelected -> {
            Timber.d("📤 Exporting selected as ${if (action.asPdf) "PDF" else "ZIP"}")
            exportSelectedDocuments(action.asPdf)
        }

        is RecordAction.MoveSelectedToRecord -> {
            Timber.d("📁 Moving selected to record ${action.targetRecordId}")
            moveSelectedToRecord(action.targetRecordId)
        }

        RecordAction.CancelBatchOperation -> {
            Timber.d("🛑 Cancelling batch operation")
            cancelBatchOperation()
        }

        RecordAction.RetryFailedDocuments -> {
            Timber.d("🔄 Retrying failed documents")
            retryFailedDocuments()
        }

        RecordAction.RetryAllOcr -> {
            Timber.d("🔄 Retrying all OCR")
            retryAllOcr()
        }

        RecordAction.RetryAllTranslation -> {
            Timber.d("🌐 Retrying all translations")
            retryAllTranslation()
        }

        RecordAction.Undo -> {
            Timber.d("↩️ Undoing last edit")
            undoLastEdit()
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// HELPER для удобного создания action handlers в UI
// ════════════════════════════════════════════════════════════════════

/**
 * Создает lambda для обработки DocumentAction с учетом clipboard operations
 * 
 * Использование в EditorScreen:
 * ```kotlin
 * val clipboardManager = LocalClipboardManager.current
 * val onDocumentAction = viewModel.createDocumentActionHandler(clipboardManager)
 * ```
 */
fun EditorViewModel.createDocumentActionHandler(
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
): (DocumentAction) -> Unit = { action ->
    when (action) {
        // Copy обрабатываем в UI
        is DocumentAction.CopyText -> {
            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(action.text))
        }
        
        // Paste получаем text из clipboard и делегируем
        is DocumentAction.PasteText -> {
            val clipText = clipboardManager.getText()?.text?.takeIf { it.isNotBlank() }
            handleDocumentAction(action.copy(text = clipText))
        }
        
        // Все остальное
        else -> handleDocumentAction(action)
    }
}
