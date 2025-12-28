package com.docs.scanner.domain.usecase

import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import com.docs.scanner.domain.repository.ScannerRepository
import javax.inject.Inject

/**
 * Retry translation for a document.
 * Production-ready with progress callback added.
 */
class RetryTranslationUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scannerRepository: ScannerRepository
) {
    suspend operator fun invoke(
        documentId: Long,
        targetLanguage: String = "ru",
        onProgress: ((String) -> Unit)? = null
    ): Result<Unit> {
        return try {
            onProgress?.invoke("Loading document...")
            
            val document = documentRepository.getDocumentById(documentId)
                ?: return Result.Error(Exception("Document not found"))

            val text = document.originalText
                ?: return Result.Error(Exception("No OCR text to translate"))
            
            if (text.isBlank()) {
                return Result.Error(Exception("Text is empty"))
            }

            onProgress?.invoke("Translating to $targetLanguage...")
            
            documentRepository.updateProcessingStatus(
                documentId, 
                ProcessingStatus.TRANSLATION_IN_PROGRESS
            )

            when (val result = scannerRepository.translateText(
                text = text,
                targetLanguage = targetLanguage
            )) {
                is Result.Success -> {
                    onProgress?.invoke("Saving translation...")
                    
                    documentRepository.updateTranslatedText(documentId, result.data)
                    documentRepository.updateProcessingStatus(
                        documentId, 
                        ProcessingStatus.COMPLETE
                    )
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    documentRepository.updateProcessingStatus(
                        documentId, 
                        ProcessingStatus.ERROR
                    )
                    Result.Error(result.exception)
                }
                is Result.Loading -> Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
