package com.docs.scanner.domain.usecase.record

import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.RecordRepository
import javax.inject.Inject

/**
 * Delete a record by ID.
 * 
 * Note: This will also delete all documents in the record
 * due to CASCADE constraints in the database.
 */
class DeleteRecordUseCase @Inject constructor(
    private val recordRepository: RecordRepository
) {
    suspend operator fun invoke(recordId: Long): Result<Unit> {
        if (recordId <= 0) {
            return Result.Error(
                IllegalArgumentException("Invalid record ID: $recordId")
            )
        }
        
        return recordRepository.deleteRecord(recordId)
    }
}