/*
 * DocumentScanner - Domain Use Cases
 * Version: 4.1.0 - Production Ready 2026 Enhanced (FINAL)
 * 
 * Improvements v4.1.0:
 * - Uses New/Existing entity separation ✅
 * - Type-safe error handling ✅
 * - Improved batch operations with generic helper ✅
 * - Better code organization ✅
 */

package com.docs.scanner.domain.usecase

import android.net.Uri
import com.docs.scanner.domain.core.*
import com.docs.scanner.domain.model.Result as LegacyResult
import com.docs.scanner.domain.model.toLegacyResult
import com.docs.scanner.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════════════════════
// 1. COMPLEX SCENARIOS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Создание документа из изображения с полной обработкой.
 */
@Singleton
class CreateDocumentFromScanUseCase @Inject constructor(
    private val fileRepo: FileRepository,
    private val docRepo: DocumentRepository,
    private val recordRepo: RecordRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider
) {
    suspend operator fun invoke(
        recordId: RecordId,
        imageUri: String,
        lang: Language = Language.AUTO
    ): DomainResult<DocumentId> = DomainResult.catching {
        if (!recordRepo.recordExists(recordId))
            throw DomainError.NotFoundError.Record(recordId).toException()
        
        val path = fileRepo.saveImage(imageUri, settings.getImageQuality()).getOrThrow()
        val size = fileRepo.getFileSize(path)
        val dim = fileRepo.getImageDimensions(path).getOrNull() ?: (0 to 0)
        
        if (size > DomainConstants.MAX_IMAGE_SIZE_BYTES)
            throw DomainError.FileTooLarge(size, DomainConstants.MAX_IMAGE_SIZE_BYTES).toException()
        
        val now = time.currentMillis()
        val newDoc = NewDocument(
            recordId = recordId,
            imagePath = path,
            sourceLanguage = lang,
            position = docRepo.getNextPosition(recordId),
            fileSize = size,
            width = dim.first,
            height = dim.second,
            createdAt = now,
            updatedAt = now
        )
        
        val id = docRepo.createDocument(newDoc).getOrThrow()
        recordRepo.updateDocumentCount(recordId)
        id
    }
}

/**
 * Состояния обработки документа.
 */
sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class OcrInProgress(val progress: Int) : ProcessingState
    data class OcrComplete(val text: String, val language: Language?) : ProcessingState
    data class TranslationInProgress(val progress: Int) : ProcessingState
    data class Complete(val originalText: String, val translatedText: String?) : ProcessingState
    data class Failed(val error: DomainError, val stage: Stage) : ProcessingState
    
    enum class Stage { OCR, TRANSLATION }
}

/**
 * Обработка документа: OCR + опциональный перевод.
 * ✅ FIXED: Убрана попытка использовать некорректный helper
 */
@Singleton
class ProcessDocumentUseCase @Inject constructor(
    private val docRepo: DocumentRepository,
    private val ocrRepo: OcrRepository,
    private val transRepo: TranslationRepository,
    private val settings: SettingsRepository
) {
    operator fun invoke(id: DocumentId): Flow<ProcessingState> = flow {
        val doc = when (val res = docRepo.getDocumentById(id)) {
            is DomainResult.Success -> res.data
            is DomainResult.Failure -> {
                emit(ProcessingState.Failed(res.error, ProcessingState.Stage.OCR))
                return@flow
            }
        }
        
        // OCR Stage
        emit(ProcessingState.OcrInProgress(0))
        docRepo.updateProcessingStatus(id, ProcessingStatus.Ocr.InProgress)
        
        val ocrResult = try {
            ocrRepo.recognizeText(doc.imagePath, doc.sourceLanguage).getOrThrow()
        } catch (e: DomainException) {
            docRepo.updateProcessingStatus(id, ProcessingStatus.Ocr.Failed)
            emit(ProcessingState.Failed(e.error, ProcessingState.Stage.OCR))
            return@flow
        }
        
        docRepo.updateOcrResult(
            id, ocrResult.text, ocrResult.detectedLanguage, 
            ocrResult.confidence, ProcessingStatus.Ocr.Complete
        )
        emit(ProcessingState.OcrComplete(ocrResult.text, ocrResult.detectedLanguage))
        
        // Translation Stage (if enabled)
        val autoTranslate = settings.isAutoTranslateEnabled()
        if (autoTranslate && ocrResult.text.isNotBlank()) {
            emit(ProcessingState.TranslationInProgress(0))
            docRepo.updateProcessingStatus(id, ProcessingStatus.Translation.InProgress)
            
            val sourceLang = ocrResult.detectedLanguage ?: doc.sourceLanguage
            val transResult = try {
                transRepo.translate(ocrResult.text, sourceLang, doc.targetLanguage).getOrThrow()
            } catch (e: DomainException) {
                docRepo.updateProcessingStatus(id, ProcessingStatus.Translation.Failed)
                emit(ProcessingState.Failed(e.error, ProcessingState.Stage.TRANSLATION))
                return@flow
            }
            
            docRepo.updateTranslationResult(id, transResult.translatedText, ProcessingStatus.Complete)
            emit(ProcessingState.Complete(ocrResult.text, transResult.translatedText))
        } else {
            docRepo.updateProcessingStatus(id, ProcessingStatus.Complete)
            emit(ProcessingState.Complete(ocrResult.text, null))
        }
    }.cancellable()
}

