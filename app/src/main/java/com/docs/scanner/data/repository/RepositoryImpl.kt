package com.docs.scanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.docs.scanner.data.local.database.dao.*
import com.docs.scanner.data.local.database.entities.*
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.data.remote.gemini.GeminiTranslator
import com.docs.scanner.data.remote.mlkit.MLKitScanner
import com.docs.scanner.domain.model.*
import com.docs.scanner.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {
    
    override fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFoldersWithCounts().map { foldersWithCount ->
            foldersWithCount.map { it.toDomain() }
        }
    }
    
    override suspend fun getFolderById(id: Long): Folder? {
        if (id <= 0) return null
        
        val folderWithCount = folderDao.getFolderWithCount(id)
        return folderWithCount?.toDomain()
    }
    
    override suspend fun createFolder(name: String, description: String?): Result<Long> {
        if (name.isBlank()) {
            return Result.Error(Exception("Folder name cannot be empty"))
        }
        
        if (name.length > MAX_NAME_LENGTH) {
            return Result.Error(Exception("Folder name too long (max $MAX_NAME_LENGTH chars)"))
        }
        
        return try {
            val folder = FolderEntity(
                name = name.trim(),
                description = description?.trim()
            )
            val id = folderDao.insertFolder(folder)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to create folder: ${e.message}", e))
        }
    }
    
    override suspend fun updateFolder(folder: Folder): Result<Unit> {
        if (folder.id <= 0) {
            return Result.Error(Exception("Invalid folder ID"))
        }
        
        if (folder.name.isBlank()) {
            return Result.Error(Exception("Folder name cannot be empty"))
        }
        
        if (folder.name.length > MAX_NAME_LENGTH) {
            return Result.Error(Exception("Folder name too long (max $MAX_NAME_LENGTH chars)"))
        }
        
        return try {
            val entity = FolderEntity(
                id = folder.id,
                name = folder.name.trim(),
                description = folder.description?.trim(),
                createdAt = folder.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            folderDao.updateFolder(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to update folder: ${e.message}", e))
        }
    }
    
    override suspend fun deleteFolder(id: Long): Result<Unit> {
        if (id <= 0) {
            return Result.Error(Exception("Invalid folder ID"))
        }
        
        return try {
            folderDao.deleteFolderById(id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to delete folder: ${e.message}", e))
        }
    }
    
    companion object {
        private const val MAX_NAME_LENGTH = 100
    }
}

class RecordRepositoryImpl @Inject constructor(
    private val recordDao: RecordDao,
    private val documentDao: DocumentDao
) : RecordRepository {
    
    override fun getRecordsByFolder(folderId: Long): Flow<List<Record>> {
        return recordDao.getRecordsByFolder(folderId).map { entities ->
            entities.map { entity ->
                val count = documentDao.getDocumentCountInRecord(entity.id)
                entity.toDomain(count)
            }
        }
    }
    
    override suspend fun getRecordById(id: Long): Record? {
        val entity = recordDao.getRecordById(id) ?: return null
        val count = documentDao.getDocumentCountInRecord(id)
        return entity.toDomain(count)
    }
    
    override suspend fun createRecord(folderId: Long, name: String, description: String?): Result<Long> {
        return try {
            val record = RecordEntity(folderId = folderId, name = name, description = description)
            val id = recordDao.insertRecord(record)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun updateRecord(record: Record): Result<Unit> {
        return try {
            val entity = RecordEntity(
                id = record.id,
                folderId = record.folderId,
                name = record.name,
                description = record.description,
                createdAt = record.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            recordDao.updateRecord(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun deleteRecord(id: Long): Result<Unit> {
        return try {
            recordDao.deleteRecordById(id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun moveRecord(recordId: Long, newFolderId: Long): Result<Unit> {
        return try {
            recordDao.moveRecordToFolder(recordId, newFolderId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

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

    // ✅ ДОБАВЛЕНА РЕАЛИЗАЦИЯ searchEverywhere
    override fun searchEverywhere(query: String): Flow<List<Document>> {
        return documentDao.searchEverywhereWithNames(query).map { documentsWithNames ->
            documentsWithNames.map { docWithNames ->
                Document(
                    id = docWithNames.id,
                    recordId = docWithNames.recordId,
                    imagePath = docWithNames.imagePath,
                    imageFile = null,
                    originalText = docWithNames.originalText,
                    translatedText = docWithNames.translatedText,
                    position = 0,
                    processingStatus = ProcessingStatus.fromInt(docWithNames.processingStatus),
                    createdAt = docWithNames.createdAt
                )
            }
        }
    }

    override fun searchEverywhereWithNames(query: String): Flow<List<DocumentWithNames>> {
        return documentDao.searchEverywhereWithNames(query)
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

class ScannerRepositoryImpl @Inject constructor(
    private val mlKitScanner: MLKitScanner,
    private val geminiTranslator: GeminiTranslator,
    private val settingsRepository: SettingsRepository
) : ScannerRepository {
    
    override suspend fun scanImage(imageUri: Uri): Result<String> {
        return mlKitScanner.scanImage(imageUri)
    }
    
    override suspend fun translateText(text: String): Result<String> {
        val apiKey = settingsRepository.getApiKey()
            ?: return Result.Error(Exception("API key not set"))
        
        return geminiTranslator.translate(text, apiKey)
    }
    
    override suspend fun fixOcrText(text: String): Result<String> {
        val apiKey = settingsRepository.getApiKey()
            ?: return Result.Error(Exception("API key not set"))
        
        return geminiTranslator.fixOcrText(text, apiKey)
    }
}

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {
    
    override suspend fun getApiKey(): String? {
        return dataStore.getGeminiApiKey()
    }
    
    override suspend fun setApiKey(key: String) {
        dataStore.setGeminiApiKey(key)
    }
    
    override suspend fun isFirstLaunch(): Boolean {
        return dataStore.getIsFirstLaunch()
    }
    
    override suspend fun setFirstLaunchCompleted() {
        dataStore.setFirstLaunchCompleted()
    }
}

private fun com.docs.scanner.data.local.database.dao.FolderWithCount.toDomain() = Folder(
    id = id,
    name = name,
    description = description,
    recordCount = recordCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun FolderEntity.toDomain(recordCount: Int = 0) = Folder(
    id = id,
    name = name,
    description = description,
    recordCount = recordCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RecordEntity.toDomain(documentCount: Int = 0) = Record(
    id = id,
    folderId = folderId,
    name = name,
    description = description,
    documentCount = documentCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

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
