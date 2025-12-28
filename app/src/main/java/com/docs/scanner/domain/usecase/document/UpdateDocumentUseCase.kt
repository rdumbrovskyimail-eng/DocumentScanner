package com.docs.scanner.domain.usecase.document

import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * Update document metadata (name, description, position, etc.).
 * 
 * Used by: EditorViewModel
 * 
 * Features:
 * - Update document properties
 * - Update texts (OCR/translation)
 * - Update processing status
 * - Validation
 * 
 * Usage:
 * ```
 * val updated = document.copy(
 *     originalText = "Corrected text",
 *     translatedText = "New translation"
 * )
 * updateDocumentUseCase(updated)
 * ```
 */
class UpdateDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    /**
     * Update document.
     * 
     * @param document Document with updated fields
     * @return Result<Unit>
     */
    suspend operator fun invoke(document: Document): Result<Unit> {
        return try {
            // Validation
            if (document.id <= 0) {
                return Result.Error(Exception("Invalid document ID"))
            }
            
            if (document.recordId <= 0) {
                return Result.Error(Exception("Invalid record ID"))
            }
            
            if (document.imagePath.isBlank()) {
                return Result.Error(Exception("Image path cannot be empty"))
            }
            
            // Check document exists
            val existing = documentRepository.getDocumentById(document.id)
            if (existing == null) {
                return Result.Error(Exception("Document not found"))
            }
            
            // Update
            documentRepository.updateDocument(document)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to update document: ${e.message}", e))
        }
    }
    
    /**
     * Update only original text (for OCR corrections).
     */
    suspend fun updateOriginalText(documentId: Long, text: String): Result<Unit> {
        return try {
            if (documentId <= 0) {
                return Result.Error(Exception("Invalid document ID"))
            }
            
            if (text.isBlank()) {
                return Result.Error(Exception("Text cannot be empty"))
            }
            
            documentRepository.updateOriginalText(documentId, text)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to update text: ${e.message}", e))
        }
    }
    
    /**
     * Update only translated text.
     */
    suspend fun updateTranslatedText(documentId: Long, text: String): Result<Unit> {
        return try {
            if (documentId <= 0) {
                return Result.Error(Exception("Invalid document ID"))
            }
            
            if (text.isBlank()) {
                return Result.Error(Exception("Text cannot be empty"))
            }
            
            documentRepository.updateTranslatedText(documentId, text)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to update translation: ${e.message}", e))
        }
    }
    
    /**
     * Update document position in record (for reordering).
     */
    suspend fun updatePosition(documentId: Long, position: Int): Result<Unit> {
        return try {
            if (documentId <= 0) {
                return Result.Error(Exception("Invalid document ID"))
            }
            
            if (position < 0) {
                return Result.Error(Exception("Position must be >= 0"))
            }
            
            val document = documentRepository.getDocumentById(documentId)
                ?: return Result.Error(Exception("Document not found"))
            
            val updated = document.copy(position = position)
            documentRepository.updateDocument(updated)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to update position: ${e.message}", e))
        }
    }
}