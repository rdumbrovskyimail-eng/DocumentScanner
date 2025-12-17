package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.*
import com.docs.scanner.domain.repository.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// ✅ FOLDERS

class GetFoldersUseCase @Inject constructor(
    private val repository: FolderRepository
) {
    operator fun invoke(): Flow<List<Folder>> {
        return repository.getAllFolders()
    }
}

class CreateFolderUseCase @Inject constructor(
    private val repository: FolderRepository
) {
    suspend operator fun invoke(name: String, description: String?): Result<Long> {
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

// ✅ RECORDS

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
    suspend operator fun invoke(folderId: Long, name: String, description: String?): Result<Long> {
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

// ✅ DOCUMENTS

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
    suspend operator fun invoke(recordId: Long, imageUri: Uri): Result<Long> {
        return try {
            // Создаем документ
            val createResult = documentRepository.createDocument(recordId, imageUri)
            
            if (createResult is Result.Success) {
                val documentId = createResult.data
                
                // Обновляем статус: OCR начался
                documentRepository.updateProcessingStatus(documentId, ProcessingStatus.OCR_IN_PROGRESS)
                
                // Запускаем OCR
                when (val ocrResult = scannerRepository.scanImage(imageUri)) {
                    is Result.Success -> {
                        val ocrText = ocrResult.data
                        documentRepository.updateOriginalText(documentId, ocrText)
                        
                        // Обновляем статус: перевод начался
                        documentRepository.updateProcessingStatus(documentId, ProcessingStatus.TRANSLATION_IN_PROGRESS)
                        
                        // Запускаем перевод
                        when (val translateResult = scannerRepository.translateText(ocrText)) {
                            is Result.Success -> {
                                documentRepository.updateTranslatedText(documentId, translateResult.data)
                                Result.Success(documentId)
                            }
                            is Result.Error -> {
                                documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                                Result.Success(documentId)
                            }
                            else -> Result.Success(documentId)
                        }
                    }
                    is Result.Error -> {
                        documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                        Result.Success(documentId)
                    }
                    else -> Result.Success(documentId)
                }
            } else {
                createResult
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to add document: ${e.message}", e))
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

class FixOcrUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    suspend operator fun invoke(documentId: Long): Result<Unit> {
        return try {
            val document = documentRepository.getDocumentById(documentId)
                ?: return Result.Error(Exception("Document not found"))
            
            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.OCR_IN_PROGRESS)
            
            when (val result = scannerRepository.fixOcrText(document.originalText ?: "")) {
                is Result.Success -> {
                    documentRepository.updateOriginalText(documentId, result.data)
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                    result
                }
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
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
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                    result
                }
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
