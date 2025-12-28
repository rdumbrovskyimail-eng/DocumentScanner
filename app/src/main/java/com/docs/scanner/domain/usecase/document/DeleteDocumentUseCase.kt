package com.docs.scanner.domain.usecase.document

import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * Delete a document by ID.
 * Also deletes the associated image file.
 */
class DeleteDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(documentId: Long): Result<Unit> {
        if (documentId <= 0) {
            return Result.Error(
                IllegalArgumentException("Invalid document ID: $documentId")
            )
        }
        
        return documentRepository.deleteDocument(documentId)
    }
}