package com.docs.scanner.domain.usecase

import com.docs.scanner.domain.usecase.document.GetDocumentByIdUseCase
import com.docs.scanner.domain.usecase.document.SearchDocumentsUseCase
import com.docs.scanner.domain.usecase.folder.GetFolderByIdUseCase
import com.docs.scanner.domain.usecase.record.GetRecordByIdUseCase
import com.docs.scanner.domain.usecase.term.CreateTermUseCase
import com.docs.scanner.domain.usecase.term.DeleteTermUseCase
import com.docs.scanner.domain.usecase.term.GetUpcomingTermsUseCase
import com.docs.scanner.domain.usecase.term.UpdateTermUseCase
import javax.inject.Inject

/**
 * Container for all Use Cases.
 * 
 * Total: 26 Use Cases across 5 domains
 * 
 * Session 6 + 8 Fixes:
 * - ✅ Added 8 missing Use Cases (GetById, Search, Terms)
 * - ✅ Organized by domain for clarity
 */
data class AllUseCases @Inject constructor(
    // ============================================
    // FOLDERS (5 Use Cases)
    // ============================================
    val getFolders: GetFoldersUseCase,
    val getFolderById: GetFolderByIdUseCase,
    val createFolder: CreateFolderUseCase,
    val updateFolder: UpdateFolderUseCase,
    val deleteFolder: DeleteFolderUseCase,
    
    // ============================================
    // RECORDS (5 Use Cases)
    // ============================================
    val getRecords: GetRecordsUseCase,
    val getRecordById: GetRecordByIdUseCase,
    val createRecord: CreateRecordUseCase,
    val updateRecord: UpdateRecordUseCase,
    val deleteRecord: DeleteRecordUseCase,
    
    // ============================================
    // DOCUMENTS (9 Use Cases)
    // ============================================
    val getDocuments: GetDocumentsUseCase,
    val getDocumentById: GetDocumentByIdUseCase,
    val addDocument: AddDocumentUseCase,
    val deleteDocument: DeleteDocumentUseCase,
    val searchDocuments: SearchDocumentsUseCase,
    val fixOcr: FixOcrUseCase,
    val retryTranslation: RetryTranslationUseCase,
    val batchOperations: BatchOperationsUseCase,
    
    // ============================================
    // TERMS (4 Use Cases)
    // ============================================
    val getUpcomingTerms: GetUpcomingTermsUseCase,
    val createTerm: CreateTermUseCase,
    val updateTerm: UpdateTermUseCase,
    val deleteTerm: DeleteTermUseCase,
    
    // ============================================
    // QUICK SCAN (1 Use Case)
    // ============================================
    val quickScan: QuickScanUseCase
)