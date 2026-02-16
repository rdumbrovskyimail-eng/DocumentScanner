package com.docs.scanner.presentation.screens.editor

import com.docs.scanner.domain.core.Document
import com.docs.scanner.domain.core.Record

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data class Success(
        val record: Record,
        val folderName: String,
        val documents: List<Document>
    ) : EditorUiState
    data class Error(val message: String) : EditorUiState
}

data class ProcessingState(
    val isActive: Boolean = false,
    val operation: ProcessingOperation? = null,
    val progress: Int = 0,
    val message: String = "",
    val canCancel: Boolean = false
)

sealed interface ProcessingOperation {
    data object AddingDocument : ProcessingOperation
    data object GeneratingPdf : ProcessingOperation
    data object CreatingZip : ProcessingOperation
    data class BatchDelete(val total: Int) : ProcessingOperation
    data class BatchExport(val total: Int, val asPdf: Boolean) : ProcessingOperation
    data class BatchMove(val total: Int) : ProcessingOperation
    data class RetryingOcr(val total: Int) : ProcessingOperation
    data class RetryingTranslation(val total: Int) : ProcessingOperation
}

data class SelectionState(
    val mode: SelectionMode = SelectionMode.Inactive,
    val selectedIds: Set<Long> = emptySet()
) {
    val count: Int get() = selectedIds.size
    val isActive: Boolean get() = mode is SelectionMode.Active
    val isEmpty: Boolean get() = selectedIds.isEmpty()

    fun toggle(id: Long): SelectionState {
        val newSelected = if (selectedIds.contains(id)) {
            selectedIds - id
        } else {
            selectedIds + id
        }
        return copy(selectedIds = newSelected)
    }
}

sealed interface SelectionMode {
    data object Inactive : SelectionMode
    data class Active(val totalItems: Int) : SelectionMode
}

data class InlineEditState(
    val documentId: Long,
    val field: TextEditField,
    val currentText: String,
    val originalText: String,
    val isDirty: Boolean = false,
    val lastSaveTimestamp: Long = 0L
)

// TextEditField — НЕ ДУБЛИРОВАТЬ, объявлен в DocumentActions.kt

data class TextEditHistoryItem(
    val documentId: Long,
    val field: TextEditField,
    val previousValue: String?,
    val newValue: String?,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface ShareEvent {
    data class File(
        val path: String,
        val mimeType: String,
        val fileName: String? = null
    ) : ShareEvent

    data class TextContent(
        val text: String,
        val title: String
    ) : ShareEvent
}

data class ErrorEvent(
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

data class OcrSettingsSnapshot(
    val confidenceThreshold: Float = 0.7f,
    val geminiEnabled: Boolean = true,
    val usedThreshold: Float? = null
)