package com.docs.scanner.domain.usecase.document

import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Get all documents in a record.
 * Returns Flow for real-time updates.
 */
class GetDocumentsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    operator fun invoke(recordId: Long): Flow<List<Document>> {
        return documentRepository.getDocumentsByRecord(recordId)
    }
}