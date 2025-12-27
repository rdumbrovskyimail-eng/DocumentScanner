package com.docs.scanner.domain.usecase.document

import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * Get document by ID.
 * 
 * Session 6 addition: Required by EditorViewModel.
 */
class GetDocumentByIdUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    suspend operator fun invoke(documentId: Long): Document? {
        return documentRepository.getDocumentById(documentId)
    }
}