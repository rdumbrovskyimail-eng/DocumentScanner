package com.docs.scanner.domain.usecase.document

import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Search documents by text content using FTS5.
 * 
 * Session 6 addition: Required by SearchViewModel.
 */
class SearchDocumentsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    operator fun invoke(query: String): Flow<List<Document>> {
        if (query.isBlank()) {
            return flowOf(emptyList())
        }
        
        return documentRepository.searchEverywhere(query)
            .map { documents ->
                documents.sortedByDescending { it.createdAt }
            }
    }
}