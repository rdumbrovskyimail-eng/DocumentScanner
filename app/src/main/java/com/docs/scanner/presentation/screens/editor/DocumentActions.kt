package com.docs.scanner.presentation.screens.editor

import com.docs.scanner.domain.core.Language
/**
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
    
    // Reorder
    data class MoveUp(val documentId: Long) : DocumentAction
    data class MoveDown(val documentId: Long) : DocumentAction
    
    // Share & Delete
    data class SharePage(val documentId: Long, val imagePath: String) : DocumentAction
    data class DeletePage(val documentId: Long) : DocumentAction
    data class MoveToRecord(val documentId: Long, val targetRecordId: Long) : DocumentAction
    
    // Text operations
    data class CopyText(val documentId: Long, val text: String, val isOcr: Boolean) : DocumentAction
    data class PasteText(val documentId: Long, val isOcr: Boolean) : DocumentAction
    data class AiRewrite(val documentId: Long, val text: String, val isOcr: Boolean) : DocumentAction
    data class ClearFormatting(val documentId: Long, val isOcr: Boolean) : DocumentAction
    
    // Inline editing
    data class StartInlineEdit(val documentId: Long, val field: TextEditField, val initialText: String) : DocumentAction
    data class UpdateInlineText(val documentId: Long, val field: TextEditField, val text: String) : DocumentAction
    data class SaveInlineEdit(val documentId: Long, val field: TextEditField) : DocumentAction
    data class CancelInlineEdit(val documentId: Long, val field: TextEditField) : DocumentAction
    
    // Confidence
    data class WordTap(val word: String, val confidence: Float) : DocumentAction
}

/**
 * Record-level actions
 */
sealed interface RecordAction {
    data class UpdateName(val name: String) : RecordAction
    data class UpdateDescription(val description: String?) : RecordAction
    data class AddTag(val tag: String) : RecordAction
    data class RemoveTag(val tag: String) : RecordAction
    data class UpdateLanguages(val source: Language, val target: Language) : RecordAction
}

/**
 * Batch actions для Selection Mode
 */
sealed interface BatchAction {
    data object DeleteSelected : BatchAction
    data class ExportSelected(val asPdf: Boolean) : BatchAction
    data class MoveSelected(val targetRecordId: Long) : BatchAction
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