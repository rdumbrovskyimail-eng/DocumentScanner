package com.docs.scanner.presentation.screens.editor

import com.docs.scanner.domain.core.*
import com.docs.scanner.domain.usecase.AllUseCases
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * КРИТИЧЕСКИ ВАЖНО:
 * Все операции можно отменить через cancellationToken
 * Progress сохраняется даже если UI state изменился
 * Используем отдельный StateFlow для операций
 */
class BatchOperationsManager(
    private val useCases: AllUseCases,
    private val scope: CoroutineScope
) {
    // Текущая активная операция
    private val _currentOperation = MutableStateFlow<BatchOperation?>(null)
    val currentOperation: StateFlow<BatchOperation?> = _currentOperation.asStateFlow()
    
    /**
     * Удалить выбранные документы
     */
    suspend fun deleteDocuments(
        docIds: List<Long>,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (docIds.isEmpty()) {
            onComplete(Result.success(Unit))
            return
        }
        
        val cancellationToken = CancellationTokenSource()
        val operation = BatchOperation.Delete(
            docIds = docIds,
            progress = 0,
            total = docIds.size,
            cancellationToken = cancellationToken
        )
        
        _currentOperation.value = operation
        
        try {
            useCases.batch.deleteDocuments(
                docIds = docIds.map { DocumentId(it) },
                onProgress = { done, total ->
                    if (cancellationToken.isCancellationRequested) {
                        throw CancellationException("User cancelled deletion")
                    }
                    
                    // Обновляем прогресс НЕЗАВИСИМО от UI state
                    val current = _currentOperation.value as? BatchOperation.Delete
                    if (current != null) {
                        _currentOperation.value = current.copy(
                            progress = done,
                            total = total
                        )
                    }
                },
                cancellationToken = cancellationToken
            )
            
            _currentOperation.value = null
            onComplete(Result.success(Unit))
            
            Timber.d("Deleted ${docIds.size} documents")
        } catch (e: CancellationException) {
            _currentOperation.value = null
            Timber.w("Deletion cancelled by user")
            onComplete(Result.failure(e))
        } catch (e: Exception) {
            _currentOperation.value = null
            Timber.e(e, "Failed to delete documents")
            onComplete(Result.failure(e))
        }
    }
    
    /**
     * Экспортировать выбранные документы
     */
    suspend fun exportDocuments(
        docIds: List<Long>,
        asPdf: Boolean,
        onComplete: (Result<String>) -> Unit // Path to exported file
    ) {
        if (docIds.isEmpty()) {
            onComplete(Result.failure(IllegalArgumentException("No documents to export")))
            return
        }
        
        val cancellationToken = CancellationTokenSource()
        val operation = BatchOperation.Export(
            docIds = docIds,
            progress = 0,
            total = 100, // Progress в процентах
            asPdf = asPdf,
            cancellationToken = cancellationToken
        )
        
        _currentOperation.value = operation
        
        try {
            val result = useCases.export.shareDocuments(
                docIds = docIds.map { DocumentId(it) },
                asPdf = asPdf
            )
            
            when (result) {
                is DomainResult.Success -> {
                    _currentOperation.value = null
                    onComplete(Result.success(result.data))
                    Timber.d("Exported ${docIds.size} documents as ${if (asPdf) "PDF" else "ZIP"}")
                }
                is DomainResult.Failure -> {
                    _currentOperation.value = null
                    onComplete(Result.failure(Exception(result.error.message)))
                    Timber.e("Export failed: ${result.error.message}")
                }
            }
        } catch (e: CancellationException) {
            _currentOperation.value = null
            Timber.w("Export cancelled by user")
            onComplete(Result.failure(e))
        } catch (e: Exception) {
            _currentOperation.value = null
            Timber.e(e, "Failed to export documents")
            onComplete(Result.failure(e))
        }
    }
    
    /**
     * Переместить выбранные документы в другую запись
     */
    suspend fun moveDocuments(
        docIds: List<Long>,
        targetRecordId: Long,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (docIds.isEmpty()) {
            onComplete(Result.success(Unit))
            return
        }
        
        val cancellationToken = CancellationTokenSource()
        val operation = BatchOperation.Move(
            docIds = docIds,
            progress = 0,
            total = docIds.size,
            targetRecordId = targetRecordId,
            cancellationToken = cancellationToken
        )
        
        _currentOperation.value = operation
        
        try {
            var successCount = 0
            var failedCount = 0
            
            docIds.forEachIndexed { index, docId ->
                if (cancellationToken.isCancellationRequested) {
                    throw CancellationException("User cancelled move")
                }
                
                when (useCases.documents.move(
                    DocumentId(docId),
                    RecordId(targetRecordId)
                )) {
                    is DomainResult.Success -> successCount++
                    is DomainResult.Failure -> failedCount++
                }
                
                // Обновляем прогресс
                val current = _currentOperation.value as? BatchOperation.Move
                if (current != null) {
                    _currentOperation.value = current.copy(
                        progress = index + 1,
                        total = docIds.size
                    )
                }
            }
            
            _currentOperation.value = null
            
            if (failedCount > 0) {
                Timber.w("Moved $successCount documents, $failedCount failed")
                onComplete(Result.failure(Exception("$failedCount documents failed to move")))
            } else {
                Timber.d("Moved ${docIds.size} documents to record $targetRecordId")
                onComplete(Result.success(Unit))
            }
        } catch (e: CancellationException) {
            _currentOperation.value = null
            Timber.w("Move cancelled by user")
            onComplete(Result.failure(e))
        } catch (e: Exception) {
            _currentOperation.value = null
            Timber.e(e, "Failed to move documents")
            onComplete(Result.failure(e))
        }
    }
    
    /**
     * Повторить OCR для всех документов
     */
    suspend fun retryAllOcr(
        docIds: List<Long>,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (docIds.isEmpty()) {
            onComplete(Result.success(Unit))
            return
        }
        
        val cancellationToken = CancellationTokenSource()
        val operation = BatchOperation.RetryOcr(
            docIds = docIds,
            progress = 0,
            total = docIds.size,
            cancellationToken = cancellationToken
        )
        
        _currentOperation.value = operation
        
        try {
            docIds.forEachIndexed { index, docId ->
                if (cancellationToken.isCancellationRequested) {
                    throw CancellationException("User cancelled OCR retry")
                }
                
                useCases.fixOcr(docId)
                
                // Обновляем прогресс
                val current = _currentOperation.value as? BatchOperation.RetryOcr
                if (current != null) {
                    _currentOperation.value = current.copy(
                        progress = index + 1,
                        total = docIds.size
                    )
                }
            }
            
            _currentOperation.value = null
            onComplete(Result.success(Unit))
            Timber.d("Retried OCR for ${docIds.size} documents")
        } catch (e: CancellationException) {
            _currentOperation.value = null
            Timber.w("OCR retry cancelled by user")
            onComplete(Result.failure(e))
        } catch (e: Exception) {
            _currentOperation.value = null
            Timber.e(e, "Failed to retry OCR")
            onComplete(Result.failure(e))
        }
    }
    
    /**
     * Повторить перевод для всех документов
     */
    suspend fun retryAllTranslation(
        docIds: List<Long>,
        targetLanguage: Language,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (docIds.isEmpty()) {
            onComplete(Result.success(Unit))
            return
        }
        
        val cancellationToken = CancellationTokenSource()
        val operation = BatchOperation.RetryTranslation(
            docIds = docIds,
            progress = 0,
            total = docIds.size,
            targetLanguage = targetLanguage,
            cancellationToken = cancellationToken
        )
        
        _currentOperation.value = operation
        
        try {
            var failedCount = 0
            
            docIds.forEachIndexed { index, docId ->
                if (cancellationToken.isCancellationRequested) {
                    throw CancellationException("User cancelled translation retry")
                }
                
                when (useCases.translation.translateDocument(
                    docId = DocumentId(docId),
                    targetLang = targetLanguage
                )) {
                    is DomainResult.Failure -> failedCount++
                    else -> {}
                }
                
                // Обновляем прогресс
                val current = _currentOperation.value as? BatchOperation.RetryTranslation
                if (current != null) {
                    _currentOperation.value = current.copy(
                        progress = index + 1,
                        total = docIds.size
                    )
                }
            }
            
            _currentOperation.value = null
            
            if (failedCount > 0) {
                Timber.w("Retried translation for ${docIds.size} documents, $failedCount failed")
                onComplete(Result.failure(Exception("$failedCount translations failed")))
            } else {
                Timber.d("Retried translation for ${docIds.size} documents")
                onComplete(Result.success(Unit))
            }
        } catch (e: CancellationException) {
            _currentOperation.value = null
            Timber.w("Translation retry cancelled by user")
            onComplete(Result.failure(e))
        } catch (e: Exception) {
            _currentOperation.value = null
            Timber.e(e, "Failed to retry translation")
            onComplete(Result.failure(e))
        }
    }
    
    /**
     * Отменить текущую операцию
     */
    fun cancelCurrentOperation() {
        val operation = _currentOperation.value ?: return
        operation.cancellationToken.cancel()
        _currentOperation.value = null
        Timber.d("Cancelled current batch operation")
    }
}

