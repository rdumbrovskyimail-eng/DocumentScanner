package com.docs.scanner.domain.usecase

import javax.inject.Inject

/**
 * ✅ Контейнер всех Use Cases для удобного Dependency Injection
 */
data class AllUseCases @Inject constructor(
    // Folders
    val getFolders: GetFoldersUseCase,
    val createFolder: CreateFolderUseCase,
    val updateFolder: UpdateFolderUseCase,
    val deleteFolder: DeleteFolderUseCase,
    
    // Records
    val getRecords: GetRecordsUseCase,
    val createRecord: CreateRecordUseCase,
    val updateRecord: UpdateRecordUseCase,
    val deleteRecord: DeleteRecordUseCase,
    
    // Documents
    val getDocuments: GetDocumentsUseCase,
    val addDocument: AddDocumentUseCase,
    val deleteDocument: DeleteDocumentUseCase,
    val fixOcr: FixOcrUseCase,
    val retryTranslation: RetryTranslationUseCase,
    val batchOperations: BatchOperationsUseCase,
    
    // Quick Scan
    val quickScan: QuickScanUseCase
)