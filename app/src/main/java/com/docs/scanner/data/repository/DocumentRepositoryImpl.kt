package com.docs.scanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*
import com.docs.scanner.domain.model.*
import com.docs.scanner.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao
) : DocumentRepository {
    
    private val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
    
    override fun getDocumentsByRecord(recordId: Long): Flow<List<Document>> {
        return documentDao.getDocumentsByRecord(recordId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getDocumentById(id: Long): Document? {
        return documentDao.getDocumentById(id)?.toDomain()
    }
    
    override suspend fun createDocument(recordId: Long, imageUri: Uri): Result<Long> {
        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val destFile = File(documentsDir, fileName)
            
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                val scaledBitmap = scaleBitmap(bitmap, 1920, 1080)
                
                FileOutputStream(destFile).use { output ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                }
                
                bitmap.recycle()
                scaledBitmap.recycle()
            }
            
            val position = documentDao.getNextPosition(recordId)
            
            val document = DocumentEntity(
                recordId = recordId,
                imagePath = "documents/$fileName",
                position = position
            )
            
            val id = documentDao.insertDocument(document)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    override fun searchEverywhere(query: String): Flow<List<Document>> {
        return documentDao.searchEverywhere(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun updateDocument(document: Document): Result<Unit> {
        return try {
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
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun deleteDocument(id: Long): Result<Unit> {
        return try {
            val doc = documentDao.getDocumentById(id)
            doc?.let {
                val file = File(context.filesDir, it.imagePath)
                if (file.exists()) file.delete()
            }
            
            documentDao.deleteDocumentById(id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun updateOriginalText(id: Long, text: String): Result<Unit> {
        return try {
            documentDao.updateOriginalText(id, text, ProcessingStatus.OCR_COMPLETE.value)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun updateTranslatedText(id: Long, text: String): Result<Unit> {
        return try {
            documentDao.updateTranslatedText(id, text, ProcessingStatus.COMPLETE.value)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun updateProcessingStatus(id: Long, status: ProcessingStatus): Result<Unit> {
        return try {
            documentDao.updateProcessingStatus(id, status.value)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

fun DocumentEntity.toDomain() = Document(
    id = id,
    recordId = recordId,
    imagePath = imagePath,
    imageFile = null,
    originalText = originalText,
    translatedText = translatedText,
    position = position,
    processingStatus = ProcessingStatus.fromInt(processingStatus),
    createdAt = createdAt
)