/**
 * Состояния Quick Scan.
 */
sealed interface QuickScanState {
    data object Preparing : QuickScanState
    data object CreatingRecord : QuickScanState
    data class SavingImage(val progress: Int) : QuickScanState
    data class Processing(val state: ProcessingState) : QuickScanState
    data class Success(val recordId: RecordId, val documentId: DocumentId) : QuickScanState
    data class Error(val error: DomainError, val stage: String) : QuickScanState
}

/**
 * Quick Scan - полный цикл быстрого сканирования.
 */
@Singleton
class QuickScanUseCase @Inject constructor(
    private val folderRepo: FolderRepository,
    private val recordRepo: RecordRepository,
    private val createDoc: CreateDocumentFromScanUseCase,
    private val processDoc: ProcessDocumentUseCase,
    private val time: TimeProvider
) {
    operator fun invoke(
        imageUri: String,
        quickScansFolderName: String = "Quick Scans",
        lang: Language = Language.AUTO
    ): Flow<QuickScanState> = flow {
        try {
            emit(QuickScanState.Preparing)
            val folderId = folderRepo.ensureQuickScansFolderExists(quickScansFolderName)
            
            emit(QuickScanState.CreatingRecord)
            val now = time.currentMillis()
            val newRecord = NewRecord(
                folderId = folderId,
                name = "Scan ${formatTimestamp(now)}",
                sourceLanguage = lang,
                createdAt = now,
                updatedAt = now
            )
            val recordId = recordRepo.createRecord(newRecord).getOrThrow()
            
            emit(QuickScanState.SavingImage(0))
            val docId = createDoc(recordId, imageUri, lang).getOrThrow()
            
            processDoc(docId).collect { processingState ->
                emit(QuickScanState.Processing(processingState))
                when (processingState) {
                    is ProcessingState.Complete -> emit(QuickScanState.Success(recordId, docId))
                    is ProcessingState.Failed -> emit(QuickScanState.Error(processingState.error, processingState.stage.name))
                    else -> {}
                }
            }
        } catch (e: Exception) {
            val error = if (e is DomainException) e.error else DomainError.Unknown(e)
            emit(QuickScanState.Error(error, "SETUP"))
        }
    }.cancellable()
    
    private fun formatTimestamp(millis: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(millis))
}

/**
 * Batch операции с контролем параллелизма.
 */
