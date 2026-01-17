/*
 * DocumentScanner - Domain Repositories
 * Version: 4.1.0 - Production Ready 2026 Enhanced
 * 
 * Improvements v4.1.0:
 * - Type-safe progress callbacks (UploadProgress/DownloadProgress) ✅
 * - Updated method signatures for New/Existing entity separation ✅
 * - Improved nullability annotations ✅
 * - Better method naming consistency ✅
 */

package com.docs.scanner.domain.repository

import com.docs.scanner.domain.core.*
import kotlinx.coroutines.flow.Flow

// ══════════════════════════════════════════════════════════════════════════════
// FOLDER REPOSITORY
// ══════════════════════════════════════════════════════════════════════════════

interface FolderRepository {
    // Observe
    fun observeAllFolders(): Flow<List<Folder>>
    fun observeAllFoldersIncludingArchived(): Flow<List<Folder>>
    fun observeFolder(id: FolderId): Flow<Folder?>
    
    // Query
    suspend fun getFolderById(id: FolderId): DomainResult<Folder>
    suspend fun folderExists(id: FolderId): Boolean
    suspend fun folderNameExists(name: String, excludeId: FolderId? = null): Boolean
    suspend fun getFolderCount(): Int
    
    // Mutate - ✅ UPDATED: NewFolder for creation
    suspend fun createFolder(newFolder: NewFolder): DomainResult<FolderId>
    suspend fun updateFolder(folder: Folder): DomainResult<Unit>
    suspend fun deleteFolder(id: FolderId, deleteContents: Boolean = false): DomainResult<Unit>
    suspend fun archiveFolder(id: FolderId): DomainResult<Unit>
    suspend fun unarchiveFolder(id: FolderId): DomainResult<Unit>
    suspend fun setPinned(id: FolderId, pinned: Boolean): DomainResult<Unit>
    suspend fun updateRecordCount(id: FolderId): DomainResult<Unit>
    suspend fun ensureQuickScansFolderExists(name: String): FolderId
    suspend fun updatePosition(id: FolderId, position: Int): DomainResult<Unit>
}

// ══════════════════════════════════════════════════════════════════════════════
// RECORD REPOSITORY
// ══════════════════════════════════════════════════════════════════════════════

interface RecordRepository {
    // Observe
    fun observeRecordsByFolder(folderId: FolderId): Flow<List<Record>>
    fun observeRecordsByFolderIncludingArchived(folderId: FolderId): Flow<List<Record>>
    fun observeRecord(id: RecordId): Flow<Record?>
    fun observeRecordsByTag(tag: String): Flow<List<Record>>
    fun observeAllRecords(): Flow<List<Record>>
    fun observeRecentRecords(limit: Int = 10): Flow<List<Record>>
    
    // Query
    suspend fun getRecordById(id: RecordId): DomainResult<Record>
    suspend fun recordExists(id: RecordId): Boolean
    suspend fun getRecordCountInFolder(folderId: FolderId): Int
    suspend fun getAllTags(): List<String>
    suspend fun searchRecords(query: String): List<Record>
    
    // Mutate - ✅ UPDATED: NewRecord for creation
    suspend fun createRecord(newRecord: NewRecord): DomainResult<RecordId>
    suspend fun updateRecord(record: Record): DomainResult<Unit>
    suspend fun deleteRecord(id: RecordId): DomainResult<Unit>
    suspend fun moveRecord(id: RecordId, toFolderId: FolderId): DomainResult<Unit>
    suspend fun duplicateRecord(id: RecordId, toFolderId: FolderId?, copyDocs: Boolean): DomainResult<RecordId>
    suspend fun archiveRecord(id: RecordId): DomainResult<Unit>
    suspend fun unarchiveRecord(id: RecordId): DomainResult<Unit>
    suspend fun setPinned(id: RecordId, pinned: Boolean): DomainResult<Unit>
    suspend fun updateLanguageSettings(id: RecordId, source: Language, target: Language): DomainResult<Unit>
    suspend fun addTag(id: RecordId, tag: String): DomainResult<Unit>
    suspend fun removeTag(id: RecordId, tag: String): DomainResult<Unit>
    suspend fun updateDocumentCount(id: RecordId): DomainResult<Unit>
    suspend fun updatePosition(id: RecordId, position: Int): DomainResult<Unit>
}

