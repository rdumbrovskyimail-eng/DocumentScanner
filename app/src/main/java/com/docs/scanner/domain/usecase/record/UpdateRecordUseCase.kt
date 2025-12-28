package com.docs.scanner.domain.usecase.record

import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.RecordRepository
import javax.inject.Inject

/**
 * Update an existing record.
 * 
 * Validation:
 * - Record ID must be valid (> 0)
 * - Folder ID must be valid (> 0)
 * - Name cannot be blank
 * - Name max length: 100 characters
 */
class UpdateRecordUseCase @Inject constructor(
    private val recordRepository: RecordRepository
) {
    suspend operator fun invoke(record: Record): Result<Unit> {
        if (record.id <= 0) {
            return Result.Error(
                IllegalArgumentException("Invalid record ID: ${record.id}")
            )
        }
        
        if (record.folderId <= 0) {
            return Result.Error(
                IllegalArgumentException("Invalid folder ID: ${record.folderId}")
            )
        }
        
        if (record.name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Record name cannot be empty")
            )
        }
        
        if (record.name.length > MAX_NAME_LENGTH) {
            return Result.Error(
                IllegalArgumentException("Record name too long (max $MAX_NAME_LENGTH characters)")
            )
        }
        
        return recordRepository.updateRecord(record)
    }
    
    companion object {
        private const val MAX_NAME_LENGTH = 100
    }
}