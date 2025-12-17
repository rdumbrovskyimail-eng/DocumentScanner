package com.docs.scanner.domain.repository

import com.docs.scanner.domain.model.*
import kotlinx.coroutines.flow.Flow
import android.net.Uri

interface FolderRepository {
    fun getAllFolders(): Flow<List<Folder>>
    suspend fun getFolderById(id: Long): Folder?
    suspend fun createFolder(name: String, description: String?): Result<Long>
    suspend fun updateFolder(folder: Folder): Result<Unit>
    suspend fun deleteFolder(id: Long): Result<Unit>
}

interface RecordRepository {
    fun getRecordsByFolder(folderId: Long): Flow<List<Record>>
    suspend fun getRecordById(id: Long): Record?
    suspend fun createRecord(folderId: Long, name: String, description: String?): Result<Long>
    suspend fun updateRecord(record: Record): Result<Unit>
    suspend fun deleteRecord(id: Long): Result<Unit>
    suspend fun moveRecord(recordId: Long, newFolderId: Long): Result<Unit>
}

interface DocumentRepository {
    fun getDocumentsByRecord(recordId: Long): Flow<List<Document>>
    suspend fun getDocumentById(id: Long): Document?
    suspend fun createDocument(recordId: Long, imageUri: Uri): Result<Long>
    suspend fun updateDocument(document: Document): Result<Unit>
    suspend fun deleteDocument(id: Long): Result<Unit>
    suspend fun updateOriginalText(id: Long, text: String): Result<Unit>
    suspend fun updateTranslatedText(id: Long, text: String): Result<Unit>
    suspend fun updateProcessingStatus(id: Long, status: ProcessingStatus): Result<Unit>
    fun searchEverywhere(query: String): Flow<List<Document>>
    // ✅ Добавлено:
    fun searchEverywhereWithNames(query: String): Flow<List<DocumentWithNames>>
}

interface ScannerRepository {
    suspend fun scanImage(imageUri: Uri): Result<String>
    suspend fun translateText(text: String): Result<String>
    suspend fun fixOcrText(text: String): Result<String>
}

interface SettingsRepository {
    suspend fun getApiKey(): String?
    suspend fun setApiKey(key: String)
    suspend fun isFirstLaunch(): Boolean
    suspend fun setFirstLaunchCompleted()
}