/**
 * Batch operation state
 */
sealed class BatchOperation {
    abstract val docIds: List<Long>
    abstract val progress: Int
    abstract val total: Int
    abstract val cancellationToken: CancellationTokenSource
    
    data class Delete(
        override val docIds: List<Long>,
        override val progress: Int,
        override val total: Int,
        override val cancellationToken: CancellationTokenSource
    ) : BatchOperation()
    
    data class Export(
        override val docIds: List<Long>,
        override val progress: Int,
        override val total: Int,
        val asPdf: Boolean,
        override val cancellationToken: CancellationTokenSource
    ) : BatchOperation()
    
    data class Move(
        override val docIds: List<Long>,
        override val progress: Int,
        override val total: Int,
        val targetRecordId: Long,
        override val cancellationToken: CancellationTokenSource
    ) : BatchOperation()
    
    data class RetryOcr(
        override val docIds: List<Long>,
        override val progress: Int,
        override val total: Int,
        override val cancellationToken: CancellationTokenSource
    ) : BatchOperation()
    
    data class RetryTranslation(
        override val docIds: List<Long>,
        override val progress: Int,
        override val total: Int,
        val targetLanguage: Language,
        override val cancellationToken: CancellationTokenSource
    ) : BatchOperation()
    
    val progressPercent: Int
        get() = if (total > 0) (progress * 100) / total else 0
}

/**
 * Cancellation token
 */
class CancellationTokenSource {
    private val cancelled = AtomicBoolean(false)
    
    val isCancellationRequested: Boolean
        get() = cancelled.get()
    
    fun cancel() {
        cancelled.set(true)
    }
}