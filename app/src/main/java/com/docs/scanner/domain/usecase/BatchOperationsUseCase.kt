package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject

/**
 * Batch operations with concurrency control.
 * 
 * Session 6 Fix:
 * - ✅ Replaced custom Semaphore with kotlinx.coroutines.sync.Semaphore
 */
class BatchOperationsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val addDocumentUseCase: AddDocumentUseCase
) {
    
    suspend fun addDocuments(
        recordId: Long,
        imageUris: List<Uri>,
        maxConcurrency: Int = DEFAULT_CONCURRENCY,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<List<Long>> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.Success(emptyList())
        }
        
        if (recordId <= 0) {
            return@withContext Result.Error(Exception("Invalid record ID"))
        }
        
        val successIds = mutableListOf<Long>()
        val errors = mutableListOf<Exception>()
        var completed = 0
        
        try {
            val semaphore = Semaphore(maxConcurrency)
            
            imageUris.map { uri ->
                async {
                    semaphore.withPermit {
                        try {
                            var finalResult: Result<Long>? = null
                            addDocumentUseCase(recordId, uri).collect { state ->
                                when (state) {
                                    is AddDocumentState.Success -> {
                                        finalResult = Result.Success(state.documentId)
                                    }
                                    is AddDocumentState.Error -> {
                                        finalResult = Result.Error(Exception(state.message))
                                    }
                                    else -> {}
                                }
                            }
                            
                            when (finalResult) {
                                is Result.Success -> {
                                    synchronized(successIds) {
                                        successIds.add((finalResult as Result.Success<Long>).data)
                                    }
                                }
                                is Result.Error -> {
                                    synchronized(errors) {
                                        errors.add((finalResult as Result.Error).exception)
                                    }
                                }
                                else -> {
                                    synchronized(errors) {
                                        errors.add(Exception("Unknown state"))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            synchronized(errors) {
                                errors.add(e)
                            }
                        } finally {
                            synchronized(this@withContext) {
                                completed++
                                onProgress?.invoke(completed, imageUris.size)
                            }
                        }
                    }
                }
            }.awaitAll()
            
            when {
                errors.isEmpty() -> Result.Success(successIds)
                successIds.isEmpty() -> Result.Error(
                    Exception("All operations failed: ${errors.first().message}")
                )
                else -> {
                    println("⚠️ Partial success: ${successIds.size}/${imageUris.size}")
                    Result.Success(successIds)
                }
            }
            
        } catch (e: Exception) {
            Result.Error(Exception("Batch operation failed: ${e.message}", e))
        }
    }
    
    suspend fun deleteDocuments(
        documentIds: List<Long>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (documentIds.isEmpty()) {
            return@withContext Result.Success(0)
        }
        
        var deletedCount = 0
        var completed = 0
        val errors = mutableListOf<Exception>()
        
        try {
            val semaphore = Semaphore(DEFAULT_CONCURRENCY)
            
            documentIds.map { id ->
                async {
                    semaphore.withPermit {
                        try {
                            when (documentRepository.deleteDocument(id)) {
                                is Result.Success -> {
                                    synchronized(this@withContext) {
                                        deletedCount++
                                    }
                                }
                                is Result.Error -> {
                                    synchronized(errors) {
                                        errors.add(Exception("Failed to delete $id"))
                                    }
                                }
                                else -> Unit
                            }
                        } catch (e: Exception) {
                            synchronized(errors) {
                                errors.add(e)
                            }
                        } finally {
                            synchronized(this@withContext) {
                                completed++
                                onProgress?.invoke(completed, documentIds.size)
                            }
                        }
                    }
                }
            }.awaitAll()
            
            when {
                errors.isEmpty() -> Result.Success(deletedCount)
                deletedCount == 0 -> Result.Error(Exception("All delete operations failed"))
                else -> {
                    println("⚠️ Partial deletion: $deletedCount/${documentIds.size}")
                    Result.Success(deletedCount)
                }
            }
            
        } catch (e: Exception) {
            Result.Error(Exception("Batch deletion failed: ${e.message}", e))
        }
    }
    
    companion object {
        private const val DEFAULT_CONCURRENCY = 3
    }
}