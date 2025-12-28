package com.docs.scanner.domain.usecase.document

import android.net.Uri
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * ✅ Session 6 Fix: Split from AddDocumentUseCase
 * Creates document without OCR/translation (SRP principle)
 */
class CreateDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    /**
     * Creates a new document from image URI
     * 
     * @param recordId parent record ID
     * @param imageUri image to save
     * @return document ID or error
     */
    suspend operator fun invoke(recordId: Long, imageUri: Uri): Result<Long> {
        if (recordId <= 0) {
            return Result.Error(Exception("Invalid record ID: $recordId"))
        }
        
        return try {
            documentRepository.createDocument(recordId, imageUri)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to create document: ${e.message}", e))
        }
    }
}