// ════════════════════════════════════════════════════════════════════════════════
// DOCUMENT REPOSITORY
// ══════════════════════════════════════════════════════════════════════════════

interface DocumentRepository {
    // Observe
    fun observeDocumentsByRecord(recordId: RecordId): Flow<List<Document>>
    fun observeDocument(id: DocumentId): Flow<Document?>
    fun observePendingDocuments(): Flow<List<Document>>
    fun observeFailedDocuments(): Flow<List<Document>>
    fun searchDocuments(query: String): Flow<List<Document>>
    fun searchDocumentsWithPath(query: String): Flow<List<Document>>

    // Search history
    fun observeSearchHistory(limit: Int = 20): Flow<List<com.docs.scanner.domain.core.SearchHistoryItem>>
    suspend fun saveSearchQuery(query: String, resultCount: Int): DomainResult<Unit>
    suspend fun deleteSearchHistoryItem(id: Long): DomainResult<Unit>
    suspend fun clearSearchHistory(): DomainResult<Unit>
    
    // Query
    suspend fun getDocumentById(id: DocumentId): DomainResult<Document>
    suspend fun getDocumentsByRecord(recordId: RecordId): List<Document>
    suspend fun documentExists(id: DocumentId): Boolean
    suspend fun getDocumentCountInRecord(recordId: RecordId): Int
    suspend fun getNextPosition(recordId: RecordId): Int
    
    // Mutate - ✅ UPDATED: NewDocument for creation
    suspend fun createDocument(newDoc: NewDocument): DomainResult<DocumentId>
    suspend fun createDocuments(newDocs: List<NewDocument>): DomainResult<List<DocumentId>>
    suspend fun updateDocument(doc: Document): DomainResult<Unit>
    suspend fun deleteDocument(id: DocumentId): DomainResult<Unit>
    suspend fun deleteDocuments(ids: List<DocumentId>): DomainResult<Int>
    suspend fun moveDocument(id: DocumentId, toRecordId: RecordId): DomainResult<Unit>
    suspend fun reorderDocuments(recordId: RecordId, docIds: List<DocumentId>): DomainResult<Unit>
    
    // Processing
    suspend fun updateProcessingStatus(id: DocumentId, status: ProcessingStatus): DomainResult<Unit>
    suspend fun updateOcrResult(id: DocumentId, text: String, lang: Language?, confidence: Float?, status: ProcessingStatus): DomainResult<Unit>
    suspend fun updateTranslationResult(id: DocumentId, text: String, status: ProcessingStatus): DomainResult<Unit>
}

// ══════════════════════════════════════════════════════════════════════════════
// TERM REPOSITORY
// ══════════════════════════════════════════════════════════════════════════════

interface TermRepository {
    // Observe
    fun observeAllTerms(): Flow<List<Term>>
    fun observeActiveTerms(): Flow<List<Term>>
    fun observeCompletedTerms(): Flow<List<Term>>
    fun observeOverdueTerms(now: Long): Flow<List<Term>>
    fun observeTermsNeedingReminder(now: Long): Flow<List<Term>>
    fun observeTermsInDateRange(start: Long, end: Long): Flow<List<Term>>
    fun observeTermsByDocument(docId: DocumentId): Flow<List<Term>>
    fun observeTermsByFolder(folderId: FolderId): Flow<List<Term>>
    fun observeTerm(id: TermId): Flow<Term?>
    
    // Query
    suspend fun getTermById(id: TermId): DomainResult<Term>
    suspend fun getNextUpcoming(now: Long): Term?
    suspend fun getActiveCount(): Int
    suspend fun getOverdueCount(now: Long): Int
    suspend fun getDueTodayCount(now: Long): Int
    
