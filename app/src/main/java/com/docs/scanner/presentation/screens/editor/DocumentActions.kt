package com.docs.scanner.presentation.screens.editor

import com.docs.scanner.domain.core.Language

/**
 * DocumentActions.kt
 * Version: 9.0.0 - FULLY FIXED (2026)
 *
 * ✅ FIX #15 APPLIED: PasteText.text is now nullable (String?)
 *
 * КРИТИЧЕСКИ ВАЖНО:
 * Sealed interface заменяет множество callback'ов
 * Вместо 21 параметра → 1 функция: (DocumentAction) -> Unit
 */
sealed interface DocumentAction {
    // Базовые действия
    data class ImageClick(val documentId: Long) : DocumentAction
    data class OcrTextClick(val documentId: Long) : DocumentAction
    data class TranslationClick(val documentId: Long) : DocumentAction

    // Selection
    data class ToggleSelection(val documentId: Long) : DocumentAction

    // Menu
    data class MenuClick(val documentId: Long) : DocumentAction

    // Retry operations
    data class RetryOcr(val documentId: Long) : DocumentAction
    data class RetryTranslation(val documentId: Long) : DocumentAction

    // Move operations
    data class MoveUp(val documentId: Long) : DocumentAction
    data class MoveDown(val documentId: Long) : DocumentAction
    data class MoveToRecord(val documentId: Long, val targetRecordId: Long) : DocumentAction

    // Share/Delete operations
    data class SharePage(val documentId: Long, val imagePath: String) : DocumentAction
    data class DeletePage(val documentId: Long) : DocumentAction

    // Text operations
    data class CopyText(val documentId: Long, val text: String, val isOcrText: Boolean) : DocumentAction
    
    // ✅ FIX #15: text is now nullable to handle empty clipboard
    data class PasteText(
        val documentId: Long, 
        val text: String?,  // ← CHANGED from String to String?
        val isOcrText: Boolean
    ) : DocumentAction
    
    data class AiRewrite(val documentId: Long, val text: String, val isOcrText: Boolean) : DocumentAction
    data class ClearFormatting(val documentId: Long, val isOcrText: Boolean) : DocumentAction

    // Confidence
    data class WordTap(val word: String, val confidence: Float) : DocumentAction

    // Inline editing
    data class StartInlineEdit(
        val documentId: Long,
        val field: TextEditField,
        val initialText: String
    ) : DocumentAction
    data class UpdateInlineText(
        val documentId: Long,
        val field: TextEditField,
        val text: String
    ) : DocumentAction
    data class SaveInlineEdit(
        val documentId: Long,
        val field: TextEditField
    ) : DocumentAction
    data class CancelInlineEdit(
        val documentId: Long,
        val field: TextEditField
    ) : DocumentAction
}

/**
 * Record-level actions
 */
sealed interface RecordAction {
    data class Rename(val name: String) : RecordAction
    data class UpdateDescription(val description: String?) : RecordAction
    data class AddTag(val tag: String) : RecordAction
    data class RemoveTag(val tag: String) : RecordAction
    data class UpdateLanguages(val source: Language, val target: Language) : RecordAction
    data object ShareAsPdf : RecordAction
    data object ShareAsZip : RecordAction
    data object EnterSelectionMode : RecordAction
    data object ExitSelectionMode : RecordAction
    data object SelectAll : RecordAction
    data object DeselectAll : RecordAction
    data object DeleteSelected : RecordAction
    data class ExportSelected(val asPdf: Boolean) : RecordAction
    data class MoveSelectedToRecord(val targetRecordId: Long) : RecordAction
    data object CancelBatchOperation : RecordAction
    data object RetryFailedDocuments : RecordAction
    data object RetryAllOcr : RecordAction
    data object RetryAllTranslation : RecordAction
    data object Undo : RecordAction
}

/**
 * Dialog actions
 */
sealed interface DialogAction {
    data object ShowRenameDialog : DialogAction
    data object ShowDescriptionDialog : DialogAction
    data object ShowTagsDialog : DialogAction
    data object ShowLanguageDialog : DialogAction
    data object ShowAddDocumentDialog : DialogAction
    data object DismissDialog : DialogAction
}