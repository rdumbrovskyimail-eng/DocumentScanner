package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.*
import com.docs.scanner.domain.repository.*
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
    suspend operator fun invoke(recordId: Long, imageUri: Uri): Result<Long> {
        return when (val result = documentRepository.createDocument(recordId, imageUri)) {
            is Result.Success -> {
                val documentId = result.data
                
                documentRepository.updateProcessingStatus(documentId, ProcessingStatus.OCR_IN_PROGRESS)
                
                when (val ocrResult = scannerRepository.scanImage(imageUri)) {
                    is Result.Success -> {
                        documentRepository.updateOriginalText(documentId, ocrResult.data)
                        
                        documentRepository.updateProcessingStatus(documentId, ProcessingStatus.TRANSLATION_IN_PROGRESS)
                        
                        when (val translationResult = scannerRepository.translateText(ocrResult.data)) {
                            is Result.Success -> {
                                documentRepository.updateTranslatedText(documentId, translationResult.data)
                            }
                            is Result.Error -> {
                                documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                            }
                            else -> {}
                        }
                    }
                    is Result.Error -> {
                        documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                    }
                    else -> {}
                }
                
                Result.Success(documentId)
            }
            is Result.Error -> result
            else -> Result.Error(Exception("Unknown error"))
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
        val document = documentRepository.getDocumentById(documentId)
            ?: return Result.Error(Exception("Document not found"))
        
        val originalText = document.originalText
            ?: return Result.Error(Exception("No original text to translate"))
        
        documentRepository.updateProcessingStatus(documentId, ProcessingStatus.TRANSLATION_IN_PROGRESS)
        
        return when (val result = scannerRepository.translateText(originalText)) {
            is Result.Success -> {
                documentRepository.updateTranslatedText(documentId, result.data)
            }
            is Result.Error -> {
                documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                Result.Error(result.exception)
            }
            else -> Result.Error(Exception("Unknown error"))
        }
    }
}

class FixOcrUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    suspend operator fun invoke(documentId: Long): Result<Unit> {
        val document = documentRepository.getDocumentById(documentId)
            ?: return Result.Error(Exception("Document not found"))
        
        val originalText = document.originalText
            ?: return Result.Error(Exception("No text to fix"))
        
        return when (val result = scannerRepository.fixOcrText(originalText)) {
            is Result.Success -> {
                documentRepository.updateOriginalText(documentId, result.data)
                RetryTranslationUseCase(documentRepository, scannerRepository).invoke(documentId)
            }
            is Result.Error -> Result.Error(result.exception)
            else -> Result.Error(Exception("Unknown error"))
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
        val foldersFlow = getFoldersUseCase()
        var testFolder: Folder? = null
        
        foldersFlow.first().let { folders ->
            testFolder = folders.find { it.name == "Test" }
        }
        
        val folderId = if (testFolder == null) {
            when (val result = folderRepository.createFolder("Test", "test")) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error(result.exception)
                else -> return Result.Error(Exception("Unknown error"))
            }
        } else {
            testFolder!!.id
        }
        
        val recordId = when (val result = recordRepository.createRecord(folderId, "Test", null)) {
            is Result.Success -> result.data
            is Result.Error -> return Result.Error(result.exception)
            else -> return Result.Error(Exception("Unknown error"))
        }
        
        return when (val result = addDocumentUseCase(recordId, imageUri)) {
            is Result.Success -> Result.Success(recordId)
            is Result.Error -> result
            else -> Result.Error(Exception("Unknown error"))
        }
    }
}