    // Mutate - ✅ UPDATED: NewTerm for creation
    suspend fun createTerm(newTerm: NewTerm): DomainResult<TermId>
    suspend fun updateTerm(term: Term): DomainResult<Unit>
    suspend fun deleteTerm(id: TermId): DomainResult<Unit>
    suspend fun markCompleted(id: TermId, timestamp: Long): DomainResult<Unit>
    suspend fun markNotCompleted(id: TermId, timestamp: Long): DomainResult<Unit>
    suspend fun cancelTerm(id: TermId, timestamp: Long): DomainResult<Unit>
    suspend fun restoreTerm(id: TermId, timestamp: Long): DomainResult<Unit>
    suspend fun deleteAllCompleted(): DomainResult<Int>
    suspend fun deleteAllCancelled(): DomainResult<Int>
}

// ══════════════════════════════════════════════════════════════════════════════
// OCR REPOSITORY - ✅ UPDATED: Added explicit engine selection methods
// ══════════════════════════════════════════════════════════════════════════════

interface OcrRepository {
    // Hybrid OCR (default - automatically chooses best engine)
    suspend fun recognizeText(imagePath: String, lang: Language = Language.AUTO): DomainResult<OcrResult>
    suspend fun recognizeTextDetailed(imagePath: String, lang: Language = Language.AUTO): DomainResult<DetailedOcrResult>
    
    // ✅ NEW: Explicit engine selection
    suspend fun recognizeTextMlKitOnly(imagePath: String): DomainResult<OcrResult>
    suspend fun recognizeTextGeminiOnly(imagePath: String): DomainResult<OcrResult>
    
    // Language & text improvement
    suspend fun detectLanguage(imagePath: String): DomainResult<Language>
    suspend fun improveOcrText(text: String, lang: Language = Language.AUTO): DomainResult<String>
    suspend fun isLanguageSupported(lang: Language): Boolean
    suspend fun getSupportedLanguages(): List<Language>
}

data class DetailedOcrResult(
    val fullText: String,
    val blocks: List<TextBlock>,
    val detectedLanguage: Language?,
    val confidence: Float?,
    val processingTimeMs: Long
)

data class TextBlock(
    val text: String,
    val lines: List<TextLine>,
    val confidence: Float?,
    val boundingBox: BoundingBox?
)

data class TextLine(
    val text: String,
    val confidence: Float?,
    val boundingBox: BoundingBox?
)

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

// ══════════════════════════════════════════════════════════════════════════════
// TRANSLATION REPOSITORY
// ══════════════════════════════════════════════════════════════════════════════

interface TranslationRepository {
    suspend fun translate(text: String, source: Language, target: Language, useCache: Boolean = true): DomainResult<TranslationResult>
    suspend fun translateBatch(texts: List<String>, source: Language, target: Language): DomainResult<List<TranslationResult>>
    suspend fun detectLanguage(text: String): DomainResult<Language>
    suspend fun isLanguagePairSupported(source: Language, target: Language): Boolean
    suspend fun getSupportedTargetLanguages(source: Language): List<Language>
    
    // Cache
    suspend fun clearCache(): DomainResult<Unit>
    suspend fun clearOldCache(maxAgeDays: Int): DomainResult<Int>
    suspend fun getCacheStats(): TranslationCacheStats
}

// ══════════════════════════════════════════════════════════════════════════════
// SETTINGS REPOSITORY
// ══════════════════════════════════════════════════════════════════════════════

interface SettingsRepository {
    // Observe
    fun observeAppLanguage(): Flow<String>
    fun observeTargetLanguage(): Flow<Language>
    fun observeThemeMode(): Flow<ThemeMode>
    fun observeAutoTranslateEnabled(): Flow<Boolean>
    
    // API Key
    suspend fun getApiKey(): String?
    suspend fun setApiKey(key: String): DomainResult<Unit>
    suspend fun clearApiKey(): DomainResult<Unit>
    suspend fun hasApiKey(): Boolean
    
    // Language
    suspend fun getAppLanguage(): String
    suspend fun setAppLanguage(code: String): DomainResult<Unit>
    suspend fun getDefaultSourceLanguage(): Language
    suspend fun setDefaultSourceLanguage(lang: Language): DomainResult<Unit>
    suspend fun getTargetLanguage(): Language
    suspend fun setTargetLanguage(lang: Language): DomainResult<Unit>
    
