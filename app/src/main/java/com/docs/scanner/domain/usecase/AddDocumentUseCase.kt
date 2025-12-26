package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.ScannerRepository
import javax.inject.Inject

class AddDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    suspend operator fun invoke(recordId: Long, imageUri: Uri): Result<Long> {
        return try {
            val createResult = documentRepository.createDocument(recordId, imageUri)
            if (createResult !is Result.Success) return createResult

            val documentId = createResult.data

            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.OCR_IN_PROGRESS)

            when (val ocrResult = scannerRepository.scanImage(imageUri)) {
                is Result.Success -> {
                    documentRepository.updateOriginalText(documentId, ocrResult.data)
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.TRANSLATION_IN_PROGRESS)

                    when (val translateResult = scannerRepository.translateText(ocrResult.data)) {
                        is Result.Success -> {
                            documentRepository.updateTranslatedText(documentId, translateResult.data)
                            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.COMPLETE)
                        }
                        is Result.Error -> {
                            documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                        }
                    }
                }
                is Result.Error -> {
                    documentRepository.updateProcessingStatus(documentId, ProcessingStatus.ERROR)
                }
            }

            Result.Success(documentId)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}