package com.docs.scanner.domain.usecase

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

import javax.inject.Inject

/**
 * Container for all Use Cases.
 * 
 * ⚠️ TESTING: Only Folders and Records enabled
 */
class AllUseCases @Inject constructor(
    // ============================================
    // FOLDERS (5 Use Cases) ✅ ENABLED
    // ============================================
    val getFolders: GetFoldersUseCase,
    val getFolderById: GetFolderByIdUseCase,
    val createFolder: CreateFolderUseCase,
    val updateFolder: UpdateFolderUseCase,
    val deleteFolder: DeleteFolderUseCase,
    
    // ============================================
    // RECORDS (6 Use Cases) ✅ ENABLED
    // ============================================
    val getRecords: GetRecordsUseCase,
    val getRecordById: GetRecordByIdUseCase,
    val createRecord: CreateRecordUseCase,
    val updateRecord: UpdateRecordUseCase,
    val deleteRecord: DeleteRecordUseCase,
    val moveRecord: MoveRecordToFolderUseCase
)