    // Appearance
    suspend fun getThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode): DomainResult<Unit>
    
    // Features
    suspend fun isAutoTranslateEnabled(): Boolean
    suspend fun setAutoTranslateEnabled(enabled: Boolean): DomainResult<Unit>
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean): DomainResult<Unit>
    suspend fun isBiometricEnabled(): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean): DomainResult<Unit>
    
    // Quality
    suspend fun getImageQuality(): ImageQuality
    suspend fun setImageQuality(quality: ImageQuality): DomainResult<Unit>
    
    // Reset
    suspend fun resetToDefaults(): DomainResult<Unit>
}

// ══════════════════════════════════════════════════════════════════════════════
// FILE REPOSITORY
// ══════════════════════════════════════════════════════════════════════════════

interface FileRepository {
    suspend fun saveImage(sourceUri: String, quality: ImageQuality): DomainResult<String>
    suspend fun createThumbnail(imagePath: String, maxSize: Int = 256): DomainResult<String>
    suspend fun deleteFile(path: String): DomainResult<Unit>
    suspend fun deleteFiles(paths: List<String>): DomainResult<Int>
    suspend fun getFileSize(path: String): Long
    suspend fun fileExists(path: String): Boolean
    suspend fun getImageDimensions(path: String): DomainResult<Pair<Int, Int>>
    suspend fun exportToPdf(docIds: List<DocumentId>, outputPath: String): DomainResult<String>
    suspend fun exportToZip(docIds: List<DocumentId>, outputPath: String): DomainResult<String>
    suspend fun shareFile(path: String): DomainResult<String>
    suspend fun clearTempFiles(): Int
    suspend fun getStorageUsage(): StorageUsage
}

data class StorageUsage(
    val imagesBytes: Long,
    val thumbnailsBytes: Long,
    val databaseBytes: Long,
    val cacheBytes: Long
) {
    val totalBytes: Long get() = imagesBytes + thumbnailsBytes + databaseBytes + cacheBytes
    
    fun formatTotal(): String = when {
        totalBytes < 1024 -> "$totalBytes B"
        totalBytes < 1024 * 1024 -> "${totalBytes / 1024} KB"
        totalBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", totalBytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", totalBytes / (1024.0 * 1024.0 * 1024.0))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// BACKUP REPOSITORY - ✅ UPDATED: Type-safe progress
// ══════════════════════════════════════════════════════════════════════════════

interface BackupRepository {
    // Local
    suspend fun createLocalBackup(includeImages: Boolean = true): DomainResult<String>
    suspend fun restoreFromLocal(path: String, merge: Boolean = false): DomainResult<RestoreResult>
    
    // Google Drive - ✅ UPDATED: Type-safe progress callbacks
    suspend fun uploadToGoogleDrive(
        localPath: String, 
        onProgress: ((UploadProgress) -> Unit)? = null
    ): DomainResult<String>
    
    suspend fun downloadFromGoogleDrive(
        fileId: String,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): DomainResult<String>
    
    suspend fun listGoogleDriveBackups(): DomainResult<List<BackupInfo>>
    suspend fun deleteGoogleDriveBackup(fileId: String): DomainResult<Unit>
    
    // Info
    suspend fun getLastBackupInfo(): BackupInfo?
    fun observeProgress(): Flow<BackupProgress>
}

data class RestoreResult(
    val foldersRestored: Int,
    val recordsRestored: Int,
    val documentsRestored: Int,
    val imagesRestored: Int,
    val errors: List<String>
) {
    val totalRestored: Int get() = foldersRestored + recordsRestored + documentsRestored
    val isFullSuccess: Boolean get() = errors.isEmpty()
}

sealed class BackupProgress {
    data object Idle : BackupProgress()
    data class InProgress(val percent: Int, val message: String) : BackupProgress()
    data class Completed(val info: BackupInfo) : BackupProgress()
    data class Failed(val error: DomainError) : BackupProgress()
}