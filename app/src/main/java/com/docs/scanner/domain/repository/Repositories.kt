package com.docs.scanner.domain.repository

import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.model.DocumentWithNames
import com.docs.scanner.domain.model.Folder
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Folder operations.
 */
interface FolderRepository {
    /**
     * Get all folders as reactive Flow.
     */
    fun getAllFolders(): Flow<List<Folder>>
    
    /**
     * Get folder by ID.
     * @return Folder or null if not found
     */
    suspend fun getFolderById(folderId: Long): Folder?
    
    /**
     * Create a new folder.
     * @param name Folder name (max 100 chars)
     * @param description Optional description
     * @return Result with folder ID on success
     */
    suspend fun createFolder(name: String, description: String?): Result<Long>
    
    /**
     * Update existing folder.
     */
    suspend fun updateFolder(folder: Folder): Result<Unit>
    
    /**
     * Delete folder and all its contents.
     */
    suspend fun deleteFolder(folderId: Long): Result<Unit>
}

/**
 * Repository interface for Record operations.
 */
interface RecordRepository {
    /**
     * Get records in a folder as reactive Flow.
     */
    fun getRecordsByFolder(folderId: Long): Flow<List<Record>>
    
    /**
     * Get record by ID.
     */
    suspend fun getRecordById(recordId: Long): Record?
    
    /**
     * Create a new record in a folder.
     */
    suspend fun createRecord(
        folderId: Long, 
        name: String, 
        description: String?
    ): Result<Long>
    
    /**
     * Update existing record.
     */
    suspend fun updateRecord(record: Record): Result<Unit>
    
    /**
     * Delete record and all its documents.
     */
    suspend fun deleteRecord(recordId: Long): Result<Unit>
    
    /**
     * Move record to another folder.
     */
    suspend fun moveRecord(recordId: Long, targetFolderId: Long): Result<Unit>
}

/**
 * Repository interface for Document operations.
 */
interface DocumentRepository {
    /**
     * Get documents in a record as reactive Flow.
     */
    fun getDocumentsByRecord(recordId: Long): Flow<List<Document>>
    
    /**
     * Get document by ID.
     */
    suspend fun getDocumentById(documentId: Long): Document?
    
    /**
     * Create a new document.
     */
    suspend fun createDocument(recordId: Long, imagePath: String): Result<Long>
    
    /**
     * Update existing document.
     */
    suspend fun updateDocument(document: Document): Result<Unit>
    
    /**
     * Delete document and its image file.
     */
    suspend fun deleteDocument(documentId: Long): Result<Unit>
    
    /**
     * Search documents by text (uses FTS).
     */
    fun searchDocuments(query: String): Flow<List<Document>>
    
    /**
     * Search documents with folder/record names for display.
     */
    fun searchDocumentsWithNames(query: String): Flow<List<DocumentWithNames>>
}

/**
 * Repository interface for Scanner operations (OCR + Translation).
 * 
 * FIX: Removed android.net.Uri - use String path instead for Clean Architecture.
 */
interface ScannerRepository {
    /**
     * Scan image and extract text using OCR.
     * @param imagePath Path to the image file
     * @return Result with extracted text
     */
    suspend fun scanImage(imagePath: String): Result<String>
    
    /**
     * Translate text to target language.
     * 
     * FIX C4: Added targetLanguage parameter
     * 
     * @param text Text to translate
     * @param targetLanguage Target language code (e.g., "ru", "en", "zh")
     * @return Result with translated text
     */
    suspend fun translateText(
        text: String,
        targetLanguage: String = "ru"
    ): Result<String>
    
    /**
     * Fix OCR errors using AI.
     * @param text Raw OCR text with potential errors
     * @return Result with corrected text
     */
    suspend fun fixOcrText(text: String): Result<String>
}

/**
 * Repository interface for Settings operations.
 */
interface SettingsRepository {
    suspend fun getApiKey(): String?
    suspend fun setApiKey(key: String)
    suspend fun clearApiKey()
    
    suspend fun getTargetLanguage(): String
    suspend fun setTargetLanguage(language: String)
    
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)
    
    suspend fun getThemeMode(): Int
    suspend fun setThemeMode(mode: Int)
    
    suspend fun isAutoTranslateEnabled(): Boolean
    suspend fun setAutoTranslateEnabled(enabled: Boolean)
}