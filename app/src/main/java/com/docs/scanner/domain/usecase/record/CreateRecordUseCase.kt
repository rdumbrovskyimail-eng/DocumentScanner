package com.docs.scanner.domain.usecase.record

import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.RecordRepository
import javax.inject.Inject

/**
 * Create a new record in a folder.
 * 
 * Validation:
 * - Folder ID must be valid (> 0)
 * - Name cannot be blank
 * - Name max length: 100 characters
 * 
 * @return Result.Success with record ID or Result.Error
 */
class CreateRecordUseCase @Inject constructor(
    private val recordRepository: RecordRepository
) {
    suspend operator fun invoke(
        folderId: Long, 
        name: String, 
        description: String?
    ): Result<Long> {
        if (folderId <= 0) {
            return Result.Error(
                IllegalArgumentException("Invalid folder ID: $folderId")
            )
        }
        
        if (name.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Record name cannot be empty")
            )
        }
        
        if (name.length > MAX_NAME_LENGTH) {
            return Result.Error(
                IllegalArgumentException("Record name too long (max $MAX_NAME_LENGTH characters)")
            )
        }
        
        return recordRepository.createRecord(
            folderId = folderId,
            name = name.trim(),
            description = description?.trim()
        )
    }
    
    companion object {
        private const val MAX_NAME_LENGTH = 100
    }
}