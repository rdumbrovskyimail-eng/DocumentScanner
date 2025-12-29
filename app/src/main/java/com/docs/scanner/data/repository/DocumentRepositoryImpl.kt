package com.docs.scanner.data.repository

import android.net.Uri
import com.docs.scanner.data.local.database.dao.DocumentDao
import com.docs.scanner.data.local.database.entities.DocumentEntity
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.ProcessingStatus
import com.docs.scanner.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DocumentRepository.
 * 
 * Responsibilities:
 * - Map between DocumentEntity (data) and Document (domain)
 * - Handle document operations via DocumentDao
 * - Manage image files
 * - Full-text search via FTS5
 */
@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao
) : DocumentRepository {
    
    override fun getDocumentsByRecord(recordId: Long): Flow<List<Document>> {
        return documentDao.getDocumentsByRecord(recordId).map { documents ->
            documents.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getDocumentById(id: Long): Document? {
        return documentDao.getDocumentById(id)?.toDomainModel()
    }
    
    override suspend fun createDocument(recordId: Long, imageUri: Uri): Result<Long> {
        return try {
            require(recordId > 0) { "Invalid record ID: $recordId" }
            
            // Get next position for this record
            val position = documentDao.getNextPosition(recordId)
            
            val entity = DocumentEntity(
                recordId = recordId,
                imagePath = imageUri.toString(),
                position = position,
                processingStatus = ProcessingStatus.INITIAL.value,
                createdAt = System.currentTimeMillis()
            )
            
            val id = documentDao.insertDocument(entity)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateDocument(document: Document): Result<Unit> {
        return try {
            require(document.id > 0) { "Invalid document ID: ${document.id}" }
            require(document.recordId > 0) { "Invalid record ID: ${document.recordId}" }
            
            val entity = DocumentEntity(
                id = document.id,
                recordId = document.recordId,
                imagePath = document.imagePath,
                originalText = document.originalText,
                translatedText = document.translatedText,
                position = document.position,
                processingStatus = document.processingStatus.value,
                createdAt = document.createdAt
            )
            
            documentDao.updateDocument(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteDocument(id: Long): Result<Unit> {
        return try {
            require(id > 0) { "Invalid document ID: $id" }
            
            // Get document to delete image file
            val document = documentDao.getDocumentById(id)
            
            // Delete from database (will also delete from FTS via trigger)
            documentDao.deleteDocumentById(id)
            
            // Delete image file if exists
            document?.imagePath?.let { path ->
                try {
                    val file = File(Uri.parse(path).path ?: path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    // Log but don't fail - database entry is already deleted
                    android.util.Log.w("DocumentRepository", "Failed to delete image file: $path", e)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateOriginalText(id: Long, text: String): Result<Unit> {
        return try {
            require(id > 0) { "Invalid document ID: $id" }
            
            documentDao.updateOriginalText(
                documentId = id,
                text = text,
                status = ProcessingStatus.OCR_COMPLETE.value
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateTranslatedText(id: Long, text: String): Result<Unit> {
        return try {
            require(id > 0) { "Invalid document ID: $id" }
            
            documentDao.updateTranslatedText(
                documentId = id,
                text = text,
                status = ProcessingStatus.COMPLETE.value
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateProcessingStatus(id: Long, status: ProcessingStatus): Result<Unit> {
        return try {
            require(id > 0) { "Invalid document ID: $id" }
            
            documentDao.updateProcessingStatus(
                documentId = id,
                status = status.value
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun searchEverywhere(query: String): Flow<List<Document>> {
        return if (query.isBlank()) {
            // Return empty flow for blank query
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            // Use FTS5 search
            documentDao.searchEverywhereWithNames(
                query = query,
                limit = 50,
                offset = 0
            ).map { results ->
                results.map { it.toDocument() }
            }
        }
    }
    
    override fun searchEverywhereWithNames(query: String): Flow<List<com.docs.scanner.domain.model.DocumentWithNames>> {
        return if (query.isBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            // Use FTS5 search
            documentDao.searchEverywhereWithNames(
                query = query,
                limit = 50,
                offset = 0
            ).map { results ->
                results.map { it.toDomainModel() }
            }
        }
    }
    
    // ============================================
    // MAPPERS: Entity → Domain
    // ============================================
    
    private fun DocumentEntity.toDomainModel(): Document {
        return Document(
            id = this.id,
            recordId = this.recordId,
            imagePath = this.imagePath,
            originalText = this.originalText,
            translatedText = this.translatedText,
            position = this.position,
            processingStatus = ProcessingStatus.fromInt(this.processingStatus),
            createdAt = this.createdAt
        )
    }
    
    private fun com.docs.scanner.data.local.database.dto.DocumentWithNames.toDomainModel(): com.docs.scanner.domain.model.DocumentWithNames {
        return com.docs.scanner.domain.model.DocumentWithNames(
            id = this.id,
            recordId = this.recordId,
            imagePath = this.imagePath,
            originalText = this.originalText,
            translatedText = this.translatedText,
            position = this.position,
            processingStatus = ProcessingStatus.fromInt(this.processingStatus),
            createdAt = this.createdAt,
            recordName = this.recordName,
            folderName = this.folderName
        )
    }
    
    private fun com.docs.scanner.data.local.database.dto.DocumentWithNames.toDocument(): Document {
        return Document(
            id = this.id,
            recordId = this.recordId,
            imagePath = this.imagePath,
            originalText = this.originalText,
            translatedText = this.translatedText,
            position = this.position,
            processingStatus = ProcessingStatus.fromInt(this.processingStatus),
            createdAt = this.createdAt,
            recordName = this.recordName,
            folderName = this.folderName
        )
    }
}