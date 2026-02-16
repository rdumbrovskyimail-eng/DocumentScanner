/*
 * DocumentActions.kt
 * Version: 9.1.0 (2026)
 *
 * Все sealed классы для actions в Editor экране
 */

package com.docs.scanner.presentation.screens.editor

import com.docs.scanner.domain.core.Language

// ════════════════════════════════════════════════════════════════════
// DOCUMENT ACTIONS
// ════════════════════════════════════════════════════════════════════

sealed class DocumentAction {

    // Clicks
    data class ImageClick(val documentId: Long) : DocumentAction()
    data class OcrTextClick(val documentId: Long) : DocumentAction()
    data class TranslationClick(val documentId: Long) : DocumentAction()

    // Selection
    data class ToggleSelection(val documentId: Long) : DocumentAction()

    // Menu
    data class MenuClick(val documentId: Long) : DocumentAction()

    // Retry
    data class RetryOcr(val documentId: Long) : DocumentAction()
    data class RetryTranslation(val documentId: Long) : DocumentAction()

    // Move
    data class MoveUp(val documentId: Long) : DocumentAction()
    data class MoveDown(val documentId: Long) : DocumentAction()
    data class MoveToRecord(val documentId: Long, val targetRecordId: Long) : DocumentAction()

    // Share / Delete
    data class SharePage(val documentId: Long, val imagePath: String) : DocumentAction()
    data class DeletePage(val documentId: Long) : DocumentAction()

    // Text
    data class CopyText(val documentId: Long, val text: String, val isOcrText: Boolean) : DocumentAction()
    data class PasteText(val documentId: Long, val text: String?, val isOcrText: Boolean) : DocumentAction()
    data class AiRewrite(val documentId: Long, val text: String, val isOcrText: Boolean) : DocumentAction()
    data class ClearFormatting(val documentId: Long, val isOcrText: Boolean) : DocumentAction()

    // Confidence
    data class WordTap(val word: String, val confidence: Float) : DocumentAction()

    // Inline editing
    data class StartInlineEdit(
        val documentId: Long,
        val field: TextEditField,
        val initialText: String
    ) : DocumentAction()

    data class UpdateInlineText(
        val documentId: Long,
        val field: TextEditField,
        val text: String
    ) : DocumentAction()

    data class SaveInlineEdit(
        val documentId: Long,
        val field: TextEditField
    ) : DocumentAction()

    data class CancelInlineEdit(
        val documentId: Long,
        val field: TextEditField
    ) : DocumentAction()
}

// ════════════════════════════════════════════════════════════════════
// RECORD ACTIONS
// ════════════════════════════════════════════════════════════════════

sealed class RecordAction {

    data class Rename(val name: String) : RecordAction()
    data class UpdateDescription(val description: String?) : RecordAction()
    data class AddTag(val tag: String) : RecordAction()
    data class RemoveTag(val tag: String) : RecordAction()
    data class UpdateLanguages(val source: Language, val target: Language) : RecordAction()

    data object ShareAsPdf : RecordAction()
    data object ShareAsZip : RecordAction()
    data object EnterSelectionMode : RecordAction()
    data object ExitSelectionMode : RecordAction()
    data object SelectAll : RecordAction()
    data object DeselectAll : RecordAction()
    data object DeleteSelected : RecordAction()

    data class ExportSelected(val asPdf: Boolean) : RecordAction()
    data class MoveSelectedToRecord(val targetRecordId: Long) : RecordAction()

    data object CancelBatchOperation : RecordAction()
    data object RetryFailedDocuments : RecordAction()
    data object RetryAllOcr : RecordAction()
    data object RetryAllTranslation : RecordAction()
    data object Undo : RecordAction()
}

// ════════════════════════════════════════════════════════════════════
// TEXT EDIT FIELD
// ════════════════════════════════════════════════════════════════════

enum class TextEditField {
    OCR_TEXT,
    TRANSLATED_TEXT
}