package com.docs.scanner.domain.usecase.record

import com.docs.scanner.domain.model.Record
import com.docs.scanner.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Get all records in a folder with document counts.
 * Returns Flow for real-time updates.
 */
class GetRecordsUseCase @Inject constructor(
    private val recordRepository: RecordRepository
) {
    operator fun invoke(folderId: Long): Flow<List<Record>> {
        return recordRepository.getRecordsByFolder(folderId)
    }
}