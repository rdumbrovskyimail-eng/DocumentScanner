package com.docs.scanner.data.repository

import android.content.Context
import com.docs.scanner.data.local.database.dao.DocumentDao
import com.docs.scanner.data.local.database.dao.DocumentWithNamesDto
import com.docs.scanner.data.local.database.entities.DocumentEntity
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.DocumentWithNames
import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DocumentRepository.
 * 
 * Handles all database operations for documents including:
 * - CRUD operations
 * - FTS search
 * - Image file management
 */
@Singleton
class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao
) : DocumentRepository {

    // ══════════════════════════════════════════════════════════════
    // READ OPERATIONS
    // ══════════════════════════════════════════════════════════════

    override fun getDocumentsByRecord(recordId: Long): Flow<List<Document>> {
        return documentDao.getByRecordId(recordId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDocumentById(documentId: Long): Document? {
        return documentDao.getById(documentId)?.toDomain()
    }

    // ══════════════════════════════════════════════════════════════
    // WRITE OPERATIONS
    // ══════════════════════════════════════════════════════════════

    override suspend fun createDocument(
        recordId: Long,
        imagePath: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Validation
            require(recordId > 0) { "Invalid record ID: $recordId" }
            require(imagePath.isNotBlank()) { "Image path cannot be blank" }
            
            // Verify image file exists
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                return@withContext Result.Error(
                    IllegalArgumentException("Image file does not exist: $imagePath")
                )
            }
            
            // Get next position
            val position = documentDao.getNextPosition(recordId)
            
            val entity = DocumentEntity(
                recordId = recordId,
                imagePath = imagePath,
                position = position,
                processingStatus = ProcessingStatus.INITIAL.value,
                createdAt = System.currentTimeMillis()
            )
            
            val id = documentDao.insert(entity)
            Timber.d("Created document $id for record $recordId")
            
            Result.Success(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create document")
            Result.Error(e)
        }
    }

    override suspend fun updateDocument(document: Document): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                require(document.id > 0) { "Invalid document ID" }
                
                val entity = DocumentEntity.fromDomain(document)
                documentDao.update(entity)
                
                Timber.d("Updated document ${document.id}")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update document")
                Result.Error(e)
            }
        }

    override suspend fun deleteDocument(documentId: Long): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                require(documentId > 0) { "Invalid document ID" }
                
                // Get document to delete image file
                val document = documentDao.getById(documentId)
                
                // Delete from database
                documentDao.deleteById(documentId)
                
                // Delete image file
                document?.let { doc ->
                    try {
                        val imageFile = File(doc.imagePath)
                        if (imageFile.exists()) {
                            imageFile.delete()
                            Timber.d("Deleted image file: ${doc.imagePath}")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to delete image file: ${doc.imagePath}")
                        // Don't fail the operation if file deletion fails
                    }
                }
                
                Timber.d("Deleted document $documentId")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete document")
                Result.Error(e)
            }
        }

    // ══════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS
    // ══════════════════════════════════════════════════════════════

    override fun searchDocuments(query: String): Flow<List<Document>> {
        if (query.isBlank()) {
            return documentDao.getAllDocuments().map { entities ->
                entities.map { it.toDomain() }
            }
        }
        
        // Use LIKE search as fallback (FTS might not be available)
        return documentDao.searchLike(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchDocumentsWithNames(query: String): Flow<List<DocumentWithNames>> {
        if (query.isBlank()) {
            return documentDao.searchWithNames("").map { dtos ->
                dtos.map { it.toDomain() }
            }
        }
        
        return documentDao.searchWithNames(query).map { dtos ->
            dtos.map { it.toDomain() }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MAPPERS
    // ══════════════════════════════════════════════════════════════

    private fun DocumentEntity.toDomain(): Document = Document(
        id = id,
        recordId = recordId,
        imagePath = imagePath,
        originalText = originalText,
        translatedText = translatedText,
        position = position,
        processingStatus = ProcessingStatus.fromInt(processingStatus),
        createdAt = createdAt
    )

    private fun DocumentWithNamesDto.toDomain(): DocumentWithNames = DocumentWithNames(
        id = id,
        recordId = recordId,
        imagePath = imagePath,
        originalText = originalText,
        translatedText = translatedText,
        position = position,
        processingStatus = ProcessingStatus.fromInt(processingStatus),
        createdAt = createdAt,
        recordName = recordName,
        folderName = folderName
    )
}
