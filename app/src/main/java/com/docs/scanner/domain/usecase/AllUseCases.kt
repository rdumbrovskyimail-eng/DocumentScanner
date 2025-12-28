package com.docs.scanner.domain.usecase

// ============================================
// DOCUMENT USE CASES
// ============================================
import com.docs.scanner.domain.usecase.document.CreateDocumentUseCase
import com.docs.scanner.domain.usecase.document.DeleteDocumentUseCase
import com.docs.scanner.domain.usecase.document.GetDocumentByIdUseCase
import com.docs.scanner.domain.usecase.document.GetDocumentsUseCase
import com.docs.scanner.domain.usecase.document.SearchDocumentsUseCase
import com.docs.scanner.domain.usecase.document.UpdateDocumentUseCase

// ============================================
// FOLDER USE CASES
// ============================================
import com.docs.scanner.domain.usecase.folder.CreateFolderUseCase
import com.docs.scanner.domain.usecase.folder.DeleteFolderUseCase
import com.docs.scanner.domain.usecase.folder.GetFolderByIdUseCase
import com.docs.scanner.domain.usecase.folder.GetFoldersUseCase
import com.docs.scanner.domain.usecase.folder.UpdateFolderUseCase

// ============================================
// RECORD USE CASES
// ============================================
import com.docs.scanner.domain.usecase.record.CreateRecordUseCase
import com.docs.scanner.domain.usecase.record.DeleteRecordUseCase
import com.docs.scanner.domain.usecase.record.GetRecordByIdUseCase
import com.docs.scanner.domain.usecase.record.GetRecordsUseCase
import com.docs.scanner.domain.usecase.record.MoveRecordToFolderUseCase
import com.docs.scanner.domain.usecase.record.UpdateRecordUseCase

// ============================================
// TERM USE CASES
// ============================================
import com.docs.scanner.domain.usecase.term.CreateTermUseCase
import com.docs.scanner.domain.usecase.term.DeleteTermUseCase
import com.docs.scanner.domain.usecase.term.GetCompletedTermsUseCase
import com.docs.scanner.domain.usecase.term.GetUpcomingTermsUseCase
import com.docs.scanner.domain.usecase.term.MarkTermCompletedUseCase
import com.docs.scanner.domain.usecase.term.UpdateTermUseCase

import javax.inject.Inject

/**
 * Container for all Use Cases.
 * 
 * Total: 28 Use Cases across 5 domains
 * 
 * ⚠️ IMPORTANT: Must be a regular class, not data class!
 * Data classes cause Dagger type resolution issues with @Inject constructors.
 */
class AllUseCases @Inject constructor(
    // ============================================
    // FOLDERS (5 Use Cases)
    // ============================================
    val getFolders: GetFoldersUseCase,
    val getFolderById: GetFolderByIdUseCase,
    val createFolder: CreateFolderUseCase,
    val updateFolder: UpdateFolderUseCase,
    val deleteFolder: DeleteFolderUseCase,
    
    // ============================================
    // RECORDS (6 Use Cases)
    // ============================================
    val getRecords: GetRecordsUseCase,
    val getRecordById: GetRecordByIdUseCase,
    val createRecord: CreateRecordUseCase,
    val updateRecord: UpdateRecordUseCase,
    val deleteRecord: DeleteRecordUseCase,
    val moveRecord: MoveRecordToFolderUseCase,
    
    // ============================================
    // DOCUMENTS (10 Use Cases)
    // ============================================
    val getDocuments: GetDocumentsUseCase,
    val getDocumentById: GetDocumentByIdUseCase,
    val addDocument: AddDocumentUseCase,
    val updateDocument: UpdateDocumentUseCase,
    val deleteDocument: DeleteDocumentUseCase,
    val searchDocuments: SearchDocumentsUseCase,
    val fixOcr: FixOcrUseCase,
    val retryTranslation: RetryTranslationUseCase,
    val batchOperations: BatchOperationsUseCase,
    
    // ============================================
    // TERMS (6 Use Cases)
    // ============================================
    val getUpcomingTerms: GetUpcomingTermsUseCase,
    val getCompletedTerms: GetCompletedTermsUseCase,
    val createTerm: CreateTermUseCase,
    val updateTerm: UpdateTermUseCase,
    val deleteTerm: DeleteTermUseCase,
    val markTermCompleted: MarkTermCompletedUseCase,
    
    // ============================================
    // QUICK SCAN (1 Use Case)
    // ============================================
    val quickScan: QuickScanUseCase
)