package com.docs.scanner.domain.usecase

import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.ScannerRepository
import javax.inject.Inject

class FixOcrUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    suspend operator fun invoke(documentId: Long): Result<Unit> {
        return try {
            val document = documentRepository.getDocumentById(documentId)
                ?: return Result.Error(Exception("Document not found"))

            val currentText = document.originalText.orEmpty()

            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.OCR_IN_PROGRESS)

            when (val result = scannerRepository.fixOcrText(currentText)) {
                is Result.Success -> {
                    documentRepository.updateOriginalText(documentId, result.data)
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.OCR_COMPLETE)
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                    Result.Error(result.exception)
                }
                is Result.Loading -> {
                    Result.Success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}