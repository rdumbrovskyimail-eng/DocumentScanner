package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.*
import com.docs.scanner.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetFoldersUseCase @Inject constructor(
    private val repository: FolderRepository
) {
    operator fun invoke(): Flow<List<Folder>> = repository.getAllFolders()
}

class CreateFolderUseCase @Inject constructor(
    private val repository: FolderRepository
) {
    suspend operator fun invoke(name: String, description: String? = null): Result<Long> {
        if (name.isBlank()) {
            return Result.Error(Exception("Folder name cannot be empty"))
        }
        return repository.createFolder(name, description)
    }
}

class UpdateFolderUseCase @Inject constructor(
    private val repository: FolderRepository
) {
    suspend operator fun invoke(folder: Folder): Result<Unit> {
        return repository.updateFolder(folder)
    }
}

class DeleteFolderUseCase @Inject constructor(
    private val repository: FolderRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> {
        return repository.deleteFolder(id)
    }
}

class GetRecordsUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(folderId: Long): Flow<List<Record>> {
        return repository.getRecordsByFolder(folderId)
    }
}

class CreateRecordUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    suspend operator fun invoke(folderId: Long, name: String, description: String? = null): Result<Long> {
        if (name.isBlank()) {
            return Result.Error(Exception("Record name cannot be empty"))
        }
        return repository.createRecord(folderId, name, description)
    }
}

class UpdateRecordUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    suspend operator fun invoke(record: Record): Result<Unit> {
        return repository.updateRecord(record)
    }
}

class DeleteRecordUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> {
        return repository.deleteRecord(id)
    }
}

class MoveRecordUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    suspend operator fun invoke(recordId: Long, newFolderId: Long): Result<Unit> {
        return repository.moveRecord(recordId, newFolderId)
    }
}

class GetDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    operator fun invoke(recordId: Long): Flow<List<Document>> {
        return repository.getDocumentsByRecord(recordId)
    }
}

class AddDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    suspend operator fun invoke(recordId: Long, imageUri: Uri): Result<Long> {
        return try {
            when (val result = documentRepository.createDocument(recordId, imageUri)) {
                is Result.Success -> {
                    val documentId = result.data
                    
                    // Launch background processing
                    scope.launch {
                        performOcrAndTranslation(documentId, imageUri)
                    }
                    
                    Result.Success(documentId)
                }
                is Result.Error -> result
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to add document: ${e.message}", e))
        }
    }
    
    private suspend fun performOcrAndTranslation(documentId: Long, imageUri: Uri) {
        try {
            // Set status to OCR in progress
            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.OCR_IN_PROGRESS)
            
            // Perform OCR
            when (val ocrResult = scannerRepository.scanImage(imageUri)) {
                is Result.Success -> {
                    documentRepository.updateOriginalText(documentId, ocrResult.data)
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.TRANSLATION_IN_PROGRESS)
                    
                    // Small delay to prevent rate limiting
                    delay(500)
                    
                    // Perform translation
                    when (val translationResult = scannerRepository.translateText(ocrResult.data)) {
                        is Result.Success -> {
                            documentRepository.updateTranslatedText(documentId, translationResult.data)
                        }
                        is Result.Error -> {
                            println("Translation error: ${translationResult.exception.message}")
                            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                        }
                        else -> {
                            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                        }
                    }
                }
                is Result.Error -> {
                    println("OCR error: ${ocrResult.exception.message}")
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                }
                else -> {
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                }
            }
        } catch (e: Exception) {
            println("Processing error: ${e.message}")
            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
        }
    }
}

class DeleteDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> {
        return repository.deleteDocument(id)
    }
}

class RetryTranslationUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    suspend operator fun invoke(documentId: Long): Result<Unit> {
        return try {
            val document = documentRepository.getDocumentById(documentId)
                ?: return Result.Error(Exception("Document not found"))
            
            val originalText = document.originalText
                ?: return Result.Error(Exception("No original text to translate"))
            
            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.TRANSLATION_IN_PROGRESS)
            
            when (val result = scannerRepository.translateText(originalText)) {
                is Result.Success -> {
                    documentRepository.updateTranslatedText(documentId, result.data)
                }
                is Result.Error -> {
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                    Result.Error(result.exception)
                }
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to retry translation: ${e.message}", e))
        }
    }
}

class FixOcrUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    suspend operator fun invoke(documentId: Long): Result<Unit> {
        return try {
            val document = documentRepository.getDocumentById(documentId)
                ?: return Result.Error(Exception("Document not found"))
            
            val originalText = document.originalText
                ?: return Result.Error(Exception("No text to fix"))
            
            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.OCR_IN_PROGRESS)
            
            when (val result = scannerRepository.fixOcrText(originalText)) {
                is Result.Success -> {
                    documentRepository.updateOriginalText(documentId, result.data)
                    
                    // Also retry translation with fixed text
                    RetryTranslationUseCase(documentRepository, scannerRepository).invoke(documentId)
                }
                is Result.Error -> {
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                    Result.Error(result.exception)
                }
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to fix OCR: ${e.message}", e))
        }
    }
}

class QuickScanUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val recordRepository: RecordRepository,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val getFoldersUseCase: GetFoldersUseCase
) {
    suspend operator fun invoke(imageUri: Uri): Result<Long> {
        return try {
            val foldersFlow = getFoldersUseCase()
            var quickScansFolder: Folder? = null
            
            foldersFlow.first().let { folders ->
                quickScansFolder = folders.find { it.name == "Quick Scans" }
            }
            
            val folderId = if (quickScansFolder == null) {
                when (val result = folderRepository.createFolder("Quick Scans", "Quickly scanned documents")) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.exception)
                    else -> return Result.Error(Exception("Unknown error creating folder"))
                }
            } else {
                quickScansFolder!!.id
            }
            
            val timestamp = System.currentTimeMillis()
            val recordName = "Scan ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(timestamp))}"
            
            val recordId = when (val result = recordRepository.createRecord(folderId, recordName, null)) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.exception)
                else -> return Result.Error(Exception("Unknown error creating record"))
            }
            
            when (val result = addDocumentUseCase(recordId, imageUri)) {
                is Result.Success -> Result.Success(recordId)
                is Result.Error -> result
                else -> Result.Error(Exception("Unknown error adding document"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Quick scan failed: ${e.message}", e))
        }
    }
}