@Singleton
class BatchOperationsUseCase @Inject constructor(
    private val createDoc: CreateDocumentFromScanUseCase,
    private val processDoc: ProcessDocumentUseCase,
    private val docRepo: DocumentRepository
) {
    data class BatchResult<T>(
        val successful: List<T>,
        val failed: List<Pair<Int, DomainError>>,
        val total: Int
    ) {
        val successCount: Int get() = successful.size
        val failedCount: Int get() = failed.size
        val isFullSuccess: Boolean get() = failed.isEmpty()
        val successRate: Float get() = if (total > 0) successCount.toFloat() / total else 0f
    }
    
    suspend fun addDocuments(
        recordId: RecordId,
        imageUris: List<String>,
        lang: Language = Language.AUTO,
        maxConcurrency: Int = DomainConstants.DEFAULT_BATCH_CONCURRENCY,
        onProgress: ((Int, Int) -> Unit)? = null
    ): BatchResult<DocumentId> = batchProcess(
        items = imageUris,
        maxConcurrency = maxConcurrency,
        onProgress = onProgress
    ) { _, uri ->
        createDoc(recordId, uri, lang)
    }
    
    suspend fun processDocuments(
        docIds: List<DocumentId>,
        maxConcurrency: Int = DomainConstants.DEFAULT_BATCH_CONCURRENCY,
        onProgress: ((Int, Int) -> Unit)? = null
    ): BatchResult<DocumentId> = withContext(Dispatchers.IO) {
        if (docIds.isEmpty()) return@withContext BatchResult(emptyList(), emptyList(), 0)
        
        val successful = mutableListOf<DocumentId>()
        val failed = mutableListOf<Pair<Int, DomainError>>()
        var completed = 0
        val semaphore = Semaphore(maxConcurrency)
        
        docIds.mapIndexed { index, docId ->
            async {
                semaphore.withPermit {
                    var lastError: DomainError? = null
                    processDoc(docId).collect { state ->
                        when (state) {
                            is ProcessingState.Complete -> synchronized(successful) { successful.add(docId) }
                            is ProcessingState.Failed -> lastError = state.error
                            else -> {}
                        }
                    }
                    lastError?.let { synchronized(failed) { failed.add(index to it) } }
                    
                    synchronized(this@withContext) {
                        completed++
                        onProgress?.invoke(completed, docIds.size)
                    }
                }
            }
        }.awaitAll()
        
        BatchResult(successful.toList(), failed.toList(), docIds.size)
    }
    
    suspend fun deleteDocuments(
        docIds: List<DocumentId>,
        maxConcurrency: Int = DomainConstants.DEFAULT_BATCH_CONCURRENCY,
        onProgress: ((Int, Int) -> Unit)? = null
    ): BatchResult<DocumentId> = batchProcess(
        items = docIds,
        maxConcurrency = maxConcurrency,
        onProgress = onProgress
    ) { _, docId ->
        docRepo.deleteDocument(docId).map { docId }
    }
    
    /**
     * ✅ Generic batch processing helper
     */
    private suspend fun <T, R> batchProcess(
        items: List<T>,
        maxConcurrency: Int,
        onProgress: ((Int, Int) -> Unit)?,
        operation: suspend (Int, T) -> DomainResult<R>
    ): BatchResult<R> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext BatchResult(emptyList(), emptyList(), 0)
        
        val successful = mutableListOf<R>()
        val failed = mutableListOf<Pair<Int, DomainError>>()
        var completed = 0
        val semaphore = Semaphore(maxConcurrency)
        
        items.mapIndexed { index, item ->
            async {
                semaphore.withPermit {
                    operation(index, item)
                        .onSuccess { synchronized(successful) { successful.add(it) } }
                        .onFailure { synchronized(failed) { failed.add(index to it) } }
                    synchronized(this@withContext) {
                        completed++
                        onProgress?.invoke(completed, items.size)
                    }
                }
            }
        }.awaitAll()
        
        BatchResult(successful.toList(), failed.toList(), items.size)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. CRUD GROUPS
// ══════════════════════════════════════════════════════════════════════════════

@Singleton
class FolderUseCases @Inject constructor(
    private val repo: FolderRepository,
    private val time: TimeProvider
) {
    suspend fun create(name: String, desc: String? = null, color: Int? = null, icon: String? = null): DomainResult<FolderId> =
        DomainResult.catching {
            val validName = FolderName.create(name).getOrThrow()
            if (repo.folderNameExists(validName.value))
                throw DomainError.AlreadyExists(validName.value).toException()
            
            val now = time.currentMillis()
            val newFolder = NewFolder(
                name = validName.value,
                description = desc,
                color = color,
                icon = icon,
                createdAt = now,
                updatedAt = now
            )
            repo.createFolder(newFolder).getOrThrow()
        }
    
    suspend fun update(folder: Folder): DomainResult<Unit> {
        if (folder.isQuickScans) return DomainResult.failure(DomainError.CannotModifyQuickScansFolder("update"))
        return repo.updateFolder(folder.copy(updatedAt = time.currentMillis()))
    }
    
    suspend fun delete(id: FolderId, deleteContents: Boolean = false): DomainResult<Unit> {
        if (id == FolderId.QUICK_SCANS) return DomainResult.failure(DomainError.CannotDeleteSystemFolder(id))
        
        if (!deleteContents) {
            val folder = repo.getFolderById(id).getOrElse { return DomainResult.failure(it) }
            if (folder.recordCount > 0) return DomainResult.failure(DomainError.CannotDeleteNonEmptyFolder(id, folder.recordCount))
        }
        return repo.deleteFolder(id, deleteContents)
    }
    
    suspend fun getById(id: FolderId): DomainResult<Folder> = repo.getFolderById(id)
    fun observeAll(): Flow<List<Folder>> = repo.observeAllFolders()
    fun observeIncludingArchived(): Flow<List<Folder>> = repo.observeAllFoldersIncludingArchived()
    
    suspend fun archive(id: FolderId): DomainResult<Unit> {
        if (id == FolderId.QUICK_SCANS) return DomainResult.failure(DomainError.CannotModifyQuickScansFolder("archive"))
        return repo.archiveFolder(id)
    }
    suspend fun unarchive(id: FolderId): DomainResult<Unit> = repo.unarchiveFolder(id)
    suspend fun pin(id: FolderId, pinned: Boolean): DomainResult<Unit> = repo.setPinned(id, pinned)
}

@Singleton
class RecordUseCases @Inject constructor(
    private val repo: RecordRepository,
    private val folderRepo: FolderRepository,
    private val time: TimeProvider
) {
    suspend fun create(
        folderId: FolderId,
        name: String,
        desc: String? = null,
        tags: List<String> = emptyList(),
        sourceLang: Language = Language.AUTO,
        targetLang: Language = Language.ENGLISH
    ): DomainResult<RecordId> = DomainResult.catching {
        val validName = RecordName.create(name).getOrThrow()
        if (!folderRepo.folderExists(folderId)) throw DomainError.NotFoundError.Folder(folderId).toException()
        
        val validTags = tags.mapNotNull { Tag.create(it).getOrNull()?.value }.distinct()
        
        val now = time.currentMillis()
        val newRecord = NewRecord(
            folderId = folderId,
            name = validName.value,
            description = desc,
            tags = validTags,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            createdAt = now,
            updatedAt = now
        )
        repo.createRecord(newRecord).getOrThrow()
    }
    
    suspend fun update(record: Record): DomainResult<Unit> = repo.updateRecord(record.copy(updatedAt = time.currentMillis()))
    
    suspend fun updateLanguage(id: RecordId, source: Language, target: Language): DomainResult<Unit> {
        if (source == target && source != Language.AUTO) return DomainResult.failure(DomainError.UnsupportedLanguagePair(source, target))
        return repo.updateLanguageSettings(id, source, target)
    }
    
    suspend fun delete(id: RecordId): DomainResult<Unit> = repo.deleteRecord(id)
    
    suspend fun move(id: RecordId, toFolder: FolderId): DomainResult<Unit> {
        if (!folderRepo.folderExists(toFolder)) return DomainResult.failure(DomainError.NotFoundError.Folder(toFolder))
        return repo.moveRecord(id, toFolder)
    }
    
    suspend fun duplicate(id: RecordId, toFolder: FolderId? = null, copyDocs: Boolean = true): DomainResult<RecordId> =
        repo.duplicateRecord(id, toFolder, copyDocs)
    
    suspend fun getById(id: RecordId): DomainResult<Record> = repo.getRecordById(id)
    fun observeByFolder(folderId: FolderId): Flow<List<Record>> = repo.observeRecordsByFolder(folderId)
    fun observeRecent(limit: Int = 10): Flow<List<Record>> = repo.observeRecentRecords(limit)
    fun observeByTag(tag: String): Flow<List<Record>> = repo.observeRecordsByTag(tag)
    fun observeAll(): Flow<List<Record>> = repo.observeAllRecords()
    
    suspend fun addTag(id: RecordId, tag: String): DomainResult<Unit> {
        val validTag = Tag.create(tag).getOrElse { return DomainResult.failure(it) }
        return repo.addTag(id, validTag.value)
    }
    suspend fun removeTag(id: RecordId, tag: String): DomainResult<Unit> = repo.removeTag(id, tag)
    suspend fun getAllTags(): List<String> = repo.getAllTags()
    
    suspend fun archive(id: RecordId): DomainResult<Unit> = repo.archiveRecord(id)
    suspend fun unarchive(id: RecordId): DomainResult<Unit> = repo.unarchiveRecord(id)
    suspend fun pin(id: RecordId, pinned: Boolean): DomainResult<Unit> = repo.setPinned(id, pinned)
}

@Singleton
class DocumentUseCases @Inject constructor(
    private val repo: DocumentRepository,
    private val time: TimeProvider
) {
    suspend fun getById(id: DocumentId): DomainResult<Document> = repo.getDocumentById(id)
    fun observeByRecord(recordId: RecordId): Flow<List<Document>> = repo.observeDocumentsByRecord(recordId)
    fun observePending(): Flow<List<Document>> = repo.observePendingDocuments()
    fun observeFailed(): Flow<List<Document>> = repo.observeFailedDocuments()
    
    fun search(query: String): Flow<List<Document>> {
        val trimmed = query.trim()
        return if (trimmed.length < DomainConstants.MIN_SEARCH_QUERY_LENGTH) flowOf(emptyList())
        else repo.searchDocumentsWithPath(trimmed)
    }
    
    suspend fun delete(id: DocumentId): DomainResult<Unit> = repo.deleteDocument(id)
    suspend fun update(doc: Document): DomainResult<Unit> = repo.updateDocument(doc.copy(updatedAt = time.currentMillis()))
    suspend fun reorder(recordId: RecordId, docIds: List<DocumentId>): DomainResult<Unit> = repo.reorderDocuments(recordId, docIds)
    suspend fun move(id: DocumentId, toRecord: RecordId): DomainResult<Unit> = repo.moveDocument(id, toRecord)
}

@Singleton
class TermUseCases @Inject constructor(
    private val repo: TermRepository,
    private val time: TimeProvider
) {
    suspend fun create(
        title: String,
        dueDate: Long,
        desc: String? = null,
        reminderMinutes: Int = 60,
        priority: TermPriority = TermPriority.NORMAL,
        docId: DocumentId? = null,
        folderId: FolderId? = null,
        color: Int? = null
    ): DomainResult<TermId> = DomainResult.catching {
        val now = time.currentMillis()
        if (dueDate <= now) throw DomainError.ValidationFailed(ValidationError.DueDateInPast).toException()
        if (title.isBlank() || title.length > Term.TITLE_MAX_LENGTH)
            throw DomainError.ValidationFailed(ValidationError.NameTooLong(title.length, Term.TITLE_MAX_LENGTH)).toException()
        
        val newTerm = NewTerm(
            title = title.trim(),
            description = desc,
            dueDate = dueDate,
            reminderMinutesBefore = reminderMinutes,
            priority = priority,
            documentId = docId,
            folderId = folderId,
            color = color,
            createdAt = now,
            updatedAt = now
        )
        repo.createTerm(newTerm).getOrThrow()
    }
    
    suspend fun update(term: Term): DomainResult<Unit> {
        val existing = repo.getTermById(term.id).getOrElse { return DomainResult.failure(it) }
        if (existing.isCompleted) return DomainResult.failure(DomainError.CannotModifyCompletedTerm(term.id))
        return repo.updateTerm(term.copy(updatedAt = time.currentMillis()))
    }
    
    suspend fun delete(id: TermId): DomainResult<Unit> = repo.deleteTerm(id)
    suspend fun deleteAllCompleted(): DomainResult<Int> = repo.deleteAllCompleted()
    suspend fun deleteAllCancelled(): DomainResult<Int> = repo.deleteAllCancelled()
    
    suspend fun getById(id: TermId): DomainResult<Term> = repo.getTermById(id)
    fun observeAll(): Flow<List<Term>> = repo.observeAllTerms()
    fun observeActive(): Flow<List<Term>> = repo.observeActiveTerms()
    fun observeCompleted(): Flow<List<Term>> = repo.observeCompletedTerms()
    fun observeOverdue(): Flow<List<Term>> = repo.observeOverdueTerms(time.currentMillis())
    fun observeByDocument(docId: DocumentId): Flow<List<Term>> = repo.observeTermsByDocument(docId)
    fun observeByFolder(folderId: FolderId): Flow<List<Term>> = repo.observeTermsByFolder(folderId)
    
    suspend fun complete(id: TermId): DomainResult<Unit> = repo.markCompleted(id, time.currentMillis())
    suspend fun uncomplete(id: TermId): DomainResult<Unit> = repo.markNotCompleted(id, time.currentMillis())
    suspend fun cancel(id: TermId): DomainResult<Unit> = repo.cancelTerm(id, time.currentMillis())
    suspend fun restore(id: TermId): DomainResult<Unit> = repo.restoreTerm(id, time.currentMillis())
    
    suspend fun getActiveCount(): Int = repo.getActiveCount()
    suspend fun getOverdueCount(): Int = repo.getOverdueCount(time.currentMillis())
    suspend fun getDueTodayCount(): Int = repo.getDueTodayCount(time.currentMillis())
}

@Singleton
class TranslationUseCases @Inject constructor(
    private val repo: TranslationRepository,
    private val docRepo: DocumentRepository
) {
    suspend fun translateText(text: String, source: Language, target: Language): DomainResult<TranslationResult> {
        if (text.isBlank()) return DomainResult.failure(DomainError.TranslationFailed(source, target, "Empty text"))
        if (source == target && source != Language.AUTO) return DomainResult.failure(DomainError.UnsupportedLanguagePair(source, target))
        return repo.translate(text, source, target)
    }
    
    suspend fun translateDocument(docId: DocumentId, targetLang: Language? = null): DomainResult<TranslationResult> {
        val doc = docRepo.getDocumentById(docId).getOrElse { return DomainResult.failure(it) }
        
        // ✅ FIXED: Check if originalText is empty without using undefined error
        if (doc.originalText.isNullOrBlank()) {
            return DomainResult.failure(
                DomainError.TranslationFailed(
                    doc.sourceLanguage, 
                    targetLang ?: doc.targetLanguage, 
                    "No text available for translation"
                )
            )
        }
        
        val target = targetLang ?: doc.targetLanguage
        val source = doc.detectedLanguage ?: doc.sourceLanguage
        
        docRepo.updateProcessingStatus(docId, ProcessingStatus.Translation.InProgress)
        return repo.translate(doc.originalText, source, target)
            .onSuccess { docRepo.updateTranslationResult(docId, it.translatedText, ProcessingStatus.Translation.Complete) }
            .onFailure { docRepo.updateProcessingStatus(docId, ProcessingStatus.Translation.Failed) }
    }
    
    suspend fun retryTranslation(docId: DocumentId): DomainResult<TranslationResult> = translateDocument(docId)
    suspend fun clearCache(): DomainResult<Unit> = repo.clearCache()
    suspend fun getCacheStats(): TranslationCacheStats = repo.getCacheStats()
}

@Singleton
class SettingsUseCases @Inject constructor(private val repo: SettingsRepository) {
    fun observeAppLanguage(): Flow<String> = repo.observeAppLanguage()
    fun observeTargetLanguage(): Flow<Language> = repo.observeTargetLanguage()
    fun observeThemeMode(): Flow<ThemeMode> = repo.observeThemeMode()
    fun observeAutoTranslate(): Flow<Boolean> = repo.observeAutoTranslateEnabled()
    
    suspend fun hasApiKey(): Boolean = repo.hasApiKey()
    suspend fun getApiKey(): String? = repo.getApiKey()
    suspend fun setApiKey(key: String): DomainResult<Unit> = repo.setApiKey(key)
    suspend fun clearApiKey(): DomainResult<Unit> = repo.clearApiKey()
    
    suspend fun getAppLanguage(): String = repo.getAppLanguage()
    suspend fun setAppLanguage(code: String): DomainResult<Unit> = repo.setAppLanguage(code)
    suspend fun getSourceLanguage(): Language = repo.getDefaultSourceLanguage()
    suspend fun setSourceLanguage(lang: Language): DomainResult<Unit> = repo.setDefaultSourceLanguage(lang)
    suspend fun getTargetLanguage(): Language = repo.getTargetLanguage()
    suspend fun setTargetLanguage(lang: Language): DomainResult<Unit> = repo.setTargetLanguage(lang)
    
    suspend fun getThemeMode(): ThemeMode = repo.getThemeMode()
    suspend fun setThemeMode(mode: ThemeMode): DomainResult<Unit> = repo.setThemeMode(mode)
    
    suspend fun isAutoTranslateEnabled(): Boolean = repo.isAutoTranslateEnabled()
    suspend fun setAutoTranslate(enabled: Boolean): DomainResult<Unit> = repo.setAutoTranslateEnabled(enabled)
    suspend fun isOnboardingCompleted(): Boolean = repo.isOnboardingCompleted()
    suspend fun setOnboardingCompleted(done: Boolean): DomainResult<Unit> = repo.setOnboardingCompleted(done)
    
    suspend fun getImageQuality(): ImageQuality = repo.getImageQuality()
    suspend fun setImageQuality(q: ImageQuality): DomainResult<Unit> = repo.setImageQuality(q)
    
    suspend fun resetToDefaults(): DomainResult<Unit> = repo.resetToDefaults()
}

@Singleton
class BackupUseCases @Inject constructor(private val repo: BackupRepository) {
    suspend fun createLocal(includeImages: Boolean = true): DomainResult<String> = repo.createLocalBackup(includeImages)
    suspend fun restoreFromLocal(path: String, merge: Boolean = false): DomainResult<RestoreResult> = repo.restoreFromLocal(path, merge)
    
    suspend fun uploadToGoogleDrive(localPath: String, onProgress: ((UploadProgress) -> Unit)? = null): DomainResult<String> =
        repo.uploadToGoogleDrive(localPath, onProgress)
    suspend fun downloadFromGoogleDrive(fileId: String, onProgress: ((DownloadProgress) -> Unit)? = null): DomainResult<String> = 
        repo.downloadFromGoogleDrive(fileId, onProgress)
    suspend fun listGoogleDriveBackups(): DomainResult<List<BackupInfo>> = repo.listGoogleDriveBackups()
    suspend fun deleteGoogleDriveBackup(fileId: String): DomainResult<Unit> = repo.deleteGoogleDriveBackup(fileId)
    
    suspend fun getLastBackupInfo(): BackupInfo? = repo.getLastBackupInfo()
    fun observeProgress(): Flow<BackupProgress> = repo.observeProgress()
}

@Singleton
class ExportUseCases @Inject constructor(
    private val fileRepo: FileRepository,
    private val time: TimeProvider
) {
    suspend fun exportToPdf(docIds: List<DocumentId>, outputPath: String): DomainResult<String> =
        fileRepo.exportToPdf(docIds, outputPath)
        
    suspend fun shareDocuments(docIds: List<DocumentId>, asPdf: Boolean): DomainResult<String> {
        return if (asPdf) {
            val tempPath = "temp_share_${time.currentMillis()}.pdf"
            fileRepo.exportToPdf(docIds, tempPath)
                .flatMap { fileRepo.shareFile(it) }
        } else {
            DomainResult.failure(DomainError.Unknown(Exception("Multi-image share not implemented")))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 3. ALL USE CASES CONTAINER - Единая точка входа
// ══════════════════════════════════════════════════════════════════════════════

@Singleton
class AllUseCases @Inject constructor(
    // Complex Scenarios
    val quickScan: QuickScanUseCase,
    val createDocumentFromScan: CreateDocumentFromScanUseCase,
    val processDocument: ProcessDocumentUseCase,
    val batch: BatchOperationsUseCase,
    val export: ExportUseCases,
    
    // CRUD Groups
    val folders: FolderUseCases,
    val records: RecordUseCases,
    val documents: DocumentUseCases,
    val terms: TermUseCases,
    val translation: TranslationUseCases,
    val settings: SettingsUseCases,
    val backup: BackupUseCases
) {
    /** DSL для функционального стиля */
    suspend operator fun <R> invoke(block: suspend AllUseCases.() -> R): R = block(this)

    // ─────────────────────────────────────────────────────────────────────────────
    // Legacy facade (presentation compatibility)
    // ─────────────────────────────────────────────────────────────────────────────

    fun getFolders(): Flow<List<Folder>> = folders.observeAll()

    suspend fun getFolderById(folderId: Long): Folder? =
        folders.getById(FolderId(folderId)).getOrNull()

    suspend fun createFolder(name: String, description: String?): LegacyResult<Unit> =
        folders.create(name, desc = description).map { Unit }.toLegacyResult()

    suspend fun updateFolder(folder: Folder): LegacyResult<Unit> =
        folders.update(folder).toLegacyResult()

    suspend fun deleteFolder(folderId: Long): LegacyResult<Unit> =
        folders.delete(FolderId(folderId)).toLegacyResult()

    fun getRecords(folderId: Long): Flow<List<Record>> =
        records.observeByFolder(FolderId(folderId))

    suspend fun getRecordById(recordId: Long): Record? =
        records.getById(RecordId(recordId)).getOrNull()

    suspend fun createRecord(folderId: Long, name: String, description: String?): LegacyResult<Unit> =
        records.create(folderId = FolderId(folderId), name = name, desc = description).map { Unit }.toLegacyResult()

    suspend fun updateRecord(record: Record): LegacyResult<Unit> =
        records.update(record).toLegacyResult()

    suspend fun deleteRecord(recordId: Long): LegacyResult<Unit> =
        records.delete(RecordId(recordId)).toLegacyResult()

    suspend fun moveRecord(recordId: Long, targetFolderId: Long): LegacyResult<Unit> =
        records.move(RecordId(recordId), FolderId(targetFolderId)).toLegacyResult()

    fun getDocuments(recordId: Long): Flow<List<Document>> =
        documents.observeByRecord(RecordId(recordId))

    suspend fun getDocumentById(documentId: Long): Document? =
        documents.getById(DocumentId(documentId)).getOrNull()

    suspend fun updateDocument(document: Document): LegacyResult<Unit> =
        documents.update(document).toLegacyResult()

    suspend fun deleteDocument(documentId: Long): LegacyResult<Unit> =
        documents.delete(DocumentId(documentId)).toLegacyResult()

    fun searchDocuments(query: String): Flow<List<Document>> = documents.search(query)

    fun addDocument(recordId: Long, imageUri: Uri): Flow<AddDocumentState> = flow {
        emit(AddDocumentState.Creating(progress = 10, message = "Saving image..."))

        val record = when (val r = records.getById(RecordId(recordId))) {
            is DomainResult.Success -> r.data
            is DomainResult.Failure -> {
                emit(AddDocumentState.Error(message = r.error.message))
                return@flow
            }
        }

        val docId = when (val created = createDocumentFromScan(RecordId(recordId), imageUri.toString(), record.sourceLanguage)) {
            is DomainResult.Success -> created.data
            is DomainResult.Failure -> {
                emit(AddDocumentState.Error(message = created.error.message))
                return@flow
            }
        }

        emit(AddDocumentState.ProcessingOcr(progress = 60, message = "Processing..."))

        processDocument(docId).collect { state ->
            when (state) {
                is ProcessingState.OcrInProgress -> emit(AddDocumentState.ProcessingOcr(70, "Running OCR..."))
                is ProcessingState.TranslationInProgress -> emit(AddDocumentState.Translating(85, "Translating..."))
                is ProcessingState.Complete -> emit(AddDocumentState.Success(documentId = docId.value))
                is ProcessingState.Failed -> emit(AddDocumentState.Error(message = state.error.message))
                else -> {}
            }
        }
    }.cancellable()

    suspend fun fixOcr(documentId: Long): LegacyResult<Unit> {
        return try {
            val terminal = processDocument(DocumentId(documentId))
                .first { it is ProcessingState.Complete || it is ProcessingState.Failed }
            when (terminal) {
                is ProcessingState.Complete -> LegacyResult.Success(Unit)
                is ProcessingState.Failed -> LegacyResult.Error(DomainException(terminal.error))
                else -> LegacyResult.Success(Unit)
            }
        } catch (e: Exception) {
            LegacyResult.Error(e as? Exception ?: Exception(e))
        }
    }

    suspend fun retryTranslation(documentId: Long): LegacyResult<Unit> =
        translation.retryTranslation(DocumentId(documentId)).map { Unit }.toLegacyResult()

    fun getUpcomingTerms(): Flow<List<Term>> = terms.observeActive()
    fun getCompletedTerms(): Flow<List<Term>> = terms.observeCompleted()

    suspend fun createTerm(term: Term): LegacyResult<Unit> =
        terms.create(
            title = term.title,
            dueDate = term.dueDate,
            desc = term.description,
            reminderMinutes = term.reminderMinutesBefore,
            priority = term.priority,
            docId = term.documentId,
            folderId = term.folderId,
            color = term.color
        ).map { Unit }.toLegacyResult()

    suspend fun updateTerm(term: Term): LegacyResult<Unit> =
        terms.update(term).toLegacyResult()

    suspend fun markTermCompleted(termId: Long, completed: Boolean): LegacyResult<Unit> =
        (if (completed) terms.complete(TermId(termId)) else terms.uncomplete(TermId(termId))).toLegacyResult()

    suspend fun deleteTerm(term: Term): LegacyResult<Unit> =
        terms.delete(term.id).toLegacyResult()
}