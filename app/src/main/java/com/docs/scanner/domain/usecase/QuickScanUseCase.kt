package com.docs.scanner.domain.usecase

import android.content.Context
import android.net.Uri
import com.docs.scanner.R
import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.FolderRepository
import com.docs.scanner.domain.repository.RecordRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Quick scan with automatic folder/record creation.
 * 
 * Session 6 Fixes:
 * - ✅ Removed hardcoded "Quick Scans" (uses R.string.quick_scans)
 * - ✅ Flow states for progress
 * - ✅ Factory method for Record creation
 */
class QuickScanUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderRepository: FolderRepository,
    private val recordRepository: RecordRepository,
    private val addDocumentUseCase: AddDocumentUseCase
) {
    operator fun invoke(imageUri: Uri): Flow<QuickScanState> = flow {
        try {
            emit(QuickScanState.CreatingStructure(5, "Preparing..."))
            
            val folderName = context.getString(R.string.quick_scans)
            val folders = folderRepository.getAllFolders().first()

            val quickFolder = folders.firstOrNull { it.name == folderName }
                ?: run {
                    emit(QuickScanState.CreatingFolder(15, "Creating folder..."))
                    
                    when (val createResult = folderRepository.createFolder(
                        name = folderName,
                        description = context.getString(R.string.quick_scans_description)
                    )) {
                        is Result.Success -> {
                            folderRepository.getFolderById(createResult.data)
                                ?: throw Exception("Failed to get created folder")
                        }
                        is Result.Error -> {
                            emit(QuickScanState.Error(
                                "Failed to create folder: ${createResult.exception.message}"
                            ))
                            return@flow
                        }
                        is Result.Loading -> {
                            emit(QuickScanState.Error("Unexpected loading state"))
                            return@flow
                        }
                    }
                }

            emit(QuickScanState.CreatingRecord(25, "Creating record..."))
            
            val record = Record.createQuickScanRecord(quickFolder.id)
            
            val recordId = when (val recordResult = recordRepository.createRecord(
                folderId = record.folderId,
                name = record.name,
                description = record.description
            )) {
                is Result.Success -> recordResult.data
                is Result.Error -> {
                    emit(QuickScanState.Error(
                        "Failed to create record: ${recordResult.exception.message}"
                    ))
                    return@flow
                }
                is Result.Loading -> {
                    emit(QuickScanState.Error("Unexpected loading state"))
                    return@flow
                }
            }

            emit(QuickScanState.ScanningImage(35, "Scanning..."))
            
            addDocumentUseCase(recordId, imageUri).collect { addState ->
                when (addState) {
                    is AddDocumentState.Creating -> {
                        emit(QuickScanState.ScanningImage(40, addState.message))
                    }
                    is AddDocumentState.ProcessingOcr -> {
                        emit(QuickScanState.ProcessingOcr(
                            40 + (addState.progress - 40) / 2,
                            addState.message
                        ))
                    }
                    is AddDocumentState.Translating -> {
                        emit(QuickScanState.Translating(
                            55 + (addState.progress - 70) / 3,
                            addState.message
                        ))
                    }
                    is AddDocumentState.Success -> {
                        emit(QuickScanState.Success(recordId))
                    }
                    is AddDocumentState.Error -> {
                        emit(QuickScanState.Error(addState.message))
                    }
                }
            }
            
        } catch (e: Exception) {
            emit(QuickScanState.Error("Quick scan failed: ${e.message}"))
        }
    }
}

sealed class QuickScanState {
    data class CreatingStructure(val progress: Int, val message: String) : QuickScanState()
    data class CreatingFolder(val progress: Int, val message: String) : QuickScanState()
    data class CreatingRecord(val progress: Int, val message: String) : QuickScanState()
    data class ScanningImage(val progress: Int, val message: String) : QuickScanState()
    data class ProcessingOcr(val progress: Int, val message: String) : QuickScanState()
    data class Translating(val progress: Int, val message: String) : QuickScanState()
    data class Success(val recordId: Long) : QuickScanState()
    data class Error(val message: String) : QuickScanState()
}