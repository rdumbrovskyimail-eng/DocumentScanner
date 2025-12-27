package com.docs.scanner.domain.usecase.record

import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.repository.RecordRepository
import javax.inject.Inject

/**
 * Get record by ID.
 * 
 * Session 6 addition: Required by EditorViewModel.
 */
class GetRecordByIdUseCase @Inject constructor(
    private val recordRepository: RecordRepository
) {
    suspend operator fun invoke(recordId: Long): Record? {
        return recordRepository.getRecordById(recordId)
    }
}