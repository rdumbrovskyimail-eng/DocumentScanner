package com.docs.scanner.domain.usecase.record

import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.FolderRepository
import com.docs.scanner.domain.repository.RecordRepository
import javax.inject.Inject

/**
 * Move record(s) to another folder.
 * 
 * Used by: RecordsViewModel, FoldersViewModel
 * 
 * Features:
 * - Move single record
 * - Move multiple records (batch)
 * - Validation (folder exists, not same folder)
 * - Update folder record counts
 * 
 * Usage:
 * ```
 * // Move single record
 * moveRecordToFolderUseCase(recordId = 123, targetFolderId = 456)
 * 
 * // Move multiple records
 * moveRecordToFolderUseCase.moveMultiple(
 *     recordIds = listOf(1, 2, 3),
 *     targetFolderId = 456
 * )
 * ```
 */
class MoveRecordToFolderUseCase @Inject constructor(
    private val recordRepository: RecordRepository,
    private val folderRepository: FolderRepository
) {
    /**
     * Move record to another folder.
     * 
     * @param recordId Record ID
     * @param targetFolderId Target folder ID
     * @return Result<Unit>
     */
    suspend operator fun invoke(
        recordId: Long,
        targetFolderId: Long
    ): Result<Unit> {
        return try {
            // Validation
            if (recordId <= 0) {
                return Result.Error(Exception("Invalid record ID"))
            }
            
            if (targetFolderId <= 0) {
                return Result.Error(Exception("Invalid target folder ID"))
            }
            
            // Check record exists
            val record = recordRepository.getRecordById(recordId)
            if (record == null) {
                return Result.Error(Exception("Record not found"))
            }
            
            // Check if already in target folder
            if (record.folderId == targetFolderId) {
                return Result.Error(Exception("Record is already in this folder"))
            }
            
            // Check target folder exists
            val targetFolder = folderRepository.getFolderById(targetFolderId)
            if (targetFolder == null) {
                return Result.Error(Exception("Target folder not found"))
            }
            
            // Move record
            recordRepository.moveRecord(recordId, targetFolderId)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to move record: ${e.message}", e))
        }
    }
    
    /**
     * Move multiple records to another folder (batch operation).
     * 
     * @param recordIds List of record IDs
     * @param targetFolderId Target folder ID
     * @return Result with count of successfully moved records
     */
    suspend fun moveMultiple(
        recordIds: List<Long>,
        targetFolderId: Long
    ): Result<Int> {
        if (recordIds.isEmpty()) {
            return Result.Success(0)
        }
        
        // Validate target folder once
        if (targetFolderId <= 0) {
            return Result.Error(Exception("Invalid target folder ID"))
        }
        
        val targetFolder = folderRepository.getFolderById(targetFolderId)
        if (targetFolder == null) {
            return Result.Error(Exception("Target folder not found"))
        }
        
        var successCount = 0
        val errors = mutableListOf<Exception>()
        
        recordIds.forEach { recordId ->
            when (invoke(recordId, targetFolderId)) {
                is Result.Success -> successCount++
                is Result.Error -> errors.add(Exception("Failed to move record $recordId"))
                else -> {}
            }
        }
        
        return when {
            errors.isEmpty() -> Result.Success(successCount)
            successCount == 0 -> Result.Error(Exception("All move operations failed"))
            else -> {
                println("⚠️ Partial success: moved $successCount/${recordIds.size} records")
                Result.Success(successCount)
            }
        }
    }
    
    /**
     * Swap folders of two records.
     * 
     * @param recordId1 First record ID
     * @param recordId2 Second record ID
     * @return Result<Unit>
     */
    suspend fun swapFolders(
        recordId1: Long,
        recordId2: Long
    ): Result<Unit> {
        return try {
            val record1 = recordRepository.getRecordById(recordId1)
                ?: return Result.Error(Exception("Record 1 not found"))
            
            val record2 = recordRepository.getRecordById(recordId2)
                ?: return Result.Error(Exception("Record 2 not found"))
            
            // Already in same folder?
            if (record1.folderId == record2.folderId) {
                return Result.Error(Exception("Records are already in the same folder"))
            }
            
            // Swap
            recordRepository.moveRecord(recordId1, record2.folderId)
            recordRepository.moveRecord(recordId2, record1.folderId)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to swap folders: ${e.message}", e))
        }
    }
}